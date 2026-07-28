<!--
  执行详情：按节点类型分别渲染（对标智能体 RagTraceDrawer），让调试能看到每个环节的调用上下文。
  数据契约（与后端 FlowNodeParser + 各 Node 落库一致）：
    outputData: 全局 sys.* 平铺 + 节点产出按 node.<cell> 分区（sys.content/sys.result/sys.purposeName/...）
    modelData : 节点原配置 + 调试字段（costMs / renderedSystemMsg / renderedUserPrompt / prompt /
                rawAnswer / hitIndex / hitName / switch.evaluation / switch.hitBranch / answerType ...）
  未知节点类型或解析失败 → 兜底回退到原始 JSON，保证不丢数据。
-->
<template>
  <div class="runtime-list">
    <n-spin :show="loading">
      <n-empty v-if="!loading && runtimeData.length === 0" description="暂无执行数据" />

      <!-- 顶部汇总（仅当有耗时数据时展示） -->
      <div v-if="!loading && totalTimeText" class="summary-bar">
        <div class="summary-item">
          <n-icon :size="15"><FieldTimeOutlined /></n-icon>
          <span class="summary-label">总耗时</span>
          <span class="summary-val">{{ totalTimeText }}</span>
        </div>
        <div class="summary-item">
          <n-icon :size="15"><ApartmentOutlined /></n-icon>
          <span class="summary-label">节点</span>
          <span class="summary-val">{{ runtimeData.length }} 个</span>
        </div>
        <div v-if="totalTokens > 0" class="summary-item">
          <n-icon :size="15"><ThunderboltOutlined /></n-icon>
          <span class="summary-label">tokens</span>
          <span class="summary-val">{{ totalTokens }}</span>
        </div>
      </div>

      <div
        v-for="(item, index) in runtimeData"
        :key="index"
        class="runtime-item"
        :class="{ 'is-hit': nodeMeta(item).costMs != null && currentIndex !== index }"
      >
        <div class="runtime-title" @click="toggle(index)">
          <div class="runtime-icon">
            <n-icon
              :component="CaretRightOutlined"
              :style="{ transform: currentIndex === index ? 'rotate(90deg)' : '' }"
            />
            <n-icon
              v-if="nodeMeta(item).icon"
              :component="nodeMeta(item).icon"
              :color="nodeMeta(item).color"
              :size="18"
            />
            <span class="node-label">{{ nodeMeta(item).name || item.nodeType }}</span>
            <!-- 耗时徽标 -->
            <span
              v-if="nodeMeta(item).costMs != null"
              class="cost-badge"
              :class="costClass(nodeMeta(item).costMs)"
            >
              {{ (nodeMeta(item).costMs / 1000).toFixed(2) }}s
            </span>
          </div>
          <div class="runtime-status">
            第<div class="run-step">{{ item.step }}</div
            >步
          </div>
        </div>
        <n-collapse-transition :show="currentIndex === index">
          <div class="runtime-content-body">
            <!-- 按节点类型分场景渲染（统一派发组件，逻辑见 runtimeDetail.js） -->
            <node-body :item="item" />
          </div>
        </n-collapse-transition>
      </div>
    </n-spin>
  </div>
</template>

<script setup>
  import { ref, computed, watch } from 'vue';
  import {
    CaretRightOutlined,
    FieldTimeOutlined,
    ApartmentOutlined,
    ThunderboltOutlined,
  } from '@vicons/antd';
  import { getRunDetail } from '@/api/system/workflow';
  // 渲染逻辑（节点类型派发 + meta 解析）统一抽到 runtimeDetail.js，供此处与 debug.vue 共用
  import { NodeBody, nodeMeta, costClass } from './runtimeDetail.js';

  const props = defineProps({
    runtimeId: { type: Number, default: 0 },
  });

  const loading = ref(false);
  const runtimeData = ref([]);
  // 初始 -1：不默认展开第一个（原 Bug：0 会命中 index=0 默认展开）
  const currentIndex = ref(-1);

  function toggle(index) {
    currentIndex.value = currentIndex.value === index ? -1 : index;
  }

  // 顶部汇总：所有节点耗时求和 + token 求和
  const totalTimeText = computed(() => {
    let total = 0;
    for (const item of runtimeData.value) {
      const m = nodeMeta(item);
      if (m.costMs != null) total += m.costMs;
    }
    if (total <= 0) return '';
    return (total / 1000).toFixed(2) + 's';
  });
  const totalTokens = computed(() => {
    let t = 0;
    for (const item of runtimeData.value) {
      try {
        const md = item.modelData ? JSON.parse(item.modelData) : {};
        if (typeof md.totalTokenCount === 'number') t += md.totalTokenCount;
      } catch {
        // ignore
      }
    }
    return t;
  });

  async function load() {
    if (!props.runtimeId) return;
    loading.value = true;
    try {
      const res = await getRunDetail(props.runtimeId);
      if (res && res.code === 0 && Array.isArray(res.data)) {
        // 过滤掉 outputData 和 modelData 都为空的脏行
        runtimeData.value = res.data.filter((x) => x && (x.outputData || x.modelData));
      } else {
        runtimeData.value = [];
      }
    } catch {
      runtimeData.value = [];
    } finally {
      loading.value = false;
    }
  }

  watch(
    () => props.runtimeId,
    () => load(),
    { immediate: true }
  );
</script>

<style scoped>
  .runtime-list {
    display: flex;
    flex-direction: column;
    width: 100%;
    max-height: 60vh;
    overflow-y: auto;
  }
  .summary-bar {
    display: flex;
    gap: 20px;
    flex-wrap: wrap;
    padding: 10px 14px;
    margin-bottom: 4px;
    background: var(--n-action-color, #f6ffed);
    border: 1px solid #b7eb8f;
    border-radius: 6px;
  }
  .summary-item {
    display: flex;
    align-items: center;
    gap: 6px;
    color: #555;
  }
  .summary-label {
    font-size: 12px;
    color: #888;
  }
  .summary-val {
    font-size: 14px;
    font-weight: 600;
    color: #18a058;
    font-variant-numeric: tabular-nums;
  }
  .runtime-item {
    margin-top: 10px;
    cursor: pointer;
    background: #fff;
    border: 1px solid #eee;
    border-radius: 6px;
    padding: 10px 14px;
  }
  .runtime-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .runtime-icon {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .node-label {
    font-size: 14px;
    font-weight: 500;
  }
  .cost-badge {
    margin-left: 4px;
    padding: 1px 7px;
    border-radius: 10px;
    font-size: 11px;
    font-variant-numeric: tabular-nums;
  }
  .cost-ok {
    background: #f6ffed;
    color: #18a058;
  }
  .cost-warning {
    background: #fff7e6;
    color: #fa8c16;
  }
  .cost-danger {
    background: #fff1f0;
    color: #cf1322;
  }
  .runtime-status {
    display: flex;
    align-items: center;
    font-size: 13px;
    color: #666;
  }
  .run-step {
    background: #646a73;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    margin: 0 5px;
    line-height: 20px;
    text-align: center;
    color: #fff;
    font-size: 12px;
  }
  .runtime-content-body {
    font-size: 13px;
    margin-top: 10px;
  }

  /* 分场景详情通用结构 */
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
    color: #333;
    margin-bottom: 2px;
  }

  /* key-value 行 */
  :deep(.kv-row) {
    display: flex;
    gap: 10px;
    align-items: flex-start;
  }
  :deep(.kv-label) {
    flex-shrink: 0;
    width: 76px;
    font-size: 12px;
    color: #999;
    padding-top: 2px;
  }
  :deep(.kv-val) {
    flex: 1;
    font-size: 13px;
    color: #333;
    line-height: 1.6;
    word-break: break-word;
  }
  :deep(.kv-strong) {
    color: #18a058;
    font-weight: 600;
  }

  /* prompt 代码块 */
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

  /* markdown 输出 */
  :deep(.md-output) {
    background: #f5f6f7;
    border-radius: 6px;
    padding: 10px 12px;
    font-size: 13px;
    line-height: 1.6;
    color: #333;
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

  /* 统计行 */
  :deep(.stat-row) {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    padding: 8px 10px;
    background: #f5f6f7;
    border-radius: 6px;
  }
  :deep(.stat-item) {
    display: flex;
    align-items: center;
    gap: 5px;
  }
  :deep(.stat-k) {
    font-size: 12px;
    color: #999;
  }
  :deep(.stat-v) {
    font-size: 13px;
    font-weight: 600;
    color: #333;
    font-variant-numeric: tabular-nums;
  }

  /* 召回片段 */
  :deep(.frag-list) {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  :deep(.frag-card) {
    padding: 8px 10px;
    background: #f5f6f7;
    border-left: 3px solid #18a058;
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
    color: #999;
    font-weight: 600;
  }
  :deep(.frag-text) {
    font-size: 12px;
    line-height: 1.6;
    color: #555;
  }

  /* 意图分类清单 */
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
    background: #f5f6f7;
    border-radius: 4px;
  }
  :deep(.cate-hit) {
    background: #f6ffed;
    border: 1px solid #b7eb8f;
  }
  :deep(.cate-idx) {
    width: 20px;
    height: 20px;
    line-height: 20px;
    text-align: center;
    background: #d0d5dc;
    color: #fff;
    border-radius: 50%;
    font-size: 11px;
    font-weight: 600;
  }
  :deep(.cate-hit .cate-idx) {
    background: #18a058;
  }
  :deep(.cate-name) {
    flex: 1;
    font-size: 13px;
    color: #333;
  }
  :deep(.cate-tag) {
    padding: 1px 7px;
    background: #18a058;
    color: #fff;
    border-radius: 10px;
    font-size: 11px;
  }

  /* switch 分支判断 */
  :deep(.branch-block) {
    padding: 8px 10px;
    background: #f5f6f7;
    border-radius: 4px;
    margin-bottom: 6px;
  }
  :deep(.branch-hit) {
    background: #f6ffed;
    border: 1px solid #b7eb8f;
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
    color: #333;
  }
  :deep(.branch-logic) {
    padding: 0 6px;
    background: #d0d5dc;
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
    color: #18a058;
  }
  :deep(.cond-fail) {
    color: #999;
  }
  :deep(.cond-field) {
    font-weight: 600;
  }
  :deep(.cond-op) {
    padding: 0 5px;
    background: #fff;
    border-radius: 3px;
    font-size: 11px;
    color: #6172f3;
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
    color: #bbb;
  }
  :deep(.cond-result) {
    font-weight: 700;
  }

  :deep(.note-tip) {
    padding: 6px 10px;
    background: #e6f7ff;
    border-left: 3px solid #1890ff;
    border-radius: 4px;
    font-size: 12px;
    color: #555;
  }

  /* 兜底 JSON */
  :deep(.detail-box) {
    background: #f5f6f7;
    border-radius: 4px;
  }
  :deep(.json-view) {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 12px;
    color: #333;
    max-height: 240px;
    overflow-y: auto;
    padding: 8px 12px;
  }
</style>
