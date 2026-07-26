import { App } from 'vue';
import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router';
import { RedirectRoute } from '@/router/base';
import { Layout } from '@/router/constant';
import { PageEnum } from '@/enums/pageEnum';
import { createRouterGuards } from './guards';
import type { IModuleType } from './types';

const modules = import.meta.glob<IModuleType>('./modules/**/*.ts', { eager: true });

const routeModuleList: RouteRecordRaw[] = Object.keys(modules).reduce((list, key) => {
  const mod = modules[key].default ?? {};
  const modList = Array.isArray(mod) ? [...mod] : [mod];
  return [...list, ...modList];
}, []);

function sortRoute(a, b) {
  return (a.meta?.sort ?? 0) - (b.meta?.sort ?? 0);
}

routeModuleList.sort(sortRoute);

export const RootRoute: RouteRecordRaw = {
  path: '/',
  name: 'Root',
  redirect: PageEnum.BASE_HOME_REDIRECT,
  meta: {
    title: 'Root',
  },
};

export const LoginRoute: RouteRecordRaw = {
  path: '/login',
  name: 'Login',
  component: () => import('@/views/login/index.vue'),
  meta: {
    title: '登录',
  },
};

export const DashboardRoute: RouteRecordRaw = {
  path: '/dashboard',
  name: 'Dashboard',
  redirect: '/dashboard/console',
  meta: {
    title: 'Dashboard',
    icon: 'DashboardOutlined',
  },
  children: [
    {
      path: 'console',
      name: 'dashboard_console',
      component: () => import('@/views/dashboard/index.vue'),
      meta: {
        title: '主控台',
      },
    },
  ],
};

// ★ 知识图谱可视化子页面（无独立菜单，从「知识图谱」主页按钮带 query.kbId 跳入）
// 不走动态路由（后端菜单 type=2 是按钮权限，不生成页面路由），这里注册为普通路由。
// 用 Layout 包裹保证侧栏/头部一致；path 用独立前缀 /kgviz 避免与动态路由 /knowledge/* 冲突。
export const KgVisualizationRoute: RouteRecordRaw = {
  path: '/kgviz',
  name: 'KgVisualizationLayout',
  component: Layout,
  meta: {
    title: '图谱可视化',
    hideInMenu: true,
  },
  children: [
    {
      path: 'visualization',
      name: 'KgVisualization',
      component: () => import('@/views/knowledge/graph/visualization.vue'),
      meta: {
        title: '图谱可视化',
        hideInMenu: true,
      },
    },
  ],
};

//需要验证权限
export const asyncRoutes = [...routeModuleList];

//普通路由 无需验证权限
export const constantRouter: RouteRecordRaw[] = [
  LoginRoute,
  DashboardRoute,
  KgVisualizationRoute,
  RootRoute,
  RedirectRoute,
];

const router = createRouter({
  history: createWebHistory(),
  routes: constantRouter,
  strict: true,
  scrollBehavior: () => ({ left: 0, top: 0 }),
});

export function setupRouter(app: App) {
  app.use(router);
  // 创建路由守卫
  createRouterGuards(router);
  // 将 router 实例挂载到 window 对象，以便在非组件环境中使用
  (window as any).$router = router;
  (window as any).$route = router.currentRoute.value;
}

export default router;
