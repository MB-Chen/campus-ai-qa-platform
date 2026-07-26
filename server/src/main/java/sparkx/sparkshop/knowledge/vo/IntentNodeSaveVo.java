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
 * 意图树节点新增/编辑入参
 */
@Data
@Schema(description = "意图树节点新增/编辑入参")
public class IntentNodeSaveVo implements Serializable {

    @Schema(description = "节点ID（编辑时必填）")
    private String id;

    @Schema(description = "父节点ID（根为空）")
    private String parentId;

    @Schema(description = "层级 0=DOMAIN 1=CATEGORY 2=TOPIC")
    private Integer level;

    @Schema(description = "类型 KB / SYSTEM / MCP")
    private String kind;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "典型问法（数组）")
    private List<String> examples;

    @Schema(description = "KB 关联的 collectionName（编辑 KB 时可改）")
    private String collectionName;

    @Schema(description = "KB 限定的文档ID列表（为空则检索整库）")
    private List<String> docIds;

    @Schema(description = "MCP 工具ID")
    private String mcpToolId;

    @Schema(description = "意图级回答模板覆盖")
    private String promptTemplate;

    @Schema(description = "MCP 参数提取提示词模板")
    private String paramPromptTemplate;

    @Schema(description = "节点级 TopK")
    private Integer topK;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "KB 创建时关联知识库ID（用于生成 collectionName）")
    private String kbId;
}
