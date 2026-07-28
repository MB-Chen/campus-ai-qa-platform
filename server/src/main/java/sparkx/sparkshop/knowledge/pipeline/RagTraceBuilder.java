// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.pipeline;

import dev.langchain4j.rag.content.Content;
import sparkx.sparkshop.knowledge.intent.GuidanceDecision;
import sparkx.sparkshop.knowledge.intent.NodeScore;
import sparkx.sparkshop.knowledge.intent.QueryIntent;
import sparkx.sparkshop.knowledge.query.RewriteResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 调用流程上下文收集器。
 *
 * <p>把 {@link PipelineContext} 中的各阶段中间产物（改写结果、召回片段、重排分数、意图分类等）
 * 抽取成一个结构化的 Map，供两个出口共用：
 * <ul>
 *   <li>落库：序列化为 JSON 存入 t_conversation_message.rag_context（jsonb）</li>
 *   <li>SSE：complete 事件回传 stageData，前端实时渲染调用流程抽屉</li>
 * </ul>
 *
 * <p>★ 设计约定：每个阶段恒定包含 {@code ran} 字段（是否执行）。
 * 未执行阶段输出 {@code {ran:false}}，前端据此区分"跳过"。
 * 阶段是否执行以 {@code stageTimings} 是否含该阶段名为准（RagPipeline 只记录已执行阶段的耗时）。
 */
public final class RagTraceBuilder {

    /** retrieve 片段文本截断长度（避免 jsonb 过大） */
    private static final int FRAGMENT_TEXT_LIMIT = 200;
    /** rerank 打分条目文本截断长度 */
    private static final int SCORED_TEXT_LIMIT = 120;
    /** rerank 打分条目最多展示数（防止上千片段拖垮前端） */
    private static final int SCORED_MAX = 30;

    private RagTraceBuilder() {}

    /**
     * 从 ctx 构造完整的 RAG 调用流程数据。
     *
     * @param ctx 管线上下文（管线跑完后调用）
     * @return 结构化 Map；不会返回 null（即使 ctx 为空也返回骨架）
     */
    public static Map<String, Object> build(PipelineContext ctx) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (ctx == null) return root;

        Map<String, Long> timings = ctx.getStageTimings();
        root.put("originalQuery", ctx.getOriginalQuery());
        root.put("totalCost", ctx.getTotalCost());
        root.put("llmCallCount", ctx.getLlmCallCount());
        root.put("stageTimings", timings != null ? timings : Collections.emptyMap());

        Map<String, Object> stages = new LinkedHashMap<>();
        stages.put("rewrite-split", buildRewriteSplit(ctx, ran(timings, "rewrite-split")));
        stages.put("intent", buildIntent(ctx, ran(timings, "intent")));
        stages.put("tree-intent", buildTreeIntent(ctx, ran(timings, "tree-intent")));
        stages.put("guidance", buildGuidance(ctx, ran(timings, "guidance")));
        stages.put("vague-clarify", buildVagueClarify(ran(timings, "vague-clarify")));
        stages.put("retrieve", buildRetrieve(ctx, ran(timings, "retrieve")));
        stages.put("rerank", buildRerank(ctx, ran(timings, "rerank")));
        stages.put("merge", buildMerge(ctx, ran(timings, "merge")));
        stages.put("fallback", buildFallback(ctx, ran(timings, "fallback")));
        stages.put("generate", buildGenerate(ctx, ran(timings, "generate")));
        root.put("stages", stages);
        return root;
    }

    /** 判断某阶段是否执行过（stageTimings 含该 key 即视为执行） */
    private static boolean ran(Map<String, Long> timings, String stage) {
        return timings != null && timings.containsKey(stage);
    }

    // ============ 各阶段构造 ============

    private static Map<String, Object> buildRewriteSplit(PipelineContext ctx, boolean ran) {
        Map<String, Object> m = baseStage(ran);
        if (!ran) return m;
        RewriteResult rr = ctx.getRewriteResult();
        if (rr == null) return m;
        m.put("original", ctx.getOriginalQuery());
        m.put("rewritten", rr.rewrittenQuestion());
        m.put("subQuestions", rr.subQuestions() != null ? rr.subQuestions() : Collections.emptyList());
        return m;
    }

    private static Map<String, Object> buildIntent(PipelineContext ctx, boolean ran) {
        Map<String, Object> m = baseStage(ran);
        // intent 可能由 kbMode=none 直接注入（不经 IntentStage，stageTimings 无 intent key）
        // 但 ctx.intent 仍存在，这里也透出
        QueryIntent it = ctx.getIntent();
        if (it != null) {
            m.put("code", it.getCode());
            m.put("desc", it.getDesc());
            m.put("needsRetrieval", it.needsRetrieval());
            if (!ran) {
                // 未走 IntentStage 但有意图（如闲聊注入）：标记为注入而非执行
                m.put("injected", true);
            }
        }
        return m;
    }

    private static Map<String, Object> buildTreeIntent(PipelineContext ctx, boolean ran) {
        Map<String, Object> m = baseStage(ran);
        if (!ran) return m;
        List<NodeScore> subs = ctx.getSubIntents();
        if (subs == null || subs.isEmpty()) return m;
        List<Map<String, Object>> candidates = new ArrayList<>(subs.size());
        for (NodeScore ns : subs) {
            Map<String, Object> c = new LinkedHashMap<>();
            if (ns.node() != null) {
                c.put("name", ns.node().getName());
                c.put("kind", ns.node().getKind() != null ? ns.node().getKind().name() : null);
                c.put("fullPath", ns.node().getFullPath());
            }
            c.put("score", ns.score());
            candidates.add(c);
        }
        m.put("candidates", candidates);
        return m;
    }

    private static Map<String, Object> buildGuidance(PipelineContext ctx, boolean ran) {
        Map<String, Object> m = baseStage(ran);
        if (!ran) return m;
        GuidanceDecision g = ctx.getGuidance();
        if (g == null) {
            m.put("prompt", false);
            m.put("message", null);
            return m;
        }
        m.put("prompt", g.isPrompt());
        m.put("message", g.prompt());
        return m;
    }

    private static Map<String, Object> buildVagueClarify(boolean ran) {
        return baseStage(ran);
    }

    private static Map<String, Object> buildRetrieve(PipelineContext ctx, boolean ran) {
        Map<String, Object> m = baseStage(ran);
        if (!ran) return m;
        List<Content> src = ctx.getSearchResult();
        if (src == null || src.isEmpty()) {
            m.put("count", 0);
            m.put("fragments", Collections.emptyList());
            return m;
        }
        m.put("count", src.size());
        List<Map<String, Object>> fragments = new ArrayList<>(Math.min(src.size(), 10));
        for (Content c : src) {
            fragments.add(fragmentMap(c));
            if (fragments.size() >= 10) break;
        }
        m.put("fragments", fragments);
        return m;
    }

    private static Map<String, Object> buildRerank(PipelineContext ctx, boolean ran) {
        Map<String, Object> m = baseStage(ran);
        if (!ran) return m;
        // threshold / candidates / scoreByText 由 RerankStage 写入 ctx attributes
        Double threshold = ctx.getAttr("rerankThreshold", Double.class);
        if (threshold != null) m.put("threshold", threshold);
        Integer topK = ctx.getAttr("rerankTopK", Integer.class);
        if (topK != null) m.put("topK", topK);
        @SuppressWarnings("unchecked")
        Map<String, Double> scoreByText = ctx.getAttr("rerankScoreByText", Map.class);
        @SuppressWarnings("unchecked")
        List<Content> candidates = ctx.getAttr("rerankCandidates", List.class);
        if (scoreByText == null || scoreByText.isEmpty() || candidates == null) {
            m.put("scored", Collections.emptyList());
            m.put("keptCount", ctx.getRerankResult() != null ? ctx.getRerankResult().size() : 0);
            return m;
        }
        List<Map<String, Object>> scored = new ArrayList<>();
        int keptCount = 0;
        for (Content c : candidates) {
            String text = c.textSegment().text();
            Double score = scoreByText.get(text);
            if (score == null) continue;
            boolean kept = threshold == null || score >= threshold;
            if (kept) keptCount++;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("text", truncate(text, SCORED_TEXT_LIMIT));
            e.put("score", round3(score));
            e.put("kept", kept);
            scored.add(e);
        }
        // 按分数降序
        scored.sort((a, b) -> Double.compare(((Number) b.get("score")).doubleValue(),
                ((Number) a.get("score")).doubleValue()));
        // 截断数量
        if (scored.size() > SCORED_MAX) {
            scored = new ArrayList<>(scored.subList(0, SCORED_MAX));
        }
        m.put("scored", scored);
        m.put("keptCount", keptCount);
        return m;
    }

    private static Map<String, Object> buildMerge(PipelineContext ctx, boolean ran) {
        Map<String, Object> m = baseStage(ran);
        if (!ran) return m;
        List<Content> merged = ctx.getMergeResult();
        m.put("count", merged != null ? merged.size() : 0);
        return m;
    }

    private static Map<String, Object> buildFallback(PipelineContext ctx, boolean ran) {
        Map<String, Object> m = baseStage(ran);
        if (!ran) return m;
        sparkx.sparkshop.knowledge.pipeline.AgentOverrides ov = ctx.getAgentOverrides();
        if (ov != null) {
            m.put("strategy", ov.getFallbackStrategy());
            m.put("response", ov.getFallbackResponse());
        }
        return m;
    }

    private static Map<String, Object> buildGenerate(PipelineContext ctx, boolean ran) {
        Map<String, Object> m = baseStage(ran);
        if (ctx.getPromptScene() != null) {
            m.put("promptScene", ctx.getPromptScene().name());
        }
        return m;
    }

    // ============ 工具方法 ============

    /** 未执行阶段的骨架 */
    private static Map<String, Object> baseStage(boolean ran) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ran", ran);
        return m;
    }

    /** 单个召回片段（截断 + 来源通道） */
    private static Map<String, Object> fragmentMap(Content c) {
        Map<String, Object> f = new LinkedHashMap<>();
        Map<String, Object> meta = c.textSegment().metadata().toMap();
        f.put("text", truncate(c.textSegment().text(), FRAGMENT_TEXT_LIMIT));
        Object docId = meta.get("document_id");
        if (docId != null) f.put("documentId", docId.toString());
        Object channel = meta.get("rrf_channels");
        if (channel == null) channel = meta.get("source");
        if (channel == null) channel = "vector";
        f.put("channel", channel.toString());
        Object rrfRank = meta.get("rrf_rank");
        if (rrfRank != null) f.put("rrfRank", rrfRank.toString());
        return f;
    }

    private static String truncate(String s, int limit) {
        if (s == null) return "";
        return s.length() > limit ? s.substring(0, limit) + "..." : s;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
