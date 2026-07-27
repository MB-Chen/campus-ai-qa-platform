<!-- 开始节点：X6 节点内部渲染组件 -->
<template>
  <div class="node-base" :class="{ 'node-active': active }">
    <div class="flex-center">
      <n-icon :component="meta.icon" :color="meta.color" :size="18" />
      <span class="node-name">开始</span>
    </div>
  </div>
</template>

<script setup>
  import { ref } from 'vue';
  import { inject } from 'vue';
  import { iconComponent } from '@/views/workflow/icons/index.js';

  const getNode = inject('getNode');
  const meta = iconComponent('start-node');
  const active = ref(false);

  const node = getNode();
  node.on('change:data', ({ current }) => {
    active.value = current.checked;
  });
</script>

<style scoped></style>
