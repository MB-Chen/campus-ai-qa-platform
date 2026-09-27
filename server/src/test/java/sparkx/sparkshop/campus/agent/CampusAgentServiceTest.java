package sparkx.sparkshop.campus.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.service.IKnowledgeService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * CampusAgentService 单元测试（Mock LLMService，不发真实请求）
 */
class CampusAgentServiceTest {

    private LLMService llmService;
    private IntentRouter router;
    private IKnowledgeService knowledgeService;
    private CampusAgentService service;

    @BeforeEach
    void setUp() {
        llmService = Mockito.mock(LLMService.class);
        when(llmService.chat(anyString(), anyDouble(), anyDouble(), anyBoolean()))
                .thenReturn("mocked answer");
        router = new IntentRouter();
        // 检索走 knowledgeService.hitTest（统一入口），单测中返回空结果即可，
        // retrieveFromKnowledgeBase 对空结果返回"（未检索到相关知识库内容）"，不抛异常
        knowledgeService = Mockito.mock(IKnowledgeService.class);
        when(knowledgeService.hitTest(any())).thenReturn(java.util.List.of());
        service = new CampusAgentService(llmService, router, knowledgeService);
    }

    @Test
    void testChatWithToolIntent() {
        // 含"课" → TOOL_QUERY Agent
        String answer = service.chat("我下学期有什么课");
        assertNotNull(answer);
        assertEquals("mocked answer", answer);
    }

    @Test
    void testChatWithKnowledgeIntent() {
        // 含"什么是" → KNOWLEDGE_QA Agent
        String answer = service.chat("什么是 RAG");
        assertNotNull(answer);
    }

    @Test
    void testChatWithFallbackIntent() {
        // 纯闲聊 → FALLBACK Agent
        String answer = service.chat("你好");
        assertNotNull(answer);
    }
}
