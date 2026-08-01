<template>
  <div class="logo">
    <h2 v-show="!collapsed" class="title">SparkX</h2>
  </div>
</template>

<script lang="ts">
  export default {
    name: 'Index',
    props: {
      collapsed: {
        type: Boolean,
      },
    },
  };
</script>

<style lang="less" scoped>
  .logo {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 64px;
    line-height: 64px;
    overflow: hidden;
    white-space: nowrap;
    font-weight: 700;
    font-size: 20px;

    img {
      width: auto;
      height: 32px;
    }

    .title {
      margin: 0;
      font-weight: 700;
      letter-spacing: 1px;
      background: linear-gradient(120deg, #10b981 0%, #059669 50%, #047857 100%);
      background-size: 200% auto;
      -webkit-background-clip: text;
      background-clip: text;
      -webkit-text-fill-color: transparent;
      color: transparent;
      animation: title-shine 5s linear infinite;
    }

    @keyframes title-shine {
      to {
        background-position: 200% center;
      }
    }
  }
</style>
