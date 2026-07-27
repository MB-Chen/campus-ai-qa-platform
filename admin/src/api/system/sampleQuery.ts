import { Alova } from '@/utils/http/alova/index';


/**
 * 样例查询问答对（对应后端 SampleQuery）
 *
 * 录入常用 Q&A：question 参与向量化，answer 命中后直接返回（不向量化）。
 * 向量独立存储在 sample_query 表的 embedding 列（pgvector），不写入 chunks 表。
 *
 * 问答时拿用户问题做 embedding，与样例问题向量算余弦相似度，
 * 高于 similarityThreshold 即直接返回 answer，不走大模型。
 */
export interface SampleQuery {
  id?: number;
  question: string; // 问题文本（参与向量化）
  answer: string; // 答案文本（命中后直接返回）
  vectorized?: number; // 0未向量化 1已向量化
  source?: string; // manual / import
  status?: number; // 1启用 2禁用
  createdAt?: string;
  updatedAt?: string;
}

/** 全局配置（整个功能共用一套，后端固定 id=1） */
export interface SampleQueryConfig {
  id?: number;
  embeddingModelId?: number; // 关联 ai_model.id（type=2）
  embeddingModelName?: string; // 冗余快照：具体模型名
  similarityThreshold?: number; // 命中相似度阈值 0~1
  updatedAt?: string;
}

/** 导入结果（同步返回） */
export interface SampleQueryImportResult {
  total: number;
  success: number;
  failed: number;
  skipped: number;
}

/** 批量向量化进度（Redis 桶，前端轮询） */
export interface SampleQueryVectorizeProgress {
  status: string; // processing / done / failed
  total: number;
  done: number;
  success: number;
  failed: number;
  message?: string;
}


// 分页列表（支持 keyword/status/vectorized 过滤）
export function getSampleQueryList(params: {
  keyword?: string;
  status?: number;
  vectorized?: number; // 1已向量化 2未向量化
  page?: number;
  size?: number;
}) {
  return Alova.Get<any>('/knowledge/sampleQuery/list', { params });
}

// 新增
export function addSampleQuery(data: { question: string; answer: string; status?: number }) {
  return Alova.Post<any>('/knowledge/sampleQuery/add', data);
}

// 编辑
export function editSampleQuery(data: { id: number; question: string; answer: string; status?: number }) {
  return Alova.Post<any>('/knowledge/sampleQuery/edit', data);
}

// 删除
export function delSampleQuery(id: number) {
  return Alova.Get<any>('/knowledge/sampleQuery/del', { params: { id } });
}


// 批量导入（Excel，同步处理）
export function importSampleQuery(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return Alova.Post<any>('/knowledge/sampleQuery/import', formData);
}

/**
 * 导出（Excel，按过滤条件）。
 *
 * 通过 Alova 取原生 fetch Response（isReturnNativeResponse=true 跳过拦截器的 response.json()，
 * 避免对二进制流按 JSON 解析报错），再 .blob() 拿字节。
 *
 * ★ 错误处理：后端 BusinessException 经全局异常处理器返回 HTTP 200 + JSON {code:1,message}。
 * 此路径下 Content-Type 是 application/json，需识别后抛出业务错误，不能当成二进制流下载。
 *
 * 返回 Blob + 从 Content-Disposition 解析出的文件名，调用方触发浏览器下载。
 */
export async function exportSampleQuery(params: {
  keyword?: string;
  status?: number;
  vectorized?: number;
}): Promise<{ blob: Blob; fileName: string }> {
  const response: any = await Alova.Get('/knowledge/sampleQuery/export', {
    params,
    // 关键：标记为「返回原生响应」，拦截器不会调 response.json()，
    // 直接把 fetch Response 透传回来，我们再 .blob() 读二进制。
    meta: { isReturnNativeResponse: true } as any,
  } as any);
  const resp: Response = response as unknown as Response;
  if (!resp || typeof resp.blob !== 'function') {
    throw new Error('导出响应异常');
  }
  // 业务错误识别：Content-Type 为 JSON 时是后端 BusinessException 错误体
  const contentType = resp.headers.get('Content-Type') || '';
  if (contentType.includes('application/json')) {
    const errBody: any = await resp.json();
    throw new Error(errBody?.message || '导出失败');
  }
  const blob = await resp.blob();
  // 从 Content-Disposition 解析文件名（后端 URLEncode 过，需解码）
  let fileName = '样例查询导出.xlsx';
  try {
    const cd = resp.headers.get('Content-Disposition') || '';
    const m = /filename\*=([^;]+)/.exec(cd) || /filename=([^;]+)/.exec(cd);
    if (m && m[1]) {
      let raw = m[1].replace(/^["']|["']$/g, '').trim();
      // filename*=UTF-8''xxx 格式需去掉前缀
      const starIdx = raw.indexOf("''");
      if (starIdx >= 0) raw = raw.slice(starIdx + 2);
      fileName = decodeURIComponent(raw);
    }
  } catch {
    // 解析失败用默认文件名
  }
  return { blob, fileName };
}


// 单条向量化
export function vectorizeSampleQuery(id: number) {
  return Alova.Get<any>('/knowledge/sampleQuery/vectorize', { params: { id } });
}

// 批量向量化（异步，返回 taskId）
export function vectorizeSampleQueryBatch(ids?: number[]) {
  const body = ids && ids.length > 0 ? { ids } : {};
  return Alova.Post<any>('/knowledge/sampleQuery/vectorize/batch', body);
}

// 查询批量向量化进度
export function getVectorizeProgress(taskId: string) {
  return Alova.Get<any>('/knowledge/sampleQuery/vectorize/progress', { params: { taskId } });
}


// 读取配置
export function getSampleQueryConfig() {
  return Alova.Get<any>('/knowledge/sampleQuery/config');
}

// 保存配置
export function saveSampleQueryConfig(data: Partial<SampleQueryConfig>) {
  return Alova.Post<any>('/knowledge/sampleQuery/config/save', data);
}
