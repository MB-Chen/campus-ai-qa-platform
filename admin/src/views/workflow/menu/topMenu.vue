<!-- 编辑器顶部菜单：返回 / 调试 / 保存 -->
<template>
  <div class="top-menu flex-center">
    <div class="menu-btn flex-center" @click="$emit('back')">
      <n-icon :size="20" color="#18a058" style="margin-right: 5px">
        <ArrowLeftOutlined />
      </n-icon>
      返回
    </div>
    <div class="menu-btn flex-center" @click="$emit('debug')">
      <n-icon :size="20" color="#17b26a" style="margin-right: 5px">
        <PlayCircleOutlined />
      </n-icon>
      调试
    </div>
    <div class="menu-btn flex-center" @click="$emit('save')">
      <n-icon :size="20" color="#6172f3" style="margin-right: 5px">
        <SaveOutlined />
      </n-icon>
      保存
    </div>
  </div>
</template>

<script setup>
  import { ArrowLeftOutlined, PlayCircleOutlined, SaveOutlined } from '@vicons/antd';
</script>

<style scoped>
  .top-menu {
    right: 0;
    padding-right: 20px;
    margin-top: 10px;
  }
  .menu-btn {
    padding: 5px 20px;
    border-radius: 5px;
    background: #fff;
    box-shadow: 0 1px 2px 0 rgba(16, 24, 40, 0.05);
    margin-left: 10px;
    cursor: pointer;
    font-size: 14px;
  }
  .menu-btn:hover {
    background: #f6ffed;
  }
</style>
