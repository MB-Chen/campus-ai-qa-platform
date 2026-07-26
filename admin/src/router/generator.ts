import { constantRouterIcon } from './icons';
import { getIcon } from '@/utils/iconLoader';
import { Layout, ParentLayout } from '@/router/constant';
import type { AppRouteRecordRaw } from '@/router/types';

const LayoutMap = new Map<string, () => Promise<typeof import('*.vue')>>();

LayoutMap.set('LAYOUT', Layout);

/**
 * 获取动态图标
 * @param iconName 图标名称
 * @returns 返回图标组件或null
 */
function getDynamicIcon(iconName: string) {
  if (!iconName) {
    return null;
  }

  // 优先使用 constantRouterIcon 中的静态图标（性能更好）
  if (constantRouterIcon[iconName]) {
    return constantRouterIcon[iconName];
  }

  // 使用动态加载器
  return getIcon(iconName);
}

/**
 * 格式化 后端 结构信息并递归生成层级路由表
 * @param routerMap
 * @param parent
 * @returns {*}
 */
export const generateRoutes = (routerMap, parent?): any[] => {
  return routerMap.map((item) => {
    let currentPath = item.path;

    // 处理父级路由路径，如果路径是#，则根据name生成路径
    if (item.path === '#' && !parent) {
      // 根据菜单名称生成路径，转换为小写和连字符
      const pathName = item.name.toLowerCase().replace(/\s+/g, '-');
      currentPath = `/${pathName}`;
    } else if (item.path === '#' && parent) {
      // 子菜单的path处理
      currentPath = item.path;
    } else if (parent && parent.path && item.path !== '#') {
      // 子路由路径拼接
      currentPath = `${parent.path}/${item.path}`;
    }

    const currentRoute: any = {
      // 路由地址
      path: currentPath,
      // 路由名称，建议唯一
      name: item.name ?? '',
      // 该路由对应页面的 组件
      component: item.component,
      // meta: 页面标题, 菜单图标, 页面权限(供指令权限用，可去掉)
      meta: {
        ...item.meta,
        label: item.meta.title,
        icon: parent ? null : getDynamicIcon(item.meta.icon), // 子菜单不显示图标
        permissions: item.meta.permissions || null,
        alwaysShow: item.meta.alwaysShow !== undefined ? item.meta.alwaysShow : true, // 默认显示父菜单
      },
    };

    // 为了防止出现后端返回结果不规范，处理有可能出现拼接出两个 反斜杠
    currentRoute.path = currentRoute.path.replace('//', '/');
    // 重定向
    item.redirect && (currentRoute.redirect = item.redirect);
    // 是否有子菜单，并递归处理
    if (item.children && item.children.length > 0) {
      //如果未定义 redirect 默认第一个子路由为 redirect
      if (!item.redirect) {
        if (item.path === '#') {
          // 如果父路由路径是#，使用当前路由路径 + 子路由路径
          currentRoute.redirect = `${currentRoute.path}/${item.children[0].path}`;
        } else {
          currentRoute.redirect = `${item.path}/${item.children[0].path}`;
        }
      }
      // Recursion
      currentRoute.children = generateRoutes(item.children, currentRoute);
    }
    return currentRoute;
  });
};

/**
 * 查找views中对应的组件文件
 * */
let viewsModules: Record<string, () => Promise<Recordable>>;
export const asyncImportRoute = (routes: AppRouteRecordRaw[] | undefined): void => {
  viewsModules = viewsModules || import.meta.glob('../views/**/*.{vue,tsx}');
  if (!routes) return;
  routes.forEach((item) => {
    const { component, name } = item;
    const { children } = item;
    if (component) {
      const layoutFound = LayoutMap.get(component as string);
      if (layoutFound) {
        item.component = layoutFound;
      } else {
        item.component = dynamicImport(viewsModules, component as string);
      }
    } else if (name) {
      item.component = ParentLayout;
    }
    children && asyncImportRoute(children);
  });
};

/**
 * 动态导入
 * */
export const dynamicImport = (
  viewsModules: Record<string, () => Promise<Recordable>>,
  component: string
) => {
  const keys = Object.keys(viewsModules);
  const matchKeys = keys.filter((key) => {
    let k = key.replace('../views', '');
    const lastIndex = k.lastIndexOf('.');
    k = k.substring(0, lastIndex);
    return k === component;
  });
  if (matchKeys?.length === 1) {
    const matchKey = matchKeys[0];
    return viewsModules[matchKey];
  }
  if (matchKeys?.length > 1) {
    console.warn(
      'Please do not create `.vue` and `.TSX` files with the same file name in the same hierarchical directory under the views folder. This will cause dynamic introduction failure'
    );
    return;
  }
};
