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

/**
 * 工具调用结果（文档 5.12）—— 简化版，与 MCP SDK 的 CallToolResult 解耦。
 *
 * @param content 工具返回内容（文本）
 * @param isError 是否为错误结果
 */
public record CallToolResult(String content, boolean isError) {

    public static CallToolResult ok(String content) { return new CallToolResult(content, false); }

    public static CallToolResult error(String msg) { return new CallToolResult(msg, true); }
}
