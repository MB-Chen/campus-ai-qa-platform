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
 * 智能体评估聚合报告（LLM-as-judge 多维打分）。
 * <p>
 * 5 个评分维度（0-10）：relevance（相关性）/ accuracy（准确性）/ completeness（完整性）
 * / groundedness（基于知识库，反向测幻觉）/ conciseness（简洁性）。
 */
@Data
@Schema(description = "智能体评估报告")
public class AgentEvalReportVo implements Serializable {

    @Schema(description = "用例总数")
    private int total;

    @Schema(description = "异常率（生成/打分失败）0~1")
    private double errorRate;

    @Schema(description = "平均综合分（5 维均值，0~10）")
    private double avgScore;

    @Schema(description = "评估耗时(ms)")
    private long costMs;


    @Schema(description = "总体评级：excellent / good / fair / poor")
    private String grade;

    @Schema(description = "评级展示文案")
    private String gradeLabel;

    @Schema(description = "一句话总评")
    private String summary;

    @Schema(description = "改进建议列表")
    private List<String> tips;


    @Schema(description = "各维度平均分")
    private List<DimensionScore> dimensions;


    @Schema(description = "综合分分桶分布")
    private List<ScoreBin> scoreBins;


    @Schema(description = "全部用例明细")
    private List<CaseDetail> details;

    /**
     * 维度评分（均值，雷达图用）。
     */
    @Data
    @Schema(description = "维度评分")
    public static class DimensionScore implements Serializable {
        @Schema(description = "维度 key")
        private String key;
        @Schema(description = "维度中文名")
        private String label;
        @Schema(description = "平均分 0~10")
        private double score;
    }

    /**
     * 综合分分桶。
     */
    @Data
    @Schema(description = "综合分分桶")
    public static class ScoreBin implements Serializable {
        @Schema(description = "桶标签，如 [8,10]")
        private String bin;
        @Schema(description = "桶内样本数")
        private int count;
    }

    /**
     * 单条用例评估结果。
     */
    @Data
    @Schema(description = "用例评估结果")
    public static class CaseDetail implements Serializable {
        @Schema(description = "问题")
        private String query;
        @Schema(description = "期望答案")
        private String expectedAnswer;
        @Schema(description = "备注")
        private String note;
        @Schema(description = "实际回答")
        private String answer;
        @Schema(description = "引用数")
        private int referenceCount;
        @Schema(description = "relevance 分 0~10")
        private double relevance;
        @Schema(description = "accuracy 分 0~10")
        private double accuracy;
        @Schema(description = "completeness 分 0~10")
        private double completeness;
        @Schema(description = "groundedness 分 0~10")
        private double groundedness;
        @Schema(description = "conciseness 分 0~10")
        private double conciseness;
        @Schema(description = "综合分（5 维均值）0~10")
        private double overall;
        @Schema(description = "LLM 评语")
        private String comment;
        @Schema(description = "是否异常（生成/打分失败）")
        private boolean error;
        @Schema(description = "异常信息")
        private String errorMsg;
    }
}
