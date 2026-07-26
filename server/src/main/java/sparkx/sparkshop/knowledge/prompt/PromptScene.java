// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.prompt;

/**
 * 提示词场景（文档 5.14.1）—— 按检索来源路由到不同系统提示词模板。
 *
 * 由 RetrieveStage/GenerateStage 根据 hasKb/hasMcp 计算后决定。
 * 每个场景对应一个 .st 模板：
 *  - KB_ONLY  → answer-chat-kb.st（信息边界最高约束）
 *  - MCP_ONLY → answer-chat-mcp.st（动态业务数据 + 脱敏）
 *  - MIXED    → answer-chat-mcp-kb-mixed.st（来源冲突仲裁）
 *  - EMPTY    → answer-chat-system.st（纯闲聊/无证据）
 */
public enum PromptScene {
    /** 仅命中知识库 → answer-chat-kb.st */
    KB_ONLY,
    /** 仅命中 MCP 工具 → answer-chat-mcp.st */
    MCP_ONLY,
    /** KB + MCP 都命中 → answer-chat-mcp-kb-mixed.st */
    MIXED,
    /** 无命中（纯闲聊/兜底）→ answer-chat-system.st */
    EMPTY;

    /** 根据是否有 KB/MCP 上下文判定场景 */
    public static PromptScene of(boolean hasKb, boolean hasMcp) {
        if (hasKb && hasMcp) return MIXED;
        if (hasMcp) return MCP_ONLY;
        if (hasKb) return KB_ONLY;
        return EMPTY;
    }
}
