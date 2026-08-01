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

import sparkx.sparkshop.knowledge.intent.NodeScore;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量+关键词全局兜底通道（文档 5.4.1）—— 现有 HybridContentRetriever 的条件化封装。
 *
 * 意图模糊/缺失/置信度不足时启用全局混合检索。
 * 互补规则（与 IntentDirectedChannel）：
 *  - 意图为空 → 启用全局兜底
 *  - 最高分 < 0.6 → 启用全局兜底
 *  - 单意图且分 < 0.8 → 启用全局兜底（中等置信度，定向+全局双通道）
 */
@Component
public class VectorKeywordHybridChannel implements ConditionalRetrievalChannel {

    private static final Logger log = LoggerFactory.getLogger(VectorKeywordHybridChannel.class);
    /** 启用全局兜底的分数门槛 */
    public static final double GLOBAL_FALLBACK_TOP_THRESHOLD = 0.6;
    public static final double SINGLE_INTENT_STRONG_THRESHOLD = 0.8;

    private final HybridContentRetriever hybridRetriever;

    public VectorKeywordHybridChannel(HybridContentRetriever hybridRetriever) {
        this.hybridRetriever = hybridRetriever;
    }

    @Override
    public String getName() { return "hybrid-global"; }

    @Override
    public int getPriority() { return 10; }

    @Override
    public boolean isEnabled(RetrievalContext ctx) {
        List<NodeScore> kbScores = ctx.getIntentScores().stream()
                .filter(s -> s.node().isKB()).toList();
        // 无 KB 意图 → 全局兜底
        if (kbScores.isEmpty()) return true;
        double top = kbScores.stream().mapToDouble(NodeScore::score).max().orElse(0);
        // 最高分 < 0.6 → 兜底；单意图且分 < 0.8 → 兜底
        return top < GLOBAL_FALLBACK_TOP_THRESHOLD
                || (kbScores.size() == 1 && top < SINGLE_INTENT_STRONG_THRESHOLD);
    }

    @Override
    public List<Content> retrieve(Query query, RetrievalContext ctx) {
        log.debug("[Channel:hybrid-global] 全局混合检索");
        // 按 kb.embedding_model_id 解析对应 embedding 模型（查询向量与入库向量维度一致）
        String primaryKbId = primaryKbId(ctx);
        // ★ 智能体覆盖：传 topK/向量阈值/关键词阈值，让「智能体配置的向量召回 topK」生效，
        //   而非回退全局默认（曾因未传导致智能体配 topK=10 却召回 30 条）。
        sparkx.sparkshop.knowledge.pipeline.AgentOverrides ov = ctx.getAgentOverrides();
        if (ov != null) {
            return hybridRetriever.retrieve(query, primaryKbId,
                    ov.getDocumentIds(), ov.getEmbeddingTopK(),
                    ov.getVectorThreshold(), ov.getKeywordThreshold(), ov.getRetrievalMode());
        }
        return hybridRetriever.retrieve(query, primaryKbId);
    }

    /** 取主知识库 id（用于按 kb 绑定的 embedding 模型查询向量化） */
    private static String primaryKbId(RetrievalContext ctx) {
        List<String> ids = ctx.getKnowledgeBaseIds();
        return (ids == null || ids.isEmpty()) ? null : ids.get(0);
    }

    @Override
    public ChannelType getType() { return ChannelType.HYBRID_GLOBAL; }
}
