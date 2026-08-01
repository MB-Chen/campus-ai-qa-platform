import './styles/tailwind.css';
import './styles/index.less';
import './styles/modern.less';
import '@file-viewer/vue3/dist/file-viewer3.css';
import '@/assets/styles/chat-markdown.less';
import { createApp } from 'vue';
import FileViewer from '@file-viewer/vue3';
import { setupNaiveDiscreteApi, setupNaive, setupDirectives, setupGlobalMethods } from '@/plugins';
import App from './App.vue';
import router, { setupRouter } from './router';
import { setupStore } from '@/store';
import { applyBaseConfig } from '@/config/website.config';

async function bootstrap() {
  const app = createApp(App);

  // 挂载状态管理
  setupStore(app);

  // 注册全局常用的 naive-ui 组件
  setupNaive(app);

  // 挂载 naive-ui 脱离上下文的 Api
  setupNaiveDiscreteApi();

  // 注册文件预览组件（企业云盘用）
  app.use(FileViewer);

  // 注册全局自定义组件
  //setupCustomComponents();

  // 注册全局自定义指令，如：v-permission权限指令
  setupDirectives(app);

  // 注册全局方法，如：app.config.globalProperties.$message = message
  setupGlobalMethods(app);

  // 挂载路由
  setupRouter(app);

  // 路由准备就绪后挂载 APP 实例
  // https://router.vuejs.org/api/interfaces/router.html#isready
  await router.isReady();

  // 异步拉取平台基础配置（平台名称/Logo），更新浏览器标题与左上角展示，不阻塞挂载
  applyBaseConfig();

  // https://www.naiveui.com/en-US/os-theme/docs/style-conflict#About-Tailwind's-Preflight-Style-Override
  const meta = document.createElement('meta');
  meta.name = 'naive-ui-style';
  document.head.appendChild(meta);

  app.mount('#app', true);
}

void bootstrap();
