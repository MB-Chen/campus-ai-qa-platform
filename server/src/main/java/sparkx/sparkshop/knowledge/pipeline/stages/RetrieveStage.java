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

import sparkx.sparkshop.knowledge.config.RagProperties;
import sparkx.sparkshop.knowledge.pipeline.AgentOverrides;
import sparkx.sparkshop.knowledge.intent.NodeScore;
import sparkx.sparkshop.knowledge.mcp.McpToolService;
import sparkx.sparkshop.knowledge.pipeline.PipelineContext;
import sparkx.sparkshop.knowledge.pipeline.PipelineStage;
import sparkx.sparkshop.knowledge.query.QueryExpansionTransformer;
import sparkx.sparkshop.knowledge.query.RewriteResult;
import sparkx.sparkshop.knowledge.retrieval.ConditionalRetrievalChannel;
import sparkx.sparkshop.knowledge.retrieval.HybridContentRetriever;
import sparkx.sparkshop.knowledge.retrieval.RetrievalContext;
import sparkx.sparkshop.knowledge.retrieval.SearchResultPostProcessor;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 检索阶段（文档 5.4.3 增强）——  三层并行（子问题×通道×collection）+ MCP 工具。
 *
 * ★ 第二/三阶段增强：优先走条件化通道（意图驱动路由）+ 后处理器链（去重→重排→MMR）+ MCP。
 * 保留原 HybridContentRetriever 直查逻辑作为"无通道/无子意图"兜底。
 *
 * 流程：
 *  1. 若有子问题（rewriteResult）→ 按子问题并行检索
 *  2. 每个子问题内：filter(isEnabled) 通道并行检索 → 后处理器链；MCP 意图并行调工具
 *  3. 合并 KB 上下文 + MCP 工具结果，计算场景（决定提示词）
 *
 * shouldRun：需检索 且 未触发歧义引导短路。
 *
 * NOTE: MCP 工具调用当前经 {@link McpToolService}（WF-7 占位实现 NoopMcpToolService 返回 null），
 * 真实能力在 WF-7 落地后自动生效（@ConditionalOnMissingBean 自动退让）。
 */
@Component
@Order(60)
public class RetrieveStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(RetrieveStage.class);

    private final HybridContentRetriever retriever;
    private final QueryExpansionTransformer expander;
    private final boolean expansionEnabled;
    private final int minHitsForExpansion;
    /** ★ RRF 融合配置（通道权重覆盖 + k + 候选池上限），由 FusionPostProcessor 读取 */
    private final RagProperties.Fusion fusionProps;

    // ★ 第二/三阶段新增依赖（可选注入，向后兼容）
    private final List<ConditionalRetrievalChannel> channels;
    private final List<SearchResultPostProcessor> postProcessors;
    private final McpToolService mcpToolService;
    private final ExecutorService retrievalExecutor;

    public RetrieveStage(HybridContentRetriever retriever,
                         RagProperties props,
                         List<ConditionalRetrievalChannel> channels,
                         List<SearchResultPostProcessor> postProcessors,
                         McpToolService mcpToolService,
                         @Qualifier("ragRetrievalExecutor") ExecutorService retrievalExecutor) {
        this.retriever = retriever;
        this.expander = new QueryExpansionTransformer();
        this.expansionEnabled = props.getRetrieval().isEnableQueryExpansion();
        this.minHitsForExpansion = props.getRetrieval().getMinHitsForExpansion();
        this.fusionProps = props.getFusion();
        this.channels = channels != null ? channels : List.of();
        this.postProcessors = postProcessors != null ? postProcessors : List.of();
        this.mcpToolService = mcpToolService;
        this.retrievalExecutor = retrievalExecutor;
    }

    @Override
    public String name() { return "retrieve"; }

    @Override
    public boolean shouldRun(PipelineContext ctx) {
        // 需检索 且 未触发歧义引导短路
        return ctx.needsRetrieval() && !ctx.isGuidancePrompt();
    }

    @Override
    public StageResult execute(PipelineContext ctx) throws Exception {
        // ★ 有子问题 + 有条件化通道 → 走三层并行检索（增强路径）
        RewriteResult rewrite = ctx.getRewriteResult();
        if (rewrite != null && rewrite.subQuestions() != null && !rewrite.subQuestions().isEmpty()
                && !channels.isEmpty()) {
            return retrieveWithChannels(ctx, rewrite);
        }
        // 兜底：原 HybridContentRetriever 直查逻辑
        return retrieveLegacy(ctx);
    }


    private StageResult retrieveWithChannels(PipelineContext ctx, RewriteResult rewrite) throws Exception {
        List<String> subQuestions = rewrite.subQuestions();
        List<NodeScore> intents = ctx.getSubIntents();

        // 1. 按子问题并行检索
        List<CompletableFuture<SubQResult>> futures = subQuestions.stream()
                .map(sq -> CompletableFuture.supplyAsync(
                        () -> retrieveForSubQuestion(sq, ctx, intents), retrievalExecutor)
                        .exceptionally(ex -> {
                            // ★ 子问题检索异常必须打日志，否则静默返回空 → hits=0 却不知原因
                            log.error("[Retrieve:subQuestion] 子问题检索异常 sq=\"{}\": {}", sq, ex.getMessage(), ex);
                            return SubQResult.empty(sq);
                        }))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        List<SubQResult> results = futures.stream().map(CompletableFuture::join).toList();

        // 2. 合并 KB 上下文 + MCP 工具结果
        String kbContext = mergeKbContext(results);
        String mcpContext = mergeMcpContext(results);
        ctx.setKbContext(kbContext);
        ctx.setMcpContext(mcpContext);

        // 3. 合并召回（供 RerankStage）
        List<Content> total = new ArrayList<>();
        for (SubQResult r : results) total.addAll(r.chunks);

        // ★ 后处理器链：去重 → 重排 → MMR（若启用）
        if (!postProcessors.isEmpty() && !total.isEmpty()) {
            total = executePostProcessors(total, Query.from(ctx.getMainQuery(), Metadata.from(UserMessage.from(""), null, null)),
                    buildRetrievalContext(ctx.getMainQuery(), ctx, intents));
        }
        ctx.setSearchResult(total);

        log.info("[Retrieve:channel] subQ={} hits={} kb={} mcp={}",
                subQuestions.size(), total.size(),
                !kbContext.isBlank(),
                !mcpContext.isBlank());

        if (total.isEmpty() && mcpContext.isBlank()) {
            return StageResult.FALLBACK;
        }
        return StageResult.CONTINUE;
    }

    /**
     * 单个子问题检索：KB 走多通道并行 + 后处理；MCP 走工具调用。
     *
     * ★ 此方法在 CompletableFuture.supplyAsync 内执行，任何异常都会被 .exceptionally() 静默吞掉。
     *   因此不要在此方法内加日志/诊断代码——任何额外代码都可能因异常阻止核心检索逻辑执行。
     */
    private SubQResult retrieveForSubQuestion(String sq, PipelineContext ctx, List<NodeScore> intents) {
        RetrievalContext rctx = buildRetrievalContext(sq, ctx, intents);
        Query query = Query.from(sq, Metadata.from(UserMessage.from(""), null, null));

        // ★ 无条件执行检索：意图分类只用于精准路由（IntentDirectedChannel），
        //   不用于「要不要检索」的开关。
        List<Content> chunks = executeChannels(query, rctx, ctx);

        // MCP 意图 → 工具调用
        String mcpData = null;
        if (intents != null) {
            List<NodeScore> mcpIntents = intents.stream().filter(s -> s.node().isMCP()).toList();
            if (!mcpIntents.isEmpty() && mcpToolService != null) {
                mcpData = mcpToolService.executeTools(sq, mcpIntents);
            }
        }
        return new SubQResult(sq, chunks, mcpData);
    }

    /** ★ 多通道并行：filter(isEnabled) → parallel retrieve → join
     *  ★ RRF 增强：每通道结果按内部顺序注入 metadata（rrf_channel / rrf_weight / rrf_rank），
     *    供 FusionPostProcessor 跨通道加权融合。
     *    - rrf_channel：通道 name（如 "knowledge-graph"），同一通道的 rank 才互斥
     *    - rrf_weight：通道 getWeight()，可被 RagProperties.Fusion.channelWeights 覆盖
     *    - rrf_rank：在所属通道返回列表中的位置（0-based），用作 RRF 公式的 rank
     */
    private List<Content> executeChannels(Query query, RetrievalContext rctx, PipelineContext ctx) {
        List<ConditionalRetrievalChannel> active = channels.stream()
                .filter(ch -> ch.isEnabled(rctx))
                .sorted(Comparator.comparingInt(ConditionalRetrievalChannel::getPriority))
                .toList();
        // ★ INFO 级诊断：列出本轮启用的通道（不依赖 debug 日志级别），便于排查「为何 hits=0」
        log.info("[Retrieve:channel:diag] 启用通道={} 意图KB数={} 主KB={}",
                active.stream().map(ConditionalRetrievalChannel::getName).toList(),
                rctx.getIntentScores().stream().filter(s -> s.node().isKB()).count(),
                primaryKbId(rctx));
        if (active.isEmpty()) {
            // 无通道启用 → 用 HybridContentRetriever 兜底（按主 kb 解析 embedding 模型）
            // ★ 智能体覆盖：带 topK/阈值覆盖参数（override != null 时生效）
            return retrieveWithOverrides(query, primaryKbId(rctx), ctx);
        }
        List<CompletableFuture<List<Content>>> futures = active.stream()
                .map(ch -> CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                List<Content> raw = ch.retrieve(query, rctx);
                                return annotateWithRrfMetadata(raw, ch);
                            } catch (Exception e) {
                                // ★ 通道检索异常必须打日志，否则会被吞成「查空」假象，
                                //   排查时只能看到 hits=0 却不知原因（曾因此把 IntentDirectedChannel
                                //   的 NPE/配置错误误判成「KB 不存在」）。单通道失败不中断其他通道。
                                log.warn("[Retrieve:channel] 通道 {} 检索异常: {}", ch.getName(), e.getMessage(), e);
                                return List.<Content>of();
                            }
                        },
                        retrievalExecutor))
                .toList();
        List<Content> merged = futures.stream().map(CompletableFuture::join)
                .flatMap(List::stream).toList();
        // ★ INFO 级诊断：每个通道返回多少（不依赖 debug 级别）
        log.info("[Retrieve:channel:diag] 各通道命中={}",
                futures.stream().map(f -> {
                    try { return f.join().size(); } catch (Exception e) { return -1; }
                }).toList());
        return merged;
    }

    /**
     * 给通道返回的 Content 注入 RRF 元数据（rrf_channel / rrf_weight / rrf_rank）。
     * 保留原始 metadata，只追加。rank 按通道内返回顺序 0-based 递增。
     */
    private List<Content> annotateWithRrfMetadata(List<Content> raw, ConditionalRetrievalChannel ch) {
        if (raw == null || raw.isEmpty()) return List.of();
        double weight = resolveChannelWeight(ch);
        String channelName = ch.getName() == null ? ch.getClass().getSimpleName() : ch.getName();
        List<Content> annotated = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Content c = raw.get(i);
            Map<String, Object> meta = new LinkedHashMap<>(c.textSegment().metadata().toMap());
            meta.put("rrf_channel", channelName);
            meta.put("rrf_weight", weight);
            meta.put("rrf_rank", i);
            annotated.add(Content.from(TextSegment.from(c.textSegment().text(), dev.langchain4j.data.document.Metadata.from(meta))));
        }
        return annotated;
    }

    /**
     * 解析通道最终权重：配置文件覆盖 > 通道自带 getWeight()。
     * 配置为 null（未配置 fusion 节点）时退回通道自带 getWeight()，保持向后兼容。
     */
    private double resolveChannelWeight(ConditionalRetrievalChannel ch) {
        // 配置未配 → 退回通道自带 getWeight()
        if (fusionProps == null || fusionProps.getChannelWeights() == null) return ch.getWeight();
        var overrides = fusionProps.getChannelWeights();
        // 按 ChannelType 枚举名分发
        return switch (ch.getType()) {
            case INTENT_DIRECTED -> overrides.getIntentDirected();
            case HYBRID_GLOBAL -> overrides.getHybridGlobal();
            case KNOWLEDGE_GRAPH -> overrides.getKnowledgeGraph();
            case KEYWORD -> overrides.getKeyword();
            case PARENT_CHILD -> overrides.getParentChild();
        };
    }

    /** 后处理器链：按 order 排序，上一个输出 = 下一个输入 */
    private List<Content> executePostProcessors(List<Content> chunks, Query q, RetrievalContext ctx) {
        List<Content> result = chunks;
        for (SearchResultPostProcessor p : postProcessors.stream()
                .filter(p -> p.isEnabled(ctx))
                .sorted(Comparator.comparingInt(SearchResultPostProcessor::getOrder)).toList()) {
            try {
                result = p.process(result, q, ctx);
            } catch (Exception e) {
                // 单处理器失败不中断整链
                log.debug("[Retrieve:postprocess] {} 失败: {}", p.getClass().getSimpleName(), e.getMessage());
            }
        }
        return result;
    }

    private RetrievalContext buildRetrievalContext(String sq, PipelineContext ctx, List<NodeScore> intents) {
        return new RetrievalContext(intents, sq, ctx.getKnowledgeBaseIds(), true, ctx.getAgentOverrides());
    }

    /** 合并 KB 上下文（逐子问题 <context> 包裹） */
    private String mergeKbContext(List<SubQResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SubQResult r = results.get(i);
            if (r.chunks == null || r.chunks.isEmpty()) continue;
            sb.append("<context index=\"").append(i + 1).append("\">\n");
            sb.append("<question>").append(r.subQuestion).append("</question>\n");
            for (Content c : r.chunks) {
                sb.append(c.textSegment().text()).append("\n");
            }
            sb.append("</context>\n");
        }
        return sb.toString().trim();
    }

    private String mergeMcpContext(List<SubQResult> results) {
        StringBuilder sb = new StringBuilder();
        for (SubQResult r : results) {
            if (r.mcpData != null && !r.mcpData.isBlank()) sb.append(r.mcpData).append("\n");
        }
        return sb.toString().trim();
    }

    /** 单子问题检索结果 */
    private record SubQResult(String subQuestion, List<Content> chunks, String mcpData) {
        static SubQResult empty(String sq) { return new SubQResult(sq, List.of(), null); }
    }


    private StageResult retrieveLegacy(PipelineContext ctx) {
        String q = ctx.getRewriteQuery() != null ? ctx.getRewriteQuery() : ctx.getOriginalQuery();
        Query query = Query.from(q, Metadata.from(UserMessage.from(""), null, null));
        // 按主 kb 解析 embedding 模型（查询向量与入库向量维度一致）
        String kbId = primaryKbId(ctx);

        // ★ 智能体覆盖：带 topK/阈值覆盖参数（override != null 时生效）
        List<Content> hits = retrieveWithOverrides(query, kbId, ctx);
        log.info("[Retrieve:legacy] first-round hits={}", hits.size());

        // 召回不足 → 查询扩写后再检索
        if (hits.size() < minHitsForExpansion && expansionEnabled) {
            var expanded = expander.transform(query);
            if (expanded.size() > 1) {
                ctx.setExpandedQueries(new ArrayList<>(expanded));
                Map<String, Content> dedup = new LinkedHashMap<>();
                for (Content c : hits) dedup.put(c.textSegment().text(), c);
                for (Query eq : expanded) {
                    if (eq.text().equals(q)) continue;
                    for (Content c : retrieveWithOverrides(eq, kbId, ctx)) {
                        dedup.putIfAbsent(c.textSegment().text(), c);
                    }
                }
                hits = new ArrayList<>(dedup.values());
                log.info("[Retrieve:legacy] after expansion hits={}", hits.size());
            }
        }
        ctx.setSearchResult(hits);
        if (hits.isEmpty()) return StageResult.FALLBACK;
        return StageResult.CONTINUE;
    }

    /**
     * ★ 智能体覆盖感知的检索入口：
     *  - 从 ctx.agentOverrides 读出 topK/向量阈值/关键词阈值/限定文档 id；
     *  - ★ 多知识库循环：对 ctx.knowledgeBaseIds 的每个 kbId 分别检索（不同库可能 embedding 维度不同，
     *    必须按库分别解析 embedding 模型），结果合并去重；只有一个库时退化为单次检索。
     *  - kbId=null（全部库模式）时单次检索全部。
     *  - override 为 null 时回退默认（等价原行为，IM 链路）。
     */
    private List<Content> retrieveWithOverrides(Query query, String primaryKb, PipelineContext ctx) {
        AgentOverrides ov = ctx.getAgentOverrides();
        if (ov == null) {
            return retriever.retrieve(query, primaryKb);
        }
        List<String> docIds = ov.getDocumentIds();
        int topK = ov.getEmbeddingTopK();
        double vecThr = ov.getVectorThreshold();
        double kwThr = ov.getKeywordThreshold();

        // 多知识库：ctx.knowledgeBaseIds 有多个时循环检索合并
        List<String> kbIds = ctx.getKnowledgeBaseIds();
        if (kbIds != null && kbIds.size() > 1) {
            java.util.Map<String, Content> dedup = new java.util.LinkedHashMap<>();
            for (String kbId : kbIds) {
                for (Content c : retriever.retrieve(query, kbId, docIds, topK, vecThr, kwThr)) {
                    dedup.putIfAbsent(c.textSegment().text(), c);
                }
            }
            return new ArrayList<>(dedup.values());
        }
        return retriever.retrieve(query, primaryKb, docIds, topK, vecThr, kwThr);
    }

    /** 取主知识库 id（用于按 kb 绑定的 embedding 模型查询向量化；单 kb 取首个） */
    private static String primaryKbId(PipelineContext ctx) {
        List<String> ids = ctx.getKnowledgeBaseIds();
        return (ids == null || ids.isEmpty()) ? null : ids.get(0);
    }

    /** 取主知识库 id（RetrievalContext 重载） */
    private static String primaryKbId(RetrievalContext ctx) {
        List<String> ids = ctx.getKnowledgeBaseIds();
        return (ids == null || ids.isEmpty()) ? null : ids.get(0);
    }
}
