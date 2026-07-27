<template>
  <div class="logo">
    <!--<img :src="websiteConfig.logo" alt="" :class="{ 'mr-2': !collapsed }" />-->
    <h2 v-show="!collapsed" class="title">{{ websiteConfig.title }}</h2>
  </div>
</template>

<script lang="ts">
  import { websiteConfig } from '@/config/website.config';
  export default {
    name: 'Index',
    props: {
      collapsed: {
        type: Boolean,
      },
    },
    data() {
      return {
        websiteConfig,
      };
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
      font-weight: 800;
      letter-spacing: 1px;
      background: linear-gradient(120deg, #00d4a3 0%, #18a058 40%, #4ade80 70%, #00d4a3 100%);
      background-size: 200% auto;
      -webkit-background-clip: text;
      background-clip: text;
      -webkit-text-fill-color: transparent;
      color: transparent;
      animation: title-shine 4s linear infinite;
    }

    @keyframes title-shine {
      to {
        background-position: 200% center;
      }
    }
  }
</style>
