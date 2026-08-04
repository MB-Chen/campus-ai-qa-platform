package sparkx.sparkshop.campus.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import sparkx.sparkshop.knowledge.infra.LLMService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private CampusAgentService service;

    @BeforeEach
    void setUp() {
        llmService = Mockito.mock(LLMService.class);
        when(llmService.chat(anyString(), anyDouble(), anyDouble(), anyBoolean()))
                .thenReturn("mocked answer");
        router = new IntentRouter();
        service = new CampusAgentService(llmService, router);
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
