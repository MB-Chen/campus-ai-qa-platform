import { Alova } from '@/utils/http/alova/index';


/**
 * AI 模型实体
 * type: 1对话 2向量 3重排 4视觉(VLM)
 *
 * credential / options 为 JSON 文本（页面动态渲染字段）：
 *  - credential: [{"field":"apiKey","value":"sk-xxx"}]
 *  - options:    [{"field":"url","value":"https://..."},{"field":"temperature","range":[0,2],"value":0.3}, ...]
 */
export interface AiModel {
  id?: number;
  name: string;
  type: number; // 1对话 2向量 3重排 4视觉
  provider: string; // openai / ollama
  credential?: string; // 凭证 JSON 数组文本
  models?: string; // 可用模型名（逗号分隔）
  functionCalling?: string; // 函数调用能力（逗号分隔）
  options?: string; // 选项 JSON 数组文本
  status?: number; // 1启用 2禁用
  priority?: number; // 候选优先级（小者优先）
  supportsThinking?: number; // 0否 1是
  createTime?: string;
  updateTime?: string;
}

/** credential / options 中的单项 */
export interface FieldValue {
  field: string;
  value: any;
  range?: number[];
}

/** 模型连通性测试结果 */
export interface ModelTestResult {
  success: boolean;
  message: string;
  latencyMs?: number;
}

/** 模型类型常量 */
export const MODEL_TYPE = {
  CHAT: 1,
  EMBEDDING: 2,
  RERANK: 3,
  VLM: 4,
} as const;

/** 模型类型标签 */
export const MODEL_TYPE_LABEL: Record<number, string> = {
  1: '对话模型',
  2: '向量模型',
  3: '重排模型',
  4: '视觉模型',
};

/** 供应商选项 */
export const PROVIDER_OPTIONS = [
  { label: 'OpenAI 兼容协议', value: 'openai' },
  { label: 'Ollama 本地协议', value: 'ollama' },
];

/**
 * 各模型类型允许的供应商白名单。
 * 依据 Ollama OpenAI 兼容层（/v1）实际暴露的端点：
 *  - 对话(1)：/v1/chat/completions ✅
 *  - 向量(2)：/v1/embeddings      ✅
 *  - 重排(3)：/v1/rerank          ❌（端点不存在，连通性测试必 404）
 *  - 视觉(4)：项目未接入 Ollama VLM 客户端，统一走 OpenAI 协议
 * 因此 Ollama 仅允许配对话/向量；重排/视觉强制 OpenAI 兼容协议。
 */
const PROVIDERS_BY_TYPE: Record<number, string[]> = {
  1: ['openai', 'ollama'],
  2: ['openai', 'ollama'],
  3: ['openai'],
  4: ['openai'],
};

/** 判断某供应商是否兼容某模型类型 */
export function isProviderSupportedByType(provider: string, type: number): boolean {
  return (PROVIDERS_BY_TYPE[type] || []).includes(provider);
}

/** 按模型类型过滤可选的供应商选项（供下拉 :options 使用） */
export function getProviderOptionsByType(type: number) {
  const allowed = PROVIDERS_BY_TYPE[type] || ['openai'];
  return PROVIDER_OPTIONS.filter((p) => allowed.includes(p.value));
}


// 模型列表（按类型）
export function getModelList(params: { type: number; status?: number }) {
  return Alova.Get<any>('/ai/model/list', { params });
}

// 模型详情
export function getModelInfo(id: number) {
  return Alova.Get<any>('/ai/model/info', { params: { id } });
}

// 新增模型
export function addModel(data: Partial<AiModel>) {
  return Alova.Post<any>('/ai/model/add', data);
}

// 编辑模型
export function editModel(data: Partial<AiModel>) {
  return Alova.Post<any>('/ai/model/edit', data);
}

// 删除模型
export function delModel(id: number) {
  return Alova.Get<any>('/ai/model/del', { params: { id } });
}

// 切换模型启停
export function setModelStatus(id: number, status: number) {
  return Alova.Get<any>('/ai/model/status', { params: { id, status } });
}

// 启用的重排模型列表（type=3,status=1）
export function getRerankModelList() {
  return Alova.Get<any>('/ai/model/rerankList');
}

// 测试模型连通性（按已保存的模型 id）
export function testModel(id: number) {
  return Alova.Get<any>('/ai/model/test', { params: { id } });
}

// 测试模型连通性（按表单参数，无需先保存；新建态用）
export function testModelConnect(data: Partial<AiModel>) {
  return Alova.Post<any>('/ai/model/testConnect', data);
}


/** 安全解析 [{field,value}] 形态的 JSON 文本；解析失败返回 [] */
export function parseFieldJson(json: string | undefined | null): FieldValue[] {
  if (!json || typeof json !== 'string') return [];
  try {
    const parsed = JSON.parse(json);
    if (Array.isArray(parsed)) return parsed as FieldValue[];
    return [];
  } catch (e) {
    return [];
  }
}

/** 将 [{field,value}] 序列化为 JSON 文本 */
export function stringifyFieldJson(list: FieldValue[]): string {
  return JSON.stringify(list || []);
}

/** 从 [{field,value}] 数组中按 field 取值 */
export function getField(list: FieldValue[], field: string): any {
  const item = (list || []).find((x) => x && x.field === field);
  return item ? item.value : undefined;
}
