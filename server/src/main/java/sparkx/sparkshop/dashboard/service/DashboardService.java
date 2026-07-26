// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.dashboard.service;

import sparkx.sparkshop.dashboard.vo.DashboardOverviewVo;
import sparkx.sparkshop.dashboard.vo.DashboardPerformanceVo;
import sparkx.sparkshop.dashboard.vo.DashboardTrendsVo;

/**
 * 首页统计服务
 */
public interface DashboardService {

    /**
     * 核心指标 + 环比
     */
    DashboardOverviewVo overview(String window);

    /**
     * AI 性能指标
     */
    DashboardPerformanceVo performance(String window);

    /**
     * 趋势数据
     */
    DashboardTrendsVo trends(String metric, String window, String granularity);
}
