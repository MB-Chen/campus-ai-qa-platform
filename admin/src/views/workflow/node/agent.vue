<!-- Agent 节点：显示关联智能体名 -->
<template>
  <div class="node-base" :class="{ 'node-active': active }">
    <div class="flex-center">
      <n-icon :component="meta.icon" :color="meta.color" :size="18" />
      <span class="node-name" v-if="no === 1">{{ meta.name }}</span>
      <span class="node-name" v-else>{{ meta.name }}{{ no - 1 }}</span>
    </div>

    <div class="flex-center tips-text">
      <span
        v-if="nodeInnerData.agentName"
        class="line1 agent-name"
      >
        {{ nodeInnerData.agentName }}
      </span>
      <span v-else class="line1 agent-name placeholder">请设置智能体</span>
    </div>
  </div>
</template>

<script setup>
  import { ref, inject } from 'vue';
  import { iconComponent } from '@/views/workflow/icons/index.js';

  const getNode = inject('getNode');
  const meta = iconComponent('agent-node');

  const nodeData = getNode().getData();
  const no = ref(nodeData.no || 1);
  const nodeInnerData = ref(nodeData);
  const active = ref(false);

  const node = getNode();
  node.on('change:data', ({ current }) => {
    active.value = current.checked;
    nodeInnerData.value = current;
  });
</script>

<style scoped>
  .tips-text {
    width: 100%;
    height: 30px;
    background: #f4f4f4;
    padding: 5px 10px;
    border-radius: 5px;
    margin-top: 10px;
  }
  .agent-name {
    font-size: 13px;
    margin-left: 5px;
  }
  .placeholder {
    color: #999;
  }
</style>
