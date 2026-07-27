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

import sparkx.sparkshop.knowledge.config.RagProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多通道融合后处理器（order=3，去重后/retrank前）—— RRF（Reciprocal Rank Fusion）。
 *
 * <p>★ 触发条件：{@link RagProperties.Fusion#isEnabled()}=true 且 Content 带 {@code rrf_channel} metadata
 * （由 {@code RetrieveStage.annotateWithRrfMetadata} 注入）。
 * 不带 rrf_channel 的 Content（HybridContentRetriever 兜底路径产出）按 rank=0、weight=1.0 兜底参与。
 *
 * <p>★ RRF 公式：{@code score(d) = Σ_channel w_ch / (k + rank_ch(d))}
 * <ul>
 *   <li>{@code w_ch}：通道权重（{@link ConditionalRetrievalChannel#getWeight()} 默认，配置可覆盖）</li>
 *   <li>{@code k}：平滑常数（默认 20，针对小候选池调小，区别于经典 RRF 的 60）</li>
 *   <li>{@code rank_ch(d)}：文档 d 在通道 ch 返回列表中的名次（0-based）</li>
 * </ul>
 *
 * <p>★ 输出顺序：按 RRF 总分降序，候选池上限 {@link RagProperties.Fusion#getRerankCandidateLimit()}（默认 40）。
 *
 * <p>★ 调参依据：归因日志（{@link sparkx.sparkshop.knowledge.pipeline.stages.RerankStage}）统计各通道证据在 rerank 前后的存活率，
 * 长期存活的通道应维持/上调权重，长期为 0 的通道应下调或关闭。
 */
@Component
public class FusionPostProcessor implements SearchResultPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(FusionPostProcessor.class);

    private final RagProperties.Fusion fusionProps;

    public FusionPostProcessor(RagProperties props) {
        this.fusionProps = props.getFusion();
    }

    @Override
    public int getOrder() { return 3; }

    @Override
    public boolean isEnabled(RetrievalContext ctx) {
        // 配置关闭或未配置 fusion 节点 → 跳过（向后兼容旧行为：通道 priority 顺序保留去重首批）
        return fusionProps != null && fusionProps.isEnabled();
    }

    @Override
    public List<Content> process(List<Content> chunks, Query query, RetrievalContext ctx) {
        if (chunks == null || chunks.isEmpty()) return List.of();
        if (fusionProps == null || !fusionProps.isEnabled()) return chunks;

        int k = fusionProps.getRrfK() > 0 ? fusionProps.getRrfK() : 20;
        int candidateLimit = fusionProps.getRerankCandidateLimit() > 0
                ? fusionProps.getRerankCandidateLimit() : 40;

        // 1. 计算每条 Content 的 RRF 总分（按 text 聚合，同文本跨通道累加）
        // 同时保留每个 text 对应的「首条 Content」（带最丰富的原始 metadata）
        Map<String, Double> rrfScoreByChunk = new HashMap<>();
        Map<String, Content> contentByText = new LinkedHashMap<>();
        Map<String, List<String>> channelHitsByChunk = new HashMap<>();  // 归因用

        for (Content c : chunks) {
            String text = c.textSegment().text();
            Metadata meta = c.textSegment().metadata();
            Map<String, Object> metaMap = meta == null ? Map.of() : meta.toMap();

            // 解析 RRF 元数据（兜底：无 rrf_channel 视为 rank=0, weight=1.0）
            String channel = getStringMeta(metaMap, "rrf_channel", "default");
            double weight = getDoubleMeta(metaMap, "rrf_weight", 1.0);
            int rank = getIntMeta(metaMap, "rrf_rank", 0);

            double contribution = weight / (k + rank);
            rrfScoreByChunk.merge(text, contribution, Double::sum);
            contentByText.putIfAbsent(text, c);
            channelHitsByChunk.computeIfAbsent(text, x -> new ArrayList<>()).add(channel);
        }

        // 2. 按 RRF 总分降序排序，截断候选池
        List<String> sortedTexts = new ArrayList<>(rrfScoreByChunk.keySet());
        sortedTexts.sort(Comparator.comparingDouble((String t) -> rrfScoreByChunk.getOrDefault(t, 0.0)).reversed());
        if (sortedTexts.size() > candidateLimit) {
            sortedTexts = sortedTexts.subList(0, candidateLimit);
        }

        // 3. 重建 Content：在原始 metadata 基础上追加 rrf_score + rrf_channels，保留 source=graph 等下游标识
        List<Content> result = new ArrayList<>(sortedTexts.size());
        for (String text : sortedTexts) {
            Content original = contentByText.get(text);
            if (original == null) continue;

            Map<String, Object> newMeta = new LinkedHashMap<>(original.textSegment().metadata().toMap());
            newMeta.put("rrf_score", rrfScoreByChunk.get(text));
            // rrf_channels 用逗号串存（LangChain4j Metadata 不支持 List 类型）
            List<String> chs = channelHitsByChunk.get(text);
            newMeta.put("rrf_channels", chs == null ? "" : String.join(",", chs));

            result.add(Content.from(TextSegment.from(original.textSegment().text(), Metadata.from(newMeta))));
        }

        // 归因日志（按通道统计输入条数，便于与 RerankStage 输出端对比存活率）
        Map<String, Integer> inputByChannel = new LinkedHashMap<>();
        for (List<String> chs : channelHitsByChunk.values()) {
            for (String ch : chs) inputByChannel.merge(ch, 1, Integer::sum);
        }
        log.info("[PostProcess:fusion] rrfK={} candidates={}/{} inByChannel={}",
                k, result.size(), chunks.size(), inputByChannel);

        return result;
    }

    private static String getStringMeta(Map<String, Object> meta, String key, String def) {
        Object v = meta.get(key);
        return v == null ? def : v.toString();
    }

    private static double getDoubleMeta(Map<String, Object> meta, String key, double def) {
        Object v = meta.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return def; }
    }

    private static int getIntMeta(Map<String, Object> meta, String key, int def) {
        Object v = meta.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return def; }
    }
}
