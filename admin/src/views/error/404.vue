<template>
  <div class="error-page">
    <h1>404</h1>
    <p>页面未找到</p>
    <n-button type="primary" @click="$router.push('/')">返回首页</n-button>
  </div>
</template>

<script setup lang="ts">
  // 简单的404页面
</script>

<style scoped>
  .error-page {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100vh;
    text-align: center;
  }
  .error-page h1 {
    font-size: 72px;
    margin-bottom: 16px;
  }
  .error-page p {
    font-size: 18px;
    margin-bottom: 24px;
  }
</style>
