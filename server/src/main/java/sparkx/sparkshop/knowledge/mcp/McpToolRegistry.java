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
 * MCP 工具注册表接口（文档 5.12.1）。
 */
public interface McpToolRegistry {

    void register(McpToolExecutor executor);

    void unregister(String toolId);

    McpToolExecutor getExecutor(String toolId);

    java.util.List<McpToolExecutor> listAllTools();

    boolean contains(String toolId);

    int size();
}
