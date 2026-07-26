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
import java.util.List;

/**
 * 趋势数据（折线图用）
 */
@Data
@Schema(description = "趋势数据")
public class DashboardTrendsVo implements Serializable {

    @Schema(description = "指标名 sessions/messages/activeUsers/avgLatency/quality")
    private String metric;

    @Schema(description = "时间窗口")
    private String window;

    @Schema(description = "粒度 hour/day")
    private String granularity;

    @Schema(description = "序列集")
    private List<TrendSeriesVo> series;

    @Data
    @Schema(description = "一条折线")
    public static class TrendSeriesVo implements Serializable {
        @Schema(description = "序列名")
        private String name;
        @Schema(description = "数据点")
        private List<TrendPointVo> data;
    }

    @Data
    @Schema(description = "数据点")
    public static class TrendPointVo implements Serializable {
        @Schema(description = "时间（毫秒）")
        private Long ts;
        @Schema(description = "值")
        private Double value;
    }
}
