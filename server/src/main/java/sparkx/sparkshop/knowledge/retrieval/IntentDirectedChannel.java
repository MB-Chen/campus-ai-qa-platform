// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.retrieval;

import sparkx.sparkshop.knowledge.entity.KnowledgeBase;
import sparkx.sparkshop.knowledge.intent.NodeScore;
import sparkx.sparkshop.knowledge.mapper.KnowledgeBaseMapper;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 意图定向通道（文档 5.4.1）—— IntentDirectedSearchChannel。
 *
 * 意图清晰时（存在 score ≥ MIN_INTENT_SCORE 的 KB 意图）启用，
 * priority 最高（1）。
 *
 * 本阶段实现：当存在高置信 KB 意图时启用，取最高置信 KB 叶子的 collectionName（= kbId）
 * 定向检索对应知识库，复用 {@link HybridContentRetriever}（底层 pgvector 单表按 kb_id 过滤）。
 */
@Component
public class IntentDirectedChannel implements ConditionalRetrievalChannel {

    private static final Logger log = LoggerFactory.getLogger(IntentDirectedChannel.class);
    /** KB 意图置信度门槛 */
    public static final double MIN_INTENT_SCORE = 0.4;

    private final HybridContentRetriever retriever;
    /** 用于校验意图节点 collection_name 指向的 KB 是否真实存在（防 KB 重建换 id 后节点脏数据导致查空） */
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public IntentDirectedChannel(HybridContentRetriever retriever, KnowledgeBaseMapper knowledgeBaseMapper) {
        this.retriever = retriever;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    @Override
    public String getName() { return "intent-directed"; }

    @Override
    public int getPriority() { return 1; }

    @Override
    public boolean isEnabled(RetrievalContext ctx) {
        // 配置开 + 存在 score≥0.4 的 KB 意图
        return ctx.isIntentDirectedEnabled()
                && ctx.getIntentScores().stream()
                        .anyMatch(s -> s.node().isKB() && s.score() >= MIN_INTENT_SCORE);
    }

    @Override
    public List<Content> retrieve(Query query, RetrievalContext ctx) {
        // 取高置信 KB 意图，按 score 降序（确保取到最高分意图的定向路由）
        List<NodeScore> kbIntents = ctx.getIntentScores().stream()
                .filter(s -> s.node().isKB() && s.score() >= MIN_INTENT_SCORE)
                .sorted(NodeScore.descending())
                .toList();
        // ★ 精准路由：取最高置信 KB 叶子的 collectionName（落库时 = kbId），定向到该知识库
        // 兜底：命中叶子的 collectionName 为空 → 退回会话绑定的首个知识库
        List<String> ids = ctx.getKnowledgeBaseIds();
        String fallbackKbId = (ids == null || ids.isEmpty()) ? null : ids.get(0);
        // 最高置信 KB 叶子（已降序，取首个）
        NodeScore topKb = kbIntents.isEmpty() ? null : kbIntents.get(0);
        String nodeKbId = (topKb == null || topKb.node().getCollectionName() == null
                || topKb.node().getCollectionName().isBlank())
                        ? null : topKb.node().getCollectionName();
        String targetKbId = resolveTargetKbId(nodeKbId, ids, fallbackKbId);
        // 文档级限定：最高置信 KB 叶子的 docIds（为空则检索整库）
        // ★ 智能体配了 documentIds 限定文档时优先用智能体的（节点级 docIds 仅作节点覆盖兜底）
        List<String> docIds = topKb == null ? null : topKb.node().getDocIds();

        // ★ 智能体覆盖：topK / 向量阈值 / 关键词阈值。
        //   原实现调 retriever.retrieve(query, kbId, docIds) 3 参数重载，topK/阈值全为 null，
        //   导致智能体配 topK=10 却走全局默认 30；且 resolveTopK 算出的 topK 是死变量从未传入。
        //   现优先用智能体覆盖，缺省回退节点级 topK×2，最终兜底 20。
        sparkx.sparkshop.knowledge.pipeline.AgentOverrides ov = ctx.getAgentOverrides();
        Integer topKOverride = ov != null ? ov.getEmbeddingTopK() : null;
        Double vecThrOverride = ov != null ? ov.getVectorThreshold() : null;
        Double kwThrOverride = ov != null ? ov.getKeywordThreshold() : null;
        if (ov != null && ov.getDocumentIds() != null && !ov.getDocumentIds().isEmpty()) {
            docIds = ov.getDocumentIds();
        }
        int topK = topKOverride != null ? topKOverride : resolveNodeTopK(kbIntents);
        log.debug("[Channel:intent-directed] KB intents={}, targetKbId={}, docIds={}, topK={}",
                kbIntents.size(), targetKbId, docIds == null ? 0 : docIds.size(), topK);
        return retriever.retrieve(query, targetKbId, docIds, topK, vecThrOverride, kwThrOverride);
    }

    /** 节点级 topK 兜底：取最高优先意图的 topK ×2，缺省 10×2（智能体未覆盖时用） */
    private int resolveNodeTopK(List<NodeScore> kbIntents) {
        if (kbIntents.isEmpty()) return 20;
        Integer nodeTopK = kbIntents.get(0).node().getTopK();
        return (nodeTopK != null ? nodeTopK : 10) * 2;
    }

    /**
     * 解析最终检索目标 KB id（含脏数据兜底）。
     *
     * <p>意图节点 {@code collection_name} 在 KB 重建/换 id 后极易残留旧 id（历史踩坑），
     * 用旧 id 去检索 chunks 表会查空 → 召回 0 → 走兜底话术。本方法做两道校验：
     * <ol>
     *   <li>存在性：{@code knowledge_base} 表里查得到；查不到降级会话 KB。</li>
     *   <li>会话范围：节点配的 KB 必须在当前会话允许的 {@code sessionKbIds} 内
     *       （防止意图节点配错/越权查到别的库）；不在范围内也降级会话 KB。</li>
     * </ol>
     * 节点未配 collection_name（null/空）时直接用会话 KB，与原行为一致。
     *
     * @param nodeKbId      意图节点配的 collection_name（可能脏）
     * @param sessionKbIds  当前会话允许的知识库 id 列表（来自 agent/调用方）
     * @param fallbackKbId  会话 KB 首个（兜底用）
     * @return 校验通过的检索目标 KB id
     */
    private String resolveTargetKbId(String nodeKbId, List<String> sessionKbIds, String fallbackKbId) {
        if (nodeKbId == null || nodeKbId.isBlank()) {
            return fallbackKbId;
        }
        // ★ 本方法绝不能抛异常——RetrieveStage 的通道异常被 catch 成空结果，抛了就等于"查不到"。
        //   校验逻辑全部包在 try-catch 里，任何失败都安全降级到会话 KB。
        try {
            // 存在性校验：KB 重建换 id 后节点 collection_name 会指向已删除的库
            KnowledgeBase kb = knowledgeBaseMapper.selectById(nodeKbId);
            if (kb == null) {
                log.warn("[Channel:intent-directed] 意图节点 collection_name={} 对应的知识库不存在"
                        + "（疑似 KB 重建换 id），降级使用会话 KB={}", nodeKbId, fallbackKbId);
                return fallbackKbId;
            }
            // 会话范围校验：节点配的 KB 必须在当前会话允许范围内（防越权查别的库）
            if (sessionKbIds != null && !sessionKbIds.isEmpty() && !sessionKbIds.contains(nodeKbId)) {
                log.warn("[Channel:intent-directed] 意图节点 collection_name={} 不在当前会话允许的知识库范围内{}，"
                        + "降级使用会话 KB={}", nodeKbId, sessionKbIds, fallbackKbId);
                return fallbackKbId;
            }
        } catch (Exception e) {
            log.warn("[Channel:intent-directed] 校验 KB 存在性时异常 nodeKbId={}, 降级会话 KB={}: {}",
                    nodeKbId, fallbackKbId, e.getMessage());
            return fallbackKbId;
        }
        return nodeKbId;
    }

    @Override
    public ChannelType getType() { return ChannelType.INTENT_DIRECTED; }
}
