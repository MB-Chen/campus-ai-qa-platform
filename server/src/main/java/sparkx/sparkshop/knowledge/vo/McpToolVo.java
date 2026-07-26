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

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MCP 工具快照出参（供意图树下拉选择）。
 */
@Data
@Schema(description = "MCP 工具出参")
public class McpToolVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "工具 id")
    private Integer id;

    @Schema(description = "关联服务 id")
    private Integer serverId;

    @Schema(description = "全局唯一工具标识（= 意图节点 mcpToolId 的值）")
    private String fullId;

    @Schema(description = "MCP Server 暴露的原始工具名")
    private String toolName;

    @Schema(description = "工具描述")
    private String description;

    @Schema(description = "工具入参 JSON Schema 原文")
    private String inputSchema;

    @Schema(description = "最近同步时间")
    private LocalDateTime lastSyncedAt;
}
