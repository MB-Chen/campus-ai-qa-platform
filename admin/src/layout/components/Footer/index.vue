<template>
  <div class="page-footer"></div>
</template>

<script lang="ts">
  export default {
    name: 'PageFooter',
  };
</script>

<script lang="ts" setup>
  // footer 颜色跟随 naive-ui 主题变量，避免黑色主题下文字过深 / hover 显示蓝色
  import { useThemeVars } from 'naive-ui';
  import { useDesignSettingStore } from '@/store/modules/designSetting';

  const themeVars = useThemeVars();
  const designStore = useDesignSettingStore();
</script>

<style lang="less" scoped>
  .page-footer {
    padding: 12px 16px;
    text-align: center;

    a {
      font-size: 13px;
      color: v-bind('themeVars.textColor3');
      text-decoration: none;
      transition: all 0.2s ease-in-out;

      &:hover {
        // 跟随系统主题色，不再写死 #2d8cf0
        color: v-bind('designStore.appTheme');
      }
    }
  }
</style>
