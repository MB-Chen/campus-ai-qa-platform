import { reactive } from 'vue';
import logoImage from '@/assets/images/logo.png';
import loginImage from '@/assets/images/account-logo.png';
import { getBaseConfig } from '@/api/system/setting';

// 默认标题（与 .env 的 VITE_GLOB_APP_TITLE 保持一致），接口拉取失败时回退
const DEFAULT_TITLE = '企业级AI客服';

export const websiteConfig = reactive({
  title: DEFAULT_TITLE,
  logo: logoImage,
  loginImage: loginImage,
  loginDesc: DEFAULT_TITLE,
});

/**
 * 从后端拉取平台基础配置，同步更新浏览器标题、左上角名称与 Logo。
 * 接口异常时静默回退默认值，不阻塞应用启动。
 */
export async function applyBaseConfig() {
  try {
    const res = await getBaseConfig();
    if (res && res.code === 0 && res.data) {
      const data = res.data;
      if (data.platform_name) {
        websiteConfig.title = data.platform_name;
        websiteConfig.loginDesc = data.platform_name;
        document.title = data.platform_name;
      }
      if (data.platform_logo) {
        websiteConfig.logo = data.platform_logo;
        // 同步浏览器标签页图标（favicon），使 tab 图标跟随平台 Logo
        setFavicon(data.platform_logo);
      }
    }
  } catch (e) {
    // 静默处理，保留默认配置
    console.warn('加载平台基础配置失败，使用默认配置', e);
  }
}

/**
 * 动态设置浏览器标签页图标。
 * 复用页面中已有的 <link rel="icon">，没有则创建一个，
 * 避免重复添加多条 link 标签污染 <head>。
 */
function setFavicon(href: string) {
  if (!href) return;
  let link = document.querySelector<HTMLLinkElement>("link[rel*='icon']");
  if (!link) {
    link = document.createElement('link');
    link.rel = 'icon';
    document.head.appendChild(link);
  }
  link.href = href;
}
