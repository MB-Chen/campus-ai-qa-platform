import { createAlova } from 'alova';
import VueHook from 'alova/vue';
import adapterFetch from 'alova/fetch';
import { createAlovaMockAdapter } from '@alova/mock';
import { isString } from 'lodash-es';
import mocks from './mocks';
import { useUser } from '@/store/modules/user';
import { storage } from '@/utils/Storage';
import { useGlobSetting, useLocalSetting } from '@/hooks/setting';
import { PageEnum } from '@/enums/pageEnum';
import { isUrl } from '@/utils';

const { apiUrl, urlPrefix } = useGlobSetting();
const { useMock, loggerMock } = useLocalSetting();

const mockAdapter = createAlovaMockAdapter([...mocks], {
  // 全局控制是否启用mock接口，默认为true
  enable: useMock,

  // 非模拟请求适配器，用于未匹配mock接口时发送请求
  httpAdapter: adapterFetch(),

  // mock接口响应延迟，单位毫秒
  delay: 1000,

  // 自定义打印mock接口请求信息
  // mockRequestLogger: (res) => {
  //   loggerMock && console.log(`Mock Request ${res.url}`, res);
  // },
  mockRequestLogger: loggerMock,
  onMockError(error, currentMethod) {
    console.error('🚀 ~ onMockError ~ currentMethod:', currentMethod);
    console.error('🚀 ~ onMockError ~ error:', error);
  },
});

// 是否已触发登录过期处理（防止并发请求返回 912 时重复弹窗/重复跳转）
let isReloginExpired = false;

export const Alova = createAlova({
  baseURL: apiUrl,
  statesHook: VueHook,
  // 完全关闭全局请求缓存
  cacheFor: null,
  // 在开发环境开启缓存命中日志
  cacheLogger: false, // 同时关闭缓存日志
  requestAdapter: useMock ? mockAdapter : adapterFetch(),
  beforeRequest(method) {
    const userStore = useUser();
    const token = userStore.getToken;
    // 添加 token 到请求头
    if (!method.meta?.ignoreToken && token) {
      method.config.headers['token'] = token;
    }
    // 处理 api 请求前缀
    const isUrlStr = isUrl(method.url as string);
    if (!isUrlStr && urlPrefix) {
      method.url = `${urlPrefix}${method.url}`;
    }
    if (!isUrlStr && apiUrl && isString(apiUrl)) {
      method.url = `${apiUrl}${method.url}`;
    }
  },
  responded: {
    onSuccess: async (response, method) => {
      const res = (response.json && (await response.json())) || response.body;

      // 是否返回原生响应头 比如：需要获取响应头时使用该属性
      if (method.meta?.isReturnNativeResponse) {
        return res;
      }
      // 请根据自身情况修改数据结构
      const { code } = res;

      // 不进行任何处理，直接返回
      // 用于需要直接获取 code、result、 message 这些信息时开启
      if (method.meta?.isTransformResponse === false) {
        return res.data;
      }

      // @ts-ignore
      const Modal = window.$dialog;

      const LoginPath = PageEnum.BASE_LOGIN;
      // 需要登录
      if (code === 912) {
        // 已经在处理过期流程中，直接挂起本次请求，不再重复弹窗/重复跳转
        if (isReloginExpired) {
          return new Promise(() => {});
        }
        isReloginExpired = true;

        Modal?.warning({
          title: '提示',
          content: '登录身份已失效，请重新登录!',
          okText: '确定',
          closable: false,
          maskClosable: false,
          onOk: async () => {
            storage.clear();
          },
        });

        setTimeout(() => {
          // 关闭弹窗
          Modal?.destroyAll?.();
          // 动态获取 router 实例，避免在模块顶层调用 useRouter()
          const router = (window as any).$router;
          const route = (window as any).$route;
          const redirectPath = route?.fullPath && route.fullPath !== '/' ? route.fullPath : '/chat/index';
          storage.clear();
          if (router) {
            router.replace({
              path: '/login',
              query: {
                redirect: redirectPath,
              },
            });
          } else {
            // 如果没有 router 实例，直接跳转到登录页
            window.location.href = LoginPath;
          }
          // 兜底重置（正常情况下页面会重新加载，标志位自然归位）
          isReloginExpired = false;
        }, 1500);
      } else {
        return res;
      }
    },
  },
});

// 项目，多个不同 api 地址，可导出多个实例
// export const AlovaTwo = createAlova({
//   baseURL: 'http://localhost:9001',
// });
