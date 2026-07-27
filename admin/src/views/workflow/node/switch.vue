<!-- 条件分支节点：显示 IF/ELSEIF/ELSE 分支条件 -->
<template>
  <div class="node-base" :class="{ 'node-active': active }">
    <div class="flex-center">
      <n-icon :component="meta.icon" :color="meta.color" :size="18" />
      <span class="node-name" v-if="no === 1">{{ meta.name }}</span>
      <span class="node-name" v-else>{{ meta.name }}{{ no - 1 }}</span>
    </div>

    <div
      class="tips-text branch-block"
      v-for="(item, index) in nodeInnerData.ifBranch"
      :key="index"
    >
      <div class="flex-center branch-head">
        <span v-if="index === 0">IF</span>
        <span v-else>ELSEIF</span>
        <span class="logic" v-if="item.switch === 1">AND</span>
        <span class="logic" v-if="item.switch === 2">OR</span>
      </div>
      <div
        class="flex-center tips-item"
        v-for="(item2, index2) in item.data"
        :key="index2"
      >
        <span class="line1 cond-text"
          >{{ condField(item2.input) }} {{ optionsMap.get(item2.tips) }}
          {{ item2.value }}</span
        >
      </div>
    </div>

    <div class="tips-text else-block">
      <span>ELSE</span>
    </div>
  </div>
</template>

<script setup>
  import { ref, inject } from 'vue';
  import initConfig from '@/views/workflow/initConfig.js';
  import { iconComponent } from '@/views/workflow/icons/index.js';

  const getNode = inject('getNode');
  const meta = iconComponent('switch-node');

  const nodeData = getNode().getData();
  const no = ref(nodeData.no || 1);
  const nodeInnerData = ref(nodeData);
  const active = ref(false);

  const optionsMap = new Map();
  JSON.parse(JSON.stringify(initConfig.switchOptions)).forEach((item) => {
    optionsMap.set(item.type, item.label);
  });

  // 条件变量显示：兼容新 [{nodeId, field}] 与旧 [nodeId, field]
  function condField(input) {
    if (!Array.isArray(input) || input.length === 0) return '';
    const first = input[0];
    if (first instanceof Object) return first.field || '';
    return input[1] || '';
  }

  const node = getNode();
  node.on('change:data', ({ current }) => {
    active.value = current.checked;
    nodeInnerData.value = current;
  });
</script>

<style scoped>
  .tips-text {
    width: 100%;
    background: #f4f4f4;
    padding: 5px 10px;
    border-radius: 5px;
    margin-top: 10px;
  }
  .branch-block {
    display: flex;
    flex-direction: column;
  }
  .branch-head {
    justify-content: space-between;
    font-size: 13px;
  }
  .logic {
    font-size: 12px;
    color: #888;
  }
  .tips-item {
    background: #fff;
    padding: 5px 10px;
    border-radius: 5px;
    font-size: 12px;
    margin-top: 5px;
  }
  .cond-text {
    color: #6172f3;
  }
</style>
