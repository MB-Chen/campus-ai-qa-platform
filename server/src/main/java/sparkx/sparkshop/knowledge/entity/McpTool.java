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
 * MCP 工具快照实体（listTools 拉取后落库，供意图树下拉选择）。
 *
 * <p>{@code fullId} 格式 {@code "svc{serverId}__{toolName}"}，作为意图节点
 * {@code t_intent_node.mcp_tool_id} 的值，全局唯一。
 */
@Data
@TableName("mcp_tool")
public class McpTool implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 关联 mcp_server.id */
    @TableField(value = "server_id")
    private Integer serverId;

    /** 全局唯一工具标识 "svc{serverId}__{toolName}"，= 意图节点 mcpToolId 的值 */
    @TableField(value = "full_id")
    private String fullId;

    /** MCP Server 暴露的原始工具名（callTool 时用这个名字） */
    @TableField(value = "tool_name")
    private String toolName;

    /** 工具描述 */
    @TableField(value = "description")
    private String description;

    /** 工具入参 JSON Schema 原文 */
    @TableField(value = "input_schema")
    private String inputSchema;

    /** 最近一次同步时间 */
    @TableField(value = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @TableField(value = "create_time")
    private LocalDateTime createTime;

    @TableField(value = "update_time")
    private LocalDateTime updateTime;
}
