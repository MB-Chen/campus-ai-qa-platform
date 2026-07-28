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
import sparkx.sparkshop.knowledge.pipeline.PipelineContext;
import sparkx.sparkshop.knowledge.pipeline.PipelineStage;
import sparkx.sparkshop.knowledge.retrieval.MmrReranker;
import sparkx.sparkshop.knowledge.agent.AgentRerankClient;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 重排阶段
 * 复用 LangChain4j 的 ScoringModel 做精排，自研 embedding-MMR 做去冗余。
 */
@Component
@Order(70)
public class RerankStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(RerankStage.class);

    private final ScoringModel scoringModel;
    private final MmrReranker mmrReranker;
    private final AgentRerankClient agentRerankClient;
    private final double rerankThreshold;
    private final int rerankTopK;
    private final double mmrLambda;

    public RerankStage(ScoringModel scoringModel,
                       MmrReranker mmrReranker,
                       RagProperties props,
                       AgentRerankClient agentRerankClient) {
        this.scoringModel = scoringModel;
        this.mmrReranker = mmrReranker;
        this.rerankThreshold = props.getRerank().getThreshold();
        this.rerankTopK = props.getRerank().getTopK();
        this.mmrLambda = 0.7;
        this.agentRerankClient = agentRerankClient;
    }

    @Override
    public String name() { return "rerank"; }

    @Override
    public boolean shouldRun(PipelineContext ctx) {
        // ★ 智能体覆盖：rerankEnabled=false 时整阶段跳过
        AgentOverrides ov = ctx.getAgentOverrides();
        if (ov != null && ov.getRerankEnabled() != null && !ov.getRerankEnabled()) {
            return false;
        }
        return ctx.needsRetrieval()
                && ctx.getSearchResult() != null
                && !ctx.getSearchResult().isEmpty();
    }

    @Override
    public StageResult execute(PipelineContext ctx) throws Exception {
        List<Content> candidates = ctx.getSearchResult();
        String query = ctx.getRewriteQuery() != null ? ctx.getRewriteQuery() : ctx.getOriginalQuery();
        // ★ 智能体覆盖：阈值 / topK（override != null 时用覆盖值）
        double threshold = resolveRerankThreshold(ctx);
        int topK = resolveRerankTopK(ctx);

        // 1. 批量打分
        //    ★ 智能体覆盖：agentOverrides.rerankModelId 非空时调真实 rerank API（AgentRerankClient），
        //      失败回退全局 ScoringModel（基于 embedding 的默认重排），保证可用性。
        List<TextSegment> passages = candidates.stream()
                .map(Content::textSegment).toList();
        List<String> passageTexts = passages.stream().map(TextSegment::text).toList();
        List<Double> scores = null;
        Integer rerankModelId = resolveRerankModelId(ctx);
        // ★ 具体重排模型名（从 ai_model.models 逗号拆分中指定，空则后端取首项）
        String rerankModelName = ctx.getAgentOverrides() != null ? ctx.getAgentOverrides().getRerankModelName() : null;
        if (rerankModelId != null) {
            try {
                scores = agentRerankClient.rerank(rerankModelId, rerankModelName, query, passageTexts);
                log.info("[Rerank] 使用智能体指定重排模型 id={} name={} scores={}",
                        rerankModelId, rerankModelName, scores.size());
            } catch (Exception e) {
                log.warn("[Rerank] 智能体重排模型(id={})调用失败，回退全局 ScoringModel: {}",
                        rerankModelId, e.getMessage());
                scores = null;
            }
        }
        if (scores == null) {
            try {
                scores = scoringModel.scoreAll(passages, query).content();
            } catch (Exception e) {
                log.warn("[Rerank] ScoringModel 失败，退化为召回顺序: {}", e.getMessage());
                ctx.setRerankResult(candidates.stream().limit(topK).toList());
                return StageResult.CONTINUE;
            }
        }

        // 2. 阈值过滤
        List<Content> filtered = new ArrayList<>();
        Map<String, Double> scoreByText = new HashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            double s = scores.get(i);
            scoreByText.put(candidates.get(i).textSegment().text(), s);
            if (s >= threshold) {
                filtered.add(candidates.get(i));
            }
        }
        // ★ 持久化重排上下文供 RagTraceBuilder 落库/透出（scoreByText 原为局部变量，方法结束即丢）
        ctx.setAttr("rerankScoreByText", scoreByText);
        ctx.setAttr("rerankThreshold", threshold);
        ctx.setAttr("rerankTopK", topK);
        ctx.setAttr("rerankCandidates", candidates);
        // 阈值兜底：全被过滤但最高分还行，保留 Top1
        if (filtered.isEmpty() && !scores.isEmpty()) {
            double top = scores.stream().max(Double::compare).orElse(0.0);
            if (top >= 0.15) {
                int topIdx = scores.indexOf(top);
                filtered.add(candidates.get(topIdx));
            }
        }

        // 3. 复合排序
        filtered.sort(Comparator.comparingDouble(
                (Content c) -> scoreByText.getOrDefault(c.textSegment().text(), 0.0)).reversed());

        // 4. MMR 去冗余（改进点 #4：用 embedding 而非 Jaccard）
        // 按 kb.embedding_model_id 解析对应 embedding 模型（保证 MMR 向量维度与入库一致）
        String primaryKbId = (ctx.getKnowledgeBaseIds() == null || ctx.getKnowledgeBaseIds().isEmpty())
                ? null : ctx.getKnowledgeBaseIds().get(0);
        List<Content> mmr = mmrReranker.applyMmr(filtered, query, topK, mmrLambda, primaryKbId);

        ctx.setRerankResult(mmr);
        log.info("[Rerank] candidates={} filtered={} mmr={}",
                candidates.size(), filtered.size(), mmr.size());

        // ★ 通道归因日志（由原 RerankPostProcessor 迁移并入，避免与后处理器链重复 rerank）
        // 统计 rerank 前后各通道证据存活率，指导 FusionPostProcessor 通道权重调参：
        // 若某通道长期 rerank 存活率 ≈ 0，说明该通道当前是「纯成本」，应下调权重或关闭通道。
        logRerankAttribution(candidates, mmr);

        if (mmr.isEmpty()) {
            // KB 证据全被 rerank 过滤，但 MCP 工具结果仍在 → 放行，
            // MergeStage 因 rerankResult 空被跳过，但 GenerateStage 会用 mcpContext 生成答案。
            if (ctx.getMcpContext() != null && !ctx.getMcpContext().isBlank()) {
                log.info("[Rerank] KB 证据全过滤，但 MCP 结果可用，跳过 fallback");
                return StageResult.CONTINUE;
            }
            return StageResult.FALLBACK;   // KB 和 MCP 都没有 → 触发兜底
        }
        return StageResult.CONTINUE;
    }

    /**
     * ★ 通道归因日志（rerank 前后按通道分桶统计存活率）。
     *
     * <p>识别逻辑：优先用 metadata.rrf_channels（FusionPostProcessor 注入的多通道列表），
     * 退化用 metadata.source（图谱通道主动打的 source=graph / source=graph_expansion）。
     *
     * <p>用途：判断图谱等通道的证据质量。若 graph 长期 rerank 存活率 ≈ 0，
     * 说明图谱当前是「纯成本」，应下调 FusionPostProcessor 通道权重或关闭 KnowledgeGraphChannel。
     */
    private void logRerankAttribution(List<Content> input, List<Content> output) {
        Map<String, int[]> stats = new LinkedHashMap<>();  // channel → [in, out]
        for (Content c : input) {
            for (String ch : extractChannels(c)) {
                stats.computeIfAbsent(ch, k -> new int[2])[0]++;
            }
        }
        for (Content c : output) {
            for (String ch : extractChannels(c)) {
                int[] arr = stats.computeIfAbsent(ch, k -> new int[2]);
                arr[1]++;
            }
        }
        if (!stats.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            stats.forEach((ch, arr) -> sb.append(ch).append('=').append(arr[1]).append('/').append(arr[0]).append(' '));
            log.info("[Rerank:attribution] in={} out={} 通道存活 {}", input.size(), output.size(), sb);
        }
    }

    /** 提取 Content 的通道归属列表（rrf_channels 优先，退化到 source） */
    private static List<String> extractChannels(Content c) {
        Map<String, Object> meta = c.textSegment().metadata().toMap();
        Object rrfCh = meta.get("rrf_channels");
        // rrf_channels 现为逗号串（FusionPostProcessor 写入时已转，LangChain4j Metadata 不支持 List）
        if (rrfCh instanceof String s && !s.isBlank()) {
            List<String> result = new ArrayList<>();
            for (String part : s.split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) result.add(t);
            }
            if (!result.isEmpty()) return result;
        }
        // 兼容旧数据：List 类型（历史遗留，新数据已不产出）
        if (rrfCh instanceof List<?> list && !list.isEmpty()) {
            List<String> result = new ArrayList<>(list.size());
            for (Object o : list) result.add(o == null ? "unknown" : o.toString());
            return result;
        }
        // 退化：用 source 字段（graph/graph_expansion 等图谱来源；其它归为 vector/keyword）
        Object source = meta.get("source");
        if (source != null) return List.of(source.toString());
        return List.of("vector");
    }


    private double resolveRerankThreshold(PipelineContext ctx) {
        AgentOverrides ov = ctx.getAgentOverrides();
        return ov != null && ov.getRerankThreshold() != null ? ov.getRerankThreshold() : rerankThreshold;
    }

    private int resolveRerankTopK(PipelineContext ctx) {
        AgentOverrides ov = ctx.getAgentOverrides();
        return ov != null && ov.getRerankTopK() != null ? ov.getRerankTopK() : rerankTopK;
    }

    /** 智能体指定的重排模型 id；null=用全局默认 ScoringModel */
    private Integer resolveRerankModelId(PipelineContext ctx) {
        AgentOverrides ov = ctx.getAgentOverrides();
        return ov != null ? ov.getRerankModelId() : null;
    }
}
