package sparkx.sparkshop.campus.agent;

/**
 * 校园智能问答平台 - 意图枚举
 *
 * 三个意图对应三个 Agent：
 * - TOOL_QUERY: 数据查询（课表/成绩/校历）
 * - KNOWLEDGE_QA: 知识问答（基于 RAG 检索）
 * - FALLBACK: 闲聊兜底
 */
public enum Intent {
    TOOL_QUERY,
    KNOWLEDGE_QA,
    FALLBACK
}
