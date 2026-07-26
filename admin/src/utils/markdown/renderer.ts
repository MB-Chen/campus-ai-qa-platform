import { marked, type Renderer } from 'marked';
import markedKatex from 'marked-katex-extension';
import type { Tokens } from 'marked';

/**
 * 聊天 markdown 渲染核心。
 *
 * 流式场景的设计要点：每收到一个 token，就对"当前累积的完整文本切片"整体重跑一次 marked.parse
 * （而非逐 token 增量解析）。这样 KaTeX 公式、表格等需要上下文的语法在流式过程中不会闪烁消失。
 *
 * 但半截 markdown（如只输出到一半的 ```代码块、`**加粗` 缺右边界）直接解析会产生闪烁：
 * 某一帧解析出 <hr>，下一帧又消失；某帧解析成 setext <h2>，下一帧又塌回普通文本。
 * 因此 streaming=true 时启用一组「流式容错 guards」，把不明确的尾部 markdown 暂时隐藏，
 * 等更多字符到达后再揭示，从而让流式渲染平滑无跳变。
 *
 * 移植自 WeKnora frontend（chatMarkdownRenderer.ts），裁剪了 citation（<kb/>/<web/>/wiki）系统
 * 和 prepareMarkdown/cachedMermaidSvg 注入钩子（xservice 不需要 agent 模式的 mermaid 中途缓存）。
 */

const STREAMING_IMAGE_PLACEHOLDER =
  '<span class="streaming-image-loading"><span class="streaming-image-loading__skeleton"></span></span>';

let markedConfigured = false;

export type ImageRendererArgs = {
  href: string;
  title: string | null;
  text: string;
};

export type ChatMarkdownRendererOptions = {
  codeRenderer?: Renderer['code'];
  imageRenderer?: (args: ImageRendererArgs) => string;
};

export type RenderChatMarkdownOptions = {
  renderer: Renderer;
  escapeMarkdown: (markdown: string) => string;
  sanitizeHtml: (html: string) => string;
  /** 源文本仍在增长，可能以歧义的半截 markdown 结尾。 */
  streaming?: boolean;
};

/**
 * 初始化 marked：breaks=true（单 \n 转 <br>，聊天风格）+ gfm=true（表格/删除线/任务列表/autolink）
 * + KaTeX 扩展（throwOnError=false，公式错误时原样显示而非抛异常）。
 */
export function configureMarkedForChatMarkdown(): void {
  if (markedConfigured) return;
  marked.use({ breaks: true, gfm: true });
  marked.use(markedKatex({ throwOnError: false, nonStandard: true }));
  markedConfigured = true;
}

/**
 * 数学定界符归一化：把 LaTeX 风格的 \[..\] → $$..$$、\(..\) → $..$，
 * 因为 marked-katex-extension 默认只认 $..$ / $$..$$。
 */
export function preprocessMathDelimiters(rawText: string): string {
  if (!rawText || typeof rawText !== 'string') return '';
  return rawText
    .replace(/\\\[([\s\S]*?)\\\]/g, '$$$$$1$$$$')
    .replace(/\\\(([\s\S]*?)\\\)/g, '$$$1$$');
}

/**
 * 流式时把半截图片 ![alt](url 替换为骨架占位，避免渲染出残缺的 ![] 文本一闪而过。
 */
export function replaceIncompleteImageWithPlaceholder(content: string): string {
  if (!content) return '';

  const lastImgStart = content.lastIndexOf('![');
  if (lastImgStart < 0) return content;

  const tail = content.slice(lastImgStart);
  const hasImageOpen = tail.startsWith('![');
  const hasBracketClose = tail.includes(']');
  const hasParenOpen = tail.includes('(');
  const hasParenClose = tail.includes(')');
  if (!hasImageOpen) return content;

  if (!hasBracketClose || (hasParenOpen && !hasParenClose)) {
    return content.slice(0, lastImgStart) + STREAMING_IMAGE_PLACEHOLDER;
  }
  return content;
}

/**
 * 流式时隐藏尾部的水平分割线候选（三个以上 - 或 * 或 _）。
 *
 * 模型常把 --- 作为表格分隔行的开头输出；在它单独出现的这一帧，marked 会把它渲染成 hr，
 * 下一帧更多字符到达后又消失。仅在流式启用，真正的水平线在内容完成后仍会渲染。
 */
export function stripTrailingStreamingHorizontalRule(content: string): string {
  if (!content) return content;
  return content.replace(
    /(^|\n)[ \t]{0,3}(?:(?:-[ \t]*){3,}|(?:\*[ \t]*){3,}|(?:_[ \t]*){3,})$/,
    '$1',
  );
}

function maskMatches(text: string, regex: RegExp): string {
  return text.replace(regex, (match) => match.replace(/[^\n]/g, '\u0000'));
}

function isOdd(text: string, marker: RegExp): boolean {
  const matches = text.match(marker);
  return matches ? matches.length % 2 === 1 : false;
}

/**
 * 流式时稳定悬空的行内标记符（双星号、单星号、双波浪号、反引号）。
 *
 * 两种中途瑕疵被抹平：
 *  - 后面**还没有内容**的标记串（如裸尾随 **），歧义且无法渲染 → 隐藏直到有真实内容跟随。
 *  - 后面**有内容**但缺右边界（如 **平台地址：…），是真正开启的强调 → 补上右边界，
 *    使 marked 从第一帧就渲染成加粗，而非先显示裸 ** 再突然变粗（含布局跳动）。
 *
 * 行内/围栏代码与列表 bullet 内的 * 会被屏蔽，避免被误计为强调。
 */
export function closeDanglingStreamingEmphasis(text: string): string {
  if (!text || !/[*~`]/.test(text)) return text;

  // 屏蔽代码，使其内部标记符不被计数；\u0000 保持偏移量不变。
  let masked = maskMatches(text, /```[\s\S]*?```/g);
  if (isOdd(masked, /```/g)) {
    const open = masked.lastIndexOf('```');
    masked = masked.slice(0, open) + masked.slice(open).replace(/[^\n]/g, '\u0000');
  }
  masked = maskMatches(masked, /`[^`\n]*`/g);
  let appendInlineCode = false;
  if (isOdd(masked, /`/g)) {
    appendInlineCode = true;
    const open = masked.lastIndexOf('`');
    masked = masked.slice(0, open) + masked.slice(open).replace(/[^\n]/g, '\u0000');
  }

  // 丢掉后面无内容的尾随标记串：歧义且尚不可渲染。锚定到当前行，前面的文本不受影响。
  let working = text;
  if (!appendInlineCode) {
    const trailing = masked.match(/[*~]+[ \t]*$/);
    if (trailing) {
      working = text.slice(0, text.length - trailing[0].length);
      masked = masked.slice(0, masked.length - trailing[0].length);
    }
  }

  // 行首列表/bullet 标记是结构，不是强调。
  masked = masked.replace(/^(\s*)([*+-])(\s)/gm, (_m, p1, _p2, p3) => `${p1}\u0000${p3}`);

  const needStrike = isOdd(masked, /~~/g);
  const withoutBold = maskMatches(masked, /\*\*/g);
  const needBold = isOdd(masked, /\*\*/g);
  const needItalic = isOdd(withoutBold, /\*/g);

  let closers = '';
  if (appendInlineCode) closers += '`';
  if (needItalic) closers += '*';
  if (needBold) closers += '**';
  if (needStrike) closers += '~~';

  if (!closers) return working;

  // CommonMark 闭合定界符前不能是空白，所以把 closers 插在尾随空白之前。
  const trailingWs = working.match(/\s+$/);
  if (!trailingWs) return working + closers;
  const cut = working.length - trailingWs[0].length;
  return working.slice(0, cut) + closers + working.slice(cut);
}

// CommonMark flanking 规则会拒绝「前置标点紧跟字母数字」的闭合 **，或「字母数字后跟标点」的开启 **，
// 导致 `**中文（…）**` 这类模型常用模式渲染成裸星号。下面把这些被拒模式直接转成显式 HTML。
const FLANKING_BOLD = /(?<!\*)\*\*(?=\S)([^*\n]*?\p{P})\*\*(?=[\p{L}\p{N}])/gu;
const FLANKING_STRIKE = /(?<!~)~~(?=\S)([^~\n]*?\p{P})~~(?=[\p{L}\p{N}])/gu;
const FLANKING_ITALIC = /(?<![*\p{L}\p{N}])\*(?=\S)([^*\n]*?\p{P})\*(?=[\p{L}\p{N}])/gu;
const FLANKING_BOLD_OPEN = /(?<=[\p{L}\p{N}])\*\*(?=\p{P})([^*\n]+?)\*\*/gu;
const FLANKING_STRIKE_OPEN = /(?<=[\p{L}\p{N}])~~(?=\p{P})([^~\n]+?)~~/gu;

function repairFlankingEmphasisSegment(segment: string): string {
  return segment
    .replace(FLANKING_BOLD, '<strong>$1</strong>')
    .replace(FLANKING_BOLD_OPEN, '<strong>$1</strong>')
    .replace(FLANKING_STRIKE, '<del>$1</del>')
    .replace(FLANKING_STRIKE_OPEN, '<del>$1</del>')
    .replace(FLANKING_ITALIC, '<em>$1</em>');
}

/**
 * 渲染模型明显想要的、但 CommonMark 拒绝解析的强调。
 * 跳过 code span/fence，使其内部字面标记符不受影响。
 */
export function repairFlankingEmphasis(text: string): string {
  if (!text || !/[*~]/.test(text)) return text;
  const parts = text.split(/(```[\s\S]*?```|`[^`\n]*`)/g);
  for (let i = 0; i < parts.length; i += 2) {
    parts[i] = repairFlankingEmphasisSegment(parts[i]);
  }
  return parts.join('');
}

/**
 * 流式时隐藏尾部还没内容的列表/下划线标记。
 *
 * 流式过程中，嵌套 bullet 的 - 先于其文本到达时（`1. **AAA**\n   - `），marked 会把孤立的 -
 * 读成 setext 下划线，把上一行渲染成巨大的 <h2>，等 bullet 内容流进来又塌回粗体——巨大布局跳动。
 * 有序列号无内容（`text\n1. `）和裸 setext 下划线（==/--）同理。丢掉悬空标记行即可推迟一拍。
 */
export function stripTrailingStreamingListMarker(text: string): string {
  if (!text) return text;
  return text.replace(/(^|\n)[ \t]*(?:[-*+]|[-=]{2,}|\d{1,9}[.)]?)[ \t]*$/, '$1');
}

const TAIL_TEXT_RE = />([^<>]+)</g;

/**
 * 给最新流式字符加尾部渐隐：把尾部文本包进 stream-fade-tail span，CSS 做右边缘渐隐。
 * 在最终 HTML 上执行，保证 span 不被 markdown 解析器/消毒器触碰；仅包裹最内层的最后一段文本。
 */
export function applyStreamingTailFade(html: string, tailLength = 24): string {
  if (!html) return html;

  // 追踪最后一个真正有可见字符的文本段，跳过纯空白段。
  let lastMatch: RegExpExecArray | null = null;
  let match: RegExpExecArray | null;
  TAIL_TEXT_RE.lastIndex = 0;
  while ((match = TAIL_TEXT_RE.exec(html)) !== null) {
    if (match[1].trim()) lastMatch = match;
  }
  if (!lastMatch) return html;

  const text = lastMatch[1];
  const textStart = lastMatch.index + 1;
  const textEnd = textStart + text.length;
  const chars = Array.from(text);
  const tailChars = chars.slice(Math.max(0, chars.length - tailLength));
  const tail = tailChars.join('');
  const tailTrimmed = tail.replace(/^\s+/, '');
  if (!tailTrimmed) return html;
  const head = text.slice(0, text.length - tailTrimmed.length);
  const wrapped = `${head}<span class="stream-fade-tail">${tailTrimmed}</span>`;
  return html.slice(0, textStart) + wrapped + html.slice(textEnd);
}

/**
 * 创建带自定义 code/image renderer 的 marked Renderer。
 */
export function createChatMarkdownRenderer(options: ChatMarkdownRendererOptions = {}): Renderer {
  const renderer = new marked.Renderer();

  if (options.imageRenderer) {
    renderer.image = ({ href, title, text }: Tokens.Image) => {
      const imageHref = href || '';
      return options.imageRenderer?.({
        href: imageHref,
        title: title || null,
        text: text || '',
      }) ?? '';
    };
  }

  if (options.codeRenderer) {
    renderer.code = options.codeRenderer;
  }

  return renderer;
}

/**
 * 把每个 <table> 包进横向滚动容器 .chat-markdown-table。
 */
export function wrapChatMarkdownTables(html: string): string {
  if (!html || !html.includes('<table')) return html;
  return html.replace(
    /<table\b[\s\S]*?<\/table>/gi,
    (tableHtml) => `<div class="chat-markdown-table">${tableHtml}</div>`,
  );
}

const STANDALONE_STRONG_PARAGRAPH_RE = /<p>\s*<strong>((?:(?!<\/strong>)[\s\S])*?)<\/strong>\s*<\/p>/g;

/**
 * 把整段内容是单个粗体 run 的段落（模型常发 **小节标题：** 而非真正的标题）标记为副标题类。
 *
 * 替代 CSS `p:has(> strong:only-child)` 启发式：后者在流式中不稳定（:only-child 忽略文本节点，
 * 普通段落只要恰好包含一个已闭合的 **粗体** run 就会被命中，等第二个粗体 run 闭合又失去——可见的间距跳变）。
 */
export function markStandaloneStrongParagraphs(html: string): string {
  if (!html || !html.includes('<strong>')) return html;
  return html.replace(
    STANDALONE_STRONG_PARAGRAPH_RE,
    (match: string, inner: string, offset: number, full: string) => {
      // 列表项内的 <p> 是列表文本，不是独立副标题。
      if (/<li>\s*$/.test(full.slice(0, offset))) return match;
      return `<p class="md-strong-title"><strong>${inner}</strong></p>`;
    },
  );
}

/**
 * 单次同步 marked.parse 全流程。流式/非流式共用此入口，streaming=true 时启用容错 guards。
 */
export function renderChatMarkdown(rawMarkdown: unknown, options: RenderChatMarkdownOptions): string {
  const rawText = typeof rawMarkdown === 'string' ? rawMarkdown : String(rawMarkdown || '');
  if (!rawText.trim()) return '';

  configureMarkedForChatMarkdown();

  // 1. 流式尾部 guard（水平线 / 列表标记）
  const streamingSafeText = options.streaming
    ? stripTrailingStreamingListMarker(stripTrailingStreamingHorizontalRule(rawText))
    : rawText;
  // 2. 半截图片占位
  const imageSafe = replaceIncompleteImageWithPlaceholder(streamingSafeText);
  // 3. 数学定界符归一化
  const mathSafe = preprocessMathDelimiters(imageSafe);
  // 4. 悬空强调闭合 / 再跑一次列表标记 guard（强调闭合可能重新暴露裸 bullet）
  const balancedInline = options.streaming
    ? stripTrailingStreamingListMarker(closeDanglingStreamingEmphasis(mathSafe))
    : mathSafe;
  // 5. CommonMark flanking 修复
  const flankingSafeMarkdown = repairFlankingEmphasis(balancedInline);
  // 6. marked 解析（同步，带自定义 renderer）
  const html = marked.parse(flankingSafeMarkdown, {
    renderer: options.renderer,
    breaks: true,
    async: false,
  }) as string;
  // 7. 后处理：表格包滚动容器 / 独立粗体标副标题
  const tableWrappedHtml = wrapChatMarkdownTables(html);
  const strongTitleHtml = markStandaloneStrongParagraphs(tableWrappedHtml);
  // 8. DOMPurify 消毒
  const sanitized = options.sanitizeHtml(strongTitleHtml);
  // 9. 流式尾部渐隐
  return options.streaming ? applyStreamingTailFade(sanitized) : sanitized;
}
