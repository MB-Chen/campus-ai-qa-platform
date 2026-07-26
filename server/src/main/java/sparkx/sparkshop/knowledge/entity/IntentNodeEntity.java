// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 意图节点实体（移植自 sparkxV2 t_intent_node）。扁平表，单层结构。
 * level 字段保留兼容旧数据（早期三级树遗留），现统一为 0；kind: KB / SYSTEM / MCP。
 */
@Data
@TableName("t_intent_node")
public class IntentNodeEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField(value = "parent_id")
    private String parentId;

    /** 0=DOMAIN 1=CATEGORY 2=TOPIC */
    @TableField(value = "level")
    private Integer level;

    /** KB / SYSTEM / MCP */
    @TableField(value = "kind")
    private String kind;

    @TableField(value = "name")
    private String name;

    @TableField(value = "description")
    private String description;

    /** 典型问法 JSON */
    @TableField(value = "examples")
    private String examples;

    /** KB：对应 collection_name */
    @TableField(value = "collection_name")
    private String collectionName;

    /** KB：限定检索的文档 id 列表（JSON 数组），为空则检索整个知识库 */
    @TableField(value = "doc_ids")
    private String docIds;

    /** MCP：工具 id */
    @TableField(value = "mcp_tool_id")
    private String mcpToolId;

    /** 意图级回答模板覆盖 */
    @TableField(value = "prompt_template")
    private String promptTemplate;

    /** MCP 参数提取自定义 */
    @TableField(value = "param_prompt_template")
    private String paramPromptTemplate;

    @TableField(value = "top_k")
    private Integer topK;

    @TableField(value = "enabled")
    private Boolean enabled;

    @TableField(value = "deleted")
    private Boolean deleted;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;
}
