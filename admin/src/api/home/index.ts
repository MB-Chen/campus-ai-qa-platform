import { Alova } from '@/utils/http/alova/index';

/**
 * 获取卡片统计数据
 */
export function getTotalStats() {
  return Alova.Get<any>('.home/index');
}

/**
 * 获取趋势数据
 * @param params date_range: week/month/custom, start_date, end_date
 */
export function getTrendData(params: { date_range: string; start_date?: string; end_date?: string }) {
  return Alova.Get<any>('.home/trend', { params });
}

/**
 * 获取用户统计（活跃用户数）
 */
export function getUserStats() {
  return Alova.Get<any>('.home/userStats');
}

/**
 * 获取用户增长趋势
 * @param params date_range: week/month/custom, start_date, end_date
 */
export function getUserGrowthTrend(params: { date_range: string; start_date?: string; end_date?: string }) {
  return Alova.Get<any>('.home/userGrowthTrend', { params });
}

/**
 * 获取服务统计（服务总数、服务预约量）
 */
export function getServiceStats() {
  return Alova.Get<any>('.home/serviceStats');
}

/**
 * 获取服务分类分布数据
 */
export function getServiceCategoryDistribution() {
  return Alova.Get<any>('.home/serviceCategoryDistribution');
}

/**
 * 获取服务预约趋势
 * @param params date_range: week/month/custom, start_date, end_date
 */
export function getServiceAppointmentTrend(params: { date_range: string; start_date?: string; end_date?: string }) {
  return Alova.Get<any>('.home/serviceAppointmentTrend', { params });
}

/**
 * 获取交易统计（总交易额、平均客单价）
 */
export function getTransactionStats() {
  return Alova.Get<any>('.home/transactionStats');
}

/**
 * 获取交易增长趋势
 * @param params date_range: week/month/custom, start_date, end_date
 */
export function getTransactionGrowthTrend(params: { date_range: string; start_date?: string; end_date?: string }) {
  return Alova.Get<any>('.home/transactionGrowthTrend', { params });
}
