import { Alova } from '@/utils/http/alova/index';
import { useUser } from '@/store/modules/user';
import { useGlobSetting } from '@/hooks/setting/index';

/** 编排列表项 */
export interface Workflow {
  id?: string;
  name?: string;
  description?: string;
  flowData?: string;
  status?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** 编排详情（含 flowData） */
export interface WorkflowInfo {
  id?: string;
  flowData?: string;
}

/** 执行详情节点 */
export interface RuntimeContextVo {
  nodeType?: string;
  step?: number;
  outputData?: string;
  modelData?: string;
}

// CRUD（走 Alova，返回 {code,message,data} 整包）
export function getWorkflowList(params: {
  keyword?: string;
  page: number;
  size: number;
}) {
  return Alova.Get<any>('/workflow/index', { params });
}

export function getWorkflowInfo(id: string) {
  return Alova.Get<any>('/workflow/info', { params: { id } });
}

export function addWorkflow(data: { name?: string; description?: string }) {
  return Alova.Post<any>('/workflow/add', data);
}

export function editWorkflowMeta(data: {
  id: string;
  name?: string;
  description?: string;
}) {
  return Alova.Post<any>('/workflow/editMeta', data);
}

export function saveWorkflow(data: { id: string; flowData: string }) {
  return Alova.Post<any>('/workflow/save', data);
}

export function delWorkflow(id: string) {
  return Alova.Get<any>('/workflow/del', { params: { id } });
}

export function copyWorkflow(id: string) {
  return Alova.Post<any>('/workflow/copy', null, { params: { id } });
}

export function getRunDetail(runtimeId: number) {
  return Alova.Get<any>('/workflow/runDetail', { params: { runtimeId } });
}

//
// Alova 基于 fetch 但强制 JSON 整体解析，无法消费 SSE 流，故单独实现。
// header 与 baseURL 复刻 alova/index.ts beforeRequest 逻辑：
//   - header 名为 'token'（非 Authorization），值取 useUser().getToken
//   - URL = apiUrl + urlPrefix + path
//
// SSE 事件协议（对齐后端 WorkflowSseHelper）：
//   event: answer    data: {type:'answer', content, runtimeId, cell}
//   event: node      data: {type:'node', runtimeId, cell, nodeType}   节点开始
//   event: node_end  data: {type:'node_end', runtimeId, cell}         节点结束
//   event: complete  data: {type:'complete', inputTokens, outputTokens, totalTokens, time}
//   event: error     data: {type:'error', message}

export interface WorkflowStreamHandlers {
  onAnswer?: (token: string) => void;
  onNode?: (payload: { runtimeId?: number; cell?: string; nodeType?: string }) => void;
  onNodeEnd?: (payload: { runtimeId?: number; cell?: string }) => void;
  onComplete?: (payload: {
    inputTokens?: number;
    outputTokens?: number;
    totalTokens?: number;
    time?: number;
  }) => void;
  onError?: (message: string) => void;
}

export async function streamWorkflowChat(
  req: { workflowId: string; conversationId?: string; query: string },
  handlers: WorkflowStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const userStore = useUser();
  const token = userStore.getToken;
  const { apiUrl, urlPrefix } = useGlobSetting();

  const url = `${apiUrl}${urlPrefix}/workflow/chat`;

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
    while ((sepIdx = buffer.indexOf('\n\n')) >= 0) {
      const frame = buffer.slice(0, sepIdx);
      buffer = buffer.slice(sepIdx + 2);
      parseWorkflowSseFrame(frame, handlers);
    }
  }
  if (buffer.trim()) {
    parseWorkflowSseFrame(buffer, handlers);
  }
}

/** 解析单个 SSE 帧（event:/data: 行） */
function parseWorkflowSseFrame(frame: string, handlers: WorkflowStreamHandlers) {
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

  let payload: any = {};
  try {
    payload = JSON.parse(dataLines.join('\n'));
  } catch {
    return;
  }

  switch (eventName) {
    case 'answer':
      handlers.onAnswer?.(payload.content ?? '');
      break;
    case 'node':
      handlers.onNode?.(payload);
      break;
    case 'node_end':
      handlers.onNodeEnd?.(payload);
      break;
    case 'complete':
      handlers.onComplete?.(payload);
      break;
    case 'error':
      handlers.onError?.(payload.message ?? '编排执行异常');
      break;
  }
}
