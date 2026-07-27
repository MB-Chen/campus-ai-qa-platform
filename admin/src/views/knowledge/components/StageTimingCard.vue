<template>
  <div v-if="stages && stages.length > 0" class="stage-timing-card">
    <div class="stage-card-title">
      <n-icon size="14" color="#07c05f"><FieldTimeOutlined /></n-icon>
      <span>阶段耗时</span>
    </div>
    <div class="stage-list">
      <div v-for="s in stages" :key="s.key" class="stage-row">
        <span class="stage-label">{{ s.label || s.key }}</span>
        <!-- running 状态：转圈独占一行，耗时数字换行在下，避免转圈与同号数字横向挤压 -->
        <span v-if="s.status === 'running'" class="stage-duration stage-running stage-running-block">
          <n-icon size="14" class="stage-spin"><LoadingOutlined /></n-icon>
          <span class="stage-running-time">{{ formatDuration(s.durationMs) }}</span>
        </span>
        <!-- 其它状态：保持原样横向排列 -->
        <span v-else class="stage-duration" :class="'stage-' + s.status">
          {{ formatDuration(s.durationMs) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { FieldTimeOutlined, LoadingOutlined } from '@vicons/antd';
  import type { StageStat } from '@/api/system/knowledge';

  defineProps<{ stages: StageStat[] }>();

  function formatDuration(ms?: number): string {
    if (ms == null || isNaN(ms) || ms < 0) return '—';
    if (ms < 1000) return Math.round(ms) + 'ms';
    if (ms < 60000) return (ms / 1000).toFixed(2) + 's';
    const mins = Math.floor(ms / 60000);
    const rem = ((ms % 60000) / 1000).toFixed(1);
    return `${mins}m${rem}s`;
  }
</script>

<style lang="less" scoped>
  .stage-timing-card {
    margin: 16px auto 0;
    max-width: 480px;
    padding: 10px 14px;
    background: #f0fdf4;
    border: 1px solid #bbf7d0;
    border-radius: 6px;
    text-align: left;
  }
  .stage-card-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #07c05f;
    margin-bottom: 8px;
  }
  .stage-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 20px;
  }
  .stage-row {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
  }
  .stage-label {
    color: #666;
  }
  .stage-duration {
    font-weight: 600;
    color: #07c05f;
    display: inline-flex;
    align-items: center;
    gap: 3px;
  }
  .stage-failed {
    color: #d03050;
  }
  .stage-skipped {
    color: #aaa;
  }
  .stage-running {
    color: #2080f0;
  }
  /* running 状态：转圈在上、数字在下，避免与同号数字横向挤压 */
  .stage-running-block {
    flex-direction: column;
    align-items: center;
    gap: 2px;
  }
  .stage-running-time {
    font-size: 11px;
    line-height: 1;
  }
  .stage-spin {
    animation: stage-spin 1s linear infinite;
  }
  @keyframes stage-spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
