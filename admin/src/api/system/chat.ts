import { Alova } from '@/utils/http/alova/index';

/**
 * 会话（Conversation / Session）相关接口。
 *
 * 端点对齐后端 ChatSessionController：/api/v1/sessions ...
 * 走全局登录拦截器，按当前登录用户隔离会话数据。
 */

export interface ChatSession {
  id: string;
  title?: string;
  description?: string;
  source?: string;
  kind?: string;
  agent_id?: string;
  created_at?: string;
  updated_at?: string;
  agent_config?: Record<string, any>;
  [key: string]: any;
}

/** 聊天消息（对齐后端 ChatMessageVo） */
export interface ChatMessageVo {
  id?: number;
  role: string;
  content: string;
  references?: any;
  stage_data?: any;
  workflow_steps?: any;
  total_cost?: number;
  total_tokens?: number;
  created_at?: string;
  [key: string]: any;
}

export interface CreateSessionData {
  agent_id?: string;
  query?: string;
  title?: string;
  kind?: string;
  agent_config?: Record<string, any>;
  [key: string]: any;
}

/** 落库单条消息入参（对齐后端 ChatMessageSaveValidate） */
export interface SaveMessageData {
  role: string;
  content: string;
  references?: any;
  stage_data?: any;
  workflow_steps?: any;
  total_cost?: number;
  total_tokens?: number;
  [key: string]: any;
}

/** 新建会话 */
export async function createSessions(data: CreateSessionData = {}) {
  // 后端按 agent_id / kind / query 解析；保持 snake_case 入参
  const payload = toSnake(data);
  // agent_config 是 jsonb 列（后端 String 入参），对象需序列化
  if (payload.agent_config != null && typeof payload.agent_config !== 'string') {
    payload.agent_config = JSON.stringify(payload.agent_config);
  }
  return Alova.Post<ChatSession>('/api/v1/sessions', payload);
}

/** 会话列表（分页） */
export async function getSessionsList(page = 1, size = 50) {
  return Alova.Get<any>('/api/v1/sessions', { params: { page, size } });
}

/** 会话详情 */
export async function getSession(session_id: string) {
  return Alova.Get<any>(`/api/v1/sessions/${session_id}`);
}

/** 更新会话标题 / 描述 */
export async function updateSession(session_id: string, data: { title?: string; description?: string }) {
  return Alova.Put<any>(`/api/v1/sessions/${session_id}`, data);
}

/** 删除会话（级联删消息） */
export async function deleteSession(session_id: string) {
  return Alova.Delete<any>(`/api/v1/sessions/${session_id}`);
}

/** 置顶会话（后端暂未实现，预留） */
export async function pinSession(session_id: string) {
  return Alova.Post<any>(`/api/v1/sessions/${session_id}/pin`, {});
}

/** 取消置顶（后端暂未实现，预留） */
export async function unpinSession(session_id: string) {
  return Alova.Delete<any>(`/api/v1/sessions/${session_id}/pin`);
}

/** 根据首条消息自动生成标题（后端暂未实现，预留） */
export async function generateSessionTitle(session_id: string, data: any = {}) {
  return Alova.Post<any>(`/api/v1/sessions/${session_id}/generate_title`, data);
}

/** 会话消息列表（按 id 升序） */
export async function getSessionMessages(session_id: string) {
  return Alova.Get<any>(`/api/v1/sessions/${session_id}/messages`);
}

/** 落库单条消息（流式回答结束后调用） */
export async function saveSessionMessage(session_id: string, data: SaveMessageData) {
  return Alova.Post<any>(`/api/v1/sessions/${session_id}/messages`, data);
}

/** 清空会话消息（会话本身保留） */
export async function clearSessionMessages(session_id: string) {
  return Alova.Delete<any>(`/api/v1/sessions/${session_id}/messages`);
}

/** 停止生成（后端暂未实现，预留） */
export async function stopSession(session_id: string, message_id: string) {
  return Alova.Post<any>(`/api/v1/sessions/${session_id}/stop`, { message_id });
}

/** 将对象 key 由 camelCase 转为 snake_case（入参对齐后端 validate） */
function toSnake(obj: Record<string, any>): Record<string, any> {
  const out: Record<string, any> = {};
  for (const [k, v] of Object.entries(obj)) {
    const sk = k.replace(/[A-Z]/g, (m) => '_' + m.toLowerCase());
    out[sk] = v;
  }
  return out;
}
