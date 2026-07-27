<!-- 执行详情：按步骤展示每个节点的 outputData / modelData（可展开折叠） -->
<template>
  <div class="runtime-list">
    <n-spin :show="loading">
      <n-empty v-if="!loading && runtimeData.length === 0" description="暂无执行数据" />
      <div
        v-for="(item, index) in runtimeData"
        :key="index"
        class="runtime-item"
      >
        <div class="runtime-title" @click="toggle(index)">
          <div class="runtime-icon">
            <n-icon
              :component="CaretRightOutlined"
              :style="{ transform: currentIndex === index ? 'rotate(90deg)' : '' }"
            />
            <n-icon
              v-if="metaOf(item.nodeType)?.icon"
              :component="metaOf(item.nodeType).icon"
              :color="metaOf(item.nodeType).color"
              :size="18"
            />
            <span class="node-label">{{ metaOf(item.nodeType)?.name || item.nodeType }}</span>
          </div>
          <div class="runtime-status">
            第<div class="run-step">{{ item.step }}</div>步
          </div>
        </div>
        <n-collapse-transition :show="currentIndex === index">
          <div class="runtime-content-body">
            <!-- 输出数据 JSON 树 -->
            <div class="detail-box">
              <div class="detail-title">输出数据</div>
              <div class="detail-content">
                <pre class="json-view">{{ formatJson(item.outputData) }}</pre>
              </div>
            </div>
            <div v-if="item.modelData" class="detail-box" style="margin-top: 10px">
              <div class="detail-title">模型/配置数据</div>
              <div class="detail-content">
                <pre class="json-view">{{ formatJson(item.modelData) }}</pre>
              </div>
            </div>
          </div>
        </n-collapse-transition>
      </div>
    </n-spin>
  </div>
</template>

<script setup>
  import { ref, watch } from 'vue';
  import { CaretRightOutlined } from '@vicons/antd';
  import { getRunDetail } from '@/api/system/workflow';
  import { NODE_ICON_META } from '@/views/workflow/icons/index.js';

  const props = defineProps({
    runtimeId: { type: Number, default: 0 },
  });

  const loading = ref(false);
  const runtimeData = ref([]);
  const currentIndex = ref(0);

  function metaOf(nodeType) {
    return NODE_ICON_META[nodeType];
  }

  function toggle(index) {
    currentIndex.value = currentIndex.value === index ? -1 : index;
  }

  function formatJson(str) {
    if (!str) return '{}';
    try {
      return JSON.stringify(JSON.parse(str), null, 2);
    } catch {
      return str;
    }
  }

  async function load() {
    if (!props.runtimeId) return;
    loading.value = true;
    try {
      const res = await getRunDetail(props.runtimeId);
      if (res && res.code === 0 && Array.isArray(res.data)) {
        runtimeData.value = res.data.filter((x) => x && x != null);
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
    { immediate: true },
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
  .detail-box {
    background: #f5f6f7;
    border-radius: 4px;
  }
  .detail-title {
    border-bottom: 1px dashed #dee0e3;
    padding: 8px 12px;
    font-weight: bold;
  }
  .detail-content {
    padding: 8px 12px;
  }
  .json-view {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 12px;
    color: #333;
    max-height: 240px;
    overflow-y: auto;
  }
</style>
