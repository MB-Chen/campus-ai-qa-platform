// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.evaluation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * RAG 检索评估报告（纯 Java 标准库，无 LLM，秒级出结果）。
 * <p>
 * 指标：Hit@K(K=1/3/5)、Recall@5、MRR@10、误拒率、过召回率、首字延迟均值。
 * 结构对齐 knowledge.vo.AgentEvalReportVo（grade/summary/tips + 整体指标 + 按样本明细），
 * 前端可复用其渲染模式（评级条 + 指标卡 + 明细表）。
 */
@Data
@Schema(description = "RAG 检索评估报告")
public class RagEvalReportVo implements Serializable {

    @Schema(description = "用例总数")
    private int total;

    @Schema(description = "评估耗时(ms)")
    private long costMs;

    @Schema(description = "Hit@1（0~1）")
    private double hit1;
    @Schema(description = "Hit@3（0~1）")
    private double hit3;
    @Schema(description = "Hit@5（0~1）")
    private double hit5;

    @Schema(description = "Recall@5（0~1）")
    private double recall5;

    @Schema(description = "MRR@10（0~1）")
    private double mrr10;

    @Schema(description = "误拒率（requires_rag=true 但召回空，0~1）")
    private double refusalRate;

    @Schema(description = "过召回率（requires_rag=false 却走了召回，0~1）")
    private double overRetrievalRate;

    @Schema(description = "首字延迟均值(ms)")
    private double ttftMeanMs;

    @Schema(description = "整流均值(ms)")
    private double totalMeanMs;

    @Schema(description = "总体评级：excellent / good / fair / poor")
    private String grade;

    @Schema(description = "评级展示文案")
    private String gradeLabel;

    @Schema(description = "一句话总评")
    private String summary;

    @Schema(description = "改进建议列表")
    private List<String> tips;

    @Schema(description = "按样本明细")
    private List<CaseDetail> details;

    /**
     * 单条用例评估明细。
     */
    @Data
    @Schema(description = "RAG 评估用例明细")
    public static class CaseDetail implements Serializable {

        @Schema(description = "问题")
        private String query;

        @Schema(description = "难度")
        private String difficulty;

        @Schema(description = "是否应走 RAG")
        private boolean requiresRag;

        @Schema(description = "期望召回文档 id")
        private List<String> expectedDocIds;

        @Schema(description = "实际召回文档 id")
        private List<String> retrievedDocIds;

        @Schema(description = "Hit@5（0/1）")
        private int hit5;

        @Schema(description = "Recall@5（0~1）")
        private double recall5;

        @Schema(description = "MRR（0~1）")
        private double mrr;

        @Schema(description = "召回文档数")
        private int docCount;

        @Schema(description = "首字延迟(ms)")
        private long firstTokenMs;

        @Schema(description = "总耗时(ms)")
        private long totalCost;

        @Schema(description = "是否异常")
        private boolean error;

        @Schema(description = "异常信息")
        private String errorMsg;

        @Schema(description = "实际回答（截断 200 字）")
        private String responsePreview;
    }
}
