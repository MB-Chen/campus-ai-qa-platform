import { computed, onBeforeUnmount, ref, watch, type ComputedRef } from 'vue';

/**
 * 自适应打字机 —— 把网络层不均匀的 SSE token 突发平滑成稳定的人类可读节奏。
 *
 * CJK 文本按 2-4 字短语推进，Latin 文本按整词推进；积压时加速，接近实时边缘时减速。
 * 尊重 prefers-reduced-motion：直接跳到全文，不做动画。
 *
 * 移植自 WeKnora frontend（原样）。
 */
export interface TypewriterOptions {
  /** 舒适下限：每秒揭示字符数。 */
  minCps?: number;
  /** 普通积压耗尽的时间窗口（秒）。 */
  drainSeconds?: number;
  /** 单次大 chunk 到达时的速度上限。 */
  maxCps?: number;
  /** 标签页后台恢复时单帧最大时长（秒）。 */
  maxFrameSeconds?: number;
}

const NATURAL_BREAK_RE = /[\s，。！？；：、,.!?;:)\]】》」』]/u;
const CJK_RE = /[\u3400-\u9fff\uf900-\ufaff\u3040-\u30ff\uac00-\ud7af]/u;
const WORD_CHARACTER_RE = /[\p{L}\p{N}_]/u;

function nextCodePointEnd(text: string, index: number): number {
  if (index >= text.length) return text.length;
  const code = text.charCodeAt(index);
  return index + (code >= 0xd800 && code <= 0xdbff ? 2 : 1);
}

function previousCodePointStart(text: string, index: number): number {
  if (index <= 0) return 0;
  const code = text.charCodeAt(index - 1);
  return code >= 0xdc00 && code <= 0xdfff ? index - 2 : index - 1;
}

function advanceCodePoints(text: string, start: number, count: number): number {
  let end = start;
  for (let i = 0; i < count && end < text.length; i += 1) {
    end = nextCodePointEnd(text, end);
  }
  return end;
}

/**
 * 选取下一个可读揭示边界。
 * CJK 按 2-4 字紧凑短语，Latin 等待整词边界而非逐字母爬行。
 */
export function nextTypewriterReveal(
  text: string,
  start: number,
  availableCharacters: number,
): number {
  if (start >= text.length) return text.length;

  const firstEnd = nextCodePointEnd(text, start);
  const firstCharacter = text.slice(start, firstEnd);
  const isCjk = CJK_RE.test(firstCharacter);
  const isWordCharacter = WORD_CHARACTER_RE.test(firstCharacter);
  const minimumGroup = isCjk ? 2 : 1;
  const maximumGroup = isCjk ? 4 : 14;
  const available = Math.max(0, Math.floor(availableCharacters));
  if (available < minimumGroup && text.length - start > available) return start;

  const hardEnd = advanceCodePoints(text, start, Math.min(maximumGroup, Math.max(minimumGroup, available)));
  const minimumEnd = advanceCodePoints(text, start, minimumGroup);

  // 优先取揭示预算内最近的完整短语/单词边界。
  let cursor = hardEnd;
  while (cursor >= minimumEnd) {
    const characterStart = previousCodePointStart(text, cursor);
    if (NATURAL_BREAK_RE.test(text.slice(characterStart, cursor))) return cursor;
    cursor = characterStart;
  }

  if (isCjk || !isWordCharacter || hardEnd === text.length || available >= maximumGroup) return hardEnd;
  return start;
}

export function useTypewriter(
  getTarget: () => string,
  getComplete: () => boolean,
  options: TypewriterOptions = {},
): { displayed: ComputedRef<string> } {
  const minCps = options.minCps ?? 72;
  const drainSeconds = options.drainSeconds ?? 0.42;
  const maxCps = options.maxCps ?? 240;
  const maxFrameSeconds = options.maxFrameSeconds ?? 0.05;

  const typedLength = ref(0);
  let revealCredit = 0;
  let raf: number | null = null;
  let lastTs = 0;
  let initialized = false;
  let reduceMotion = false;
  let motionQuery: MediaQueryList | null = null;

  if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
    motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    reduceMotion = motionQuery.matches;
  }

  const displayed = computed(() => {
    const full = getTarget();
    let n = Math.min(typedLength.value, full.length);
    // 绝不切在高代理位上（会渲染出残缺字形）。
    if (n > 0 && n < full.length) {
      const code = full.charCodeAt(n - 1);
      if (code >= 0xd800 && code <= 0xdbff) n -= 1;
    }
    return full.slice(0, n);
  });

  const stop = () => {
    if (raf !== null) {
      cancelAnimationFrame(raf);
      raf = null;
    }
    lastTs = 0;
  };

  const tick = (ts: number) => {
    const full = getTarget();
    const target = full.length;
    if (typedLength.value > target) {
      typedLength.value = 0;
      revealCredit = 0;
    }
    if (typedLength.value >= target) {
      stop();
      return;
    }

    const dt = lastTs ? Math.min((ts - lastTs) / 1000, maxFrameSeconds) : 0;
    lastTs = ts;
    const remaining = target - typedLength.value;
    const cps = Math.min(maxCps, Math.max(minCps, remaining / drainSeconds));
    revealCredit += cps * dt;

    const next = nextTypewriterReveal(full, typedLength.value, revealCredit);
    if (next > typedLength.value) {
      revealCredit = Math.max(0, revealCredit - (next - typedLength.value));
      typedLength.value = next;
    }

    raf = requestAnimationFrame(tick);
  };

  const ensure = () => {
    if (raf === null) {
      lastTs = 0;
      raf = requestAnimationFrame(tick);
    }
  };

  const handleMotionChange = (event: MediaQueryListEvent) => {
    reduceMotion = event.matches;
    if (reduceMotion) {
      typedLength.value = getTarget().length;
      revealCredit = 0;
      stop();
    }
  };
  motionQuery?.addEventListener('change', handleMotionChange);

  watch(
    getTarget,
    (full) => {
      const target = full.length;
      if (!initialized) {
        initialized = true;
        // 已完成的消息（如历史记录）或无障碍偏好下直接显示全文，不做打字动画。
        if (getComplete() || reduceMotion) {
          typedLength.value = target;
          return;
        }
      }
      if (typedLength.value > target) {
        typedLength.value = 0;
        revealCredit = 0;
      }
      if (reduceMotion) {
        typedLength.value = target;
      } else if (typedLength.value < target) {
        ensure();
      }
    },
    { immediate: true },
  );

  onBeforeUnmount(() => {
    stop();
    motionQuery?.removeEventListener('change', handleMotionChange);
  });

  return { displayed };
}
