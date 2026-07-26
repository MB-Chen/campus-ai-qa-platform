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

/**
 * 知识库列表展示 VO
 */
@Data
@Schema(description = "知识库列表展示")
public class KnowledgeBaseVo implements Serializable {

    @Schema(description = "知识库 id")
    private String id;

    @Schema(description = "知识库名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "嵌入模型显示名（如 Ollama）")
    private String embeddingModel;

    @Schema(description = "嵌入模型 id（ai_model 主键）")
    private Integer embeddingModelId;

    @Schema(description = "绑定的具体模型名（ai_model.models 某一项）")
    private String embeddingModelName;

    @Schema(description = "向量维度")
    private Integer dimension;

    @Schema(description = "文档数")
    private Integer docCount;

    @Schema(description = "状态 1正常 2禁用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
