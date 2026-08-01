<template>
  <NConfigProvider
    v-if="!isLock"
    :locale="zhCN"
    :theme="getDarkTheme"
    :theme-overrides="getThemeOverrides"
    :date-locale="dateZhCN"
  >
    <AppProvider>
      <RouterView />
      <MessageNotification />
    </AppProvider>
  </NConfigProvider>

  <transition v-if="isLock && $route.name !== 'login'" name="slide-up">
    <LockScreen />
  </transition>
</template>

<script lang="ts" setup>
  import { computed, onMounted, onUnmounted } from 'vue';
  import { zhCN, dateZhCN, darkTheme } from 'naive-ui';
  import { LockScreen } from '@/components/Lockscreen';
  import { AppProvider } from '@/components/Application';
  import { useScreenLockStore } from '@/store/modules/screenLock.js';
  import { useDesignSettingStore } from '@/store/modules/designSetting';
  import { buildThemeOverrides } from '@/settings/theme';

  const useScreenLock = useScreenLockStore();
  const designStore = useDesignSettingStore();
  const isLock = computed(() => useScreenLock.isLocked);

  // 全局主题覆盖：翡翠绿 Apple 风（圆角/阴影/字重/过渡曲线 + 逐组件精修）
  const getThemeOverrides = computed(() => buildThemeOverrides(designStore.appTheme));

  const getDarkTheme = computed(() => (designStore.darkTheme ? darkTheme : undefined));

  onMounted(() => {
    // document.addEventListener('mousedown', timekeeping); // 自动锁屏监听已关闭
  });

  onUnmounted(() => {
    // document.removeEventListener('mousedown', timekeeping); // 自动锁屏监听已关闭
  });
</script>
