<template>
  <div
    :class="[
      prefixCls,
      {
        [`${prefixCls}--fixed`]: multiTabsSetting?.fixed || state.isMultiHeaderFixed,
      },
    ]"
    class="tabs-view"
  >
    <div class="tabs-view-main">
      <div v-if="state.scrollable" class="tabs-view-scroll-prev" @click="scrollPrev">
        <n-icon size="14" :component="LeftOutlined" />
      </div>
      <div class="tabs-view-scroll" ref="navScroll">
        <div
          v-for="item in tabsList"
          :key="item.fullPath"
          :class="[
            'tabs-card-scroll-item',
            {
              active: item.fullPath === state.activeKey,
            },
          ]"
          @click="handleClickTab(item)"
          @contextmenu="handleContextmenu(item, $event)"
        >
          <span class="tabs-card-scroll-item-title">{{ item.meta.title }}</span>
          <n-icon
            v-if="!item.meta?.affix"
            size="14"
            class="tabs-card-scroll-item-close"
            :component="CloseOutlined"
            @click.stop="handleCloseTab(item)"
          />
        </div>
      </div>
      <div v-if="state.scrollable" class="tabs-view-scroll-next" @click="scrollNext">
        <n-icon size="14" :component="RightOutlined" />
      </div>
      <div class="tabs-view-close" @click="handleContextMenu">
        <n-dropdown
          trigger="click"
          placement="bottom-end"
          :show="state.showDropdown"
          :options="TabsMenuOptions"
          :x="state.tabDropdown.x"
          :y="state.tabDropdown.y"
          :show-arrow="true"
          @clickoutside="state.showDropdown = false"
          @select="handleSelect"
        />
        <n-icon size="16" :component="DownOutlined" />
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
  import { useRouter, useRoute } from 'vue-router';
  import { computed, nextTick, onMounted, ref, watch, provide } from 'vue';
  import { useTabsViewStore } from '@/store/modules/tabsView';
  import { useAsyncRouteStore } from '@/store/modules/asyncRoute';
  import { storage } from '@/utils/Storage';
  import { TABS_ROUTES } from '@/store/mutation-types';
  import { useProjectSetting } from '@/hooks/setting/useProjectSetting';
  import type { RouteItem } from '@/store/modules/tabsView';
  import { useMessage } from 'naive-ui';
  import { PageEnum } from '@/enums/pageEnum';
  import { ReloadOutlined, CloseOutlined, ColumnWidthOutlined, MinusOutlined, LeftOutlined, RightOutlined, DownOutlined } from '@vicons/antd';
  import { renderIcon } from '@/utils';

  const { getHeaderSetting, getMultiTabsSetting } = useProjectSetting();
  const headerSetting = computed(() => getHeaderSetting);
  const multiTabsSetting = computed(() => getMultiTabsSetting);

  const router = useRouter();
  const route = useRoute();
  const tabsViewStore = useTabsViewStore();
  const asyncRouteStore = useAsyncRouteStore();
  // 移除 useDesign hook，使用固定的 prefixCls
  const prefixCls = 'tabs-view';
  const message = useMessage();

  const navScroll = ref<any>(null);
  const isCurrent = computed(() => {
    return route.fullPath === state.value.activeKey;
  });

  const state = ref({
    activeKey: route.fullPath,
    scrollable: false,
    showDropdown: false,
    tabDropdown: {
      x: 0,
      y: 0,
    },
    isMultiHeaderFixed: false,
  });

  // 获取简易路由
  const getSimpleRoute = (route) => {
    const { fullPath, hash, meta, name, params, path, query } = route;
    return { fullPath, hash, meta, name, params, path, query };
  };

  //tags 右侧下拉菜单
  const TabsMenuOptions = computed(() => {
    const isDisabled = tabsList.value.length <= 1;
    return [
      {
        label: '刷新当前',
        key: '1',
        icon: renderIcon(ReloadOutlined),
      },
      {
        label: `关闭当前`,
        key: '2',
        disabled: isCurrent.value || isDisabled,
        icon: renderIcon(CloseOutlined),
      },
      {
        label: '关闭其他',
        key: '3',
        disabled: isDisabled,
        icon: renderIcon(ColumnWidthOutlined),
      },
      {
        label: '关闭全部',
        key: '4',
        disabled: isDisabled,
        icon: renderIcon(MinusOutlined),
      },
    ];
  });

  let cacheRoutes: RouteItem[] = [];
  const simpleRoute = getSimpleRoute(route);
  try {
    const routesStr = storage.get(TABS_ROUTES) as string | null | undefined;
    cacheRoutes = routesStr ? JSON.parse(routesStr) : [simpleRoute];
  } catch (e) {
    cacheRoutes = [simpleRoute];
  }

  // 将最新的路由信息同步到 localStorage 中
  const routes = router.getRoutes();
  cacheRoutes.forEach((cacheRoute) => {
    const route = routes.find((route) => route.path === cacheRoute.path);
    if (route) {
      cacheRoute.meta = route.meta || cacheRoute.meta;
      cacheRoute.name = (route.name || cacheRoute.name) as string;
    }
  });

  // 初始化标签页
  tabsViewStore.initTabs(cacheRoutes);

  //监听滚动条
  function onScroll(e) {
    let scrollTop =
      e.target.scrollTop ||
      document.documentElement.scrollTop ||
      window.pageYOffset ||
      document.body.scrollTop; // 滚动条偏移量

    // 安全检查，确保设置对象存在
    const headerFixed = headerSetting.value?.fixed ?? false;
    const multiTabsFixed = multiTabsSetting.value?.fixed ?? false;

    state.value.isMultiHeaderFixed = !!(!headerFixed && multiTabsFixed && scrollTop >= 64);
  }

  window.addEventListener('scroll', onScroll, true);

  // 移除缓存组件名称
  const delKeepAliveCompName = () => {
    if (route.meta.keepAlive) {
      const name = router.currentRoute.value.matched.find((item) => item.name == route.name)
        ?.components?.default.name;
      if (name) {
        asyncRouteStore.keepAliveComponents = asyncRouteStore.keepAliveComponents.filter(
          (item) => item != name
        );
      }
    }
  };

  // 标签页列表
  const tabsList: any = computed(() => tabsViewStore.tabsList);
  const whiteList: string[] = [
    PageEnum.BASE_LOGIN_NAME,
    PageEnum.REDIRECT_NAME,
    PageEnum.ERROR_PAGE_NAME,
  ];

  watch(
    () => route.fullPath,
    (to) => {
      if (whiteList.includes(route.name as string)) return;
      state.value.activeKey = to;
      tabsViewStore.addTab(getSimpleRoute(route));
      updateNavScroll(true);
    },
    { immediate: true }
  );

  // 在页面关闭或刷新之前，保存数据
  window.addEventListener('beforeunload', () => {
    storage.set(TABS_ROUTES, JSON.stringify(tabsList.value));
  });

  // 关闭当前页面
  const removeTab = (route) => {
    if (tabsList.value.length === 1) {
      return message.warning('这已经是最后一页，不能再关闭了！');
    }
    delKeepAliveCompName();
    tabsViewStore.closeCurrentTab(route);
    // 如果关闭的是当前页
    if (state.value.activeKey === route.fullPath) {
      const currentRoute = tabsList.value[Math.max(0, tabsList.value.length - 1)];
      state.value.activeKey = currentRoute.fullPath;
      router.push(currentRoute);
    }
    updateNavScroll();
  };

  // 刷新页面
  const reloadPage = () => {
    delKeepAliveCompName();
    router.push({
      path: '/redirect' + route.fullPath,
    });
  };

  // 注入刷新页面方法
  provide('reloadPage', reloadPage);

  // 关闭左侧
  const closeLeft = (route) => {
    tabsViewStore.closeLeftTabs(route);
    state.value.activeKey = route.fullPath;
    router.replace(route.fullPath);
    updateNavScroll();
  };

  // 关闭右侧
  const closeRight = (route) => {
    tabsViewStore.closeRightTabs(route);
    state.value.activeKey = route.fullPath;
    router.replace(route.fullPath);
    updateNavScroll();
  };

  // 关闭其他
  const closeOther = (route) => {
    tabsViewStore.closeOtherTabs(route);
    state.value.activeKey = route.fullPath;
    router.replace(route.fullPath);
    updateNavScroll();
  };

  // 关闭全部
  const closeAll = () => {
    tabsViewStore.closeAllTabs();
    router.replace(PageEnum.BASE_HOME);
    updateNavScroll();
  };

  //tab 操作
  const closeHandleSelect = (key) => {
    switch (key) {
      //刷新
      case '1':
        reloadPage();
        break;
      //关闭
      case '2':
        removeTab(route);
        break;
      //关闭其他
      case '3':
        closeOther(route);
        break;
      //关闭所有
      case '4':
        closeAll();
        break;
    }
    updateNavScroll();
    state.value.showDropdown = false;
  };

  /**
   * @param value 要滚动到的位置
   * @param amplitude 每次滚动的长度
   */
  function scrollTo(value: number, amplitude: number) {
    const currentScroll = navScroll.value.scrollLeft;
    const scrollWidth =
      (amplitude > 0 && currentScroll + amplitude >= value) ||
      (amplitude < 0 && currentScroll + amplitude <= value)
        ? value
        : currentScroll + amplitude;
    navScroll.value && navScroll.value.scrollTo(scrollWidth, 0);
    if (scrollWidth === value) return;
    return window.requestAnimationFrame(() => scrollTo(value, amplitude));
  }

  function scrollPrev() {
    const containerWidth = navScroll.value.offsetWidth;
    const currentScroll = navScroll.value.scrollLeft;

    if (!currentScroll) return;
    const scrollLeft = currentScroll > containerWidth ? currentScroll - containerWidth : 0;
    scrollTo(scrollLeft, (scrollLeft - currentScroll) / 20);
  }

  function scrollNext() {
    const containerWidth = navScroll.value.offsetWidth;
    const navWidth = navScroll.value.scrollWidth;
    const currentScroll = navScroll.value.scrollLeft;

    if (navWidth - currentScroll <= containerWidth) return;
    const scrollLeft =
      navWidth - currentScroll > containerWidth * 2
        ? currentScroll + containerWidth
        : navWidth - containerWidth;
    scrollTo(scrollLeft, (scrollLeft - currentScroll) / 20);
  }

  /**
   * @param autoScroll 是否开启自动滚动功能
   */
  async function updateNavScroll(autoScroll?: boolean) {
    await nextTick();
    if (!navScroll.value) return;
    const containerWidth = navScroll.value.offsetWidth;
    const navWidth = navScroll.value.scrollWidth;

    if (containerWidth < navWidth) {
      state.value.scrollable = true;
      if (autoScroll) {
        let tagList = navScroll.value.querySelectorAll('.tabs-card-scroll-item') || [];
        [...tagList].forEach((tag: HTMLElement) => {
          // fix: tagsView多标签时，点击当前标签滚动到最左边，导致当前标签被遮挡
          const tabItem = navScroll.value.querySelector('.tabs-card-scroll-item.active');
          if (!tabItem) return;
          const tabItemLeft = tabItem.offsetLeft;
          const tabItemWidth = tabItem.offsetWidth;
          const navScrollWidth = navScroll.value.scrollWidth;
          const navScrollLeft = navScroll.value.scrollLeft;
          const navWidth = navScroll.value.offsetWidth;
          // 标签页居中
          const scrollPosition = tabItemLeft - (navWidth - tabItemWidth) / 2;
          // 最大滚动位置
          const maxScroll = navScrollWidth - navWidth;
          // 确保滚动位置在有效范围内
          const finalScroll = Math.max(0, Math.min(scrollPosition, maxScroll));
          scrollTo(finalScroll, (finalScroll - navScrollLeft) / 20);
        });
      }
    } else {
      state.value.scrollable = false;
    }
  }

  function handleContextmenu(tabItem, e) {
    e.preventDefault();
    state.value.showDropdown = false;
    nextTick().then(() => {
      state.value.showDropdown = true;
      state.value.tabDropdown = {
        x: e.clientX,
        y: e.clientY,
      };
    });
  }

  function handleSelect(key) {
    closeHandleSelect(key);
  }

  function handleCloseTab(tabItem) {
    removeTab(tabItem);
  }

  function handleClickTab(tabItem) {
    state.value.activeKey = tabItem.fullPath;
    router.push(tabItem);
  }

  function handleContextMenu(e) {
    e.preventDefault();
    state.value.showDropdown = false;
    nextTick().then(() => {
      state.value.showDropdown = true;
      state.value.tabDropdown = {
        x: e.clientX,
        y: e.clientY,
      };
    });
  }

  onMounted(() => {
    updateNavScroll();
  });
</script>

<style lang="less" scoped>
.tabs-view {
  position: relative;
  background-color: v-bind('multiTabsSetting?.bgColor || "#fff"');
  border-top: 1px solid #f0f0f0;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  z-index: 10;

  &--fixed {
    position: fixed;
    top: 64px;
    left: 0;
    right: 0;
    z-index: 99;
  }

  &-main {
    display: flex;
    align-items: center;
    padding: 0 10px;
    height: 40px;
    user-select: none;
  }

  &-scroll {
    flex: 1;
    overflow: hidden;
    white-space: nowrap;
    margin: 0 10px;
  }

  &-scroll-prev,
  &-scroll-next {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    border-radius: 4px;
    transition: all 0.3s;

    &:hover {
      background-color: #f5f5f5;
    }
  }

  &-close {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    border-radius: 4px;
    transition: all 0.3s;

    &:hover {
      background-color: #f5f5f5;
    }
  }
}

.tabs-card-scroll-item {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 12px;
  margin-right: 6px;
  border-radius: 3px;
  background-color: #f5f5f5;
  border: 1px solid #e8e8e8;
  cursor: pointer;
  transition: all 0.3s;
  font-size: 12px;

  &:hover {
    background-color: #e8e8e8;
  }

  &.active {
    background-color: #1890ff;
    border-color: #1890ff;
    color: #fff;

    .tabs-card-scroll-item-close {
      color: #fff;
      
      &:hover {
        background-color: rgba(255, 255, 255, 0.2);
      }
    }
  }

  &-title {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &-close {
    margin-left: 8px;
    width: 16px;
    height: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 2px;
    color: rgba(0, 0, 0, 0.45);
    transition: all 0.3s;

    &:hover {
      background-color: rgba(0, 0, 0, 0.1);
    }
  }
}
</style>
