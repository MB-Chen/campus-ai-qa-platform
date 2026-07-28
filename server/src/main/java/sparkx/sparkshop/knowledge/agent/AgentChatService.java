// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.agent;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import dev.langchain4j.rag.content.Content;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.entity.KnowledgeAgent;
import sparkx.sparkshop.knowledge.intent.QueryIntent;
import sparkx.sparkshop.knowledge.pipeline.AgentOverrides;
import sparkx.sparkshop.knowledge.pipeline.PipelineContext;
import sparkx.sparkshop.knowledge.pipeline.RagPipeline;
import sparkx.sparkshop.knowledge.service.IKnowledgeAgentService;
import sparkx.sparkshop.knowledge.validate.AgentChatValidate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 智能体测试对话服务 —— 桥接 SSE 出口与知识库 RAG 管线。
 * <p>
 * 核心流程（仿 {@code AiReceptionService}，但参数从智能体表读 + SSE 流式出口）：
 * <ol>
 *   <li>读智能体配置 → 构造 {@link AgentOverrides}</li>
 *   <li>构建 {@link PipelineContext}：知识库 id、记忆 key（agent: 前缀隔离）、agentOverrides</li>
 *   <li>设 tokenConsumer → SSE 推 answer 事件</li>
 *   <li>跑 {@link RagPipeline#run}（完整 RAG：检索/rerank/记忆/兜底）</li>
 *   <li>完成推 complete 事件（含 answer + references）；异常推 error 事件</li>
 * </ol>
 * <p>
 * 记忆持久化：复用 {@code GenerateStage} 内的 appendAssistantMemory，
 * 按 ctx.sessionId/userId 写入 t_conversation_message，前端传 conversationId 维持多轮。
 * <p>
 * SSE 事件协议（对齐 WeKnora）：
 * <pre>
 * event: answer   data: {"type":"answer","content":"token"}
 * event: complete data: {"type":"complete","answer":"...","references":[...]}
 * event: error    data: {"type":"error","message":"..."}
 * </pre>
 */
@Slf4j
@Service
public class AgentChatService {

    /** 记忆 userId 前缀（单租户一体，靠 conversationId 隔离） */
    private static final String OPERATOR_USER_ID = "agent:operator";

    @Resource
    private IKnowledgeAgentService agentService;

    @Resource
    private RagPipeline ragPipeline;

    @Resource(name = "ragTaskExecutor")
    private ExecutorService ragTaskExecutor;

    /** 进行中的对话标记：conversationKey → true。用于客户端断开时停止后续推送。 */
    private final ConcurrentHashMap<String, Boolean> activeChats = new ConcurrentHashMap<>();

    /**
     * 启动一次智能体测试对话（SSE 流式）。
     *
     * @param req 入参（agentId + conversationId + query）
     * @return SseEmitter
     */
    public SseEmitter chat(AgentChatValidate req) {
        KnowledgeAgent agent = agentService.getById(req.getAgentId());
        if (agent == null) {
            throw new BusinessException("智能体不存在");
        }
        if (agent.getStatus() != null && agent.getStatus() == 2) {
            throw new BusinessException("智能体已禁用");
        }

        // 会话 key：用 agent 前缀隔离；conversationId 为空则单轮（每次新建）
        String conversationId = (req.getConversationId() == null || req.getConversationId().isBlank())
                ? IdUtil.fastSimpleUUID() : req.getConversationId();
        String sessionKey = "agent:" + agent.getId() + ":" + conversationId;
        activeChats.put(sessionKey, Boolean.TRUE);

        // SSE：不超时（0L），由客户端断开或完成回调驱动结束
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> activeChats.remove(sessionKey));
        emitter.onTimeout(() -> {
            activeChats.remove(sessionKey);
            emitter.complete();
        });
        emitter.onError(t -> {
            activeChats.remove(sessionKey);
            log.warn("[AgentChat] SSE 异常 session={}: {}", sessionKey, t.getMessage());
        });

        ragTaskExecutor.execute(() -> runPipelineAndStream(agent, req.getQuery(), sessionKey, emitter));

        return emitter;
    }

    /** 实际跑管线 + SSE 推送（在 ragTaskExecutor 线程内执行） */
    private void runPipelineAndStream(KnowledgeAgent agent, String query, String sessionKey, SseEmitter emitter) {
        StringBuilder fullAnswer = new StringBuilder();
        try {
            PipelineContext ctx = buildContext(agent, query, sessionKey);

            ctx.setTokenConsumer(token -> {
                // 客户端已断开则停止推送
                if (!isChatActive(sessionKey)) return;
                if (StrUtil.isBlank(token)) return;
                fullAnswer.append(token);
                sendEvent(emitter, "answer", Map.of("type", "answer", "content", token));
            });

            ragPipeline.run(ctx);

            ChatResult result = collectResult(agent, ctx, fullAnswer.toString());

            // 推送 complete 事件（含 answer + references + 各阶段耗时）
            // ★ 排查日志：确认耗时数据已写入 complete 事件（前端时间线展示依赖此字段）
            log.info("[AgentChat:complete] session={} totalCost={}ms stageTimings={} answerLen={}",
                    sessionKey, ctx.getTotalCost(), ctx.getStageTimings(),
                    result.answer == null ? 0 : result.answer.length());
            sendEvent(emitter, "complete", Map.of(
                    "type", "complete",
                    "answer", result.answer,
                    "references", result.references,
                    "conversationId", sessionKey.substring(sessionKey.lastIndexOf(':') + 1),
                    "stageTimings", ctx.getStageTimings(),
                    "totalCost", ctx.getTotalCost(),
                    // ★ RAG 各阶段上下文（改写/召回/重排分数/意图等），供前端调用流程抽屉展示
                    "stageData", sparkx.sparkshop.knowledge.pipeline.RagTraceBuilder.build(ctx)
            ));
            emitter.complete();

        } catch (Exception e) {
            log.error("[AgentChat] 管线执行失败 session={}: {}", sessionKey, e.getMessage(), e);
            try {
                sendEvent(emitter, "error", Map.of(
                        "type", "error",
                        "message", "回答生成失败：" + e.getMessage()));
            } catch (Exception ignore) {
                // 推 error 也失败时仅记录
            }
            emitter.completeWithError(e);
        } finally {
            activeChats.remove(sessionKey);
        }
    }

    /**
     * 同步跑一次智能体问答（不推送 SSE，供评估调用）。
     * <p>每次用独立 conversationId（单轮，不带历史污染评估结果）。
     *
     * @param agent 智能体
     * @param query 问题
     * @return 问答结果（answer + references）
     */
    public ChatResult chatSync(KnowledgeAgent agent, String query) {
        // 评估用独立会话 key，避免评估污染正式测试对话记忆
        String sessionKey = "agent:" + agent.getId() + ":eval:" + IdUtil.fastSimpleUUID();
        StringBuilder fullAnswer = new StringBuilder();
        try {
            PipelineContext ctx = buildContext(agent, query, sessionKey);
            ctx.setTokenConsumer(fullAnswer::append);
            ragPipeline.run(ctx);
            return collectResult(agent, ctx, fullAnswer.toString());
        } catch (Exception e) {
            log.warn("[AgentChat] 同步问答失败 query=\"{}\": {}", query, e.getMessage());
            ChatResult r = new ChatResult();
            r.answer = "抱歉，回答生成失败。";
            r.references = Collections.emptyList();
            r.error = true;
            r.errorMsg = e.getMessage();
            return r;
        }
    }

    /** 汇总裁剪管线产出为最终结果（answer 空兜底 + 抽取 references） */
    private ChatResult collectResult(KnowledgeAgent agent, PipelineContext ctx, String streamed) {
        String answer = ctx.getAnswer();
        if (StrUtil.isBlank(answer)) {
            answer = StrUtil.isBlank(streamed) ? "抱歉，我暂时无法回答这个问题。" : streamed;
        }
        ChatResult r = new ChatResult();
        r.answer = answer;
        r.references = extractReferences(ctx);
        return r;
    }

    /** 同步问答结果（供评估消费） */
    public static class ChatResult {
        /** 最终答案 */
        public String answer;
        /** 检索引用 */
        public List<Map<String, Object>> references;
        /** 是否异常 */
        public boolean error;
        /** 异常信息 */
        public String errorMsg;
    }

    /** 构造管线上下文：注入智能体的知识库 + 参数覆盖 */
    private PipelineContext buildContext(KnowledgeAgent agent, String query, String sessionKey) {
        PipelineContext ctx = new PipelineContext();
        ctx.setOriginalQuery(query);
        ctx.setSessionId(sessionKey);
        ctx.setUserId(OPERATOR_USER_ID);
        ctx.setLanguage("中文");

        // ★ 知识库三态（对齐 WeKnora）：
        //   all     → knowledgeBaseIds=null（检索全部库，SQL 过滤 1=1）
        //   selected → knowledgeBaseIds=配置列表（可多库循环检索）；可选 documentIds 限定文档
        //   none    → knowledgeBaseIds=空 + intent=CHITCHAT（needsRetrieval=false，跳过检索链路走纯生成）
        String kbMode = agent.getKbMode() == null ? "selected" : agent.getKbMode().toLowerCase();
        switch (kbMode) {
            case "all" -> {
                ctx.setKnowledgeBaseIds(null);
            }
            case "none" -> {
                ctx.setKnowledgeBaseIds(Collections.emptyList());
                // 强制设为闲聊意图：needsRetrieval() 返回 false，
                // 跳过 Rewrite/Intent/Retrieve/Rerank/Fallback，直接走 GenerateStage 纯 LLM 对话
                ctx.setIntent(QueryIntent.CHITCHAT);
            }
            default -> {
                // selected
                ctx.setKnowledgeBaseIds(parseKbIds(agent.getKnowledgeBaseIds()));
            }
        }
        ctx.setAgentOverrides(buildOverrides(agent));
        return ctx;
    }

    /** 智能体配置 → AgentOverrides（包装类型，null=不覆盖） */
    private AgentOverrides buildOverrides(KnowledgeAgent agent) {
        AgentOverrides ov = new AgentOverrides();
        ov.setKbMode(agent.getKbMode());
        ov.setDocumentIds(parseKbIds(agent.getDocumentIds()));
        ov.setChatModelId(agent.getChatModelId());
        ov.setChatModelName(agent.getChatModelName());
        ov.setTemperature(agent.getTemperature());
        // topP 不开放给用户配置，走场景默认；如需可扩展
        ov.setMaxTokens(agent.getMaxTokens());
        ov.setHistoryTurns(agent.getHistoryTurns());
        ov.setEmbeddingTopK(agent.getEmbeddingTopK());
        ov.setVectorThreshold(agent.getVectorThreshold());
        ov.setKeywordThreshold(agent.getKeywordThreshold());
        ov.setRerankModelId(agent.getRerankModelId());
        ov.setRerankModelName(agent.getRerankModelName());
        ov.setRerankEnabled(agent.getRerankEnabled() != null && agent.getRerankEnabled() == 1);
        ov.setRerankTopK(agent.getRerankTopK());
        ov.setRerankThreshold(agent.getRerankThreshold());
        ov.setRewriteModelId(agent.getRewriteModelId());
        ov.setRewriteModelName(agent.getRewriteModelName());
        ov.setSystemPrompt(agent.getSystemPrompt());
        ov.setFallbackStrategy(agent.getFallbackStrategy());
        ov.setFallbackResponse(agent.getFallbackResponse());
        return ov;
    }

    /**
     * 从 ctx 抽取检索引用（供前端展示来源片段）。
     *
     * <p>★ 取值优先级：mergeResult > rerankResult > searchResult，但有一个关键约束——
     * <b>rerank 跑过（rerankResult != null）后，即使它过滤成空，也绝不再回退到 searchResult</b>。
     * 否则 rerank 已判定"都不相关"的文档会被当成引用返回（如"哈哈你好"误检索后，rerank 过滤光，
     * 却把 searchResult 的 6 条无关碎片展示成引用）。
     * 仅当 rerank 未执行（rerankResult == null，如未启用 rerank）才考虑 searchResult。
     */
    private List<Map<String, Object>> extractReferences(PipelineContext ctx) {
        List<Content> src = ctx.getMergeResult();
        // mergeResult 为空：看 rerankResult。注意 rerankResult 可能是空 list（rerank 跑了但全被过滤）
        if (src == null || src.isEmpty()) {
            List<Content> rerankResult = ctx.getRerankResult();
            if (rerankResult != null) {
                // rerank 跑过：以它为准（即使为空也不再回退 searchResult，避免无关文档被当成引用）
                src = rerankResult;
            } else {
                // rerank 未执行：才考虑原始召回
                src = ctx.getSearchResult();
            }
        }
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> refs = new ArrayList<>();
        int idx = 1;
        for (Content c : src) {
            String text = c.textSegment().text();
            if (StrUtil.isBlank(text)) continue;
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("index", idx++);
            // 截断过长的片段（前端展示用）
            ref.put("content", text.length() > 300 ? text.substring(0, 300) + "..." : text);
            Object docId = c.textSegment().metadata().toMap().get("document_id");
            if (docId != null) {
                ref.put("documentId", docId.toString());
            }
            refs.add(ref);
            // 引用最多展示 5 条
            if (refs.size() >= 5) break;
        }
        return refs;
    }

    private List<String> parseKbIds(String raw) {
        if (StrUtil.isBlank(raw)) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }

    private boolean isChatActive(String sessionKey) {
        return activeChats.containsKey(sessionKey);
    }

    /** 发送一个 SSE 事件（event + data），异常时仅记录不中断 */
    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (IOException | IllegalStateException e) {
            log.debug("[AgentChat] SSE send 失败 event={}: {}", eventName, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
