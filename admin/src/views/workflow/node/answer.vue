<!-- 回复节点 -->
<template>
  <div class="node-base" :class="{ 'node-active': active }">
    <div class="flex-center">
      <n-icon :component="meta.icon" :color="meta.color" :size="18" />
      <span class="node-name" v-if="no === 1">{{ meta.name }}</span>
      <span class="node-name" v-else>{{ meta.name }}{{ no - 1 }}</span>
    </div>
  </div>
</template>

<script setup>
  import { ref, inject } from 'vue';
  import { iconComponent } from '@/views/workflow/icons/index.js';

  const getNode = inject('getNode');
  const meta = iconComponent('answer-node');

  const nodeData = getNode().getData();
  const no = ref(nodeData.no || 1);
  const active = ref(false);

  const node = getNode();
  node.on('change:data', ({ current }) => {
    active.value = current.checked;
  });
</script>

<style scoped></style>
