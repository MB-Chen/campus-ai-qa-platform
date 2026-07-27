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

import sparkx.sparkshop.knowledge.infra.EmbeddingModelProvider;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 embedding 的 MMR（Maximal Marginal Relevance）去冗余。
 * 改进点用 Jaccard token 集合，语义去重弱；
 * 这里用 embedding 余弦相似度做 MMR，语义级去冗余更准。
 *
 * MMR = λ * relevance(query, d) - (1-λ) * max_sim(d, selected)
 *
 * <p>★ embedding 模型按 kbId 动态解析（与入库侧保持维度一致），
 * 走 {@link EmbeddingModelProvider#resolve(String)}。
 */
@Component
public class MmrReranker {

    private static final Logger log = LoggerFactory.getLogger(MmrReranker.class);

    private final EmbeddingModelProvider embeddingModelProvider;

    public MmrReranker(EmbeddingModelProvider embeddingModelProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
    }

    /**
     * @param ranked   已按相关性排序的候选
     * @param query    原始查询
     * @param k        最终保留数量
     * @param lambda   相关性 vs 多样性权重（0~1，越大越偏相关性）
     * @param kbId     知识库 id（用于按 kb.embedding_model_id 解析对应 embedding 模型，保证维度一致）
     */
    public List<Content> applyMmr(List<Content> ranked, String query, int k, double lambda, String kbId) {
        if (ranked.size() <= k) return new ArrayList<>(ranked);

        EmbeddingModel embeddingModel = embeddingModelProvider.resolve(kbId);

        // 预计算各候选的 embedding
        List<double[]> embeddings = new ArrayList<>();
        List<Double> relToQuery = new ArrayList<>();
        double[] qVec = null;
        try {
            qVec = normalize(toDouble(embeddingModel.embed(query).content()));
        } catch (Exception e) {
            log.warn("[MMR] query 嵌入失败，退化为简单截断: {}", e.getMessage());
            return ranked.stream().limit(k).toList();
        }

        final double[] qv = qVec;
        for (Content c : ranked) {
            try {
                double[] v = normalize(toDouble(embeddingModel.embed(c.textSegment().text()).content()));
                embeddings.add(v);
                relToQuery.add(cosine(qv, v));
            } catch (Exception e) {
                embeddings.add(null);
                relToQuery.add(0.0);
            }
        }

        List<Integer> selected = new ArrayList<>();
        Set<Integer> remaining = new HashSet<>();
        for (int i = 0; i < ranked.size(); i++) remaining.add(i);

        // 贪心选 k 个
        while (selected.size() < k && !remaining.isEmpty()) {
            int bestIdx = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int idx : remaining) {
                double rel = relToQuery.get(idx);
                double[] ve = embeddings.get(idx);
                // 已选中的最大相似度
                double maxSim = 0;
                if (ve != null) {
                    for (int s : selected) {
                        double[] se = embeddings.get(s);
                        if (se != null) {
                            maxSim = Math.max(maxSim, cosine(ve, se));
                        }
                    }
                }
                double mmr = lambda * rel - (1 - lambda) * maxSim;
                if (mmr > bestScore) {
                    bestScore = mmr;
                    bestIdx = idx;
                }
            }
            if (bestIdx < 0) break;
            selected.add(bestIdx);
            remaining.remove(bestIdx);
        }

        List<Content> result = new ArrayList<>();
        for (int idx : selected) result.add(ranked.get(idx));
        return result;
    }

    private static double[] toDouble(Embedding e) {
        float[] v = e.vector();
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i];
        return out;
    }

    private static double[] normalize(double[] v) {
        double norm = 0;
        for (double x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm == 0) return v;
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] / norm;
        return out;
    }

    private static double cosine(double[] a, double[] b) {
        double sum = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) sum += a[i] * b[i];
        return sum;
    }
}
