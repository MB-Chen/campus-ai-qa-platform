import DOMPurify from 'dompurify';

/**
 * 聊天 markdown 的 DOMPurify 安全配置。
 *
 * markdown 经 marked 解析后、注入 DOM 前必须过一遍消毒，防御 XSS：
 *  - 移除 script/style/object/embed/form/input 等危险标签
 *  - 移除 onerror/onload/onclick 等事件属性
 *  - 白名单协议（http/https/mailto/blob 等），拦截 javascript: 等
 *  - 外链 <a> 加 rel=noopener noreferrer + target=_blank
 *
 * 白名单标签/属性同时覆盖 KaTeX 的 MathML 输出（math/mi/mo/mn/mfrac/msup...）
 * 和 mermaid 渲染出的 SVG（svg/g/path/rect/...），保证公式与图表不被误删。
 *
 * 移植自 WeKnora frontend，按 dompurify v3 API 调整（hooks 用 afterSanitizeAttributes）。
 */
const FORBID_TAGS = ['script', 'style', 'object', 'embed', 'form', 'input', 'iframe', 'frame', 'frameset', 'base'];
const FORBID_ATTR = ['onerror', 'onload', 'onclick', 'onmouseover', 'onmouseout', 'onmouseenter', 'onmouseleave', 'onfocus', 'onblur', 'onchange', 'onsubmit', 'onreset', 'onkeydown', 'onkeyup', 'onkeypress', 'ontoggle', 'onanimationstart'];

const ALLOWED_URI_REGEXP =
  /^(?:(?:(?:f|ht)tps?|mailto|tel|callto|cid|xmpp|blob|data:image\/):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i;

const ALLOWED_TAGS = [
  'p', 'br', 'strong', 'em', 'u', 'del', 's', 'code', 'pre', 'ul', 'ol', 'li', 'blockquote', 'hr',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'a', 'span', 'div', 'sub', 'sup', 'mark', 'abbr', 'kbd',
  'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'caption', 'colgroup', 'col',
  'img', 'figure', 'figcaption',
  // mermaid SVG 输出
  'svg', 'g', 'path', 'rect', 'circle', 'ellipse', 'line', 'polygon', 'polyline', 'text', 'tspan',
  'defs', 'marker', 'filter', 'use', 'clippath', 'lineargradient', 'radialgradient', 'stop',
  'pattern', 'image', 'foreignobject', 'desc', 'title', 'switch', 'symbol', 'mask',
  // KaTeX MathML 输出
  'math', 'annotation', 'semantics', 'mo', 'mi', 'mn', 'msup', 'msub', 'msubsup', 'mrow', 'mfrac',
  'msqrt', 'mroot', 'mstyle', 'mtable', 'mtr', 'mtd', 'mtext', 'mspace', 'mfenced', 'menclose',
  // 复制按钮、放大按钮
  'button',
];

const ALLOWED_ATTR = [
  'href', 'title', 'target', 'rel', 'class', 'role', 'tabindex', 'src', 'alt', 'colspan', 'rowspan',
  'width', 'height', 'style', 'id', 'type', 'aria-label', 'aria-hidden', 'disabled',
  'align', 'valign',
  'data-mermaid', 'data-tooltip', 'data-lang',
  // SVG 属性
  'd', 'fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin',
  'stroke-dasharray', 'stroke-dashoffset', 'stroke-miterlimit', 'stroke-opacity',
  'fill-opacity', 'opacity', 'transform', 'viewbox', 'preserveaspectratio',
  'x', 'y', 'x1', 'y1', 'x2', 'y2', 'cx', 'cy', 'rx', 'ry', 'r',
  'dx', 'dy', 'text-anchor', 'dominant-baseline', 'font-family', 'font-size',
  'font-weight', 'font-style', 'letter-spacing', 'word-spacing',
  'marker-start', 'marker-mid', 'marker-end', 'markerunits', 'markerwidth', 'markerheight', 'refx', 'refy', 'orient', 'points', 'offset',
  'gradientunits', 'gradienttransform', 'spreadmethod', 'stop-color', 'stop-opacity',
  'patternunits', 'patterntransform', 'clippathunits', 'maskunits', 'maskcontentunits',
  'filterunits', 'primitiveunits', 'xmlns', 'xmlns:xlink', 'xlink:href', 'xlink:title',
  'version', 'baseprofile', 'enable-background', 'overflow', 'visibility', 'display', 'pointer-events', 'cursor',
  'mathvariant', 'encoding', 'stretchy', 'fence', 'separator', 'accent', 'accentunder', 'lspace', 'rspace', 'movablelimits',
];

let hooksRegistered = false;

function registerHooks(): void {
  if (hooksRegistered) return;
  hooksRegistered = true;

  // 元素消毒后：外链补 rel/target，图片补 alt。
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    const el = node as Element;
    if (el.tagName === 'A') {
      const href = el.getAttribute('href');
      if (href && /^https?:/i.test(href)) {
        el.setAttribute('rel', 'noopener noreferrer');
        el.setAttribute('target', '_blank');
      }
    }
    if (el.tagName === 'IMG' && !el.getAttribute('alt')) {
      el.setAttribute('alt', '');
    }
  });
}

/**
 * 对 marked 输出的 HTML 做消毒，返回安全的 HTML 字符串。
 */
export function sanitizeChatMarkdown(html: string): string {
  if (!html) return '';
  registerHooks();
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    FORBID_TAGS,
    FORBID_ATTR,
    ALLOWED_URI_REGEXP,
    KEEP_CONTENT: true,
    SANITIZE_DOM: true,
    SANITIZE_NAMED_PROPS: true,
    USE_PROFILES: { html: true, svg: true, mathMl: true },
  });
}
