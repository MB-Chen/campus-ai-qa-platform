<template>
  <n-layout class="layout" :position="fixedMenu" has-sider>
    <n-layout-sider
      v-if="
        !isMobile && isMixMenuNoneSub && (navMode === 'vertical' || navMode === 'horizontal-mix')
      "
      :position="fixedMenu"
      :collapsed="collapsed"
      collapse-mode="width"
      :collapsed-width="64"
      :width="leftMenuWidth"
      :native-scrollbar="false"
      :inverted="inverted"
      class="layout-sider"
    >
      <Logo :collapsed="collapsed" />
      <AsideMenu v-model:collapsed="collapsed" v-model:location="getMenuLocation" />
    </n-layout-sider>

    <!-- 侧边栏底部用户区 + 操作区，独立 fixed 在浏览器左下角 -->
    <div
      v-if="
        !isMobile && isMixMenuNoneSub && (navMode === 'vertical' || navMode === 'horizontal-mix')
      "
      class="sider-fixed-bottom"
      :class="{ collapsed }"
    >
      <!-- 当前登录用户 -->
      <n-dropdown
        placement="right-start"
        trigger="click"
        :options="userMenuOptions"
        @select="handleUserMenuSelect"
      >
        <div class="sider-user" :class="{ collapsed }">
          <n-avatar round size="small" :color="avatarBgColor" style="flex-shrink: 0">
            {{ userInitial }}
          </n-avatar>
          <span v-if="!collapsed" class="sider-user-name">{{ displayName }}</span>
          <n-icon v-if="!collapsed" size="14" class="sider-user-arrow">
            <RightOutlined />
          </n-icon>
        </div>
      </n-dropdown>

      <!-- 折叠 + 主题切换 -->
      <div class="sider-bottom-actions" :class="{ collapsed }">
        <div class="sider-action-btn" @click="collapsed = !collapsed">
          <n-icon size="18">
            <MenuUnfoldOutlined v-if="collapsed" />
            <MenuFoldOutlined v-else />
          </n-icon>
        </div>
        <div class="sider-action-btn" @click="toggleDarkTheme">
          <n-icon size="18">
            <Sunny v-if="!isDarkTheme" />
            <Moon v-else />
          </n-icon>
        </div>
      </div>
    </div>

    <!-- 修改密码弹窗 -->
    <n-modal v-model:show="showPwdModal" preset="card" title="修改密码" style="width: 420px">
      <n-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-placement="top">
        <n-form-item label="原密码" path="oldPassword">
          <n-input
            v-model:value="pwdForm.oldPassword"
            type="password"
            show-password-on="click"
            placeholder="请输入原密码"
          />
        </n-form-item>
        <n-form-item label="新密码" path="newPassword">
          <n-input
            v-model:value="pwdForm.newPassword"
            type="password"
            show-password-on="click"
            placeholder="请输入新密码"
          />
        </n-form-item>
        <n-form-item label="确认新密码" path="confirmPassword">
          <n-input
            v-model:value="pwdForm.confirmPassword"
            type="password"
            show-password-on="click"
            placeholder="请再次输入新密码"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showPwdModal = false">取消</n-button>
          <n-button type="primary" :loading="pwdLoading" @click="handleChangePassword"
            >确认修改</n-button
          >
        </n-space>
      </template>
    </n-modal>

    <n-drawer
      v-model:show="showSideDrawer"
      :width="menuWidth"
      :placement="'left'"
      class="layout-side-drawer"
    >
      <n-layout-sider
        :position="fixedMenu"
        :collapsed="false"
        :width="menuWidth"
        :native-scrollbar="false"
        :inverted="inverted"
        class="layout-sider"
      >
        <Logo :collapsed="collapsed" />
        <AsideMenu v-model:location="getMenuLocation" />
      </n-layout-sider>
    </n-drawer>

    <n-layout :inverted="inverted">
      <n-layout-content
        ref="contentRef"
        class="layout-content"
        :class="{ 'layout-default-background': getDarkTheme === false }"
      >
        <div class="layout-content-main">
          <div class="main-view">
            <MainView />
          </div>
        </div>
      </n-layout-content>
      <n-back-top v-if="contentRef?.value" :right="100" :listen-to="() => contentRef?.value" />
    </n-layout>
  </n-layout>
</template>

<script lang="ts" setup>
  import { ref, unref, computed, onMounted, reactive } from 'vue';
  import { useRouter, useRoute } from 'vue-router';
  import { useDialog, useMessage, useThemeVars } from 'naive-ui';
  import { Logo } from './components/Logo';
  import { MainView } from './components/Main';
  import { AsideMenu } from './components/Menu';
  import { MenuFoldOutlined, MenuUnfoldOutlined, RightOutlined } from '@vicons/antd';
  import { Moon, Sunny } from '@vicons/ionicons5';
  import { useProjectSetting } from '@/hooks/setting/useProjectSetting';
  import { useDesignSettingStore } from '@/store/modules/designSetting';
  import { useProjectSettingStore } from '@/store/modules/projectSetting';
  import { useUserStore } from '@/store/modules/user';
  import { changePassword } from '@/api/user';
  import { storage } from '@/utils/Storage';
  import { PageEnum } from '@/enums/pageEnum';

  const designStore = useDesignSettingStore();
  const { getDarkTheme } = { getDarkTheme: computed(() => designStore.darkTheme) };
  // naive-ui 当前主题变量（响应式）：暗色下 bodyColor 为真实黑色，亮色为白色；
  // primaryColor 跟随 appTheme，避免硬编码蓝色/绿色。
  const themeVars = useThemeVars();
  const { navMode, navTheme, headerSetting, menuSetting } = useProjectSetting();

  const settingStore = useProjectSettingStore();

  const collapsed = ref<boolean>(false);
  const contentRef = ref<HTMLElement | null>(null);

  // 暗色/亮色主题：初始化时读取持久化状态
  const isDarkTheme = ref<boolean>(storage.get('APP_DARK_THEME') || designStore.darkTheme || false);
  designStore.darkTheme = isDarkTheme.value;

  // 切换暗色/亮色主题
  function toggleDarkTheme() {
    isDarkTheme.value = !isDarkTheme.value;
    designStore.darkTheme = isDarkTheme.value;
    storage.set('APP_DARK_THEME', isDarkTheme.value);
  }

  // 当前登录用户
  const userStore = useUserStore();
  const router = useRouter();
  const dialog = useDialog();
  const message = useMessage();

  // 显示名优先昵称，其次账号
  const displayName = computed(() => {
    const info: any = userStore.getUserInfo || {};
    return info.username || info.account || '用户';
  });
  // 头像首字
  const userInitial = computed(() => {
    const name = displayName.value;
    return name ? name.charAt(0).toUpperCase() : 'U';
  });

  // 用户下拉菜单
  const userMenuOptions = computed(() => [
    { label: '修改密码', key: 'password' },
    { type: 'divider', key: 'd1' },
    { label: '退出登录', key: 'logout' },
  ]);

  function handleUserMenuSelect(key: string) {
    if (key === 'password') {
      showPwdModal.value = true;
    } else if (key === 'logout') {
      handleLogout();
    }
  }

  // 退出登录
  function handleLogout() {
    dialog.warning({
      title: '确认退出',
      content: '确定要退出登录吗？',
      positiveText: '退出',
      negativeText: '取消',
      onPositiveClick: async () => {
        await userStore.logout();
        router.replace(PageEnum.BASE_LOGIN);
      },
    });
  }

  // 修改密码
  const showPwdModal = ref(false);
  const pwdLoading = ref(false);
  const pwdFormRef = ref();
  const pwdForm = reactive({
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
  });
  const pwdRules = {
    oldPassword: { required: true, message: '请输入原密码', trigger: 'blur' },
    newPassword: { required: true, message: '请输入新密码', trigger: 'blur' },
    confirmPassword: {
      required: true,
      trigger: 'blur',
      validator: (_rule, value) =>
        value === pwdForm.newPassword || new Error('两次输入的密码不一致'),
    },
  };

  function handleChangePassword() {
    pwdFormRef.value?.validate(async (errors) => {
      if (errors) return;
      pwdLoading.value = true;
      try {
        const res = await changePassword({
          oldPassword: pwdForm.oldPassword,
          newPassword: pwdForm.newPassword,
        });
        if (res.code === 0) {
          message.success('密码修改成功，请重新登录');
          showPwdModal.value = false;
          pwdForm.oldPassword = '';
          pwdForm.newPassword = '';
          pwdForm.confirmPassword = '';
          setTimeout(async () => {
            await userStore.logout();
            router.replace(PageEnum.BASE_LOGIN);
          }, 1200);
        } else {
          message.error(res.message || '密码修改失败');
        }
      } catch (e) {
        message.error('密码修改失败，请重试');
      } finally {
        pwdLoading.value = false;
      }
    });
  }

  const { mobileWidth, menuWidth } = unref(menuSetting);

  const isMobile = computed<boolean>({
    get: () => settingStore.getIsMobile,
    set: (val) => settingStore.setIsMobile(val),
  });

  const isMixMenuNoneSub = computed(() => {
    const mixMenu = unref(menuSetting).mixMenu;
    const currentRoute = useRoute();
    if (unref(navMode) != 'horizontal-mix') return true;
    if (unref(navMode) === 'horizontal-mix' && mixMenu && currentRoute.meta.isRoot) {
      return false;
    }
    return true;
  });

  const fixedMenu = computed(() => {
    const { fixed } = unref(headerSetting);
    return fixed ? 'absolute' : 'static';
  });

  const inverted = computed(() => {
    return ['dark', 'header-dark'].includes(unref(navTheme));
  });

  const leftMenuWidth = computed(() => {
    const { minMenuWidth, menuWidth } = unref(menuSetting);
    return collapsed.value ? minMenuWidth : menuWidth;
  });

  // 侧边栏宽度（带 px），供 CSS v-bind 使用，footer 和底部操作区跟随
  const siderWidthPx = computed(() => `${unref(leftMenuWidth)}px`);

  // 侧边栏底部区背景色：取 naive-ui layout 的 sider 同源主题变量（响应式，无时序问题）
  //   sider 真实背景 = cardColor（亮色 #fff / 暗色 #101014），inverted 时 = invertedColor
  //   详见 naive-ui layout/styles/light.js：siderColor: cardColor, siderColorInverted: invertedColor
  const siderActionBg = computed(() => {
    return unref(inverted) ? themeVars.value.invertedColor : themeVars.value.cardColor;
  });

  // 后台底部 footer 背景：跟随内容区（右侧），与侧边栏无关
  //   - 亮色：内容区浅灰 #f5f7f9（与 .layout-default-background 一致）
  //   - 暗色：跟随 naive-ui 真实页面背景色（黑色系）
  const footerBg = computed(() => {
    return isDarkTheme.value ? themeVars.value.bodyColor : '#f5f7f9';
  });

  // 头像背景色：跟随系统主题色，避免硬编码绿色
  const avatarBgColor = computed(() => designStore.appTheme);

  // 侧边栏底部区文字色（确定性逻辑，无时序问题）：
  //   - inverted（暗色侧边栏）或全局暗色主题 → 浅色字（菜单背景为深色）
  //   - 否则（亮色侧边栏 + 亮色主题）→ 深色字
  const siderTextColor = computed(() => {
    return isDarkTheme.value || unref(inverted) ? 'rgba(255,255,255,0.82)' : 'rgba(0,0,0,0.82)';
  });

  const getMenuLocation = computed(() => {
    return 'left';
  });

  // 控制显示或隐藏移动端侧边栏
  const showSideDrawer = computed({
    get: () => isMobile.value && collapsed.value,
    set: (val) => (collapsed.value = val),
  });

  //判断是否触发移动端模式
  const checkMobileMode = () => {
    if (document.body.clientWidth <= mobileWidth) {
      isMobile.value = true;
    } else {
      isMobile.value = false;
    }
    collapsed.value = false;
  };

  const watchWidth = () => {
    const Width = document.body.clientWidth;
    if (Width <= 950) {
      collapsed.value = true;
    } else collapsed.value = false;

    checkMobileMode();
  };

  onMounted(() => {
    checkMobileMode();
    window.addEventListener('resize', watchWidth);
  });
</script>

<style lang="less">
  .layout-side-drawer {
    background-color: rgb(0, 20, 40);

    .layout-sider {
      min-height: 100vh;
      box-shadow: 2px 0 8px 0 rgb(29 35 41 / 5%);
      position: relative;
      z-index: 13;
      transition: all 0.2s ease-in-out;
    }
  }
</style>
<style lang="less" scoped>
  .layout {
    display: flex;
    flex-direction: row;
    flex: auto;

    &-default-background {
      background: #f5f7f9;
    }

    .layout-sider {
      min-height: 100vh;
      box-shadow: 2px 0 8px 0 rgb(29 35 41 / 5%);
      position: relative;
      z-index: 13;
      transition: all 0.2s ease-in-out;

      // 给底部固定区（用户信息 + 操作按钮）留出空间，避免菜单最后一项被遮挡
      .n-layout-sider-scroll-container {
        padding-bottom: 96px;
      }
    }

    .layout-sider-fix {
      position: fixed;
      top: 0;
      left: 0;
    }

    .ant-layout {
      overflow: hidden;
    }

    .layout-right-fix {
      overflow-x: hidden;
      padding-left: 200px;
      min-height: 100vh;
      transition: all 0.2s ease-in-out;
    }

    .layout-content {
      flex: auto;
      min-height: 100vh;
      height: 100vh;
      display: flex;
      flex-direction: column;
      overflow: auto;
    }

    .n-layout-header.n-layout-header--absolute-positioned {
      z-index: 11;
    }

    .layout-footer {
      position: fixed;
      bottom: 0;
      right: 0;
      text-align: center;
      padding: 8px 0;
      z-index: 10;
      // 背景跟随右侧内容区，不跟侧边栏
      background: v-bind('footerBg');
      // 顶部分隔线，跟随 naive-ui 主题（亮暗均可见）
      border-top: 1px solid v-bind('themeVars.dividerColor');
      // 左侧避开侧边栏，给底部操作按钮留出空间
      left: v-bind('siderWidthPx');
    }
  }

  .layout-content-main {
    margin: 10px;
    padding-bottom: 36px;
    flex: 1;

    &.no-spacing {
      margin-top: 0;
      margin-bottom: 0;
      padding-bottom: 0;
    }
  }

  .main-view {
    flex: 1;
  }

  // 侧边栏底部固定区（用户信息 + 操作按钮），固定在浏览器左下角
  // 背景取 naive-ui 同源主题变量（invertedColor / bodyColor），与菜单完全一致且无时序问题
  .sider-fixed-bottom {
    position: fixed;
    bottom: 0;
    left: 0;
    width: v-bind('siderWidthPx');
    z-index: 14;
    background-color: v-bind('siderActionBg');
    border-top: 1px solid v-bind('themeVars.dividerColor');
    transition: width 0.2s ease-in-out, background-color 0.2s;
  }

  // 当前登录用户
  .sider-user {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 14px;
    cursor: pointer;
    color: v-bind('siderTextColor');
    transition: background-color 0.2s;

    &:hover {
      background-color: rgba(127, 127, 127, 0.18);
    }

    &.collapsed {
      justify-content: center;
      padding: 8px 0;
    }

    .sider-user-name {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 14px;
    }

    .sider-user-arrow {
      flex-shrink: 0;
    }
  }

  // 操作区（折叠 + 主题切换），图标居中均匀分布
  .sider-bottom-actions {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    height: 48px;
    padding: 0 8px;

    &.collapsed {
      justify-content: center;
      padding: 0;
    }
  }

  .sider-action-btn {
    flex: 1;
    max-width: 40px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 4px;
    cursor: pointer;
    color: v-bind('siderTextColor');
    transition: background-color 0.2s;

    &:hover {
      background-color: rgba(127, 127, 127, 0.18);
    }
  }
</style>
