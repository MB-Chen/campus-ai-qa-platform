// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.dashboard.service.DashboardService;
import sparkx.sparkshop.dashboard.vo.DashboardOverviewVo;
import sparkx.sparkshop.dashboard.vo.DashboardPerformanceVo;
import sparkx.sparkshop.dashboard.vo.DashboardTrendsVo;

/**
 * 首页统计看板
 */
@Tag(name = "首页统计看板")
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Resource
    private DashboardService dashboardService;

    @Operation(summary = "核心指标总览")
    @GetMapping("/overview")
    public AjaxResult<DashboardOverviewVo> overview(@RequestParam(defaultValue = "7d") String window) {
        return AjaxResult.success(dashboardService.overview(window));
    }

    @Operation(summary = "AI 性能指标")
    @GetMapping("/performance")
    public AjaxResult<DashboardPerformanceVo> performance(@RequestParam(defaultValue = "7d") String window) {
        return AjaxResult.success(dashboardService.performance(window));
    }

    @Operation(summary = "趋势数据")
    @GetMapping("/trends")
    public AjaxResult<DashboardTrendsVo> trends(@RequestParam(defaultValue = "sessions") String metric,
                                                @RequestParam(defaultValue = "7d") String window,
                                                @RequestParam(required = false) String granularity) {
        return AjaxResult.success(dashboardService.trends(metric, window, granularity));
    }
}
