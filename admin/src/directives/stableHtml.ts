import type { ObjectDirective } from 'vue';

/**
 * v-stable-html —— 流式场景下替代 v-html 的 DOM morphing 指令。
 *
 * 流式问答每收到一个 token 都会重新 parse 整段 markdown 并触发一次更新。
 * 若用 v-html（每帧 `el.innerHTML = ...`），浏览器会把整棵子树拆掉重建，
 * 已解码的 <img> 会丢掉绘制层导致闪烁，光标/滚动也会跳动。
 *
 * 本指令把新 HTML 解析到 <template>，再与现有 DOM 做 diff，
 * 仅 patch 变化的文本/属性，匹配的已加载 <img> 节点保持挂载不重建。
 *
 * 移植自 WeKnora frontend，裁剪了受保护图片代理（data-protected-src）逻辑。
 */
function canMorph(current: Node, next: Node): boolean {
  if (current.nodeType !== next.nodeType) return false;
  if (current.nodeType !== Node.ELEMENT_NODE) return true;
  return (current as Element).tagName === (next as Element).tagName;
}

function syncAttributes(current: Element, next: Element, preserved: Set<string> = new Set()): void {
  Array.from(current.attributes).forEach(({ name }) => {
    if (!preserved.has(name) && !next.hasAttribute(name)) current.removeAttribute(name);
  });
  Array.from(next.attributes).forEach(({ name, value }) => {
    if (!preserved.has(name) && current.getAttribute(name) !== value) {
      current.setAttribute(name, value);
    }
  });
}

function imageIdentity(img: HTMLImageElement): string {
  return (img.currentSrc || img.src || '').trim();
}

function morphImage(current: HTMLImageElement, next: HTMLImageElement): void {
  // 已解码完成且来源相同的图片：保留该 DOM 节点（不重建），避免 Chromium 丢绘制层。
  const keepDecodedImage =
    current.complete &&
    current.naturalWidth > 1 &&
    imageIdentity(current) === imageIdentity(next);

  if (keepDecodedImage) {
    syncAttributes(current, next, new Set(['src']));
    return;
  }
  syncAttributes(current, next);
}

function morphNode(current: Node, next: Node): void {
  if (current.nodeType === Node.TEXT_NODE || current.nodeType === Node.COMMENT_NODE) {
    if (current.nodeValue !== next.nodeValue) current.nodeValue = next.nodeValue;
    return;
  }

  const currentElement = current as Element;
  const nextElement = next as Element;
  if (currentElement instanceof HTMLImageElement && nextElement instanceof HTMLImageElement) {
    morphImage(currentElement, nextElement);
    return;
  }

  syncAttributes(currentElement, nextElement);
  morphChildren(currentElement, nextElement);
}

function morphChildren(currentParent: ParentNode, nextParent: ParentNode): void {
  const desiredChildren = Array.from(nextParent.childNodes);
  let current = currentParent.firstChild;

  for (const desired of desiredChildren) {
    if (current && canMorph(current, desired)) {
      const following = current.nextSibling;
      morphNode(current, desired);
      current = following;
      continue;
    }
    // 仅插入新增/不匹配的子树，已有匹配祖先及其图片后代始终保持挂载。
    currentParent.insertBefore(desired.cloneNode(true), current);
  }

  while (current) {
    const following = current.nextSibling;
    currentParent.removeChild(current);
    current = following;
  }
}

export const vStableHtml: ObjectDirective<HTMLElement, string> = {
  beforeMount(el, binding) {
    el.innerHTML = binding.value || '';
  },
  updated(el, binding) {
    const html = binding.value || '';
    if (html === binding.oldValue) return;
    const template = document.createElement('template');
    template.innerHTML = html;
    morphChildren(el, template.content);
  },
};
