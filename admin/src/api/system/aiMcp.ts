import { Alova } from '@/utils/http/alova/index';


/**
 * MCP 服务配置实体（外部 MCP Server 连接配置）。
 * 密钥脱敏：apiKey/token 不回显，仅返回 hasApiKey/hasToken 布尔。
 *
 * authConfig JSON schema 按 authType：
 *  - none: 忽略
 *  - api_key: { apiKey, apiKeyHeader(默认 X-API-Key) }
 *  - bearer: { token }
 * headers 为自定义请求头 JSON { "k": "v" }
 */
export interface McpServer {
  id?: number;
  name: string;
  description?: string;
  enabled?: boolean;
  transportType: 'sse' | 'http_streamable';
  url: string;
  authType: 'none' | 'api_key' | 'bearer';
  authConfig?: string; // 认证 JSON 文本
  headers?: string; // 自定义请求头 JSON 文本
  timeoutSec?: number;
  retryCount?: number;
  remark?: string;
  sort?: number;
  // VO 出参附加字段（密钥脱敏标记）
  hasApiKey?: boolean;
  hasToken?: boolean;
  apiKeyHeader?: string;
  toolCount?: number;
  createTime?: string;
  updateTime?: string;
}

/** MCP 工具快照（供意图树下拉选择） */
export interface McpTool {
  id?: number;
  serverId?: number;
  fullId: string; // 全局唯一工具标识，= 意图节点 mcpToolId 的值
  toolName: string;
  description?: string;
  inputSchema?: string; // 工具入参 JSON Schema 原文
  lastSyncedAt?: string;
}

/** 测试连接结果 */
export interface McpTestResult {
  success: boolean;
  message?: string;
  tools?: McpTestTool[];
  resources?: McpTestResource[];
  latencyMs?: number;
}

export interface McpTestTool {
  name: string;
  description?: string;
  inputSchema?: string;
}

export interface McpTestResource {
  uri: string;
  name?: string;
  description?: string;
  mimeType?: string;
}

/** 新增/编辑入参（对齐后端 McpServerSaveValidate） */
export interface McpServerSave {
  id?: number;
  name: string;
  description?: string;
  enabled?: boolean;
  transportType: 'sse' | 'http_streamable';
  url: string;
  authType: 'none' | 'api_key' | 'bearer';
  authConfig?: string;
  headers?: string;
  timeoutSec?: number;
  retryCount?: number;
  remark?: string;
  sort?: number;
}

// 注：langchain4j 的 legacy SSE 专用 HttpMcpTransport 已标记 forRemoval，底层统一用
// StreamableHttpMcpTransport（它是 HttpMcpTransport 的扩展）。前端只暴露 HTTP Streamable
// 一种传输类型，简化配置；后端仍兼容存量 sse 类型记录（底层同样走 StreamableHttpMcpTransport）。

/** 传输类型选项 */
export const TRANSPORT_OPTIONS = [
  { label: 'HTTP Streamable（MCP 标准传输）', value: 'http_streamable' },
];

export const TRANSPORT_LABEL: Record<string, string> = {
  sse: 'SSE',
  http_streamable: 'HTTP Streamable',
};

export const TRANSPORT_TAG_TYPE: Record<string, 'success' | 'info' | 'warning'> = {
  sse: 'success',
  http_streamable: 'info',
};

/** 认证类型选项 */
export const AUTH_OPTIONS = [
  { label: '无认证', value: 'none' },
  { label: 'API Key（自定义头）', value: 'api_key' },
  { label: 'Bearer Token', value: 'bearer' },
];

export const AUTH_LABEL: Record<string, string> = {
  none: '无',
  api_key: 'API Key',
  bearer: 'Bearer',
};

// 注：拦截器返回完整 {code,message,data}，泛型用 <any>（与 aiModel.ts 范式一致）。
// 调用方须严格校验 res.code===0 + Array.isArray(res.data) 再用。

/** 服务列表（可选 enabled 过滤） */
export function getMcpList(enabled?: boolean) {
  return Alova.Get<any>('/ai/mcp/list', { params: enabled === undefined ? {} : { enabled } });
}

/** 服务详情 */
export function getMcpInfo(id: number) {
  return Alova.Get<any>('/ai/mcp/info', { params: { id } });
}

/** 新增服务 */
export function addMcp(data: McpServerSave) {
  return Alova.Post<any>('/ai/mcp/add', data);
}

/** 编辑服务 */
export function editMcp(data: McpServerSave) {
  return Alova.Post<any>('/ai/mcp/edit', data);
}

/** 删除服务 */
export function delMcp(id: number) {
  return Alova.Get<any>('/ai/mcp/del', { params: { id } });
}

/** 切换启停 */
export function switchMcpStatus(id: number, enabled: boolean) {
  return Alova.Get<any>('/ai/mcp/status', { params: { id, enabled } });
}

/** 测试已保存配置连通性 */
export function testMcp(id: number) {
  return Alova.Get<any>('/ai/mcp/test', { params: { id } });
}

/** 测试连通性（按表单参数，无需保存） */
export function testMcpConnect(data: McpServerSave) {
  return Alova.Post<any>('/ai/mcp/testConnect', data);
}

/** 获取某服务的工具列表（从快照读） */
export function getMcpTools(serverId: number) {
  return Alova.Get<any>('/ai/mcp/tools', { params: { serverId } });
}

/** 获取全部已启用服务的工具（供意图树下拉） */
export function getEnabledMcpTools() {
  return Alova.Get<any>('/ai/mcp/enabledTools');
}

/** 重新拉取工具并刷新快照 */
export function refreshMcpTools(id: number) {
  return Alova.Get<any>('/ai/mcp/refresh', { params: { id } });
}
