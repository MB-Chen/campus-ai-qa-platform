import { Alova } from '@/utils/http/alova/index';

/**
 * 校园智能问答平台 - 意图枚举（与后端 Intent.java 同步）
 */
export const Intent = {
  TOOL_QUERY: 'TOOL_QUERY',
  KNOWLEDGE_QA: 'KNOWLEDGE_QA',
  FALLBACK: 'FALLBACK',
};

/**
 * 校园平台聊天 API
 *
 * 走 Alova 封装：自动拼后端 baseURL（开发环境 http://localhost:7026）
 * + 自动注入登录 token（后端 /api/campus/chat 在 LoginInterceptor 拦截范围内）。
 *
 * @param {string} question 用户问题
 * @returns {Promise<{answer: string, status: string}>}
 */
export function campusChat(question) {
  return Alova.Post('/api/campus/chat', { question });
}
