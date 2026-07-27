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

import sparkx.sparkshop.knowledge.intent.NodeScore;

import java.util.List;

/**
 * MCP 工具服务 —— 最小占位接口。
 *
 * TODO WF-7: 真实实现尚未移植（sparkxV2 的 McpToolService 在 WF-7 阶段统一落地）。
 * 当前仅为让 {@code RetrieveStage} 编译通过而保留本接口，仅声明管线实际用到的方法。
 * WF-7 落地后请用完整实现替换（含工具注册、参数填充、批量执行、错误降级等）。
 */
public interface McpToolService {

    /**
     * 针对命中的 MCP 意图节点批量执行工具调用，返回拼接后的工具结果文本。
     *
     * @param question 原始/子问题文本（作为工具入参）
     * @param mcpIntents 命中的 MCP 类型意图节点（{@link NodeScore#node()#isMCP()} 为 true）
     * @return 工具结果文本（失败可返回 null/空串，由调用方判定走兜底）
     */
    String executeTools(String question, List<NodeScore> mcpIntents);
}
