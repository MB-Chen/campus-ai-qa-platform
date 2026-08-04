package sparkx.sparkshop.campus.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.knowledge.infra.LLMService;

/**
 * 校园智能问答平台 - Agent 调度服务（简化版）
 *
 * 设计：Router -> 3 Agent switch，用 prompt 前缀区分 Agent 人设。
 * 不用 LangChain4j AiServices 装配，直接调 SparkX 现有的 LLMService 门面。
 *
 * 优势：
 * - 复用 2.0 的多模型路由 + 三态熔断 + 首包探测
 * - 简化代码（不用建 3 个 Assistant 实例）
 * - 不破坏 2.0 现有架构
 *
 * 调用流程：
 * 1. IntentRouter.classify(question) -> Intent
 * 2. switch Intent: 拼对应 prompt 前缀（人设）
 * 3. llmService.chat(fullPrompt, 0.7, 0.9, false) -> answer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampusAgentService {

    private final LLMService llmService;
    private final IntentRouter router;

    // 3 个 Agent 的 system prompt（人设）
    private static final String TOOL_SYSTEM = """
            你是"校园智能问答平台"的工具查询助手，专长是查询学生个人数据（课表/成绩/校历）。
            回答要简洁、友好，可以加 emoji。
            """;

    private static final String KNOWLEDGE_SYSTEM = """
            你是"校园智能问答平台"的知识问答助手，专长是基于知识库回答校园生活问题。
            回答时引用信息来源（"根据校园知识库..."）。
            """;

    private static final String FALLBACK_SYSTEM = """
            你是"校园智能问答平台"的闲聊助手，负责回答无法归类到数据查询或知识问答的通用问题。
            回复要友好、年轻化、贴近大学生口吻。
            """;

    /**
     * 主入口：接收问题 -> 路由 -> 调 LLMService -> 返回答案
     */
    public String chat(String question) {
        Intent intent = router.classify(question);
        log.info("[校园平台] 路由分类: question='{}' intent='{}'", question, intent);

        String systemPrompt = switch (intent) {
            case TOOL_QUERY -> {
                log.info("[校园平台] 路由到 ToolAgent（数据查询）");
                yield TOOL_SYSTEM;
            }
            case KNOWLEDGE_QA -> {
                log.info("[校园平台] 路由到 FaqAgent（知识问答）");
                yield KNOWLEDGE_SYSTEM;
            }
            case FALLBACK -> {
                log.info("[校园平台] 路由到 AggregatorAgent（闲聊兜底）");
                yield FALLBACK_SYSTEM;
            }
        };

        // 拼完整 prompt: system + user
        String fullPrompt = systemPrompt + "\n\n用户问题：" + question;

        // 调 SparkX 现有的 LLMService（自带多模型路由+熔断+首包探测）
        return llmService.chat(fullPrompt, 0.7, 0.9, false);
    }
}
