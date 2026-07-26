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
import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体列表/详情展示 VO。
 * <p>knowledgeBaseIds / suggestedQuestions 从逗号串、JSON 数组解析为 List，便于前端直接消费。
 */
@Data
@Schema(description = "智能体展示")
public class KnowledgeAgentVo implements Serializable {

    @Schema(description = "智能体 id")
    private String id;

    @Schema(description = "智能体名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "头像 emoji")
    private String avatar;

    @Schema(description = "知识库模式 all/selected/none")
    private String kbMode;

    @Schema(description = "关联知识库 id 列表")
    private List<String> knowledgeBaseIds;

    @Schema(description = "关联知识库名称列表（冗余展示用）")
    private List<String> knowledgeBaseNames;

    @Schema(description = "限定文档 id 列表（selected 模式可选）")
    private List<String> documentIds;

    @Schema(description = "对话模型 id")
    private Integer chatModelId;

    @Schema(description = "对话模型显示名")
    private String chatModelName;

    @Schema(description = "自定义系统提示词")
    private String systemPrompt;

    @Schema(description = "温度")
    private Double temperature;

    @Schema(description = "最大生成 token")
    private Integer maxTokens;

    @Schema(description = "上下文记忆轮数")
    private Integer historyTurns;

    @Schema(description = "向量召回 topK")
    private Integer embeddingTopK;

    @Schema(description = "向量相似度阈值")
    private Double vectorThreshold;

    @Schema(description = "关键词阈值")
    private Double keywordThreshold;

    @Schema(description = "重排模型 id（type=3），空用全局默认")
    private Integer rerankModelId;

    @Schema(description = "重排模型显示名")
    private String rerankModelName;

    @Schema(description = "是否启用重排 1启用 2禁用")
    private Integer rerankEnabled;

    @Schema(description = "重排 topK")
    private Integer rerankTopK;

    @Schema(description = "重排阈值")
    private Double rerankThreshold;

    @Schema(description = "意图/改写专用模型 id（type=1，对话模型），空用全局默认大模型")
    private Integer rewriteModelId;

    @Schema(description = "意图/改写专用模型显示名")
    private String rewriteModelName;

    @Schema(description = "兜底策略 model/fixed")
    private String fallbackStrategy;

    @Schema(description = "兜底固定话术")
    private String fallbackResponse;

    @Schema(description = "开场白")
    private String welcome;

    @Schema(description = "推荐问题列表")
    private List<String> suggestedQuestions;

    @Schema(description = "状态 1正常 2禁用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
