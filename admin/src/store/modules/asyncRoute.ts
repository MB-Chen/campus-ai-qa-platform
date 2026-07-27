import { toRaw, unref } from 'vue';
import { defineStore } from 'pinia';
import { RouteRecordRaw } from 'vue-router';
import { store } from '@/store';
import { asyncRoutes, constantRouter } from '@/router/index';
import { useProjectSetting } from '@/hooks/setting/useProjectSetting';
import { useUser } from '@/store/modules/user';
import { generateRoutes, asyncImportRoute } from '@/router/generator';
import { preloadIcons } from '@/utils/iconLoader';
import { STATIC_MENUS } from '@/router/staticMenus';

interface TreeHelperConfig {
  id: string;
  children: string;
  pid: string;
}

const DEFAULT_CONFIG: TreeHelperConfig = {
  id: 'id',
  children: 'children',
  pid: 'pid',
};

const getConfig = (config: Partial<TreeHelperConfig>) => Object.assign({}, DEFAULT_CONFIG, config);

/**
 * 从菜单数据中提取所有图标名称
 * @param menus 菜单数据
 * @returns 图标名称数组
 */
function extractIconNames(menus: any[]): string[] {
  const iconNames = new Set<string>();

  function extractFromMenu(items: any[]) {
    items.forEach((item) => {
      if (item.icon && typeof item.icon === 'string') {
        iconNames.add(item.icon);
      }
      if (item.children && item.children.length > 0) {
        extractFromMenu(item.children);
      }
    });
  }

  extractFromMenu(menus);
  return Array.from(iconNames);
}

export interface IAsyncRouteState {
  menus: RouteRecordRaw[];
  routers: any[];
  routersAdded: any[];
  keepAliveComponents: string[];
  isDynamicRouteAdded: boolean;
}

function filter<T = any>(
  tree: T[],
  func: (n: T) => boolean,
  config: Partial<TreeHelperConfig> = {}
): T[] {
  config = getConfig(config);
  const children = config.children as string;

  function listFilter(list: T[]) {
    return list
      .map((node: any) => ({ ...node }))
      .filter((node) => {
        node[children] = node[children] && listFilter(node[children]);
        return func(node) || (node[children] && node[children].length);
      });
  }

  return listFilter(tree);
}

export const useAsyncRouteStore = defineStore({
  id: 'app-async-route',
  state: (): IAsyncRouteState => ({
    menus: [],
    routers: constantRouter,
    routersAdded: [],
    keepAliveComponents: [],
    isDynamicRouteAdded: false,
  }),
  getters: {
    getMenus(): RouteRecordRaw[] {
      return this.menus;
    },
    getIsDynamicRouteAdded(): boolean {
      return this.isDynamicRouteAdded;
    },
  },
  actions: {
    getRouters() {
      return toRaw(this.routersAdded);
    },
    setDynamicRouteAdded(added: boolean) {
      this.isDynamicRouteAdded = added;
    },
    // 设置动态路由
    setRouters(routers: RouteRecordRaw[]) {
      this.routersAdded = routers;
      this.routers = constantRouter.concat(routers);
    },
    setMenus(menus: RouteRecordRaw[]) {
      // 设置动态路由
      this.menus = menus;
    },
    setKeepAliveComponents(compNames: string[]) {
      // 设置需要缓存的组件
      this.keepAliveComponents = compNames;
    },
    async generateRoutes(data: any) {
      let accessedRouters: any[] = [];
      const { permissionMode } = useProjectSetting();

      if (unref(permissionMode) === 'BACK') {
        // 菜单写死在前端（STATIC_MENUS），不再依赖后端 /login/doLogin 下发的 menu 字段。
        // 后续若恢复动态菜单，把这里的 STATIC_MENUS 换回 userStore.getMenus 即可。
        const userMenus = STATIC_MENUS;

        // 预加载菜单中的所有图标
        const iconNames = extractIconNames(userMenus);
        if (iconNames.length > 0) {
          preloadIcons(iconNames).catch((error) => {
            console.warn('图标预加载失败:', error);
          });
        }

        // 使用路由生成器处理菜单数据
        accessedRouters = generateRoutes(userMenus);
        asyncImportRoute(accessedRouters);

        // 为 Dashboard 父菜单添加重定向到第一个子路由（覆盖 constantRouter 中同名记录）
        const dashboardRoute = accessedRouters.find(
          (route: any) =>
            route.name === 'Dashboard' && route.children && route.children.length > 0
        );
        if (dashboardRoute && !dashboardRoute.redirect) {
          if (dashboardRoute.children) {
            dashboardRoute.redirect = dashboardRoute.children[0].path;
          }
        }
      } else {
        try {
          // 使用静态路由（前端静态配置）
          const permissionsList = data.permissions ?? [];
          const routeFilter = (route: any) => {
            const { meta } = route;
            const { permissions } = meta || {};
            if (!permissions) return true;
            return permissionsList.some(
              (item: any) => permissions.includes && permissions.includes(item.value)
            );
          };
          accessedRouters = filter(asyncRoutes, routeFilter);
          accessedRouters = accessedRouters.filter(routeFilter);
        } catch (error) {
          console.log(error);
        }
      }

      this.setRouters(accessedRouters as RouteRecordRaw[]);
      this.setMenus(accessedRouters as RouteRecordRaw[]);
      return toRaw(accessedRouters as RouteRecordRaw[]);
    },
  },
});

// Need to be used outside the setup
export function useAsyncRoute() {
  return useAsyncRouteStore(store);
}
