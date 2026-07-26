import { defineStore } from 'pinia';
import { store } from '@/store';
import { ACCESS_TOKEN, CURRENT_USER, IS_SCREENLOCKED } from '@/store/mutation-types';

import { storage } from '@/utils/Storage';
import { login } from '@/api/user';

// 菜单存储key
const USER_MENUS = 'USER_MENUS';

export type UserInfoType = {
  // TODO: add your own data
  username: string;
  email: string;
};

export interface LoginResponse {
  code: number;
  message?: string;
  data: {
    token: string;
    useInfo: {
      real_name: string;
      id: number;
      avatar: string;
      role_id: number;
    };
    menu: any[];
  };
}

export interface UserInfoResponse {
  code: number;
  message: string;
  result: UserInfoType & {
    avatar: string;
    permissions: string[];
  };
}

export interface IUserState {
  token: string;
  username: string;
  welcome: string;
  avatar: string;
  permissions: any[];
  info: UserInfoType;
  menus: any[];
}

export const useUserStore = defineStore({
  id: 'app-user',
  state: (): IUserState => ({
    token: storage.get(ACCESS_TOKEN, ''),
    username: '',
    welcome: '',
    avatar: '',
    permissions: [],
    info: storage.get(CURRENT_USER, {}),
    menus: storage.get(USER_MENUS, []),
  }),
  getters: {
    getToken(): string {
      return this.token;
    },
    getAvatar(): string {
      return this.avatar;
    },
    getNickname(): string {
      return this.username;
    },
    getPermissions(): [any][] {
      return this.permissions;
    },
    getUserInfo(): UserInfoType {
      return this.info;
    },
    getMenus(): any[] {
      return this.menus;
    },
  },
  actions: {
    setToken(token: string) {
      this.token = token;
    },
    setAvatar(avatar: string) {
      this.avatar = avatar;
    },
    setPermissions(permissions) {
      this.permissions = permissions;
    },
    setUserInfo(info: UserInfoType) {
      this.info = info;
    },
    setMenus(menus: any[]) {
      this.menus = menus;
    },
    // 登录
    async login(params: any): Promise<any> {
      const response: any = await login(params);
      // API返回的是 {code: 0, data: {...}, message: "success"} 结构，需要访问 data 部分
      const loginData = response.data || response;

      if (response.code == 0 && loginData && loginData.token) {
        const ex = 7 * 24 * 60 * 60;
        storage.set(ACCESS_TOKEN, loginData.token, ex);
        storage.set(CURRENT_USER, loginData.userInfo, ex);
        storage.set(IS_SCREENLOCKED, false);
        this.setToken(loginData.token);
        this.setUserInfo(loginData.userInfo as any);
        this.setAvatar(loginData.userInfo.avatar || '');

        // 从menu中提取权限并保存菜单数据
        if (loginData.menu && loginData.menu.length > 0) {
          const permissions = loginData.menu.map((item) => item.name);
          this.setPermissions(permissions);
          // 保存菜单数据到缓存和store
          storage.set(USER_MENUS, loginData.menu, 7 * 24 * 60 * 60);
          this.setMenus(loginData.menu);
        } else {
          // 设置默认权限
          this.setPermissions([
            'dashboard_console',
            'permission',
            'permission_role',
            'permission_menu',
          ]);
        }
      }
      // 返回完整的响应结构供前端使用
      return {
        code: response.code,
        message: response.message,
        data: loginData,
      };
    },

    // 获取用户信息
    async getInfo() {
      // 直接从已存储的用户信息中获取，避免重复请求接口
      const userInfo = storage.get(CURRENT_USER);
      const userMenus = storage.get(USER_MENUS);

      if (userInfo) {
        // 如果用户信息存在，直接返回
        this.setUserInfo(userInfo);
        this.setAvatar(userInfo.avatar || '');

        // 设置菜单数据
        if (userMenus && userMenus.length > 0) {
          this.setMenus(userMenus);
          const permissions = userMenus.map((item) => item.name);
          this.setPermissions(permissions);
        } else {
          // 设置默认权限，确保路由能正常工作
          this.setPermissions([
            'dashboard_console',
            'permission',
            'permission_role',
            'permission_menu',
          ]);
        }

        return userInfo;
      } else {
        // 如果没有用户信息，抛出错误需要重新登录
        throw new Error('用户信息不存在，请重新登录');
      }
    },

    // 登出
    async logout() {
      this.setPermissions([]);
      this.setUserInfo({ username: '', email: '' });
      this.setMenus([]);
      storage.remove(ACCESS_TOKEN);
      storage.remove(CURRENT_USER);
      storage.remove(USER_MENUS);
    },
  },
});

// Need to be used outside the setup
export function useUser() {
  return useUserStore(store);
}
