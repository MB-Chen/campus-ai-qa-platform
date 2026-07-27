import { Alova } from '@/utils/http/alova/index';


/** 知识库（列表展示 VO） */
export interface KnowledgeBase {
  id: string;
  name: string;
  description?: string;
  embeddingModel?: string;
  embeddingModelId?: number;
  embeddingModelName?: string;
  dimension?: number;
  docCount?: number;
  status?: number; // 1正常 2禁用
  createdAt?: string;
}

/** 知识库文档 */
export interface KnowledgeDocument {
  id: string;
  kbId: string;
  fileName: string;
  fileSize?: number;
  storageUrl?: string;
  status?: string; // pending/processing/done/failed
  chunkCount?: number;
  questionStatus?: number; // 1待生成 2生成中 3已生成
  active?: number; // 1正常 2禁用
  kgEnabled?: number; // 知识图谱开关 1=启用 2=禁用（文档级）
  kgExtractStatus?: string | null; // 图谱抽取状态 null/pending/extracting/done/failed
  kgEntityCount?: number | null; // 已抽取实体数
  ingestionSummary?: IngestionSummary | null;
  createdAt?: string;
  updatedAt?: string; // 状态变更时刻
  /** 向量化实时进度（仅 status=processing 时有值，含 embed 阶段实时耗时） */
  embedProgress?: StageStat | null;
}

/** 入库耗时阶段统计 */
export interface StageStat {
  key: string; // parse/chunk/persist/embed
  label: string; // 解析/分块/入库/向量化
  durationMs: number;
  status: string; // success/failed/skipped/running
  detail?: string;
}

/** 文档入库耗时统计（全引擎，落 document.ingestion_summary） */
export interface IngestionSummary {
  engine?: string;
  totalMs: number;
  stages: StageStat[];
}

/** 段落/子块 */
export interface ParagraphChunk {
  id: string;
  kbId?: string;
  content: string;
  metadata?: string;
  /** 所属父块 id（文档维度列表回填，前端按父块分组用） */
  parentId?: string;
  /** 所属父块全文（文档维度列表回填，折叠展示用） */
  parentContent?: string;
  createdAt?: string;
}

/** 知识库问题（存储与文档/分块对应的问题，用于增加召回率） */
export interface KnowledgeQuestion {
  id: number;
  kbId: string;
  documentId?: string;
  chunkId?: string;
  content: string;
  source?: string; // manual / ai
  status?: number; // 1正常 2禁用
  createdAt?: string;
  updatedAt?: string;
}

/** 命中测试结果 */
export interface HitTestResult {
  chunkId: string;
  content: string;
  score: number;
  documentName?: string;
  metadata?: string;
}

/** 问答结果 */
export interface ChatResult {
  answer: string;
  sources?: string[];
  intent?: any;
}

/** 检索模式 */
export type HitTestMode = 'embedding' | 'text' | 'mix';


// 知识库列表（分页）
export function getKbList(params: { keyword?: string; page?: number; size?: number }) {
  return Alova.Get<any>('/knowledge/index', { params });
}

// 新增知识库
export function addKb(data: {
  name: string;
  description?: string;
  embeddingModelId: number;
  embeddingModelName?: string;
  status?: number;
}) {
  return Alova.Post<any>('/knowledge/add', data);
}

// 编辑知识库
export function editKb(data: {
  id: string;
  name?: string;
  description?: string;
  status?: number;
}) {
  return Alova.Post<any>('/knowledge/edit', data);
}

// 删除知识库（级联删除文档/子块/问题）
export function delKb(id: string) {
  return Alova.Get<any>('/knowledge/del', { params: { id } });
}

// 重新向量化整个知识库
export function embeddingKb(id: string) {
  return Alova.Get<any>('/knowledge/embedding', { params: { id } });
}

// 命中测试
export function hitTest(data: {
  kbId: string;
  documentId?: string;
  query: string;
  mode?: HitTestMode;
  similarity?: number;
  topRank?: number;
}) {
  return Alova.Post<any>('/knowledge/hitTest', data);
}


// 文档列表（按知识库）
export function getDocumentList(params: {
  kbId?: string;
  keyword?: string;
  page?: number;
  size?: number;
}) {
  return Alova.Get<any>('/knowledge/document/list', { params });
}

// 上传文档（multipart：file + kbId + engine）
export function uploadDocument(kbId: string, file: File, engine?: string) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('kbId', kbId);
  formData.append('engine', engine || 'tika');
  // 注意：FormData 不要手动设置 Content-Type，让浏览器自动带 boundary
  return Alova.Post<any>('/knowledge/document/upload', formData);
}

// 试切预览（multipart：files[] + 完整配置）
// 只解析+分块，不落库不向量化。返回每个文件的切片预览。
export interface ParserEngineRule {
  fileTypes: string[];
  engine: string;
}

export interface PreviewChunkItem {
  title?: string;
  content: string;
  charCount?: number;
  parentContext?: string;
  parentIndex?: number;
}

export interface PreviewDocItem {
  fileName: string;
  fileSize: number;
  storageUrl?: string;
  stages?: StageStat[];
  /** 实际解析引擎（后端按扩展名+规则推导，透传给 save） */
  engine?: string;
  chunks: PreviewChunkItem[];
}

export function previewDocument(data: {
  files: File[];
  engine?: string;
  parserEngineRules?: ParserEngineRule[];
  chunkSize?: number;
  overlap?: number;
  strategy?: string;
  enableParentChild?: boolean;
  parentChunkSize?: number;
  childChunkSize?: number;
  qaMode?: boolean;
  separators?: string[];
}) {
  const formData = new FormData();
  data.files.forEach((f) => formData.append('files', f));
  formData.append('engine', data.engine || 'tika');
  if (data.chunkSize != null) formData.append('chunkSize', String(data.chunkSize));
  if (data.overlap != null) formData.append('overlap', String(data.overlap));
  if (data.strategy) formData.append('strategy', data.strategy);
  if (data.enableParentChild != null) formData.append('enableParentChild', String(data.enableParentChild));
  if (data.parentChunkSize != null) formData.append('parentChunkSize', String(data.parentChunkSize));
  if (data.childChunkSize != null) formData.append('childChunkSize', String(data.childChunkSize));
  if (data.qaMode != null) formData.append('qaMode', String(data.qaMode));
  // parserEngineRules 序列化为 JSON 字符串（multipart 不支持嵌套对象）
  if (data.parserEngineRules?.length) {
    formData.append('parserEngineRulesJson', JSON.stringify(data.parserEngineRules));
  }
  // 自定义分隔符（仅 strategy=legacy 生效）序列化为 JSON，规避换行符在 multipart 里的编码问题
  if (data.separators?.length) {
    formData.append('separatorsJson', JSON.stringify(data.separators));
  }
  return Alova.Post<any>('/knowledge/document/preview', formData);
}

/**
 * 预览进度（mineru 异步预览轮询用，后端 PreviewProgressVo）。
 * - data.data.taskId：刚提交时返回，后续轮询用
 * - data.data.result：status=done 时为切片数组（PreviewDocItem[]）
 */
export interface PreviewProgress {
  status: 'processing' | 'done' | 'failed';
  total: number;
  done: number;
  success: number;
  failed: number;
  message?: string;
  /** 最近完成文件的阶段耗时快照（parse/chunk） */
  stages?: StageStat[];
  /** 已完成文件的切片预览（done 时为完整结果） */
  result?: PreviewDocItem[];
}

// 查询预览进度（mineru 异步预览轮询用）
export function getPreviewProgress(taskId: string) {
  return Alova.Get<any>('/knowledge/document/preview/progress', { params: { taskId } });
}

// 保存试切结果入库（JSON：kbId + documentList）
// 异步入库：接口立即返回 taskId，前端轮询 getSaveProgress 看进度。
// 只落库切片文本，不向量化；向量由用户主动点「向量化」触发。
export interface SaveChunkItem {
  title?: string;
  content: string;
  parentContext?: string;
}
export interface SaveDocItem {
  fileName: string;
  fileSize?: number;
  storageUrl?: string;
  stages?: StageStat[];
  /** 实际解析引擎（从预览结果透传，落 ingestion_summary.engine） */
  engine?: string;
  chunks: SaveChunkItem[];
}
export function saveDocument(data: {
  kbId: string;
  engine?: string;
  enableParentChild?: boolean;
  documentList: SaveDocItem[];
}) {
  return Alova.Post<any>('/knowledge/document/save', data);
}

/** 入库进度（后端 DocumentSaveProgressVo） */
export interface SaveProgress {
  status: 'processing' | 'done' | 'failed';
  total: number;
  done: number;
  success: number;
  failed: number;
  message?: string;
  /** 最近完成文档的阶段耗时快照（parse/chunk/persist） */
  stages?: StageStat[];
}

// 查询入库存度（轮询用，与 saveDocument 返回的 taskId 配套）
export function getSaveProgress(taskId: string) {
  return Alova.Get<any>('/knowledge/document/save/progress', { params: { taskId } });
}

// 重新向量化指定文档（documentIds 逗号分隔）
export function embeddingDocument(documentIds: string) {
  return Alova.Get<any>('/knowledge/document/embedding', { params: { documentIds } });
}

// 生成问题（按所选文档，每个原文分块逐个生成，问题作为独立 chunk 入库并 embedding，增加召回）
// 异步处理：接口立即返回，文档 questionStatus 置为 2(生成中)，前端轮询到 3(已生成)
export function generateKbQuestions(data: {
  documentIds: string[];
  modelId?: number;
  questionCount?: number;
}) {
  return Alova.Post<any>('/knowledge/document/generateKbQuestions', data);
}

// 删除文档（documentIds 逗号分隔）
export function delDocument(documentIds: string) {
  return Alova.Get<any>('/knowledge/document/del', { params: { documentIds } });
}

// 切换文档级知识图谱开关（kgEnabled: 1启用 2禁用，关闭时后端自动清理已有图谱）
export function toggleDocumentKg(documentId: string, kgEnabled: number) {
  return Alova.Post<any>('/knowledge/document/kgToggle', null, {
    params: { documentId, kgEnabled },
  });
}

// 文档详情（含子块列表）
export function getDocumentDetail(documentId: string) {
  return Alova.Get<any>('/knowledge/document/detail', { params: { documentId } });
}

// 查询文档入库耗时统计（各阶段耗时，供「统计」弹窗用）
export function getIngestionSummary(documentId: string) {
  return Alova.Get<any>('/knowledge/document/ingestionSummary', { params: { documentId } });
}

// 下载文档原文件：通过 Alova 取原生 fetch Response（isReturnNativeResponse=true
// 跳过拦截器的 response.json()，避免对二进制流按 JSON 解析报错），再 .blob() 拿字节。
// 带 token 头，绕过 window.open 无法加请求头的限制。
// 返回 Blob + 从 Content-Disposition 解析出的文件名。
//
// ★ 错误处理：后端 BusinessException 经全局异常处理器返回 HTTP 200 + JSON {code:1,message}
// （而非 4xx）。此路径下 Content-Type 是 application/json，需识别后抛出业务错误，
// 不能当成二进制流下载（否则会下载到一个含错误信息的脏文件）。
export async function downloadDocument(documentId: string): Promise<{ blob: Blob; fileName: string }> {
  const response: any = await Alova.Get('/knowledge/document/download', {
    params: { documentId },
    // 关键：标记为「返回原生响应」，拦截器不会调 response.json()，
    // 直接把 fetch Response 透传回来，我们再 .blob() 读二进制。
    meta: { isReturnNativeResponse: true } as any,
  } as any);
  const resp: Response = response as unknown as Response;
  if (!resp || typeof resp.blob !== 'function') {
    throw new Error('下载响应异常');
  }
  // 业务错误识别：Content-Type 为 JSON 时是后端 BusinessException 错误体
  const contentType = resp.headers.get('Content-Type') || '';
  if (contentType.includes('application/json')) {
    const errBody: any = await resp.json();
    throw new Error(errBody?.message || '该文档无原文件可下载');
  }
  const blob = await resp.blob();
  // 从 Content-Disposition 解析文件名（后端 URLEncode 过，需解码）
  let fileName = 'document';
  try {
    const cd = resp.headers.get('Content-Disposition') || '';
    const m = /filename=([^;]+)/.exec(cd);
    if (m && m[1]) {
      fileName = decodeURIComponent(m[1].replace(/^["']|["']$/g, '').trim());
    }
  } catch {
    // 解析失败用兜底名
  }
  return { blob, fileName };
}


// 段落列表（按知识库或文档）
export function getParagraphList(params: {
  kbId?: string;
  documentId?: string;
  page?: number;
  size?: number;
}) {
  return Alova.Get<any>('/knowledge/paragraph/list', { params });
}

// 手动新增段落
export function addParagraph(data: { kbId: string; content: string }) {
  return Alova.Post<any>('/knowledge/paragraph/add', data);
}

// 编辑段落内容
export function editParagraph(data: { id: string; kbId?: string; content: string }) {
  return Alova.Post<any>('/knowledge/paragraph/edit', data);
}

// 切换段落启停
export function activeParagraph(id: string, active: number) {
  return Alova.Get<any>('/knowledge/paragraph/active', { params: { id, active } });
}

// 删除段落
export function delParagraph(id: string) {
  return Alova.Get<any>('/knowledge/paragraph/del', { params: { id } });
}


// 问题列表（按知识库）
export function getQuestionList(params: { kbId: string; page?: number; size?: number }) {
  return Alova.Get<any>('/knowledge/question/list', { params });
}

// 手动新增问题
export function addQuestion(data: { kbId: string; content: string }) {
  return Alova.Post<any>('/knowledge/question/add', data);
}

// 编辑问题
export function editQuestion(data: { id: number; kbId?: string; content: string }) {
  return Alova.Post<any>('/knowledge/question/edit', data);
}

// 删除问题
export function delQuestion(id: number) {
  return Alova.Get<any>('/knowledge/question/del', { params: { id } });
}

// 关联问题到子块
export function relateQuestion(data: { questionId: number; chunkId: string }) {
  return Alova.Post<any>('/knowledge/question/doRelation', data);
}

// 批量导入问题（Excel，异步处理，返回 taskId）
export function importQuestions(kbId: string, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('kbId', kbId);
  // 注意：FormData 不要手动设置 Content-Type，让浏览器自动带 boundary
  return Alova.Post<any>('/knowledge/question/import', formData);
}

// 查询批量导入进度
export function getImportProgress(taskId: string) {
  return Alova.Get<any>('/knowledge/question/import/progress', { params: { taskId } });
}


// 流水线节点列表（按 pipelineId）
export function getPipelineList(pipelineId: string) {
  return Alova.Get<any>('/knowledge/pipeline/list', { params: { pipelineId } });
}

// 保存流水线节点定义
export function savePipeline(data: any) {
  return Alova.Post<any>('/knowledge/pipeline/save', data);
}

// 手动触发文档入库
export function runPipeline(documentId: string) {
  return Alova.Get<any>('/knowledge/pipeline/run', { params: { documentId } });
}

// 任务节点日志
export function getPipelineLogs(taskId: string) {
  return Alova.Get<any>('/knowledge/pipeline/logs', { params: { taskId } });
}


/** 意图节点（扁平列表，按 kind 分组展示） */
export interface IntentNodeTree {
  id: string;
  parentId?: string;
  level?: number; // 兼容旧字段，单层模型下恒为 0，无业务语义
  kind: string;  // KB / SYSTEM / MCP
  name: string;
  description?: string;
  examples?: string[];
  collectionName?: string;
  docIds?: string[]; // KB：限定文档列表，为空检索整库
  mcpToolId?: string;
  promptTemplate?: string;
  paramPromptTemplate?: string;
  topK?: number;
  enabled?: boolean;
  createdAt?: string;
  children?: IntentNodeTree[];
}

/** 新增/编辑入参 */
export interface IntentNodeSave {
  id?: string;
  parentId?: string;
  level?: number; // 可选，后端恒置 0
  kind: string;
  name: string;
  description?: string;
  examples?: string[];
  collectionName?: string;
  docIds?: string[]; // KB：限定文档列表，为空检索整库
  mcpToolId?: string;
  promptTemplate?: string;
  paramPromptTemplate?: string;
  topK?: number;
  enabled?: boolean;
  kbId?: string;
}

// 意图树（含禁用节点）
export function getIntentTree() {
  return Alova.Get<any>('/knowledge/intent/tree');
}

// 新增节点
export function addIntentNode(data: IntentNodeSave) {
  return Alova.Post<any>('/knowledge/intent/add', data);
}

// 编辑节点
export function editIntentNode(data: IntentNodeSave) {
  return Alova.Post<any>('/knowledge/intent/edit', data);
}

// 删除节点
export function delIntentNode(id: string) {
  return Alova.Post<any>('/knowledge/intent/del', { id });
}

// 批量启用
export function batchEnableIntent(ids: string[]) {
  return Alova.Post<any>('/knowledge/intent/batchEnable', ids);
}

// 批量禁用
export function batchDisableIntent(ids: string[]) {
  return Alova.Post<any>('/knowledge/intent/batchDisable', ids);
}


/** 评估单条用例（入参） */
export interface IntentEvalCase {
  query: string;
  expectNodeId: string;
  expectNodeName?: string;
  note?: string;
}

/** 候选项 */
export interface IntentHitCandidate {
  id: string;
  name: string;
  kind?: string;
  score: number;
}

/** 单条结果 */
export interface IntentEvalResult {
  query: string;
  expectNodeId: string;
  expectNodeName?: string;
  note?: string;
  hitNodeId?: string | null;
  hitNodeName?: string | null;
  hitKind?: string | null;
  score?: number | null;
  topKHits?: IntentHitCandidate[];
  top1Hit: boolean;
  top3Hit: boolean;
  empty: boolean;
  error: boolean;
  errorMsg?: string;
  ruleShortCircuit?: boolean;
}

/** 节点级指标 */
export interface IntentNodeMetric {
  nodeId: string;
  name?: string;
  kind?: string;
  caseCount: number;
  correct: number;
  wrong: number;
  precision: number;
  recall: number;
  f1: number;
}

/** 置信度校准桶 */
export interface IntentCalibrationBin {
  bin: string;
  acc: number;
  count: number;
}

/** 评估报告 */
export interface IntentEvalReport {
  total: number;
  accuracy1: number;
  accuracy3: number;
  emptyRate: number;
  errorRate: number;
  avgTop1Score: number;
  costMs: number;
  /** 总体评级：excellent / good / fair / poor */
  grade?: string;
  /** 评级文案，如「优秀」「良好」 */
  gradeLabel?: string;
  /** 一句话总评 */
  summary?: string;
  /** 改进建议 */
  tips?: string[];
  perNode: IntentNodeMetric[];
  calibration: IntentCalibrationBin[];
  misclassified: IntentEvalResult[];
  details: IntentEvalResult[];
}

/** 批量评估 */
export function evalIntentBatch(
  cases: IntentEvalCase[],
  opts?: { topN?: number; minScore?: number }
) {
  return Alova.Post<any>('/knowledge/intent/eval', {
    cases,
    topN: opts?.topN ?? 3,
    minScore: opts?.minScore ?? 0.35,
  });
}

/** 单条实时分类 */
export function evalIntentSingle(
  query: string,
  opts?: { topN?: number; minScore?: number }
) {
  return Alova.Post<any>('/knowledge/intent/evalSingle', {
    query,
    topN: opts?.topN ?? 3,
    minScore: opts?.minScore ?? 0.35,
  });
}

/** AI 生成测试集（基于意图配置自动生成多样化测试用例） */
export function evalIntentSeedGen(countPerNode?: number) {
  return Alova.Post<any>('/knowledge/intent/seedGen', {
    countPerNode: countPerNode ?? 4,
  });
}
