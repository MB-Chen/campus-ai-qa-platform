package sparkx.sparkshop.campus.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.service.IKnowledgeService;
import sparkx.sparkshop.knowledge.validate.HitTestValidate;
import sparkx.sparkshop.knowledge.vo.HitTestVo;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 校园智能问答平台 - Agent 调度服务（RAG 增强版）
 *
 * 设计：Router -> 3 Agent switch + 知识库检索增强。
 * TOOL_QUERY 和 KNOWLEDGE_QA 都会先检索知识库，把检索结果拼进 prompt，
 * 让 LLM 基于真实知识库内容回答，而非自由发挥。
 *
 * 调用流程：
 * 1. IntentRouter.classify(question) -> Intent
 * 2. 检索知识库（向量检索，top-3）-> context
 * 3. switch Intent: 拼对应 prompt（人设 + 知识库上下文 + 用户问题）
 * 4. llmService.chat(fullPrompt, 0.7, 0.9, false) -> answer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampusAgentService {

    private final LLMService llmService;
    private final IntentRouter router;
    private final IKnowledgeService knowledgeService;

    // 5 个校园知识库 ID
    private static final List<String> CAMPUS_KB_IDS = List.of(
            "e282cb9764a24b9d971ecac8c3d94cc6",  // 新生指南
            "084084dd4725408e8b866cebe7e2d6c2",  // 教务信息
            "8e3284018850435597c2aec0031a2aa1",  // 校园生活
            "359fc277426f498ababc15824cf15bce",  // 请假审批
            "92059eecf159402eb35229c45521adbf"   // 奖助学金
    );

    // 检索参数
    private static final double SIMILARITY_THRESHOLD = 0.5;
    private static final int TOP_K = 3;

    // 3 个 Agent 的 system prompt（人设）
    private static final String TOOL_SYSTEM = """
            你是"校园智能问答平台"的工具查询助手，专长是查询学生个人数据（课表/成绩/校历）。
            请根据下方【知识库检索结果】回答用户问题。如果检索结果中有相关信息，优先引用；
            如果没有直接匹配的内容，可以适当补充常识，但要注明"以上为通用建议"。
            回答要简洁、友好，可以加 emoji。
            """;

    private static final String KNOWLEDGE_SYSTEM = """
            你是"校园智能问答平台"的知识问答助手，专长是基于知识库回答校园生活问题。
            请根据下方【知识库检索结果】回答用户问题。必须引用信息来源（"根据校园知识库..."）。
            如果检索结果没有覆盖用户问题，如实说明"知识库暂无此信息"。
            """;

    private static final String FALLBACK_SYSTEM = """
            你是"校园智能问答平台"的闲聊助手，负责回答无法归类到数据查询或知识问答的通用问题。
            回复要友好、年轻化、贴近大学生口吻。
            """;

    /**
     * 主入口：接收问题 -> 路由 -> 检索知识库 -> 调 LLM -> 返回答案
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

        // 拼完整 prompt: system + 知识库上下文（仅 TOOL/KNOWLEDGE）+ 用户问题
        String fullPrompt;
        if (intent == Intent.TOOL_QUERY || intent == Intent.KNOWLEDGE_QA) {
            // 检索知识库
            String context = retrieveFromKnowledgeBase(question);
            fullPrompt = systemPrompt + "\n\n【知识库检索结果】\n" + context + "\n\n用户问题：" + question;
            log.info("[校园平台] 知识库检索完成, context长度={}", context.length());
        } else {
            // 闲聊不需要知识库
            fullPrompt = systemPrompt + "\n\n用户问题：" + question;
        }

        // 调 SparkX 现有的 LLMService（自带多模型路由+熔断+首包探测）
        return llmService.chat(fullPrompt, 0.7, 0.9, false);
    }

    /**
     * 从 5 个校园知识库中检索相关内容
     *
     * 流程：对每个知识库调用 hitTest（复用 SparkX 2.0 内置检索管线，
     * 含正确的 embedding 模型解析 + 向量检索 + FTS 混合检索），
     * 合并结果取 top-3。
     *
     * ★ 为什么不用 llmService.embed() + chunkMapper.vectorSearch 自行组装？
     * 因为 EmbeddingModelProvider 的缓存/回退机制导致 embed() 可能返回默认模型
     * 的向量（与入库向量不在同一空间），而 hitTest 已验证能正确检索。
     */
    private String retrieveFromKnowledgeBase(String question) {
        try {
            // 对每个知识库调用 hitTest，合并结果
            List<HitTestVo> allHits = new ArrayList<>();
            for (String kbId : CAMPUS_KB_IDS) {
                try {
                    HitTestValidate validate = new HitTestValidate();
                    validate.setKbId(kbId);
                    validate.setQuery(question);
                    validate.setMode("embedding");   // 纯向量检索，避免 FTS 分词问题
                    validate.setSimilarity(SIMILARITY_THRESHOLD);
                    validate.setTopRank(TOP_K);

                    List<HitTestVo> hits = knowledgeService.hitTest(validate);
                    allHits.addAll(hits);
                } catch (Exception e) {
                    log.warn("[校园平台] 知识库 {} 检索失败: {}", kbId, e.getMessage());
                }
            }

            if (allHits.isEmpty()) {
                return "（未检索到相关知识库内容）";
            }

            // 按相似度降序排序，取 top-3
            allHits.sort((a, b) -> {
                double scoreA = a.getScore() != null ? a.getScore() : 0;
                double scoreB = b.getScore() != null ? b.getScore() : 0;
                return Double.compare(scoreB, scoreA);
            });

            return allHits.stream()
                    .limit(TOP_K)
                    .map(hit -> String.format(Locale.ROOT, "[相似度: %.2f]\n%s",
                            hit.getScore() != null ? hit.getScore() : 0,
                            hit.getContent() != null ? hit.getContent() : ""))
                    .collect(Collectors.joining("\n---\n"));

        } catch (Exception e) {
            log.warn("[校园平台] 知识库检索异常: {}", e.getMessage());
            return "（知识库检索失败，请稍后再试）";
        }
    }
}
