import hljs from 'highlight.js';

/**
 * 代码块 / mermaid 块的 DOM 增强（挂载后幂等执行）。
 *
 * marked 的 code renderer 会在解析阶段产出带语言标签+复制按钮的代码块 HTML；
 * 但有些场景（防御性二次高亮、按钮可用态同步、点击事件绑定）需要在 DOM 挂载后处理。
 *
 * 移植自 WeKnora frontend，去掉了 i18n 依赖（文案硬编码中文）和 mermaidViewer 的放大交互。
 */
const ENHANCE_FLAG = 'data-markdown-enhancements';
const boundMarkdownRoots = new WeakSet<HTMLElement>();

const MERMAID_DIAGRAM_SVG_SELECTOR = '.chat-mermaid-block__canvas svg, pre.mermaid svg';

function getMermaidDiagramSvg(block: Element | null | undefined): SVGElement | null {
  if (!block) return null;
  const svg = block.querySelector(MERMAID_DIAGRAM_SVG_SELECTOR);
  return svg instanceof SVGElement ? svg : null;
}

function openMermaidFromBlock(block: Element | null | undefined): void {
  const svg = getMermaidDiagramSvg(block);
  if (svg) openMermaidFullscreen(svg.outerHTML);
}

const COPY_ICON =
  '<svg class="chat-code-block__copy-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>';

const EXPAND_ICON =
  '<svg class="chat-mermaid-block__expand-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M8 3H5a2 2 0 0 0-2 2v3"/><path d="M21 8V5a2 2 0 0 0-2-2h-3"/><path d="M3 16v3a2 2 0 0 0 2 2h3"/><path d="M16 21h3a2 2 0 0 0 2-2v-3"/></svg>';

const LANG_LABELS: Record<string, string> = {
  js: 'JavaScript', javascript: 'JavaScript',
  ts: 'TypeScript', typescript: 'TypeScript',
  py: 'Python', python: 'Python',
  go: 'Go', rust: 'Rust', java: 'Java', kotlin: 'Kotlin', swift: 'Swift',
  rb: 'Ruby', ruby: 'Ruby', php: 'PHP',
  cs: 'C#', cpp: 'C++', 'c++': 'C++', c: 'C',
  sql: 'SQL', bash: 'Bash', sh: 'Shell', shell: 'Shell', zsh: 'Shell',
  json: 'JSON', yaml: 'YAML', yml: 'YAML', toml: 'TOML',
  xml: 'XML', html: 'HTML', css: 'CSS', scss: 'SCSS', less: 'LESS',
  markdown: 'Markdown', md: 'Markdown',
  dockerfile: 'Dockerfile', makefile: 'Makefile', graphql: 'GraphQL',
  vue: 'Vue', tsx: 'TSX', jsx: 'JSX',
};

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export function formatCodeLang(lang: string): string {
  const normalized = (lang || 'Code').trim();
  if (!normalized) return 'Code';
  const key = normalized.toLowerCase();
  return LANG_LABELS[key] || normalized.charAt(0).toUpperCase() + normalized.slice(1);
}

export function buildCodeBlockHtml(lang: string, highlighted: string, highlightLang: string): string {
  const displayLang = escapeHtml(formatCodeLang(lang));
  const copyLabel = escapeHtml('复制代码');
  const safeLang = escapeHtml(highlightLang || lang || 'text');
  return `<div class="chat-code-block">
    <div class="chat-code-block__header">
      <span class="chat-code-block__lang">${displayLang}</span>
      <div class="chat-code-block__actions">
        <button type="button" class="chat-code-block__copy" aria-label="${copyLabel}" title="${copyLabel}">
          ${COPY_ICON}<span class="chat-code-block__copy-text">${copyLabel}</span>
        </button>
      </div>
    </div>
    <pre class="chat-code-block__pre"><code class="hljs language-${safeLang}">${highlighted}</code></pre>
  </div>`;
}

export function buildMermaidBlockHtml(innerHtml: string, preAttrs = ''): string {
  const label = escapeHtml('图表');
  const expandLabel = escapeHtml('放大');
  const attrs = preAttrs ? ` ${preAttrs}` : '';
  return `<div class="chat-mermaid-block">
    <div class="chat-mermaid-block__header">
      <span class="chat-mermaid-block__badge">${label}</span>
      <div class="chat-mermaid-block__actions">
        <button type="button" class="chat-mermaid-block__expand" aria-label="${expandLabel}" title="${expandLabel}">${EXPAND_ICON}</button>
      </div>
    </div>
    <pre class="chat-mermaid-block__canvas mermaid"${attrs}>${innerHtml}</pre>
  </div>`;
}

export function buildMermaidLoadingHtml(): string {
  const label = escapeHtml('图表');
  return `<div class="chat-mermaid-block chat-mermaid-block--loading">
    <div class="chat-mermaid-block__header">
      <span class="chat-mermaid-block__badge">${label}</span>
    </div>
    <div class="streaming-mermaid-loading" aria-hidden="true"><span class="streaming-mermaid-loading__skeleton"></span></div>
  </div>`;
}

async function handleCodeCopy(btn: HTMLButtonElement): Promise<void> {
  const code = btn.closest('.chat-code-block')?.querySelector('code')?.textContent ?? '';
  if (!code) return;

  const textEl = btn.querySelector<HTMLElement>('.chat-code-block__copy-text');
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(code);
    } else {
      const textArea = document.createElement('textarea');
      textArea.value = code;
      textArea.style.position = 'fixed';
      textArea.style.opacity = '0';
      document.body.appendChild(textArea);
      textArea.select();
      document.execCommand('copy');
      document.body.removeChild(textArea);
    }
    btn.classList.add('is-copied');
    if (textEl) textEl.textContent = '已复制';
    window.setTimeout(() => {
      btn.classList.remove('is-copied');
      if (textEl) textEl.textContent = '复制代码';
    }, 1600);
  } catch {
    btn.classList.add('is-error');
    window.setTimeout(() => btn.classList.remove('is-error'), 1600);
  }
}

/**
 * 防御性二次高亮：对容器内尚未高亮的代码块补一次 highlight.js。
 * 正常流程里 code renderer 已经高亮过了，这里只兜底遗漏的。
 */
export function highlightCodeBlocksInContainer(root: HTMLElement | null | undefined): void {
  if (!root) return;
  root.querySelectorAll<HTMLElement>('.chat-code-block__pre code').forEach((codeEl) => {
    if (codeEl.querySelector('span[class^="hljs-"], span.hljs')) return;
    const language = [...codeEl.classList]
      .find((cls) => cls.startsWith('language-'))
      ?.slice('language-'.length);
    if (language && hljs.getLanguage(language)) {
      try {
        codeEl.innerHTML = hljs.highlight(codeEl.textContent || '', { language }).value;
        codeEl.classList.add('hljs');
        return;
      } catch {
        // fall through
      }
    }
    hljs.highlightElement(codeEl);
  });
}

/**
 * 同步 mermaid 放大按钮的可用状态（SVG 渲染完成后才可点）。
 */
export function syncMermaidExpandButtons(root: HTMLElement | null | undefined): void {
  if (!root) return;
  root.querySelectorAll<HTMLElement>('.chat-mermaid-block').forEach((block) => {
    const expandBtn = block.querySelector<HTMLButtonElement>('.chat-mermaid-block__expand');
    if (!expandBtn) return;
    const hasSvg = !!getMermaidDiagramSvg(block);
    expandBtn.disabled = !hasSvg;
    expandBtn.classList.toggle('is-disabled', !hasSvg);
  });
}

/**
 * 幂等的 DOM 增强刷新（每次组件更新后调用）。
 */
export function refreshMarkdownEnhancements(root: HTMLElement | null | undefined): void {
  if (!root) return;
  highlightCodeBlocksInContainer(root);
  syncMermaidExpandButtons(root);
}

/**
 * 绑定容器内的点击事件（复制代码 / 放大 mermaid），capture 阶段委托，仅绑一次。
 */
export function attachMarkdownEnhancementListeners(root: HTMLElement | null | undefined): void {
  if (!root || boundMarkdownRoots.has(root)) return;
  boundMarkdownRoots.add(root);
  root.setAttribute(ENHANCE_FLAG, 'true');

  const onClick = (event: Event) => {
    const target = event.target as HTMLElement;

    const copyBtn = target.closest<HTMLButtonElement>('.chat-code-block__copy');
    if (copyBtn) {
      event.preventDefault();
      event.stopPropagation();
      void handleCodeCopy(copyBtn);
      return;
    }

    const expandBtn = target.closest<HTMLButtonElement>('.chat-mermaid-block__expand');
    if (expandBtn) {
      event.preventDefault();
      event.stopPropagation();
      if (expandBtn.disabled) return;
      openMermaidFromBlock(expandBtn.closest('.chat-mermaid-block'));
      return;
    }

    const canvas = target.closest<HTMLElement>(
      '.chat-mermaid-block__canvas[data-mermaid="true"], pre.mermaid[data-mermaid="true"]',
    );
    if (canvas && !target.closest('button')) {
      const svg = canvas.querySelector('svg');
      if (svg) {
        event.preventDefault();
        event.stopPropagation();
        openMermaidFullscreen(svg.outerHTML);
      }
    }
  };

  root.addEventListener('click', onClick, true);
  syncMermaidExpandButtons(root);
}

// ---- 简化版 mermaid 全屏查看器（缩放 + 关闭，无 PNG 导出） ----

let fullscreenOverlay: HTMLElement | null = null;
let currentScale = 1;

export function openMermaidFullscreen(svgHtml: string): void {
  if (!svgHtml) return;
  closeMermaidFullscreen();

  const overlay = document.createElement('div');
  overlay.className = 'chat-mermaid-fullscreen';
  overlay.innerHTML = `
    <div class="chat-mermaid-fullscreen__bar">
      <span class="chat-mermaid-fullscreen__title">图表</span>
      <div class="chat-mermaid-fullscreen__actions">
        <button type="button" class="chat-mermaid-fullscreen__btn" data-action="zoom-out" title="缩小">−</button>
        <button type="button" class="chat-mermaid-fullscreen__btn" data-action="zoom-in" title="放大">+</button>
        <button type="button" class="chat-mermaid-fullscreen__btn" data-action="reset" title="重置">重置</button>
        <button type="button" class="chat-mermaid-fullscreen__btn" data-action="close" title="关闭">✕</button>
      </div>
    </div>
    <div class="chat-mermaid-fullscreen__viewport">
      <div class="chat-mermaid-fullscreen__inner">${svgHtml}</div>
    </div>
  `;
  document.body.appendChild(overlay);
  fullscreenOverlay = overlay;
  currentScale = 1;

  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'Escape') closeMermaidFullscreen();
  };
  const onBarClick = (e: MouseEvent) => {
    const action = (e.target as HTMLElement).closest('[data-action]')?.getAttribute('data-action');
    if (action === 'close') closeMermaidFullscreen();
    else if (action === 'zoom-in') setMermaidZoom(currentScale + 0.2);
    else if (action === 'zoom-out') setMermaidZoom(currentScale - 0.2);
    else if (action === 'reset') setMermaidZoom(1);
  };
  const onOverlayClick = (e: MouseEvent) => {
    if (e.target === overlay || (e.target as HTMLElement).classList.contains('chat-mermaid-fullscreen__viewport')) {
      closeMermaidFullscreen();
    }
  };

  overlay.addEventListener('click', onBarClick);
  overlay.addEventListener('click', onOverlayClick);
  document.addEventListener('keydown', onKey);
  overlay.dataset.cleanup = '';
  (overlay as any).__cleanup = () => {
    overlay.removeEventListener('click', onBarClick);
    overlay.removeEventListener('click', onOverlayClick);
    document.removeEventListener('keydown', onKey);
  };
}

function setMermaidZoom(scale: number): void {
  if (!fullscreenOverlay) return;
  currentScale = Math.min(4, Math.max(0.3, Number(scale.toFixed(2))));
  const inner = fullscreenOverlay.querySelector<HTMLElement>('.chat-mermaid-fullscreen__inner');
  if (inner) inner.style.transform = `scale(${currentScale})`;
}

export function closeMermaidFullscreen(): void {
  if (!fullscreenOverlay) return;
  const cleanup = (fullscreenOverlay as any).__cleanup;
  if (typeof cleanup === 'function') cleanup();
  fullscreenOverlay.remove();
  fullscreenOverlay = null;
}
