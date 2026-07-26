/**
 * 表格数据处理工具
 * 用于统一处理不同接口返回的数据结构，适配表格组件期望的格式
 */

/**
 * 标准分页数据结构
 */
export interface PaginationData {
  list: any[]; // 数据列表
  pageCount: number; // 总页数
  page: number; // 当前页
  itemCount: number; // 总条数
  pageSize: number; // 每页数量
  [key: string]: any; // 其他字段
}

/**
 * 接口返回的标准响应结构
 */
export interface ApiResponse {
  code: number;
  data: {
    total?: number; // 总条数
    per_page?: number; // 每页数量
    current_page?: number; // 当前页
    last_page?: number; // 总页数
    data?: any[]; // 数据列表
    has_more?: boolean; // 是否有更多数据
    [key: string]: any; // 其他字段
  };
  message: string;
}

/**
 * 字段映射配置
 */
export interface FieldMapping {
  // 数据列表字段映射
  listField?: string;
  // 总页数字段映射
  totalPageField?: string;
  // 当前页字段映射
  currentPageField?: string;
  // 总条数字段映射
  totalField?: string;
  // 每页数量字段映射
  pageSizeField?: string;
}

/**
 * 默认字段映射配置（适配 ThinkPHP 风格接口）
 */
const DEFAULT_FIELD_MAPPING: FieldMapping = {
  listField: 'data',
  totalPageField: 'last_page',
  currentPageField: 'current_page',
  totalField: 'total',
  pageSizeField: 'per_page',
};

/**
 * 标准化分页数据
 * 将接口返回的数据转换为表格组件期望的格式
 *
 * @param response 接口返回的数据
 * @param mapping 字段映射配置，可选
 * @returns 标准化的分页数据
 */
export function normalizeTableData(
  response: ApiResponse | any,
  mapping: FieldMapping = {}
): PaginationData {
  const config = { ...DEFAULT_FIELD_MAPPING, ...mapping };

  // 检查是否为直接返回的分页数据结构（无嵌套的 data 字段）
  if (response && (response.total !== undefined || response.data !== undefined)) {
    // 直接的分页数据结构
    const list = response.data || [];
    const pageCount = response.last_page || 0;
    const page = response.current_page || 1;
    const itemCount = response.total || 0;
    const pageSize = response.per_page || 10;

    return {
      list,
      pageCount,
      page,
      itemCount,
      pageSize,
      // 保留原始数据的其他字段
      ...response,
    };
  }

  // 检查是否为嵌套的数据结构
  if (!response || !response.data) {
    return getEmptyData();
  }

  const data = response.data;

  // 提取数据
  const list = data[config.listField!] || [];
  const pageCount = data[config.totalPageField!] || 0;
  const page = data[config.currentPageField!] || 1;
  const itemCount = data[config.totalField!] || 0;
  const pageSize = data[config.pageSizeField!] || 10;

  return {
    list,
    pageCount,
    page,
    itemCount,
    pageSize,
    // 保留原始数据的其他字段
    ...data,
  };
}

/**
 * 获取空的分页数据结构
 */
export function getEmptyData(): PaginationData {
  return {
    list: [],
    pageCount: 0,
    page: 1,
    itemCount: 0,
    pageSize: 10,
  };
}

/**
 * 处理分页请求参数
 * 保持使用标准的 page 和 limit 参数，不做转换
 *
 * @param params 表格组件的分页参数
 * @returns 接口期望的参数格式
 */
export function normalizeTableParams(params: any): any {
  const { page = 1, pageSize = 10, ...otherParams } = params;

  // 保持使用标准的 page 和 limit 参数
  return {
    page,
    limit: pageSize,
    ...otherParams,
  };
}

/**
 * 创建表格数据加载器
 * 高阶函数，用于创建标准化的表格数据加载函数
 *
 * @param apiCall API 调用函数
 * @param mapping 字段映射配置，可选
 * @returns 包装后的数据加载函数
 */
export function createTableDataLoader(
  apiCall: (params: any) => Promise<any>,
  mapping: FieldMapping = {}
) {
  return async (params: any): Promise<PaginationData> => {
    try {
      // 标准化请求参数 (使用 page 和 limit)
      const normalizedParams = normalizeTableParams(params);

      // 调用 API
      const response = await apiCall(normalizedParams);

      // 标准化返回数据
      return normalizeTableData(response.data, mapping);
    } catch (error) {
      console.error('表格数据加载失败:', error);
      return getEmptyData();
    }
  };
}

// 常用的预设映射配置
export const FIELD_MAPPINGS = {
  // ThinkPHP 风格（默认）
  THINKPHP: DEFAULT_FIELD_MAPPING,

  // Laravel 风格
  LARAVEL: {
    listField: 'data',
    totalPageField: 'last_page',
    currentPageField: 'current_page',
    totalField: 'total',
    pageSizeField: 'per_page',
  },

  // 简单分页格式
  SIMPLE: {
    listField: 'data',
    totalPageField: 'totalPage',
    currentPageField: 'page',
    totalField: 'total',
    pageSizeField: 'pageSize',
  },
};
