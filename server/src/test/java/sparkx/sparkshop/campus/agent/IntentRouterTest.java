package sparkx.sparkshop.campus.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IntentRouter 单元测试
 *
 * 注意测试用例与实现的匹配顺序约定：
 * KNOWLEDGE 疑问句式（什么是/怎么/流程…）优先于 TOOL 关键词（课/成绩…），
 * 因为"怎么选/怎么办"这类疑问句是比"课"更强的"问知识"信号。
 */
class IntentRouterTest {

    private final IntentRouter router = new IntentRouter();

    @Test
    void testRouteToToolQuery() {
        // 含"课" → 工具查询
        assertEquals(Intent.TOOL_QUERY, router.classify("我下学期有什么课"));
        // 含"成绩" → 工具查询
        assertEquals(Intent.TOOL_QUERY, router.classify("查询我的成绩"));
        // 含"开学" → 工具查询
        assertEquals(Intent.TOOL_QUERY, router.classify("这学期什么时候开学"));
    }

    @Test
    void testRouteToKnowledge() {
        // 含"什么是" → 知识问答
        assertEquals(Intent.KNOWLEDGE_QA, router.classify("什么是 RAG"));
        // 含"怎么"（疑问句式优先于"课"字） → 知识问答
        assertEquals(Intent.KNOWLEDGE_QA, router.classify("选课怎么选"));
        // 含"流程" → 知识问答
        assertEquals(Intent.KNOWLEDGE_QA, router.classify("请假流程是什么"));
    }

    @Test
    void testRouteToFallback() {
        // 纯闲聊 → 兜底
        assertEquals(Intent.FALLBACK, router.classify("你好"));
        // 无关键词的通用短句 → 兜底
        assertEquals(Intent.FALLBACK, router.classify("在吗"));
    }

    @Test
    void testEdgeCases() {
        // 空问题 / null → 兜底，不抛异常
        assertEquals(Intent.FALLBACK, router.classify(""));
        assertEquals(Intent.FALLBACK, router.classify(null));
        assertEquals(Intent.FALLBACK, router.classify("   "));
    }
}
