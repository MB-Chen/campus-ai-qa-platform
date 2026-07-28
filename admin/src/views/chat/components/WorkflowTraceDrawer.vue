<template>
  <n-drawer v-model:show="show" :width="560" placement="right">
    <n-drawer-content title="调用流程" :native-scrollbar="false">
      <div v-if="steps.length" class="wf-trace-body">
        <!-- 顶部汇总 -->
        <div class="trace-summary">
          <div class="summary-item">
            <n-icon :size="16"><FieldTimeOutlined /></n-icon>
            <span class="summary-label">总耗时</span>
            <span class="summary-val">{{ ((meta.totalCost || 0) / 1000).toFixed(2) }}s</span>
          </div>
          <div class="summary-item">
            <n-icon :size="16"><ThunderboltOutlined /></n-icon>
            <span class="summary-label">节点数</span>
            <span class="summary-val">{{ steps.length }} 个</span>
          </div>
          <div v-if="meta.totalTokens" class="summary-item">
            <n-icon :size="16"><ThunderboltOutlined /></n-icon>
            <span class="summary-label">Tokens</span>
            <span class="summary-val">{{ meta.totalTokens }}</span>
          </div>
        </div>

        <!-- 步骤时间线 -->
        <div class="trace-section">
          <div class="section-title">执行步骤</div>
          <n-collapse :default-expanded-names="defaultExpanded">
            <n-collapse-item
              v-for="(step, i) in steps"
              :key="step.cell || i"
              :name="String(step.cell || i)"
            >
              <template #header>
                <div class="step-head">
                  <n-icon
                    v-if="m(step).icon"
                    :component="m(step).icon"
                    :color="m(step).color"
                    :size="16"
                    class="step-head-icon"
                  />
                  <span class="step-num">{{ step.step ?? i + 1 }}</span>
                  <span class="step-name">{{ m(step).name || step.nodeType || '节点' }}</span>
                  <span
                    v-if="m(step).costMs != null"
                    class="cost-badge"
                    :class="costClass(m(step).costMs)"
                  >
                    {{ (m(step).costMs / 1000).toFixed(2) }}s
                  </span>
                </div>
              </template>
              <node-body :item="step" />
            </n-collapse-item>
          </n-collapse>
        </div>
      </div>
      <n-empty v-else description="暂无调用流程数据" style="margin-top: 80px" />
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { FieldTimeOutlined, ThunderboltOutlined } from '@vicons/antd';
  import { NodeBody, nodeMeta, costClass } from '@/views/workflow/menu/runtimeDetail.js';
  import type { WorkflowStep } from '@/api/system/workflow';

  const props = defineProps<{
    show: boolean;
    steps: WorkflowStep[];
    meta: { name?: string; totalCost?: number; totalTokens?: number };
  }>();
  const emit = defineEmits<{ 'update:show': [v: boolean] }>();

  const show = computed({
    get: () => props.show,
    set: (v) => emit('update:show', v),
  });

  // 取节点 meta（图标/名称/耗时）
  function m(step: WorkflowStep) {
    return nodeMeta(step);
  }

  // 默认全部展开
  const defaultExpanded = computed(() => props.steps.map((s, i) => String(s.cell || i)));
</script>

<style lang="less" scoped>
  .wf-trace-body {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  /* 顶部汇总 */
  .trace-summary {
    display: flex;
    gap: 24px;
    padding: 12px 14px;
    background: var(--n-color);
    border: 1px solid var(--n-border-color);
    border-radius: 8px;
  }
  .summary-item {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--n-text-color-3);
    .summary-label {
      font-size: 13px;
    }
    .summary-val {
      font-size: 15px;
      font-weight: 600;
      color: var(--n-text-color);
      font-variant-numeric: tabular-nums;
    }
  }

  .trace-section {
    .section-title {
      font-size: 13px;
      font-weight: 600;
      color: var(--n-text-color);
      margin-bottom: 8px;
      padding-left: 2px;
    }
  }

  /* 步骤头 */
  .step-head {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .step-head-icon {
    flex-shrink: 0;
  }
  .step-num {
    width: 18px;
    height: 18px;
    line-height: 18px;
    text-align: center;
    background: var(--n-text-color-3);
    color: #fff;
    border-radius: 50%;
    font-size: 11px;
    flex-shrink: 0;
  }
  .step-name {
    flex: 1;
    font-size: 13px;
    font-weight: 500;
    color: var(--n-text-color);
  }
  .cost-badge {
    padding: 1px 7px;
    border-radius: 10px;
    font-size: 11px;
    font-variant-numeric: tabular-nums;
    flex-shrink: 0;
  }

  /* ===== NodeBody 渲染依赖的深层 class（从 debug.vue 同步） ===== */
  .detail-stack {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .detail-grid {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .detail-title {
    font-size: 12px;
    font-weight: 600;
    color: var(--n-text-color);
    margin-bottom: 2px;
  }

  :deep(.kv-row) {
    display: flex;
    gap: 10px;
    align-items: flex-start;
  }
  :deep(.kv-label) {
    flex-shrink: 0;
    width: 76px;
    font-size: 12px;
    color: var(--n-text-color-3);
    padding-top: 2px;
  }
  :deep(.kv-val) {
    flex: 1;
    font-size: 13px;
    color: var(--n-text-color);
    line-height: 1.6;
    word-break: break-word;
  }
  :deep(.kv-strong) {
    color: var(--n-primary-color);
    font-weight: 600;
  }

  :deep(.code-view) {
    background: #282c34;
    color: #abb2bf;
    padding: 10px;
    border-radius: 6px;
    overflow-x: auto;
    font-size: 12px;
    line-height: 1.5;
    white-space: pre-wrap;
    word-break: break-word;
    margin: 4px 0 0;
  }

  :deep(.md-output) {
    background: var(--n-action-color);
    border-radius: 6px;
    padding: 10px 12px;
    font-size: 13px;
    line-height: 1.6;
    color: var(--n-text-color);
    word-break: break-word;
  }
  :deep(.md-output p) {
    margin: 4px 0;
  }
  :deep(.md-output pre) {
    background: #282c34;
    color: #abb2bf;
    padding: 10px;
    border-radius: 6px;
    overflow-x: auto;
    font-size: 12px;
  }
  :deep(.md-output code) {
    background: rgba(0, 0, 0, 0.06);
    padding: 2px 4px;
    border-radius: 3px;
    font-size: 12px;
  }
  :deep(.md-output pre code) {
    background: transparent;
    padding: 0;
  }

  :deep(.stat-row) {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    padding: 8px 10px;
    background: var(--n-action-color);
    border-radius: 6px;
  }
  :deep(.stat-item) {
    display: flex;
    align-items: center;
    gap: 5px;
  }
  :deep(.stat-k) {
    font-size: 12px;
    color: var(--n-text-color-3);
  }
  :deep(.stat-v) {
    font-size: 13px;
    font-weight: 600;
    color: var(--n-text-color);
    font-variant-numeric: tabular-nums;
  }

  :deep(.frag-list) {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  :deep(.frag-card) {
    padding: 8px 10px;
    background: var(--n-action-color);
    border-left: 3px solid var(--n-primary-color);
    border-radius: 4px;
  }
  :deep(.frag-head) {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 4px;
  }
  :deep(.frag-idx) {
    font-size: 11px;
    color: var(--n-text-color-3);
    font-weight: 600;
  }
  :deep(.frag-text) {
    font-size: 12px;
    line-height: 1.6;
    color: var(--n-text-color-2);
  }

  :deep(.cate-list) {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  :deep(.cate-item) {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 10px;
    background: var(--n-action-color);
    border-radius: 4px;
  }
  :deep(.cate-hit) {
    background: var(--n-success-color-suppl);
    border: 1px solid var(--n-success-color);
  }
  :deep(.cate-idx) {
    width: 20px;
    height: 20px;
    line-height: 20px;
    text-align: center;
    background: var(--n-border-color);
    color: #fff;
    border-radius: 50%;
    font-size: 11px;
    font-weight: 600;
  }
  :deep(.cate-hit .cate-idx) {
    background: var(--n-success-color);
  }
  :deep(.cate-name) {
    flex: 1;
    font-size: 13px;
    color: var(--n-text-color);
  }
  :deep(.cate-tag) {
    padding: 1px 7px;
    background: var(--n-success-color);
    color: #fff;
    border-radius: 10px;
    font-size: 11px;
  }

  :deep(.branch-block) {
    padding: 8px 10px;
    background: var(--n-action-color);
    border-radius: 4px;
    margin-bottom: 6px;
  }
  :deep(.branch-hit) {
    background: var(--n-success-color-suppl);
    border: 1px solid var(--n-success-color);
  }
  :deep(.branch-head) {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
  }
  :deep(.branch-name) {
    font-size: 13px;
    font-weight: 600;
    color: var(--n-text-color);
  }
  :deep(.branch-logic) {
    padding: 0 6px;
    background: var(--n-border-color);
    color: #fff;
    border-radius: 8px;
    font-size: 10px;
  }
  :deep(.cond-row) {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 3px 0;
    font-size: 12px;
    flex-wrap: wrap;
  }
  :deep(.cond-pass) {
    color: var(--n-success-color);
  }
  :deep(.cond-fail) {
    color: var(--n-text-color-3);
  }
  :deep(.cond-field) {
    font-weight: 600;
  }
  :deep(.cond-op) {
    padding: 0 5px;
    background: var(--n-color);
    border-radius: 3px;
    font-size: 11px;
    color: var(--n-primary-color);
  }
  :deep(.cond-val),
  :deep(.cond-actual) {
    font-family: monospace;
    font-size: 11px;
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  :deep(.cond-arrow) {
    color: var(--n-text-color-3);
  }
  :deep(.cond-result) {
    font-weight: 700;
  }

  :deep(.note-tip) {
    padding: 6px 10px;
    background: var(--n-info-color-suppl);
    border-left: 3px solid var(--n-info-color);
    border-radius: 4px;
    font-size: 12px;
    color: var(--n-text-color-2);
  }

  :deep(.detail-box) {
    background: var(--n-action-color);
    border-radius: 4px;
  }
  :deep(.json-view) {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 12px;
    color: var(--n-text-color);
    max-height: 240px;
    overflow-y: auto;
    padding: 8px 12px;
  }
</style>
