import { App } from 'vue';
import type { MessageApi } from 'naive-ui';

/**
 * 注册全局方法
 * @param app
 */
export function setupGlobalMethods(app: App) {
  // 统一提示工具
  const msg = (title: string, duration = 3000) => {
    if (Boolean(title) === false) {
      return;
    }
    window.$message?.info(title, { duration });
  };

  const success = (title: string, duration = 3000) => {
    if (Boolean(title) === false) {
      return;
    }
    window.$message?.success(title, { duration });
  };

  const error = (title: string, duration = 3000) => {
    if (Boolean(title) === false) {
      return;
    }
    window.$message?.error(title, { duration });
  };

  const warning = (title: string, duration = 3000) => {
    if (Boolean(title) === false) {
      return;
    }
    window.$message?.warning(title, { duration });
  };

  const loading = (title: string = '加载中...', duration = 0) => {
    if (Boolean(title) === false) {
      return;
    }
    return window.$message?.loading(title, { duration });
  };

  // 全局挂载工具
  app.config.globalProperties.$tool = {
    msg,
    success,
    error,
    warning,
    loading,
  };
}

// 类型声明
declare module '@vue/runtime-core' {
  interface ComponentCustomProperties {
    $tool: {
      msg: (title: string, duration?: number) => void;
      success: (title: string, duration?: number) => void;
      error: (title: string, duration?: number) => void;
      warning: (title: string, duration?: number) => void;
      loading: (title?: string, duration?: number) => ReturnType<MessageApi['loading']> | undefined;
    };
  }
}
