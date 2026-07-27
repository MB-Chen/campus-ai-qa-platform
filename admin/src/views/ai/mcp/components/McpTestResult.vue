<template>
  <n-modal v-model:show="show" preset="card" title="MCP 测试连接结果" style="width: 640px; max-height: 85vh; overflow-y: auto">
    <div v-if="result">
      <!-- 状态条 -->
      <n-alert :type="result.success ? 'success' : 'error'" :title="result.success ? '连接成功' : '连接失败'" class="mb-3">
        {{ result.message || (result.success ? '成功' : '失败') }}
        <span v-if="result.latencyMs != null" class="latency">（{{ result.latencyMs }} ms）</span>
      </n-alert>

      <!-- 工具列表 -->
      <div v-if="result.tools && result.tools.length" class="section">
        <div class="section-title">工具列表（{{ result.tools.length }} 个）</div>
        <div v-for="(tool, i) in result.tools" :key="i" class="tool-item">
          <div class="tool-head" @click="toggleTool(i)">
            <n-icon class="tool-arrow" :class="{ expanded: expandedTools.has(i) }">
              <svg viewBox="0 0 24 24" width="14" height="14"><path fill="currentColor" d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6z"/></svg>
            </n-icon>
            <span class="tool-name">{{ tool.name }}</span>
            <n-tag v-if="tool.description" size="tiny" round>{{ tool.description.length > 20 ? tool.description.slice(0, 20) + '...' : tool.description }}</n-tag>
          </div>
          <div v-if="tool.description" class="tool-desc">{{ tool.description }}</div>
          <div v-if="expandedTools.has(i) && tool.inputSchema" class="tool-schema">
            <pre>{{ formatJson(tool.inputSchema) }}</pre>
          </div>
        </div>
      </div>

      <!-- 资源列表 -->
      <div v-if="result.resources && result.resources.length" class="section">
        <div class="section-title">资源列表（{{ result.resources.length }} 个）</div>
        <div v-for="(res, i) in result.resources" :key="'r' + i" class="resource-item">
          <span class="resource-uri">{{ res.uri }}</span>
          <span v-if="res.name" class="resource-name">{{ res.name }}</span>
          <n-tag v-if="res.mimeType" size="tiny">{{ res.mimeType }}</n-tag>
        </div>
      </div>

      <div v-if="!result.success && (!result.tools || !result.tools.length)" class="empty-tip">
        请检查服务地址、认证配置是否正确，以及 MCP Server 是否在线。
      </div>
    </div>
  </n-modal>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import type { McpTestResult } from '@/api/system/aiMcp';

  const show = ref(false);
  const result = ref<McpTestResult | null>(null);
  const expandedTools = ref<Set<number>>(new Set());

  function showResult(r: McpTestResult) {
    result.value = r;
    expandedTools.value = new Set();
    show.value = true;
  }

  function toggleTool(i: number) {
    if (expandedTools.value.has(i)) {
      expandedTools.value.delete(i);
    } else {
      expandedTools.value.add(i);
    }
  }

  function formatJson(str?: string): string {
    if (!str) return '';
    try {
      return JSON.stringify(JSON.parse(str), null, 2);
    } catch {
      return str;
    }
  }

  defineExpose({ show: showResult });
</script>

<style lang="less" scoped>
  .latency {
    color: #999;
    margin-left: 4px;
  }
  .section {
    margin-top: 12px;
  }
  .section-title {
    font-weight: 600;
    font-size: 14px;
    margin-bottom: 8px;
    color: #333;
  }
  .tool-item {
    border: 1px solid #eee;
    border-radius: 6px;
    padding: 8px 10px;
    margin-bottom: 8px;
  }
  .tool-head {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
  }
  .tool-arrow {
    transition: transform 0.2s;
    color: #999;
    &.expanded {
      transform: rotate(90deg);
    }
  }
  .tool-name {
    font-weight: 600;
    font-size: 13px;
    flex: 1;
  }
  .tool-desc {
    font-size: 12px;
    color: #666;
    margin: 6px 0 0 20px;
  }
  .tool-schema {
    margin: 6px 0 0 20px;
    background: #f7f8fa;
    border-radius: 4px;
    padding: 8px;
    overflow-x: auto;
    pre {
      margin: 0;
      font-size: 12px;
      white-space: pre-wrap;
      word-break: break-all;
    }
  }
  .resource-item {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    padding: 4px 0;
  }
  .resource-uri {
    font-family: monospace;
    color: #07c05f;
    flex: 1;
  }
  .resource-name {
    color: #666;
  }
  .empty-tip {
    color: #bbb;
    text-align: center;
    padding: 20px;
  }
</style>
