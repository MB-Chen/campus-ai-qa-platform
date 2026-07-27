<!-- 开始节点配置：展示系统变量（只读） -->
<template>
  <div class="opt-form">
    <div class="title-row">
      <n-icon :component="meta.icon" :color="meta.color" :size="20" />
      <span class="node-name">开始</span>
    </div>

    <div class="set-content-box">
      <div class="section-title">系统变量</div>
      <div class="param-data">
        <div
          class="flex-center data-item"
          v-for="(item, index) in formData.sysData"
          :key="index"
        >
          <div class="flex-center">
            <div class="menu-icon">
              <n-icon :component="meta.icon" color="#fff" :size="14" />
            </div>
            <div class="var-field">{{ item.field }}</div>
          </div>
          <div class="var-name">{{ item.name }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
  import { iconComponent } from '@/views/workflow/icons/index.js';
  const meta = iconComponent('start-node');
  defineProps({ formData: { type: Object, default: () => ({}) } });
</script>

<style scoped>
  .opt-form {
    width: 100%;
    background: #f4f4f4;
    border-radius: 5px;
    padding: 20px;
  }
  .title-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 16px;
  }
  .node-name {
    font-weight: bold;
  }
  .section-title {
    font-weight: 600;
    margin-bottom: 8px;
  }
  .param-data {
    display: flex;
    flex-direction: column;
  }
  .data-item {
    padding: 10px;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    background: #fff;
    border-radius: 5px;
  }
  .menu-icon {
    background: #6172f3;
    padding: 3px;
    border-radius: 5px;
  }
  .var-field {
    margin-left: 10px;
    font-size: 13px;
  }
  .var-name {
    font-size: 13px;
    color: #646a73;
  }
</style>
