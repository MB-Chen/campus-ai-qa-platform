<template>
  <div class="processing-status">
    <!-- 状态行：左 loading 转圈 / 右 耗时 + 标签 -->
    <div class="status-dial" :class="{ 'status-dial-failed': failed }">
      <div class="dial-spinner">
        <n-spin :size="dialSize" />
      </div>
      <div class="dial-info">
        <div class="dial-time">{{ formatElapsed(elapsedMs) }}</div>
        <div v-if="label" class="dial-label">{{ label }}</div>
      </div>
    </div>

    <!-- 主提示文字 -->
    <p class="status-text">{{ text }}</p>

    <!-- 可选：线性进度条 + 计数 -->
    <template v-if="showProgress">
      <n-progress
        type="line"
        :percentage="percent"
        :show-indicator="false"
        :status="failed ? 'error' : 'default'"
        processing
        class="status-progress"
      />
      <div class="status-count">
        <span class="count-main">{{ done }} / {{ total }}</span>
        <span v-if="success != null || failedCount != null" class="count-sub">
          成功 {{ success || 0 }} · 失败 {{ failedCount || 0 }}
        </span>
      </div>
    </template>

    <!-- 可选：错误消息 -->
    <div v-if="message" class="status-msg">{{ message }}</div>

    <!-- 可选：已完成文件列表（文件级实时进度，让并发效果可见） -->
    <div v-if="completedItems && completedItems.length > 0" class="completed-list scrollbar-thin">
      <div class="completed-list-head">
        <span>已完成 {{ completedItems.length }} / {{ total || completedItems.length }}</span>
        <span class="completed-list-hint">实时刷新</span>
      </div>
      <div
        v-for="(item, idx) in completedItems"
        :key="idx"
        class="completed-item"
        :class="{ 'completed-item-failed': item.status === 'failed' }"
      >
        <n-icon size="14" :color="item.status === 'failed' ? '#d03050' : '#07c05f'">
          <component :is="item.status === 'failed' ? CloseCircleFilled : CheckCircleFilled" />
        </n-icon>
        <span class="completed-name">{{ item.fileName }}</span>
        <span v-if="item.chunkCount != null && item.status !== 'failed'" class="completed-meta">
          {{ item.chunkCount }} 切片
        </span>
        <span v-else-if="item.status === 'failed'" class="completed-meta">失败</span>
      </div>
    </div>

    <!-- 可选：阶段耗时卡片插槽 -->
    <slot name="stages"></slot>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { CheckCircleFilled, CloseCircleFilled } from '@vicons/antd';

  /** 已完成文件列表的展示项（文件级进度） */
  interface CompletedItem {
    fileName: string;
    /** success / failed */
    status?: string;
    /** 切片数（failed 时可能为 0 或无） */
    chunkCount?: number;
  }

  const props = withDefaults(
    defineProps<{
      /** 已耗时（毫秒） */
      elapsedMs: number;
      /** 圆盘下方的简短标签（如「解析中」「入库中」） */
      label?: string;
      /** 主提示文字 */
      text: string;
      /** 是否失败（失败时圆盘变红） */
      failed?: boolean;
      /** 是否显示线性进度条 + 计数 */
      showProgress?: boolean;
      /** 进度百分比（0-100） */
      percent?: number;
      /** 已完成数 */
      done?: number;
      /** 总数 */
      total?: number;
      /** 成功数 */
      success?: number;
      /** 失败数 */
      failedCount?: number;
      /** 错误/附加消息 */
      message?: string;
      /** 圆盘尺寸 */
      dialSize?: 'small' | 'medium' | 'large';
      /** 已完成文件列表（按完成顺序，实时填充，让并发进度可视化） */
      completedItems?: CompletedItem[];
    }>(),
    {
      label: '',
      failed: false,
      showProgress: false,
      percent: 0,
      done: 0,
      total: 0,
      success: 0,
      failedCount: 0,
      message: '',
      dialSize: 'large',
      completedItems: () => [],
    }
  );

  // n-spin 的 size 属性映射到实际像素，用于计算 overlay 定位
  const dialPx = computed(() => {
    const map = { small: 48, medium: 80, large: 120 };
    return map[props.dialSize] || 120;
  });

  // ms → 人类可读
  function formatElapsed(ms: number): string {
    if (!ms || ms < 0) return '0.0s';
    if (ms < 1000) return (ms / 1000).toFixed(1) + 's';
    if (ms < 60000) return (ms / 1000).toFixed(1) + 's';
    const mins = Math.floor(ms / 60000);
    const secs = Math.floor((ms % 60000) / 1000);
    return `${mins}m${String(secs).padStart(2, '0')}s`;
  }
</script>

<style lang="less" scoped>
  .processing-status {
    padding: 48px 20px 40px;
    text-align: center;
  }

  /* 状态行：左 loading 转圈 / 右 耗时 + 标签 */
  .status-dial {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 18px;
    margin-bottom: 20px;
    /* 呼吸光晕，让圆盘有「正在工作」的呼吸感 */
    &::before {
      content: '';
      position: absolute;
      inset: -12px;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(7, 192, 95, 0.12) 0%, transparent 70%);
      animation: dial-breathe 2.4s ease-in-out infinite;
      pointer-events: none;
    }
    &.status-dial-failed::before {
      background: radial-gradient(circle, rgba(208, 48, 80, 0.12) 0%, transparent 70%);
    }
  }

  /* 左侧：纯转圈 */
  .dial-spinner {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  /* 右侧：耗时 + 标签（纵向排列） */
  .dial-info {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    justify-content: center;
    text-align: left;
  }
  .dial-time {
    font-size: 22px;
    font-weight: 700;
    color: #07c05f;
    font-variant-numeric: tabular-nums;
    line-height: 1.1;
    letter-spacing: -0.5px;
  }
  .status-dial-failed .dial-time {
    color: #d03050;
  }
  .dial-label {
    margin-top: 4px;
    font-size: 12px;
    color: #999;
    letter-spacing: 1px;
  }

  .status-text {
    margin: 0 0 16px;
    color: #666;
    font-size: 14px;
  }

  .status-progress {
    max-width: 420px;
    margin: 0 auto;
  }

  .status-count {
    margin-top: 10px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    .count-main {
      font-size: 14px;
      font-weight: 600;
      color: #555;
      font-variant-numeric: tabular-nums;
    }
    .count-sub {
      font-size: 12px;
      color: #aaa;
    }
  }

  .status-msg {
    margin: 12px auto 0;
    max-width: 480px;
    padding: 8px 12px;
    background: #fff5f5;
    border: 1px solid #ffb3b3;
    border-radius: 4px;
    font-size: 12px;
    color: #d03050;
    text-align: left;
    word-break: break-all;
  }

  /* 已完成文件列表：文件级实时进度（让并发效果可见，避免「卡着不动」错觉） */
  .completed-list {
    margin: 16px auto 0;
    max-width: 520px;
    max-height: 240px;
    overflow-y: auto;
    text-align: left;
    /* 顶部计数条 */
    .completed-list-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 6px 10px;
      margin-bottom: 6px;
      background: #f5f7fa;
      border-radius: 4px;
      font-size: 12px;
      color: #555;
      font-weight: 600;
      position: sticky;
      top: 0;
      z-index: 1;
    }
    .completed-list-hint {
      font-size: 11px;
      color: #aaa;
      font-weight: 400;
    }
    /* 单行完成项 */
    .completed-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 6px 10px;
      border-radius: 4px;
      font-size: 12px;
      color: #555;
      /* 完成动画：新出现的项淡入 + 微微下落 */
      animation: completed-item-in 0.3s ease-out;
      &:hover {
        background: #f9fafb;
      }
    }
    .completed-item-failed {
      color: #d03050;
    }
    .completed-name {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .completed-meta {
      font-size: 11px;
      color: #aaa;
      flex-shrink: 0;
      font-variant-numeric: tabular-nums;
    }
    .completed-item-failed .completed-meta {
      color: #d03050;
    }
  }

  @keyframes completed-item-in {
    from {
      opacity: 0;
      transform: translateY(-4px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  @keyframes dial-breathe {
    0%, 100% {
      opacity: 0.6;
      transform: scale(1);
    }
    50% {
      opacity: 1;
      transform: scale(1.08);
    }
  }

  /* 滚动条 */
  .scrollbar-thin {
    scrollbar-width: thin;
    &::-webkit-scrollbar {
      width: 6px;
      height: 6px;
    }
    &::-webkit-scrollbar-thumb {
      background: #ddd;
      border-radius: 3px;
    }
  }
</style>
