// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 意图分类评估聚合报告
 */
@Data
@Schema(description = "意图分类评估报告")
public class IntentEvalReportVo implements Serializable {

    @Schema(description = "用例总数")
    private int total;

    @Schema(description = "Top1 准确率 0~1")
    private double accuracy1;

    @Schema(description = "Top3 准确率 0~1")
    private double accuracy3;

    @Schema(description = "空预测率 0~1")
    private double emptyRate;

    @Schema(description = "异常率 0~1")
    private double errorRate;

    @Schema(description = "Top1 平均置信度（非空预测的均值）")
    private double avgTop1Score;

    @Schema(description = "评估耗时(ms)")
    private long costMs;


    @Schema(description = "总体评级：excellent / good / fair / poor")
    private String grade;

    @Schema(description = "评级展示文案，如「优秀」「良好」「一般」「较差」")
    private String gradeLabel;

    @Schema(description = "一句话总评，直白说明当前效果如何")
    private String summary;

    @Schema(description = "改进建议列表（针对本次评估暴露的问题）")
    private List<String> tips;

    @Schema(description = "各节点 P/R/F1")
    private List<NodeMetric> perNode;

    @Schema(description = "置信度校准分桶")
    private List<CalibrationBin> calibration;

    @Schema(description = "误判用例（Top1 未命中期望）")
    private List<IntentEvalResultVo> misclassified;

    @Schema(description = "全部用例结果（用于前端明细展示）")
    private List<IntentEvalResultVo> details;

    /**
     * 单节点指标
     */
    @Data
    @Schema(description = "节点级指标")
    public static class NodeMetric implements Serializable {
        @Schema(description = "节点 id（期望标签）")
        private String nodeId;
        @Schema(description = "节点名称")
        private String name;
        @Schema(description = "节点类型")
        private String kind;
        @Schema(description = "该类正例数")
        private int caseCount;
        @Schema(description = "Top1 命中数")
        private int correct;
        @Schema(description = "Top1 未命中数")
        private int wrong;
        @Schema(description = "精确率 P")
        private double precision;
        @Schema(description = "召回率 R")
        private double recall;
        @Schema(description = "F1")
        private double f1;
    }

    /**
     * 置信度校准分桶
     */
    @Data
    @Schema(description = "置信度校准桶")
    public static class CalibrationBin implements Serializable {
        @Schema(description = "桶标签，如 [0.6,0.8)")
        private String bin;
        @Schema(description = "桶内 Top1 命中率")
        private double acc;
        @Schema(description = "桶内样本数")
        private int count;
    }
}
