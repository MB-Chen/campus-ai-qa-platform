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
import java.util.List;

/**
 * MCP 服务测试连接结果（对齐 WeKnora MCPTestResult，含工具列表/资源列表）。
 */
@Data
@Schema(description = "MCP 测试连接结果")
public class McpTestResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "提示信息")
    private String message;

    @Schema(description = "工具列表")
    private List<Tool> tools;

    @Schema(description = "资源列表")
    private List<Resource> resources;

    @Schema(description = "耗时（毫秒）")
    private Long latencyMs;

    /**
     * 测试结果中的工具项（langchain4j ToolSpecification 简化版）。
     */
    @Data
    @Schema(description = "MCP 工具项")
    public static class Tool implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        @Schema(description = "工具名")
        private String name;
        @Schema(description = "工具描述")
        private String description;
        @Schema(description = "入参 JSON Schema 原文")
        private String inputSchema;
    }

    /**
     * 测试结果中的资源项（langchain4j McpResource 简化版）。
     */
    @Data
    @Schema(description = "MCP 资源项")
    public static class Resource implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        @Schema(description = "资源 URI")
        private String uri;
        @Schema(description = "资源名")
        private String name;
        @Schema(description = "资源描述")
        private String description;
        @Schema(description = "MIME 类型")
        private String mimeType;
    }
}
