import { reactive } from 'vue';
import logoImage from '@/assets/images/logo.png';
import loginImage from '@/assets/images/account-logo.png';

// 默认标题（与 .env 的 VITE_GLOB_APP_TITLE 保持一致）
const DEFAULT_TITLE = 'SparkX';

export const websiteConfig = reactive({
  title: DEFAULT_TITLE,
  logo: logoImage,
  loginImage: loginImage,
  loginDesc: DEFAULT_TITLE,
});

/**
 * 平台基础配置已写死在 websiteConfig 中（标题/Logo），无需后端拉取。
 * 保留为空函数以兼容 main.ts / guards.ts 的调用点。
 */
export async function applyBaseConfig() {
  document.title = websiteConfig.title;
}
