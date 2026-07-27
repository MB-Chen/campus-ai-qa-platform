import { Alova } from '@/utils/http/alova/index';

/**
 * 会话（Conversation / Session）相关接口。
 *
 * 端点对齐 WeKnora 契约：/api/v1/sessions ...
 * 注意：xservice 后端当前尚未实现这些端点，前端已可编译 / 导航；
 * 待后端补齐 /api/v1/sessions 系列接口即可联调（无需改动本文件）。
 */

export interface ChatSession {
  id: string;
  title?: string;
  description?: string;
  source?: string;
  created_at?: string;
  updated_at?: string;
  agent_config?: Record<string, any>;
  [key: string]: any;
}

export interface CreateSessionData {
  agent_id?: string;
  query?: string;
  title?: string;
  agent_config?: Record<string, any>;
  [key: string]: any;
}

/** 新建会话 */
export async function createSessions(data: CreateSessionData = {}) {
  return Alova.Post<{ id: string } | ChatSession>('/api/v1/sessions', data);
}

/** 会话列表（分页） */
export async function getSessionsList(page = 1, page_size = 20, source?: string) {
  const params: Record<string, any> = { page, page_size };
  if (source) params.source = source;
  return Alova.Get<any>('/api/v1/sessions', { params });
}

/** 会话详情 */
export async function getSession(session_id: string) {
  return Alova.Get<any>(`/api/v1/sessions/${session_id}`);
}

/** 更新会话标题 / 描述 */
export async function updateSession(session_id: string, data: { title?: string; description?: string }) {
  return Alova.Put<any>(`/api/v1/sessions/${session_id}`, data);
}

/** 删除会话 */
export async function deleteSession(session_id: string) {
  return Alova.Delete<any>(`/api/v1/sessions/${session_id}`);
}

/** 置顶会话 */
export async function pinSession(session_id: string) {
  return Alova.Post<any>(`/api/v1/sessions/${session_id}/pin`, {});
}

/** 取消置顶 */
export async function unpinSession(session_id: string) {
  return Alova.Delete<any>(`/api/v1/sessions/${session_id}/pin`);
}

/** 根据首条消息自动生成标题 */
export async function generateSessionTitle(session_id: string, data: any = {}) {
  return Alova.Post<any>(`/api/v1/sessions/${session_id}/generate_title`, data);
}

/** 清空会话消息 */
export async function clearSessionMessages(session_id: string) {
  return Alova.Delete<any>(`/api/v1/sessions/${session_id}/messages`);
}

/** 停止生成 */
export async function stopSession(session_id: string, message_id: string) {
  return Alova.Post<any>(`/api/v1/sessions/${session_id}/stop`, { message_id });
}
