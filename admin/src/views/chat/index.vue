<template>
  <div class="chat-placeholder">
    <n-card class="placeholder-card" :bordered="false">
      <n-empty description="新对话 - 开发中">
        <template #icon>
          <n-icon>
            <MessageOutlined />
          </n-icon>
        </template>
        <template #extra>
          <n-tag type="warning" size="small" round>占位页面</n-tag>
        </template>
      </n-empty>
    </n-card>
  </div>
</template>

<script lang="ts" setup name="Chat">
  import { MessageOutlined } from '@vicons/antd';
</script>

<style lang="less" scoped>
  .chat-placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    width: 100%;

    .placeholder-card {
      width: 100%;
      max-width: 480px;
      text-align: center;
    }
  }
</style>
