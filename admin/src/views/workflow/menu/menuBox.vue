<!-- 添加节点面板：6 种节点。支持点击添加（画布中心）与拖拽放置（鼠标落点）。 -->
<template>
  <div class="add-menu-box">
    <div class="hint">拖拽到画布 / 点击添加</div>
    <div
      v-for="item in nodes"
      :key="item.type"
      class="menu-item flex-center"
      draggable="true"
      @dragstart="onDragStart($event, item.type)"
      @click="$emit('addNode', item.type)"
    >
      <div class="menu-icon" :style="{ background: item.color }">
        <n-icon :component="item.icon" color="#fff" :size="18" />
      </div>
      <div class="menu-title">{{ item.label }}</div>
    </div>
  </div>
</template>

<script setup>
  import {
    MessageOutlined,
    ThunderboltOutlined,
    RobotOutlined,
    ApartmentOutlined,
    DatabaseOutlined,
    BranchesOutlined,
    PartitionOutlined,
  } from '@vicons/antd';

  defineEmits(['addNode']);

  const nodes = [
    { type: 'answer', label: '回复', color: '#06ae4d', icon: MessageOutlined },
    { type: 'llm', label: 'LLM', color: '#6172f3', icon: ThunderboltOutlined },
    { type: 'agent', label: 'Agent', color: '#17b26a', icon: RobotOutlined },
    { type: 'purpose', label: '意图分类', color: '#f79009', icon: ApartmentOutlined },
    { type: 'dataset', label: '知识检索', color: '#6172f3', icon: DatabaseOutlined },
    { type: 'graph', label: '知识图谱', color: '#722ed1', icon: PartitionOutlined },
    { type: 'switch', label: '条件分支', color: '#6172f3', icon: BranchesOutlined },
  ];

  // 拖拽开始：把节点类型写进 dataTransfer，edit.vue 的 drop 事件读取
  function onDragStart(e, type) {
    if (e.dataTransfer) {
      e.dataTransfer.setData('application/x-workflow-node', type);
      e.dataTransfer.effectAllowed = 'copy';
    }
  }
</script>

<style scoped>
  .add-menu-box {
    width: 170px;
    background: #ffffff;
    display: flex;
    flex-direction: column;
    padding: 8px 14px;
    border-radius: 5px;
    box-shadow: 0 1px 6px rgba(0, 0, 0, 0.08);
  }
  .hint {
    font-size: 11px;
    color: #999;
    text-align: center;
    padding: 4px 0 8px;
    border-bottom: 1px dashed #eee;
    margin-bottom: 4px;
  }
  .menu-item {
    height: 40px;
    cursor: grab;
  }
  .menu-item:active {
    cursor: grabbing;
  }
  .menu-item:hover {
    background: #f6ffed;
  }
  .menu-title {
    margin-left: 10px;
    font-size: 14px;
  }
  .menu-icon {
    padding: 3px;
    border-radius: 5px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
</style>
