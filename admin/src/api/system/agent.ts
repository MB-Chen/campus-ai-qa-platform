import { Alova } from '@/utils/http/alova/index';
import { useUser } from '@/store/modules/user';
import { useGlobSetting } from '@/hooks/setting/index';


/** 智能体列表/详情 */
export interface Agent {
  id?: string;
  name?: string;
  description?: string;
  avatar?: string;
  kbMode?: string; // all/selected/none
  knowledgeBaseIds?: string[];
  knowledgeBaseNames?: string[];
  documentIds?: string[];
  chatModelId?: number;
  chatModelName?: string;
  systemPrompt?: string;
  temperature?: number;
  maxTokens?: number;
  historyTurns?: number;
  embeddingTopK?: number;
  vectorThreshold?: number;
  keywordThreshold?: number;
  rerankModelId?: number;
  rerankModelName?: string;
  rerankEnabled?: number; // 1启用 2禁用
  rerankTopK?: number;
  rerankThreshold?: number;
  rewriteModelId?: number; // 意图/改写专用模型 id，空用全局默认
  rewriteModelName?: string;
  fallbackStrategy?: string; // model/fixed
  fallbackResponse?: string;
  welcome?: string;
  suggestedQuestions?: string[];
  status?: number; // 1正常 2禁用
  createdAt?: string;
}

/** 新增/编辑入参 */
export interface AgentSave {
  id?: string;
  name: string;
  description?: string;
  avatar?: string;
  kbMode?: string;
  knowledgeBaseIds?: string[];
  documentIds?: string[];
  chatModelId?: number;
  chatModelName?: string;
  systemPrompt?: string;
  temperature?: number;
  maxTokens?: number;
  historyTurns?: number;
  embeddingTopK?: number;
  vectorThreshold?: number;
  keywordThreshold?: number;
  rerankModelId?: number;
  rerankModelName?: string;
  /** ★ 仅前端用：重排模型组合 key `${modelId}::${modelName}`，提交时拆成 id+name（不入库） */
  rerankModelKey?: string;
  rerankEnabled?: number;
  rerankTopK?: number;
  rerankThreshold?: number;
  rewriteModelId?: number; // 意图/改写专用模型 id，空用全局默认
  rewriteModelName?: string;
  fallbackStrategy?: string;
  fallbackResponse?: string;
  welcome?: string;
  suggestedQuestions?: string[];
  status?: number;
}

/** 测试对话入参 */
export interface AgentChatRequest {
  agentId: string;
  conversationId?: string;
  query: string;
}

/** 测试对话消息（前端组装） */
export interface AgentChatMessage {
  role: 'user' | 'assistant';
  content: string;
  /** assistant 消息的引用来源 */
  references?: AgentReference[];
  /** 是否正在生成（流式） */
  streaming?: boolean;
  /** RAG 各阶段耗时（name → ms），由后端 complete 事件回传 */
  stageTimings?: Record<string, number>;
  /** RAG 管线总耗时(ms) */
  totalCost?: number;
  /** RAG 各阶段上下文（调用流程抽屉展示用），由后端 complete 事件回传 */
  stageData?: RagStageData;
  /** 编排智能体专用：各节点执行步骤（含召回片段/耗时/prompt），由 workflow runDetail 回填 */
  workflowSteps?: import('./workflow').WorkflowStep[];
  /** 编排智能体专用：本轮 token 总量，由 workflow complete 事件回传 */
  totalTokens?: number;
}

/** 引用来源 */
export interface AgentReference {
  index: number;
  content: string;
  documentId?: string;
}

// ===== RAG 调用流程上下文类型（对齐后端 RagTraceBuilder 产出结构） =====

/** 单阶段基类 */
export interface RagStageBase {
  /** 是否执行过该阶段 */
  ran: boolean;
}

/** 改写拆分阶段 */
export interface RagRewriteSplitStage extends RagStageBase {
  original?: string;
  rewritten?: string;
  subQuestions?: string[];
}

/** 意图判定阶段 */
export interface RagIntentStage extends RagStageBase {
  code?: string;
  desc?: string;
  needsRetrieval?: boolean;
  /** 是否注入（非走 IntentStage，如 kbMode=none 直接注入闲聊） */
  injected?: boolean;
}

/** 意图分类（tree-intent）候选 */
export interface RagIntentCandidate {
  name?: string;
  kind?: string;
  fullPath?: string;
  score: number;
}
export interface RagTreeIntentStage extends RagStageBase {
  candidates?: RagIntentCandidate[];
}

/** 歧义引导 */
export interface RagGuidanceStage extends RagStageBase {
  prompt?: boolean;
  message?: string | null;
}

/** 检索召回片段 */
export interface RagRetrieveFragment {
  text: string;
  documentId?: string;
  channel?: string;
  rrfRank?: string;
}
export interface RagRetrieveStage extends RagStageBase {
  count?: number;
  fragments?: RagRetrieveFragment[];
}

/** 重排打分条目 */
export interface RagRerankScored {
  text: string;
  score: number;
  kept: boolean;
}
export interface RagRerankStage extends RagStageBase {
  threshold?: number;
  topK?: number;
  scored?: RagRerankScored[];
  keptCount?: number;
}

/** 合并阶段 */
export interface RagMergeStage extends RagStageBase {
  count?: number;
}

/** 兜底阶段 */
export interface RagFallbackStage extends RagStageBase {
  strategy?: string;
  response?: string;
}

/** 生成阶段 */
export interface RagGenerateStage extends RagStageBase {
  promptScene?: string;
}

/** 各阶段集合 */
export interface RagStages {
  'rewrite-split'?: RagRewriteSplitStage;
  intent?: RagIntentStage;
  'tree-intent'?: RagTreeIntentStage;
  guidance?: RagGuidanceStage;
  'vague-clarify'?: RagStageBase;
  retrieve?: RagRetrieveStage;
  rerank?: RagRerankStage;
  merge?: RagMergeStage;
  fallback?: RagFallbackStage;
  generate?: RagGenerateStage;
  [key: string]: any;
}

/** RAG 调用流程完整数据 */
export interface RagStageData {
  originalQuery?: string;
  totalCost?: number;
  llmCallCount?: number;
  stageTimings?: Record<string, number>;
  stages?: RagStages;
}

/** complete 事件载荷（含 RAG 各阶段耗时，供前端时间线展示） */
export interface AgentChatCompletePayload {
  answer: string;
  references: AgentReference[];
  conversationId: string;
  /** RAG 各阶段耗时（name → ms），无则空对象 */
  stageTimings: Record<string, number>;
  /** RAG 管线总耗时(ms)，无则 0 */
  totalCost: number;
  /** RAG 各阶段上下文（调用流程抽屉展示用），无则 undefined */
  stageData?: RagStageData;
}

/** 评估用例 */
export interface AgentEvalCase {
  query: string;
  expectedAnswer?: string;
  note?: string;
}

/** 评估报告 */
export interface AgentEvalReport {
  total: number;
  errorRate: number;
  avgScore: number;
  costMs: number;
  grade: string;
  gradeLabel: string;
  summary: string;
  tips: string[];
  dimensions: AgentDimension[];
  scoreBins: AgentScoreBin[];
  details: AgentEvalCaseDetail[];
}

export interface AgentDimension {
  key: string;
  label: string;
  score: number;
}

export interface AgentScoreBin {
  bin: string;
  count: number;
}

export interface AgentEvalCaseDetail {
  query: string;
  expectedAnswer?: string;
  note?: string;
  answer: string;
  referenceCount: number;
  relevance: number;
  accuracy: number;
  completeness: number;
  groundedness: number;
  conciseness: number;
  overall: number;
  comment: string;
  error: boolean;
  errorMsg?: string;
}


export const FALLBACK_OPTIONS = [
  { label: '走模型兜底（让 LLM 自由回答）', value: 'model' },
  { label: '固定话术', value: 'fixed' },
];

/** 知识库关联模式（对齐 WeKnora 三态） */
export const KB_MODE_OPTIONS = [
  { label: '全部知识库', value: 'all' },
  { label: '指定知识库', value: 'selected' },
  { label: '不使用知识库', value: 'none' },
];


export function getAgentList(params: { keyword?: string; page: number; size: number }) {
  return Alova.Get<any>('/knowledge/agent/index', { params });
}

export function getAgentEnabledList() {
  return Alova.Get<any>('/knowledge/agent/list');
}

export function getAgentInfo(id: string) {
  return Alova.Get<any>('/knowledge/agent/info', { params: { id } });
}

export function addAgent(data: AgentSave) {
  return Alova.Post<any>('/knowledge/agent/add', data);
}

export function editAgent(data: AgentSave) {
  return Alova.Post<any>('/knowledge/agent/edit', data);
}

export function delAgent(id: string) {
  return Alova.Get<any>('/knowledge/agent/del', { params: { id } });
}


export function evalAgent(agentId: string, cases: AgentEvalCase[]) {
  return Alova.Post<any>('/knowledge/agent/eval', { agentId, cases });
}

export function evalAgentSeed(agentId: string, sampleCount?: number) {
  return Alova.Post<any>('/knowledge/agent/evalSeed', { agentId, sampleCount });
}

//
// Alova 基于 fetch 但强制 JSON 整体解析，无法消费 SSE 流，故单独实现。
// header 与 baseURL 复刻 alova/index.ts beforeRequest 逻辑：
//   - header 名为 'token'（非 Authorization），值取 useUser().getToken
//   - URL = apiUrl + urlPrefix + path

/**
 * 发起 SSE 流式对话。
 *
 * @param req 请求体
 * @param handlers 事件回调（onAnswer 收 token 增量；onComplete 收完整答案+引用；onError 收错误）
 * @param signal 可选 AbortSignal（取消生成）
 */
export async function streamAgentChat(
  req: AgentChatRequest,
  handlers: {
    onAnswer?: (token: string) => void;
    onComplete?: (payload: AgentChatCompletePayload) => void;
    onError?: (message: string) => void;
  },
  signal?: AbortSignal,
): Promise<void> {
  const userStore = useUser();
  const token = userStore.getToken;
  const { apiUrl, urlPrefix } = useGlobSetting();

  const url = `${apiUrl}${urlPrefix}/knowledge/agent/chat`;

  const resp = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      token: token || '',
    },
    body: JSON.stringify(req),
    signal,
  });

  if (!resp.ok || !resp.body) {
    throw new Error(`SSE 连接失败：${resp.status}`);
  }

  const reader = resp.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  // 解析 SSE 帧：按 \n\n 分帧，每帧内 event:/data: 行
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let sepIdx: number;
    // 帧分隔符 \n\n
    while ((sepIdx = buffer.indexOf('\n\n')) >= 0) {
      const frame = buffer.slice(0, sepIdx);
      buffer = buffer.slice(sepIdx + 2);
      parseSseFrame(frame, handlers);
    }
  }
  // 处理尾包
  if (buffer.trim()) {
    parseSseFrame(buffer, handlers);
  }
}

/** 解析单个 SSE 帧（event:/data: 行） */
function parseSseFrame(
  frame: string,
  handlers: {
    onAnswer?: (token: string) => void;
    onComplete?: (payload: AgentChatCompletePayload) => void;
    onError?: (message: string) => void;
  },
) {
  let eventName = 'message';
  const dataLines: string[] = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim());
    }
  }
  if (dataLines.length === 0) return;
  const dataStr = dataLines.join('\n');
  let payload: any;
  try {
    payload = JSON.parse(dataStr);
  } catch {
    return;
  }
  if (eventName === 'answer' || payload.type === 'answer') {
    handlers.onAnswer?.(payload.content || '');
  } else if (eventName === 'complete' || payload.type === 'complete') {
    handlers.onComplete?.({
      answer: payload.answer || '',
      references: Array.isArray(payload.references) ? payload.references : [],
      conversationId: payload.conversationId || '',
      stageTimings:
        payload.stageTimings && typeof payload.stageTimings === 'object' ? payload.stageTimings : {},
      totalCost: typeof payload.totalCost === 'number' ? payload.totalCost : 0,
      stageData:
        payload.stageData && typeof payload.stageData === 'object' ? payload.stageData : undefined,
    });
  } else if (eventName === 'error' || payload.type === 'error') {
    handlers.onError?.(payload.message || '生成失败');
  }
}
