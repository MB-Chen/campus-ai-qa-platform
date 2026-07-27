import { Alova } from '@/utils/http/alova';


// 核心指标总览
export function getDashboardOverview(window = '7d') {
  return Alova.Get<any>('/dashboard/overview', { params: { window } });
}

// AI 性能指标
export function getDashboardPerformance(window = '7d') {
  return Alova.Get<any>('/dashboard/performance', { params: { window } });
}

// 趋势数据
export function getDashboardTrends(metric: string, window = '7d', granularity?: string) {
  return Alova.Get<any>('/dashboard/trends', {
    params: { metric, window, granularity },
  });
}
