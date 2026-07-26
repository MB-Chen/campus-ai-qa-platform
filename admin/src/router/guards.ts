import { PageEnum } from '@/enums/pageEnum';
import { ErrorPageRoute } from '@/router/base';
import { useAsyncRoute } from '@/store/modules/asyncRoute';
import { useUser } from '@/store/modules/user';
import { ACCESS_TOKEN } from '@/store/mutation-types';
import { storage } from '@/utils/Storage';
import type { RouteRecordRaw } from 'vue-router';
import { isNavigationFailure, Router } from 'vue-router';
import { RedirectName } from './constant';
import { websiteConfig, applyBaseConfig } from '@/config/website.config';

const LOGIN_PATH = PageEnum.BASE_LOGIN;

const whitePathList = [LOGIN_PATH]; // no redirect whitelist

// 标记登录后是否已拉取过基础配置（避免每次路由切换重复请求）
let baseConfigLoaded = false;

export function createRouterGuards(router: Router) {
  const userStore = useUser();
  const asyncRouteStore = useAsyncRoute();
  router.beforeEach(async (to, from, next) => {
    const Loading = window['$loading'] || null;
    Loading && Loading.start();
    if (from.path === LOGIN_PATH && to.name === 'errorPage') {
      next(PageEnum.BASE_HOME);
      return;
    }

    // Whitelist can be directly entered
    if (whitePathList.includes(to.path as PageEnum)) {
      next();
      return;
    }

    const token = storage.get(ACCESS_TOKEN);

    if (!token) {
      // You can access without permissions. You need to set the routing meta.ignoreAuth to true
      if (to.meta.ignoreAuth) {
        next();
        return;
      }
      // redirect login page
      const redirectData: { path: string; replace: boolean; query?: Recordable<string> } = {
        path: LOGIN_PATH,
        replace: true,
      };
      if (to.path) {
        redirectData.query = {
          ...redirectData.query,
          redirect: to.path === '/' ? PageEnum.BASE_HOME_REDIRECT : to.path,
        };
      }
      next(redirectData);
      return;
    }

    if (asyncRouteStore.getIsDynamicRouteAdded) {
      // 已登录但尚未拉取过基础配置时补拉一次（覆盖从登录页跳转进来的场景）
      if (token && !baseConfigLoaded) {
        baseConfigLoaded = true;
        applyBaseConfig();
      }
      // 即使动态路由已加载，也要检查是否需要重定向
      const redirectPath = (from.query.redirect || to.query.redirect || to.path) as string;
      const redirect = decodeURIComponent(redirectPath);
      if (to.path !== redirect) {
        next({ path: redirect, replace: true });
      } else {
        next();
      }
      return;
    }

    try {
      const userInfo = await userStore.getInfo();
      const routes = await asyncRouteStore.generateRoutes(userInfo);

      // 动态添加可访问路由表
      routes.forEach((item) => {
        router.addRoute(item as unknown as RouteRecordRaw);
      });

      //添加404
      const isErrorPage = router.getRoutes().findIndex((item) => item.name === ErrorPageRoute.name);
      if (isErrorPage === -1) {
        router.addRoute(ErrorPageRoute as unknown as RouteRecordRaw);
      }

      // 检查是否有重定向参数，优先使用 from.query.redirect（登录页面传过来的），其次是 to.query.redirect
      let redirectPath = to.path;
      if (from.query.redirect) {
        redirectPath = from.query.redirect as string;
      } else if (to.query.redirect) {
        redirectPath = to.query.redirect as string;
      }

      const redirect = decodeURIComponent(redirectPath);
      const nextData = to.path === redirect ? { ...to, replace: true } : { path: redirect };
      asyncRouteStore.setDynamicRouteAdded(true);
      next(nextData);
    } catch (error) {
      console.error('路由守卫错误:', error);
      next(PageEnum.BASE_HOME_REDIRECT);
    }
    Loading && Loading.finish();
  });

  router.afterEach((to, _, failure) => {
    const routeTitle = to?.meta?.title as string;
    document.title = routeTitle ? (websiteConfig.title || '江油康养服务商城') : document.title;
    if (isNavigationFailure(failure)) {
      //console.log('failed navigation', failure)
    }
    const asyncRouteStore = useAsyncRoute();
    // 在这里设置需要缓存的组件名称
    const keepAliveComponents = asyncRouteStore.keepAliveComponents;
    const currentComName: any = to.matched.find((item) => item.name == to.name)?.name;
    if (currentComName && !keepAliveComponents.includes(currentComName) && to.meta?.keepAlive) {
      // 需要缓存的组件
      keepAliveComponents.push(currentComName);
    } else if (!to.meta?.keepAlive || to.name == RedirectName) {
      // 不需要缓存的组件
      const index = asyncRouteStore.keepAliveComponents.findIndex((name) => name == currentComName);
      if (index != -1) {
        keepAliveComponents.splice(index, 1);
      }
    }
    asyncRouteStore.setKeepAliveComponents(keepAliveComponents);
    const Loading = window['$loading'] || null;
    Loading && Loading.finish();
  });

  router.onError((error) => {
    console.log(error, '路由错误');
  });
}
