// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.validate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 新增/编辑智能体参数（全字段）。
 * <p>编辑时需带 id；新增时 id 留空，由后端生成。
 */
@Data
@Schema(description = "新增/编辑智能体参数")
public class KnowledgeAgentValidate implements Serializable {

    @Schema(description = "智能体 id（编辑时必填）")
    private String id;

    @Schema(description = "智能体名称")
    @NotBlank(message = "智能体名称不能为空")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "头像 emoji")
    private String avatar;

    @Schema(description = "知识库模式 all全部/selected指定/none不使用")
    private String kbMode;

    @Schema(description = "关联知识库 id 列表")
    private List<String> knowledgeBaseIds;

    @Schema(description = "限定文档 id 列表（selected 模式可选）")
    private List<String> documentIds;

    @Schema(description = "对话模型 id（空走默认模型）")
    private Integer chatModelId;

    @Schema(description = "自定义系统提示词（空走场景模板）")
    private String systemPrompt;

    @Schema(description = "温度（0~2）")
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

    @Schema(description = "重排具体模型名（从 ai_model.models 逗号拆分中指定，空则用首项）")
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

    @Schema(description = "兜底固定话术（strategy=fixed 时生效）")
    private String fallbackResponse;

    @Schema(description = "是否启用样例查询优先匹配 1启用 2禁用")
    private Integer sampleQueryEnabled;

    @Schema(description = "样例匹配相似度阈值（0~1，空用全局配置）")
    private Double sampleQueryThreshold;

    @Schema(description = "开场白")
    private String welcome;

    @Schema(description = "推荐问题列表")
    private List<String> suggestedQuestions;

    @Schema(description = "状态 1正常 2禁用")
    private Integer status;
}
