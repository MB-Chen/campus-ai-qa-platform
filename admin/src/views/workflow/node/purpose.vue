<!-- 意图分类节点：显示模型名 + 分类清单 -->
<template>
  <div class="node-base" :class="{ 'node-active': active }">
    <div class="flex-center">
      <n-icon :component="meta.icon" :color="meta.color" :size="18" />
      <span class="node-name" v-if="no === 1">{{ meta.name }}</span>
      <span class="node-name" v-else>{{ meta.name }}{{ no - 1 }}</span>
    </div>

    <div class="flex-center tips-text">
      <div class="menu-icon" :style="{ background: meta.color }">
        <n-icon :component="meta.icon" color="#fff" :size="14" />
      </div>
      <span
        v-if="nodeInnerData.modelInfo && nodeInnerData.modelInfo.modelName"
        class="line1 model-name"
        >{{ nodeInnerData.modelInfo.modelName }}</span
      >
      <span v-else class="line1 model-name placeholder">请设置语言模型</span>
    </div>

    <div
      class="flex-center tips-text cate-item"
      v-for="(item, index) in nodeInnerData.cateList"
      :key="index"
    >
      {{ item.name }}
    </div>
  </div>
</template>

<script setup>
  import { ref, inject } from 'vue';
  import { iconComponent } from '@/views/workflow/icons/index.js';

  const getNode = inject('getNode');
  const meta = iconComponent('purpose-node');

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
  .cate-item {
    height: auto;
    min-height: 26px;
    font-size: 12px;
  }
  .menu-icon {
    color: #fff;
    padding: 2px;
    border-radius: 5px;
  }
  .model-name {
    font-size: 13px;
    margin-left: 5px;
  }
  .placeholder {
    color: #999;
  }
</style>
