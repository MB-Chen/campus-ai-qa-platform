// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.pipeline.stages;

import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.knowledge.infra.chat.StreamCallback;
import sparkx.sparkshop.knowledge.intent.QueryIntent;
import sparkx.sparkshop.knowledge.memory.ConversationMemoryService;
import sparkx.sparkshop.knowledge.prompt.PromptPlanner;
import sparkx.sparkshop.knowledge.prompt.PromptScene;
import sparkx.sparkshop.knowledge.pipeline.AgentOverrides;
import sparkx.sparkshop.knowledge.pipeline.PipelineContext;
import sparkx.sparkshop.knowledge.pipeline.PipelineStage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 生成阶段（文档 6.2）—— ★ 经容错层流式生成 + 场景化提示词。
 *
 * 改造点（相对第一版骨架）：
 *  1. ★ 经 {@link LLMService#streamChat}（三态熔断 + 首包探测 + 降级链），模型故障业务无感
 *  2. ★ 用 {@link PromptPlanner} 场景路由系统提示词（KB_ONLY/MCP_ONLY/MIXED/EMPTY）
 *  3. ★ 拼结构化 user 消息（{@code <documents>}/{@code <tool-data>}/{@code <question>}）
 *  4. ★ 温度策略：MCP 场景 temp=0.3（动态数据自然转述），纯 KB temp=0（严格事实性）
 *  5. ★ 加载会话记忆（滑动窗口历史 + 话题摘要），长对话抗 token 爆炸
 *
 * 失败兜底：容错层全候选失败时 onError 回调设置固定话术，仍走 FallbackProvider。
 */
@Component
@Order(90)
public class GenerateStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(GenerateStage.class);

    private final LLMService llmService;
    private final PromptPlanner promptPlanner;
    private final ConversationMemoryService memoryService;

    public GenerateStage(LLMService llmService,
                         PromptPlanner promptPlanner,
                         ConversationMemoryService memoryService) {
        this.llmService = llmService;
        this.promptPlanner = promptPlanner;
        this.memoryService = memoryService;
    }

    @Override
    public String name() { return "generate"; }

    @Override
    public boolean shouldRun(PipelineContext ctx) {
        // 兜底阶段已设 answer 则跳过
        return ctx.getAnswer() == null;
    }

    @Override
    public StageResult execute(PipelineContext ctx) throws Exception {
        long t0 = System.currentTimeMillis();
        // 1. KB 证据：优先用 RetrieveStage 产出的 kbContext（结构化），否则回退渲染 searchResult
        String kbContext = ctx.getKbContext();
        if (kbContext == null || kbContext.isBlank()) {
            kbContext = renderKbContext(ctx);
        }
        // MCP 工具结果：用 RetrieveStage 产出的 mcpContext
        String mcpContext = ctx.getMcpContext();

        // 2. 计算场景（决定提示词模板）
        boolean hasKb = kbContext != null && !kbContext.isBlank();
        boolean hasMcp = mcpContext != null && !mcpContext.isBlank();
        PromptScene scene = PromptScene.of(hasKb, hasMcp);
        ctx.setPromptScene(scene);

        // 3. 场景路由系统提示词（KB_ONLY → 信息边界最高约束；MIXED → 来源冲突仲裁）
        //    ★ 智能体覆盖：agentOverrides.systemPrompt 非空时优先使用（自定义人设）
        //    ★ 意图专用模板：chitchat/follow_up 各有差异化人设（ctx.getIntent 由 IntentStage 设定）
        List<String> intentTpls = extractIntentTemplates(ctx);
        String systemPrompt = resolveSystemPrompt(ctx, scene, ctx.getIntent(), intentTpls, kbContext, mcpContext);

        // 4. 加载会话记忆：摘要（标签包裹）+ 滑动窗口历史
        List<ChatMessage> history = loadHistory(ctx);
        long tMem = System.currentTimeMillis();

        // 5. 构建结构化消息：system → history(含摘要) → user(证据+问题)
        String userMsg = buildUserMessage(ctx, kbContext, mcpContext);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        messages.add(UserMessage.from(userMsg));

        // 6. 温度策略：MCP 场景放宽，纯 KB/EMPTY 严格
        //    ★ 智能体覆盖：agentOverrides 非空时优先用其温度/topP/maxTokens
        boolean mcpScene = scene == PromptScene.MCP_ONLY || scene == PromptScene.MIXED;
        double temp = resolveTemperature(ctx, mcpScene);
        double topP = resolveTopP(ctx, mcpScene);
        int maxTokens = resolveMaxTokens(ctx);

        // 7. ★ 经容错层流式生成（模型挂了自动降级，首包探测无感切换）
        //    ★ 智能体覆盖：chatModelId 非空时强制路由到该模型（仍走熔断降级骨架），让「配置的对话模型」生效
        LlmChatRequest request = new LlmChatRequest(messages, temp, topP, maxTokens, -1, false);
        Integer chatModelId = ctx.getAgentOverrides() != null ? ctx.getAgentOverrides().getChatModelId() : null;
        StringBuilder fullAnswer = new StringBuilder();
        final PipelineContext finalCtx = ctx;
        final long tStreamStart = System.currentTimeMillis();
        // 首包时间（首个 onContent 到达）
        final long[] tFirstToken = {-1};

        // streamChat 内部把 SSE 读取异步提交到流式线程池，主调用只阻塞到首包探测成功即返回；
        // 剩余 token（含 onComplete）在后台线程推送。因此用 future 在流结束时 release，
        // 本阶段阻塞等待流真正完成后再返回，避免 run() 返回后 ctx.answer 仍为 null 的竞态。
        java.util.concurrent.CompletableFuture<Void> streamDone = new java.util.concurrent.CompletableFuture<>();

        llmService.streamChat(request, new StreamCallback() {
            @Override
            public void onContent(String token) {
                if (tFirstToken[0] < 0) {
                    tFirstToken[0] = System.currentTimeMillis();
                    log.info("[Generate:diag] 首包耗时={}ms scene={} histMsgs={} (记忆加载={}ms, stream调用前共={}ms)",
                            tFirstToken[0] - tStreamStart, scene,
                            history != null ? history.size() : 0,
                            tMem - t0, tStreamStart - t0);
                }
                fullAnswer.append(token);
                if (finalCtx.getTokenConsumer() != null) {
                    finalCtx.getTokenConsumer().accept(token);
                }
            }

            @Override
            public void onComplete() {
                String answer = fullAnswer.toString();
                if (answer.isEmpty()) answer = "(模型未返回内容)";
                finalCtx.setAnswer(answer);
                // 追加 ASSISTANT 消息到记忆（触发异步摘要）
                appendAssistantMemory(finalCtx, answer);
                streamDone.complete(null);
                log.info("[Generate:diag] 生成完成 总耗时={}ms (记忆={}ms, 首包={}ms, 流式={}ms) answerLen={}",
                        System.currentTimeMillis() - t0,
                        tMem - t0,
                        tFirstToken[0] < 0 ? -1 : tFirstToken[0] - tStreamStart,
                        tFirstToken[0] < 0 ? -1 : System.currentTimeMillis() - tFirstToken[0],
                        answer.length());
            }

            @Override
            public void onError(Throwable e) {
                log.error("[Generate] 流式生成失败（全候选降级失败）: {}", e.getMessage());
                finalCtx.setAnswer("抱歉，回答生成失败，请稍后重试。");
                if (finalCtx.getTokenConsumer() != null) {
                    finalCtx.getTokenConsumer().accept(finalCtx.getAnswer());
                }
                // 用 complete 而非 completeExceptionally：onError 已把失败话术写入 ctx.answer，
                // 这里仅 release 等待，让本阶段正常返回（话术经 collectResult 输出），不抛异常。
                streamDone.complete(null);
            }
        }, false, chatModelId);

        // 阻塞等流结束（流式线程池内 doStream 会在末尾回调 onComplete/onError）
        streamDone.join();

        return StageResult.CONTINUE;
    }

    /** 渲染 KB 证据为 <documents> 内的逐条文档 */
    private String renderKbContext(PipelineContext ctx) {
        if (ctx.getMergeResult() == null || ctx.getMergeResult().isEmpty()) {
            // 用 rerankResult 兜底（merge 可能未执行）
            List<dev.langchain4j.rag.content.Content> src =
                    ctx.getRerankResult() != null ? ctx.getRerankResult() : ctx.getSearchResult();
            if (src == null || src.isEmpty()) return "";
            return src.stream()
                    .map(c -> "<context>" + c.textSegment().text() + "</context>")
                    .collect(Collectors.joining("\n"));
        }
        return ctx.getMergeResult().stream()
                .map(c -> "<context>" + c.textSegment().text() + "</context>")
                .collect(Collectors.joining("\n"));
    }

    /** 加载会话记忆：失败时空列表降级（不影响生成）。★ 智能体覆盖：historyTurns 非空时用覆盖值 */
    private List<ChatMessage> loadHistory(PipelineContext ctx) {
        try {
            String convId = ctx.getSessionId();
            String userId = ctx.getUserId();
            if (convId == null || userId == null) return List.of();
            Integer turns = ctx.getAgentOverrides() != null
                    ? ctx.getAgentOverrides().getHistoryTurns() : null;
            return memoryService.load(convId, userId, turns);
        } catch (Exception e) {
            log.debug("[Generate] 加载会话记忆失败，降级为空历史: {}", e.getMessage());
            return List.of();
        }
    }

    /** 提取意图级模板覆盖候选（从 subIntents 取 promptTemplate，本阶段预留） */
    private List<String> extractIntentTemplates(PipelineContext ctx) {
        if (ctx.getSubIntents() == null || ctx.getSubIntents().isEmpty()) return null;
        List<String> tpls = new ArrayList<>();
        for (var s : ctx.getSubIntents()) {
            if (s.node().getPromptTemplate() != null && !s.node().getPromptTemplate().isBlank()) {
                tpls.add(s.node().getPromptTemplate());
            }
        }
        return tpls.isEmpty() ? null : tpls;
    }


    /**
     * 系统提示词优先级链：智能体自定义 prompt > 意图专用模板/场景默认（由 PromptPlanner 路由）。
     *
     * <p>智能体自定义 systemPrompt（人设）级别最高，完全覆盖意图专用模板与场景模板；
     * 否则交给 PromptPlanner 按「意图专用 → 场景默认」路由。
     */
    private String resolveSystemPrompt(PipelineContext ctx, PromptScene scene, QueryIntent intent,
                                       List<String> intentTpls, String kbContext, String mcpContext) {
        AgentOverrides ov = ctx.getAgentOverrides();
        if (ov != null && ov.getSystemPrompt() != null && !ov.getSystemPrompt().isBlank()) {
            return ov.getSystemPrompt();
        }
        return promptPlanner.buildSystemPrompt(scene, intent, intentTpls, kbContext, mcpContext);
    }

    /** 温度：智能体覆盖优先，否则 MCP 场景 0.3 / 纯 KB 0.0 */
    private double resolveTemperature(PipelineContext ctx, boolean mcpScene) {
        AgentOverrides ov = ctx.getAgentOverrides();
        if (ov != null && ov.getTemperature() != null) {
            return ov.getTemperature();
        }
        return mcpScene ? 0.3 : 0.0;
    }

    /** topP：智能体覆盖优先，否则 MCP 场景 0.8 / 纯 KB 1.0 */
    private double resolveTopP(PipelineContext ctx, boolean mcpScene) {
        AgentOverrides ov = ctx.getAgentOverrides();
        if (ov != null && ov.getTopP() != null) {
            return ov.getTopP();
        }
        return mcpScene ? 0.8 : 1.0;
    }

    /** maxTokens：智能体覆盖优先，否则 -1（不限制） */
    private int resolveMaxTokens(PipelineContext ctx) {
        AgentOverrides ov = ctx.getAgentOverrides();
        if (ov != null && ov.getMaxTokens() != null && ov.getMaxTokens() > 0) {
            return ov.getMaxTokens();
        }
        return -1;
    }

    /** 证据 + 问题合并为一条 user 消息（用 <documents>/<tool-data>/<question> 容器） */
    private String buildUserMessage(PipelineContext ctx, String kbContext, String mcpContext) {
        StringBuilder sb = new StringBuilder();
        if (kbContext != null && !kbContext.isBlank()) {
            sb.append("<documents>\n").append(kbContext).append("\n</documents>\n\n");
        }
        if (mcpContext != null && !mcpContext.isBlank()) {
            sb.append(mcpContext).append("\n\n");
        }
        String question = ctx.getRewriteQuery() != null
                ? ctx.getRewriteQuery() : ctx.getOriginalQuery();
        sb.append("<question>").append(question).append("</question>");
        return sb.toString();
    }

    /** 追加本轮 USER+ASSISTANT 消息到记忆（按 user→assistant 时序；ASSISTANT 后触发异步摘要） */
    private void appendAssistantMemory(PipelineContext ctx, String answer) {
        try {
            String convId = ctx.getSessionId();
            String userId = ctx.getUserId();
            if (convId == null || userId == null) return;
            // 先追加本轮 USER 消息（保证 user→assistant 配对，时序正确）
            String userQuery = ctx.getRewriteQuery() != null
                    ? ctx.getRewriteQuery() : ctx.getOriginalQuery();
            memoryService.append(convId, userId, UserMessage.from(userQuery));
            // 再追加 ASSISTANT 消息（携带 RAG 调用流程上下文；此调用触发异步摘要压缩）
            String ragContextJson = null;
            try {
                ragContextJson = cn.hutool.json.JSONUtil.toJsonStr(
                        sparkx.sparkshop.knowledge.pipeline.RagTraceBuilder.build(ctx));
            } catch (Exception je) {
                log.debug("[Generate] 序列化 RAG 上下文失败（忽略，不影响落库）: {}", je.getMessage());
            }
            memoryService.append(convId, userId, dev.langchain4j.data.message.AiMessage.from(answer), ragContextJson);
        } catch (Exception e) {
            log.debug("[Generate] 追加会话记忆失败（不影响主流程）: {}", e.getMessage());
        }
    }
}
