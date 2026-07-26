// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.mcp;

import java.util.Map;

/**
 * MCP 工具执行器统一抽象（文档 5.12.1）。
 *
 * getToolDefinition() 返回工具名/描述/参数 schema；execute(parameters) 调用工具。
 * toolId 默认 = 工具定义的 name。
 *
 * 注册双路径：
 *  ① Spring 容器内本地 McpToolExecutor Bean（实现本接口）
 *  ② 远程 MCP Server 动态发现（McpClientToolExecutor 包装 McpSyncClient）
 */
public interface McpToolExecutor {

    /** 工具 id（默认 = 工具定义名） */
    default String getToolId() { return getToolDefinition().name(); }

    /** 工具定义（名/描述/参数 schema） */
    ToolDefinition getToolDefinition();

    /** 执行工具调用 */
    CallToolResult execute(Map<String, Object> parameters);
}
