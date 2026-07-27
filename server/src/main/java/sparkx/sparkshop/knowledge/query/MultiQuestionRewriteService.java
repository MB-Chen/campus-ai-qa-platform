// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.query;

import sparkx.sparkshop.knowledge.config.RagProperties;
import sparkx.sparkshop.knowledge.entity.ConversationMessageEntity;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.knowledge.mapper.ConversationMessageMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多问题改写拆分服务（文档 5.3）—— MultiQuestionRewriteService 设计。
 *
 * 两步：
 *  (1) 规则归一化（确定性）：QueryTermMappingService 按 priority 替换同义词（"社保"→"社会保险"）
 *  (2) LLM 改写+拆分：低温生成，输出 {rewrite, should_split, sub_questions}
 *
 * 开关关闭时只做归一化 + 规则拆分（按分隔符），实现"快速路径 + LLM 兜底"。
 * LLM 失败降级为归一化结果（不阻断检索）。
 *
 * <p>移植自 sparkxV2：
 *  - 原依赖 {@code ConversationMemoryService.recentHistoryForRewrite}（memory 包尚未移植），
 *    在此内联等价实现：用 {@link ConversationMessageMapper#findRecent} 取最近 N 轮 user/assistant
 *    （过滤 system 摘要省 token），拼接成对话历史文本。
 *  - 原依赖 {@code PromptTemplateLoader.render("user-question-rewrite.st", ...)}（WF-6 未落地），
 *    在此把 user-question-rewrite.st 内容内联为 {@link #REWRITE_PROMPT_TEMPLATE} 私有常量，
 *    手工替换 {conversation} 占位符。TODO WF-6：PromptTemplateLoader 落地后改回 render()。
 */
@Component
public class MultiQuestionRewriteService {

    private static final Logger log = LoggerFactory.getLogger(MultiQuestionRewriteService.class);
    private static final Pattern SPLIT_DELIM = Pattern.compile("[?？。；;\\n]+");
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    /**
     * ★ 快速跳过阈值：首次对话（无历史）且问题简短（≤此字符数）时，跳过 LLM 改写，
     *   直接用归一化结果。这类问题无指代可消解、无历史可参考，LLM 改写收益极低却耗时数秒。
     *   例：首句"试用期一般是多长时间?"（11字）无需改写。
     */
    private static final int SHORT_QUERY_NO_HISTORY_SKIP = 20;

    /**
     * user-question-rewrite.st 内联（与 sparkxV2 resources/prompt/zh/user-question-rewrite.st 一致）。
     * TODO WF-6: PromptTemplateLoader 落地后改回 templateLoader.render("user-question-rewrite.st", ...)。
     */
    private static final String REWRITE_PROMPT_TEMPLATE = """
            # 角色
            你是查询改写与拆分助手。对用户问题做两件事：
            1. 改写：基于对话历史补全指代、修正口语化表达，让问题更利于检索
            2. 拆分：若问题实际包含多个独立子问题，拆成多个独立子问题

            # 规则
            - 改写不改变原意，只优化检索表达
            - 拆分只针对"并列的多个独立问题"（如"介绍下OA和数据安全要求"→两个子问题）
            - 单个问题不拆分（should_split=false）
            - 子问题保持独立可检索，不互相依赖

            # 输出格式（严格 JSON，禁止其他文字）
            {
              "rewrite": "改写后的主问题",
              "should_split": true/false,
              "sub_questions": ["子问题1", "子问题2"]
            }

            # 对话历史（仅供参考指代消解，不作为问题来源）
            {conversation}""";

    private final LLMService llmService;
    private final QueryTermMappingService termMappingService;
    private final ConversationMessageMapper messageMapper;
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean enabled;

    public MultiQuestionRewriteService(LLMService llmService,
                                       QueryTermMappingService termMappingService,
                                       ConversationMessageMapper messageMapper,
                                       RagProperties props) {
        this.llmService = llmService;
        this.termMappingService = termMappingService;
        this.messageMapper = messageMapper;
        this.enabled = props.getRetrieval().isEnableRewrite();
    }

    /**
     * 改写+拆分（默认对话模型）。
     *
     * @param question       原始问题
     * @param conversationId 会话 id（取最近 2 轮历史做指代消解）
     * @param userId         用户 id（admin 端 = adminId，IM 访客端 = visitorId），用于按用户隔离历史
     * @return 改写结果（主问题 + 子问题列表）
     */
    public RewriteResult rewriteWithSplit(String question, String conversationId, String userId) {
        return rewriteWithSplit(question, conversationId, userId, null);
    }

    /**
     * 改写+拆分（指定模型）。
     *
     * @param modelId ai_model.id（type=1，对话模型）。null = 走默认对话候选链；非空强制路由到该模型
     *                （智能体配了专用小快模型时用，降本提速）。RoutingLLMService 保证 modelId 不可用时回退默认链。
     */
    public RewriteResult rewriteWithSplit(String question, String conversationId, String userId, Integer modelId) {
        // (1) 规则归一化（改写前后都作为兜底）
        String normalized = termMappingService.normalize(question);

        // 开关关闭：仅归一化 + 规则拆分
        if (!enabled) {
            return new RewriteResult(normalized, ruleBasedSplit(normalized));
        }

        // (2) LLM 改写+拆分
        String history = safeRecentHistory(conversationId, userId, 2);

        // ★ 快速跳过：无历史（首次对话）且问题简短时，无需 LLM 改写。
        //   这类问题没有指代可消解、没有历史可参考，LLM 改写对检索几无增益却耗时数秒（实测 11s）。
        //   归一化 + 规则拆分已足够，直接返回省掉一次 LLM 调用。
        boolean noHistory = history == null || history.isBlank();
        if (noHistory && normalized.length() <= SHORT_QUERY_NO_HISTORY_SKIP) {
            log.info("[Rewrite:diag] 跳过 LLM 改写（首次对话+简短问题 len={}）question=\"{}\"",
                    normalized.length(), normalized);
            return new RewriteResult(normalized, List.of(normalized));
        }

        String prompt = REWRITE_PROMPT_TEMPLATE.replace("{conversation}",
                noHistory ? "(无历史)" : history);
        try {
            LlmChatRequest req = LlmChatRequest.ofUser(
                    prompt + "\n\n用户问题：" + normalized, 0.1, 0.3);
            String resp = llmService.chat(req, modelId);
            return parseRewriteAndSplit(resp, normalized);
        } catch (Exception e) {
            // LLM 失败降级为归一化结果
            log.debug("[Rewrite] LLM 改写失败，降级为归一化结果: {}", e.getMessage());
            return new RewriteResult(normalized, ruleBasedSplit(normalized));
        }
    }

    /** 解析 {rewrite, should_split, sub_questions}，失败兜底 */
    private RewriteResult parseRewriteAndSplit(String resp, String fallback) {
        try {
            Matcher m = JSON_OBJECT.matcher(resp);
            if (!m.find()) return RewriteResult.single(fallback);
            JsonNode node = mapper.readTree(m.group());
            String rewrite = node.path("rewrite").asText(fallback);
            if (rewrite.isBlank()) rewrite = fallback;

            List<String> subs = new ArrayList<>();
            JsonNode subNode = node.path("sub_questions");
            if (subNode.isArray()) {
                subNode.forEach(s -> {
                    String t = s.asText().trim();
                    if (!t.isEmpty()) subs.add(t);
                });
            }
            // should_split=false 或子问题为空 → 单问题
            if (!node.path("should_split").asBoolean(false) || subs.isEmpty()) {
                return new RewriteResult(rewrite, List.of(rewrite));
            }
            return new RewriteResult(rewrite, subs);
        } catch (Exception e) {
            return RewriteResult.single(fallback);
        }
    }

    /** 规则拆分（开关关闭/LLM 失败时兜底）：按分隔符切分 */
    private List<String> ruleBasedSplit(String q) {
        String[] parts = SPLIT_DELIM.split(q);
        List<String> result = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                result.add(t.endsWith("?") || t.endsWith("？") ? t : t + "？");
            }
        }
        return result.isEmpty() ? List.of(q) : result;
    }

    /**
     * 安全读取最近历史（会话 id 为空或失败时返回空）。
     *
     * <p>等价于 sparkxV2 ConversationMemoryService.recentHistoryForRewrite →
     * ConversationMemoryStore.loadRecentUserAssistant：只取最近 keepTurns 轮 user/assistant
     * （过滤 system 摘要省 token）。
     */
    private String safeRecentHistory(String conversationId, String userId, int keepTurns) {
        if (conversationId == null || conversationId.isBlank()) return null;
        if (userId == null || userId.isBlank()) return null;
        try {
            // findRecent 按 id DESC，取 keepTurns*2 条（1 轮 = 1 user + 1 assistant）
            List<ConversationMessageEntity> recent = messageMapper.findRecent(
                    conversationId, userId, keepTurns * 2);
            if (recent == null || recent.isEmpty()) return null;
            // 反转为时间正序
            Collections.reverse(recent);
            StringBuilder sb = new StringBuilder();
            for (ConversationMessageEntity msg : recent) {
                String role = msg.getRole();
                // 过滤 system 摘要（省 token）
                if ("user".equalsIgnoreCase(role) || "assistant".equalsIgnoreCase(role)) {
                    if (!sb.isEmpty()) sb.append('\n');
                    sb.append(role.toLowerCase()).append(": ").append(msg.getContent());
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
