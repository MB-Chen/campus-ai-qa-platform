import { Alova } from '@/utils/http/alova/index';


/**
 * 解析引擎配置实体（MinerU、Neo4j 等非 LLM 外部服务）
 *
 * 与 ai_model 区分：ai_model 管 LLM 模型（对话/向量/重排/视觉，含容错降级链）；
 * 本表管非 LLM 外部服务（PDF 解析引擎 MinerU、知识图谱 Neo4j 等），按 category 分类，config 存 JSON。
 *
 * config JSON schema 按 category 不同：
 *  - mineru_self（自建MinerU）:
 *      { endpoint, model, vlmServerUrl, enableFormula, enableTable, enableOcr, language, timeoutSec }
 *  - mineru_cloud（云端MinerU）:
 *      { apiKey, model, enableFormula, enableTable, enableOcr, language, pollIntervalSec, timeoutSec }
 *  - neo4j_self（自建Neo4j）:
 *      { uri, username, password }
 */
export interface ExtServiceConfig {
  id?: number;
  name: string;
  category: string; // mineru_self / mineru_cloud / neo4j_self / ...
  config?: string; // 配置 JSON 文本（schema 按 category）
  remark?: string;
  status?: number; // 1启用 2禁用
  sort?: number; // 排序（数值小者靠前）
  createTime?: string;
  updateTime?: string;
}

/** 配置连通性测试结果（复用后端 ModelTestVo） */
export interface ServiceTestResult {
  success: boolean;
  message: string;
  latencyMs?: number;
}


/** 子类别（对应 ext_service_config.category） */
interface SubCategory {
  key: string;
  label: string;
}

/** 服务类型定义（顶层展示） */
export interface ServiceType {
  key: string;           // 'mineru' | 'neo4j'
  label: string;         // 'MinerU' | 'Neo4j'
  desc: string;          // 简要描述
  icon?: string;         // 图标（可选）
  subCategories: SubCategory[];
}

/**
 * 全局注册表（未来加服务只改这里）
 * subCategories 的 key 对应 ext_service_config.category
 */
export const SERVICE_TYPES: ServiceType[] = [
  {
    key: 'mineru',
    label: 'MinerU',
    desc: 'PDF/文档解析引擎，支持 OCR、公式识别、表格提取',
    icon: '📄',
    subCategories: [
      { key: 'mineru_self', label: '自建' },
      { key: 'mineru_cloud', label: '云端' },
    ],
  },
  {
    key: 'neo4j',
    label: 'Neo4j',
    desc: '知识图谱数据库，存储实体与关系',
    icon: '🌐',
    subCategories: [
      { key: 'neo4j_self', label: '自建' },
    ],
  },
];

/** 返回服务类型注册表 */
export function getServiceTypes(): ServiceType[] {
  return SERVICE_TYPES;
}


/** 服务类别常量（从 SERVICE_TYPES 派生） */
export const SERVICE_CATEGORY: Record<string, string> = (() => {
  const map: Record<string, string> = {};
  for (const st of SERVICE_TYPES) {
    for (const sub of st.subCategories) {
      // key 例：MINERU_SELF, NEO4J_SELF
      const constKey = sub.key.toUpperCase();
      map[constKey] = sub.key;
    }
  }
  return map;
})();

/** 服务类别标签（tab 标题，从 SERVICE_TYPES 派生） */
export const SERVICE_CATEGORY_LABEL: Record<string, string> = (() => {
  const map: Record<string, string> = {};
  for (const st of SERVICE_TYPES) {
    for (const sub of st.subCategories) {
      map[sub.key] = `${sub.label} ${st.label}`;
    }
  }
  return map;
})();

/** tab 顺序（从 SERVICE_TYPES 派生，未来加服务只改 SERVICE_TYPES） */
export const SERVICE_CATEGORY_TABS: { name: string; label: string }[] = (() => {
  const tabs: { name: string; label: string }[] = [];
  for (const st of SERVICE_TYPES) {
    for (const sub of st.subCategories) {
      tabs.push({ name: sub.key, label: `${sub.label} ${st.label}` });
    }
  }
  return tabs;
})();

/** 自建 MinerU 后端选项（config.model，对标后端 backend 参数） */
export const MINERU_SELF_MODEL_OPTIONS = [
  { label: 'pipeline（默认）', value: 'pipeline' },
  { label: 'vlm-sglang-client', value: 'vlm-sglang-client' },
  { label: 'vlm-http-client', value: 'vlm-http-client' },
  { label: 'hybrid-http-client', value: 'hybrid-http-client' },
];

/** 云端 MinerU 模型版本选项（config.model，对标后端 model_version） */
export const MINERU_CLOUD_MODEL_OPTIONS = [
  { label: 'pipeline（默认）', value: 'pipeline' },
  { label: 'vlm', value: 'vlm' },
  { label: 'MinerU-HTML', value: 'MinerU-HTML' },
];

/** MinerU OCR 语言选项 */
export const MINERU_LANGUAGE_OPTIONS = [
  { label: '中文', value: 'ch' },
  { label: '英文', value: 'en' },
];

/** 需要配 vlmServerUrl 的后端（仅 vlm-http-client / hybrid-http-client） */
export function needVlmServerUrl(model: string | undefined): boolean {
  if (!model) return false;
  return model.includes('vlm-http') || model.includes('hybrid-http');
}


// 配置列表（按类别，可选状态过滤）
export function getServiceList(params: { category?: string; status?: number }) {
  return Alova.Get<any>('/ai/service/list', { params });
}

// 配置详情
export function getServiceInfo(id: number) {
  return Alova.Get<any>('/ai/service/info', { params: { id } });
}

// 新增配置
export function addService(data: Partial<ExtServiceConfig>) {
  return Alova.Post<any>('/ai/service/add', data);
}

// 编辑配置
export function editService(data: Partial<ExtServiceConfig>) {
  return Alova.Post<any>('/ai/service/edit', data);
}

// 删除配置
export function delService(id: number) {
  return Alova.Get<any>('/ai/service/del', { params: { id } });
}

// 切换配置启停
export function setServiceStatus(id: number, status: number) {
  return Alova.Get<any>('/ai/service/status', { params: { id, status } });
}

// 测试已保存配置连通性
export function testService(id: number) {
  return Alova.Get<any>('/ai/service/test', { params: { id } });
}

// 测试连通性（按表单参数，无需保存；新建态用）
export function testServiceConnect(data: Partial<ExtServiceConfig>) {
  return Alova.Post<any>('/ai/service/testConnect', data);
}
