/**
 * 分块配置类型。
 *
 * 抽到独立 .ts 文件而非放在 ChunkingSettings.vue 内 export：
 * SFC 作为类型具名导出源在 vue-tsc / Volar 较新版本下会触发 TS2614
 * （Module "*.vue" has no exported member ...）。Vue 3 + TS 官方推荐
 * 把跨组件复用的类型放到独立 .ts。
 */
export interface ChunkingConfig {
  chunkSize: number;
  chunkOverlap: number;
  separators: string[];
  enableParentChild: boolean;
  parentChunkSize: number;
  childChunkSize: number;
  strategy: string;
  tokenLimit: number;
  languages: string[];
  /** Excel/CSV QA 切分（仅对 xls/xlsx/csv 生效） */
  qaMode: boolean;
}
