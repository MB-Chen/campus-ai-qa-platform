import { Alova } from '@/utils/http/alova/index';


/** 知识图谱全局配置（kg_config 表，固定 id=1） */
export interface KgConfig {
  id?: number;
  extractModelId?: number; // 抽取 LLM（ai_model.type=1）
  extractModelName?: string;
  embeddingModelId?: number; // 实体向量化（ai_model.type=2）
  embeddingModelName?: string;
  enabled?: number; // 知识图谱开关（全局）1=启用 2=禁用
  similarityThreshold?: number; // 实体向量召回阈值 0~1
  extractBatchSize?: number; // 单次 LLM 合并抽取父块数
  hopDepth?: number; // 子图跳数 1 或 2
  secondHopWeight?: number; // 二跳衰减权重 0~1
  entityMergeThreshold?: number; // 第三期：实体 embedding 合并阈值 0.5~1
  retrievalMode?: string; // 第四期：检索模式 local/global/hybrid
  communityEnabled?: number; // 第四期：社区检测开关 1=启用 2=禁用
  createdAt?: string;
  updatedAt?: string;
}

/** 抽取记录（kg_extraction_record 表） */
export interface KgExtractionRecord {
  id?: number;
  kbId: string;
  documentId: string;
  status: string; // pending/extracting/done/failed
  parentTotal: number;
  parentDone: number;
  entityCount: number;
  relationCount: number;
  errorMsg?: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** 抽取进度（Redis 桶，前端轮询） */
export interface KgExtractionProgress {
  status: string; // processing / done / failed
  total: number;
  done: number;
  success: number;
  failed: number;
  entityCount: number;
  relationCount: number;
  message?: string;
}


/** 读取全局配置 */
export function getKgConfig() {
  return Alova.Get<any>('/knowledge/graph/config');
}

/** 保存全局配置 */
export function saveKgConfig(data: Partial<KgConfig>) {
  return Alova.Post<any>('/knowledge/graph/config/save', data);
}


/** 测试 Neo4j 连通性 */
export function testKgConnect() {
  return Alova.Get<any>('/knowledge/graph/testConnect');
}


/** 查询 KB 级 KG 开关 */
export function getKgKbSetting(kbId: string) {
  return Alova.Get<any>('/knowledge/graph/kbSetting', { params: { kbId } });
}

/** 设置 KB 级 KG 开关 */
export function saveKgKbSetting(data: { kbId: string; kgEnabled: number }) {
  return Alova.Post<any>('/knowledge/graph/kbSetting', data);
}


/** 触发抽取（异步，返回 taskId） */
export function triggerKgExtract(data: { kbId: string; documentIds?: string[] }) {
  return Alova.Post<any>('/knowledge/graph/extract', data);
}

/** 查询抽取进度 */
export function getKgExtractProgress(taskId: string) {
  return Alova.Get<any>('/knowledge/graph/extract/progress', { params: { taskId } });
}


/** 抽取记录分页列表 */
export function getKgRecords(params: {
  kbId?: string;
  documentId?: string;
  status?: string;
  page?: number;
  size?: number;
}) {
  return Alova.Get<any>('/knowledge/graph/records', { params });
}


/**
 * 检索测试（documentId 非空时限定在该文档子图内检索，仅对 local 路生效）。
 * retrievalMode：local/global/hybrid，不传则后端读全局 kg_config.retrieval_mode。
 */
export function kgHitTest(data: {
  kbId: string;
  query: string;
  topK?: number;
  documentId?: string;
  retrievalMode?: 'local' | 'global' | 'hybrid';
}) {
  return Alova.Post<any>('/knowledge/graph/hitTest', data);
}


/** 图谱可视化数据（nodes/edges）。documentId 非空时只返回该文档贡献的子图 */
export function getKgVisualization(kbId: string, documentId?: string) {
  return Alova.Get<any>('/knowledge/graph/visualization', {
    params: documentId ? { kbId, documentId } : { kbId },
  });
}


/** 删除某文档的图谱数据 */
export function deleteKgByDocument(kbId: string, documentId: string) {
  return Alova.Get<any>('/knowledge/graph/delete', { params: { kbId, documentId } });
}


/** 运行社区检测 + 生成社区摘要（异步，global/hybrid 模式前置） */
export function triggerCommunityDetect(kbId: string) {
  return Alova.Post<any>('/knowledge/graph/community/detect', null, { params: { kbId } });
}
