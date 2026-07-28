<!-- 底部缩放/居中/加节点控制条 -->
<template>
  <div class="flex-center menu-box">
    <n-tooltip placement="top">
      <template #trigger>
        <n-icon :size="18" @click="$emit('zoomOut')"><ZoomOutOutlined /></n-icon>
      </template>
      缩小
    </n-tooltip>
    <n-tooltip placement="top">
      <template #trigger>
        <n-icon :size="18" @click="$emit('zoomIn')"><ZoomInOutlined /></n-icon>
      </template>
      放大
    </n-tooltip>
    <n-tooltip placement="top">
      <template #trigger>
        <div class="add-btn" :class="{ 'add-active': open }" @click="openMenu">
          <n-icon :size="18"><PlusCircleOutlined /></n-icon>
        </div>
      </template>
      添加节点
    </n-tooltip>
    <n-tooltip placement="top">
      <template #trigger>
        <n-icon :size="18" @click="$emit('center')"><AimOutlined /></n-icon>
      </template>
      居中显示
    </n-tooltip>
  </div>
</template>

<script setup>
  import { ref, watch } from 'vue';
  import {
    ZoomOutOutlined,
    ZoomInOutlined,
    PlusCircleOutlined,
    AimOutlined,
  } from '@vicons/antd';

  const props = defineProps({
    outOpen: { type: Boolean, default: false },
  });
  const emit = defineEmits(['zoomOut', 'zoomIn', 'center', 'openMenu']);

  const open = ref(props.outOpen);
  watch(
    () => props.outOpen,
    (v) => {
      open.value = v;
    },
  );

  function openMenu() {
    open.value = !open.value;
    emit('openMenu', open.value);
  }
</script>

<style scoped>
  .menu-box {
    width: 200px;
    height: 40px;
    border-radius: 10px;
    background: #fff;
    box-shadow: 0 1px 2px 0 rgba(16, 24, 40, 0.05);
    justify-content: space-between;
    padding: 0 20px;
  }
  .add-btn {
    padding: 5px;
    border-radius: 3px;
    display: flex;
    align-items: center;
    cursor: pointer;
  }
  .add-active {
    background-color: rgba(24, 160, 88, 0.1);
    color: #18a058;
  }
</style>
