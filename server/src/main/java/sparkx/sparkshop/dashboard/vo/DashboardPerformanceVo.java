// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 性能指标（成功率 / 响应耗时 / 错误率 / 无知识率 / 慢响应率）
 */
@Data
@Schema(description = "AI 性能指标")
public class DashboardPerformanceVo implements Serializable {

    @Schema(description = "时间窗口 24h/7d/30d")
    private String window;

    @Schema(description = "平均响应耗时(ms)")
    private Double avgLatencyMs;

    @Schema(description = "P95 响应耗时(ms)")
    private Double p95LatencyMs;

    @Schema(description = "成功率(%)")
    private Double successRate;

    @Schema(description = "错误率(%)")
    private Double errorRate;

    @Schema(description = "无知识率(%)")
    private Double noDocRate;

    @Schema(description = "慢响应率(%)，duration>20s 占比")
    private Double slowRate;
}
