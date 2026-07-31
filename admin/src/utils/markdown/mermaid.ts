import type { Tokens } from 'marked';
import hljs from 'highlight.js';
import {
  buildCodeBlockHtml,
  buildMermaidBlockHtml,
} from './enhancements';

/**
 * Mermaid 渲染子系统。
 *
 * - createMermaidCodeRenderer：marked 的 code renderer，mermaid 语言产出占位块（data-mermaid="false"），
 *   其它语言正常高亮。流式期间只占位，不调 mermaid.render（避免半截代码块报错 + 性能）。
 * - renderMermaidInContainer：流式结束后调用，遍历占位块，动态 import('mermaid') 并 render 成 SVG。
 *
 * 移植自 WeKnora frontend，裁剪了暗色主题（SparkX admin 当前为亮色）。
 */
hljs.registerAliases('mermaid', { languageName: 'plaintext' });

let mermaidMod: typeof import('mermaid') | null = null;
let mermaidInitialized = false;
let initPromise: Promise<void> | null = null;

const MERMAID_THEME_VARIABLES = {
  darkMode: false,
  background: '#ffffff',
  primaryColor: '#e2e8f0',
  primaryTextColor: '#334155',
  primaryBorderColor: '#94a3b8',
  secondaryColor: '#f1f5f9',
  secondaryTextColor: '#475569',
  secondaryBorderColor: '#cbd5e1',
  tertiaryColor: '#f8fafc',
  tertiaryTextColor: '#64748b',
  tertiaryBorderColor: '#e2e8f0',
  lineColor: '#94a3b8',
  textColor: '#334155',
  mainBkg: '#ffffff',
  nodeBorder: '#94a3b8',
  clusterBkg: '#f8fafc',
  clusterBorder: '#cbd5e1',
  titleColor: '#1e293b',
  edgeLabelBackground: '#ffffff',
  actorBorder: '#94a3b8',
  actorBkg: '#f1f5f9',
  actorTextColor: '#334155',
  actorLineColor: '#94a3b8',
  signalColor: '#94a3b8',
  labelBoxBkgColor: '#f1f5f9',
  labelBoxBorderColor: '#cbd5e1',
  labelTextColor: '#334155',
  loopTextColor: '#475569',
  noteBkgColor: '#f8fafc',
  noteTextColor: '#475569',
  noteBorderColor: '#cbd5e1',
  sectionBkgColor: '#f8fafc',
  altSectionBkgColor: '#ffffff',
  gridColor: '#e2e8f0',
  todayLineColor: '#64748b',
  taskBorderColor: '#94a3b8',
  taskBkgColor: '#e2e8f0',
  activeTaskBorderColor: '#64748b',
  activeTaskBkgColor: '#94a3b8',
  doneTaskBkgColor: '#cbd5e1',
  doneTaskBorderColor: '#94a3b8',
  critBkgColor: '#fecaca',
  critBorderColor: '#f87171',
  fontSize: '14px',
};

const MERMAID_CONFIG = {
  startOnLoad: false,
  theme: 'base' as const,
  securityLevel: 'strict' as const,
  fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif',
  flowchart: { useMaxWidth: true, htmlLabels: true, curve: 'basis', padding: 16 },
  sequence: { useMaxWidth: true, diagramMarginX: 12, diagramMarginY: 12, actorMargin: 56, width: 156, height: 68, boxMargin: 10 },
  gantt: { useMaxWidth: true, leftPadding: 80, gridLineStartPadding: 40, barHeight: 22, barGap: 6, topPadding: 56 },
  er: { useMaxWidth: true },
  journey: { useMaxWidth: true },
};

async function getMermaid() {
  if (!mermaidMod) {
    mermaidMod = await import('mermaid');
  }
  return mermaidMod.default;
}

export const ensureMermaidInitialized = (): Promise<void> => {
  if (!initPromise) {
    initPromise = (async () => {
      const mermaid = await getMermaid();
      if (!mermaidInitialized) {
        mermaid.initialize({
          ...MERMAID_CONFIG,
          themeVariables: MERMAID_THEME_VARIABLES,
        } as Parameters<typeof mermaid.initialize>[0]);
        mermaidInitialized = true;
      }
    })();
  }
  return initPromise;
};

function highlightCode(text: string, lang?: string | null) {
  const language = (lang || '').trim();
  if (language && language !== 'mermaid' && hljs.getLanguage(language)) {
    try {
      const result = hljs.highlight(text, { language });
      return { html: result.value, language: result.language || language };
    } catch {
      // fall through
    }
  }
  if (language === 'mermaid') {
    // mermaid 代码不高亮，原样转义返回。
    const escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return { html: escaped, language: 'mermaid' };
  }
  const auto = hljs.highlightAuto(text, language ? [language] : undefined);
  return { html: auto.value, language: auto.language || language || 'plaintext' };
}

let mermaidCount = 0;

/**
 * marked 的 code renderer：mermaid 出占位块（流式结束后再渲染成 SVG），其它语言高亮。
 */
export const createMermaidCodeRenderer = (idPrefix: string) => {
  return ({ text, lang }: Tokens.Code) => {
    const { html: highlighted, language: highlightLang } = highlightCode(text, lang);
    if (lang === 'mermaid') {
      const id = `${idPrefix}-${++mermaidCount}`;
      const inner = `<code class="hljs language-${highlightLang}">${highlighted}</code>`;
      return buildMermaidBlockHtml(inner, `id="${id}" data-mermaid="false"`);
    }
    return buildCodeBlockHtml(lang || highlightLang, highlighted, highlightLang);
  };
};

/**
 * 容器内把所有 data-mermaid="false" 的占位块渲染成 SVG。
 * 仅在流式结束后调用（半截 mermaid 代码会 parse 失败）。
 */
export const renderMermaidInContainer = async (rootElement: HTMLElement | null | undefined): Promise<void> => {
  if (!rootElement) return;

  const mermaid = await getMermaid();
  await ensureMermaidInitialized();

  const mermaidElements = rootElement.querySelectorAll<HTMLElement>(
    'pre[data-mermaid="false"], .chat-mermaid-block__canvas[data-mermaid="false"]',
  );
  for (const el of mermaidElements) {
    try {
      const code = el.innerText;
      await mermaid.parse(code);
      const renderId = el.id ? `${el.id}-svg` : `mermaid-render-${++mermaidCount}`;
      const { svg } = await mermaid.render(renderId, code);
      el.classList.add('mermaid');
      el.innerHTML = svg;
      el.setAttribute('data-mermaid', 'true');
    } catch (e) {
      // 解析/渲染失败（通常是代码不完整），静默跳过，保留原始代码块。
      console.error('Mermaid rendering error:', e);
      continue;
    }
  }
};
