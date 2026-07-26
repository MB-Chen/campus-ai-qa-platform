/**
 * Vue 单文件组件模块声明。
 *
 * TypeScript 默认无法识别 `.vue` 后缀的 import，会报：
 *   TS2307: Cannot find module './xxx.vue' or its corresponding type declarations.
 * 这里给所有 `*.vue` 导入一个通用的 DefineComponent 类型声明，让 IDE/编译器认识它。
 *
 * 项目的实际组件类型在用到的地方会通过 `typeof Xxx` 或 defineProps/defineEmits 推断。
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  // eslint-disable-next-line @typescript-eslint/no-explicit-any, typescript/no-explicit-any
  const component: DefineComponent<{}, {}, any>;
  export default component;
}
