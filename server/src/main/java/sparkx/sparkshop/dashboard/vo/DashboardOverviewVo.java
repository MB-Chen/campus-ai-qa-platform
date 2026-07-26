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
 * 首页总览看板（核心 KPI + 环比）
 */
@Data
@Schema(description = "首页总览看板")
public class DashboardOverviewVo implements Serializable {

    @Schema(description = "时间窗口 24h/7d/30d")
    private String window;

    @Schema(description = "对比窗口")
    private String compareWindow;

    @Schema(description = "最后更新时间（毫秒）")
    private Long updatedAt;

    @Schema(description = "核心指标集")
    private Kpis kpis;

    @Data
    @Schema(description = "核心指标")
    public static class Kpis {
        @Schema(description = "活跃用户数")
        private KpiVo activeUsers;
        @Schema(description = "窗口内会话数")
        private KpiVo sessions;
        @Schema(description = "窗口内消息数")
        private KpiVo messages;
        @Schema(description = "历史总会话数")
        private KpiVo totalSessions;
        @Schema(description = "历史总消息数")
        private KpiVo totalMessages;
    }

    @Data
    @Schema(description = "单个指标（含环比）")
    public static class KpiVo {
        @Schema(description = "当前值")
        private Long value;
        @Schema(description = "环比差值（当前 - 上周期）")
        private Long delta;
        @Schema(description = "环比百分比")
        private Double deltaPct;
    }
}
