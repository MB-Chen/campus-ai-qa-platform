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
 * 意图分类评估单条用例结果
 */
@Data
@Schema(description = "意图分类评估单条结果")
public class IntentEvalResultVo implements Serializable {

    @Schema(description = "用户问题")
    private String query;

    @Schema(description = "期望命中节点 id")
    private String expectNodeId;

    @Schema(description = "期望命中节点名称")
    private String expectNodeName;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "Top1 实际命中节点 id（空预测时为 null）")
    private String hitNodeId;

    @Schema(description = "Top1 实际命中节点名称")
    private String hitNodeName;

    @Schema(description = "Top1 命中节点类型 KB/SYSTEM/MCP")
    private String hitKind;

    @Schema(description = "Top1 分数")
    private Double score;

    @Schema(description = "TopK 候选列表（已过滤 + 降序）")
    private List<HitCandidate> topKHits;

    @Schema(description = "Top1 是否命中期望")
    private boolean top1Hit;

    @Schema(description = "Top3 是否命中期望")
    private boolean top3Hit;

    @Schema(description = "是否空预测（无任何候选）")
    private boolean empty;

    @Schema(description = "运行异常标记（单条降级，不中断整体）")
    private boolean error;

    @Schema(description = "异常信息（error=true 时）")
    private String errorMsg;

    @Schema(description = "规则层短路标记（greeting/chitchat 等被正则识别，未调 LLM）")
    private boolean ruleShortCircuit;

    /**
     * 单个候选（轻量，只回前端需要的字段）
     */
    @Data
    @Schema(description = "命中候选")
    public static class HitCandidate implements Serializable {
        @Schema(description = "节点 id")
        private String id;
        @Schema(description = "节点名称")
        private String name;
        @Schema(description = "节点类型")
        private String kind;
        @Schema(description = "分数")
        private double score;
    }
}
