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
import sparkx.sparkshop.knowledge.entity.ChunkEntity;
import sparkx.sparkshop.knowledge.infra.EmbeddingModelProvider;
import sparkx.sparkshop.knowledge.infra.TsVectorGenerator;
import sparkx.sparkshop.knowledge.mapper.ChunkMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 混合检索器
 * 向量检索（pgvector）+ 关键词检索（tsvector），结果用 RRF 融合。
 * 实现 LangChain4j 的 ContentRetriever 接口。
 *
 * <p>★ 查询向量化按 kbId 动态解析模型：{@link #retrieve(Query, String)} 重载走
 * {@link EmbeddingModelProvider#resolve(String)}，与入库侧保持维度一致，
 * 避免 pgvector 维度不匹配导致检索哑火。
 * 原 {@link #retrieve(Query)} 保留 langchain4j 接口契约，用默认兜底模型。
 */
@Component
public class HybridContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridContentRetriever.class);

    /** 局部 ObjectMapper（不暴露为 Bean，避免破坏全局 Jackson 自动配置） */
    private static final ObjectMapper META_MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final EmbeddingModelProvider embeddingModelProvider;
    private final RagProperties props;
    private final ChunkMapper chunkMapper;

    public HybridContentRetriever(JdbcTemplate jdbc,
                                   EmbeddingModelProvider embeddingModelProvider,
                                   RagProperties props,
                                   ChunkMapper chunkMapper) {
        this.jdbc = jdbc;
        this.embeddingModelProvider = embeddingModelProvider;
        this.props = props;
        this.chunkMapper = chunkMapper;
    }

    @Override
    public List<Content> retrieve(Query query) {
        // langchain4j 接口契约：无 kbId 时用默认兜底模型（向后兼容）
        return doRetrieve(query, null, null, null, null, null);
    }

    /**
     * 按知识库解析 embedding 模型后检索（查询向量与入库向量维度一致）。
     *
     * @param query 查询
     * @param kbId  知识库 id；为空回退默认模型
     */
    public List<Content> retrieve(Query query, String kbId) {
        return doRetrieve(query, kbId, null, null, null, null);
    }

    /**
     * 按知识库 + 限定文档检索（意图节点关联文档时用）。
     *
     * @param query  查询
     * @param kbId   知识库 id
     * @param docIds 限定文档 id 列表；为 null 或空则检索整库
     */
    public List<Content> retrieve(Query query, String kbId, List<String> docIds) {
        return doRetrieve(query, kbId, docIds, null, null, null);
    }

    /**
     * 按知识库 + 限定文档检索，并支持请求级覆盖检索参数（智能体用）。
     *
     * @param query                  查询
     * @param kbId                   知识库 id
     * @param docIds                 限定文档 id 列表；null/空则检索整库
     * @param topKOverride           向量召回 topK 覆盖；null 走默认
     * @param vectorThresholdOverride 向量相似度阈值覆盖；null 走默认
     * @param keywordThresholdOverride 关键词阈值覆盖；null 走默认
     */
    public List<Content> retrieve(Query query, String kbId, List<String> docIds,
                                  Integer topKOverride, Double vectorThresholdOverride,
                                  Double keywordThresholdOverride) {
        return doRetrieve(query, kbId, docIds, topKOverride, vectorThresholdOverride, keywordThresholdOverride);
    }

    private List<Content> doRetrieve(Query query, String kbId, List<String> docIds,
                                     Integer topKOverride, Double vectorThresholdOverride,
                                     Double keywordThresholdOverride) {
        String qText = query.text();
        int topK = topKOverride != null ? topKOverride : props.getRetrieval().getEmbeddingTopK();
        double vectorThreshold = vectorThresholdOverride != null
                ? vectorThresholdOverride : props.getRetrieval().getVectorThreshold();
        int overFetch = Math.max(topK * 5, 50);

        EmbeddingModel embeddingModel = embeddingModelProvider.resolve(kbId);

        // 1. 向量检索：5x 过度召回
        Embedding qEmb = embeddingModel.embed(qText).content();
        String vec = embToArray(qEmb);
        // ★ 过滤条件：kb_id 隔离 + 可选文档限定。
        // kbId/docId 均为业务生成的 UUID hex，不含特殊字符，额外 strip 单引号防注入。
        // 不用占位符是因为「有/无 kb_id」「有/无 doc_ids」组合导致参数数量不固定，拼接更简单。
        StringBuilder filter = new StringBuilder();
        if (kbId == null || kbId.isBlank()) {
            filter.append("1=1");
        } else {
            filter.append("kb_id = '").append(kbId.replace("'", "").trim()).append("'");
        }
        if (docIds != null && !docIds.isEmpty()) {
            String inList = docIds.stream()
                    .filter(d -> d != null && !d.isBlank())
                    .map(d -> "'" + d.replace("'", "").trim() + "'")
                    .collect(Collectors.joining(","));
            if (!inList.isEmpty()) {
                filter.append(" AND metadata->>'document_id' IN (").append(inList).append(")");
            }
        }
        String kbFilter = filter.toString();

        // ★ 向量直接嵌入 SQL 字符串（embToArray 产出的格式安全，无注入风险），
        //   而非走 JDBC ? 参数绑定 —— PostgreSQL JDBC 驱动对 CAST(? AS vector) 的参数绑定
        //   会把 ? 误解析为 JSON 操作符，导致 bad SQL grammar。
        //   kg_entity 的 MyBatis #{emb} 走的是不同绑定路径所以没问题，JdbcTemplate 的 ? 不行。
        String vecLiteral = "'" + vec + "'::vector";
        String vectorSql = "SELECT id, content, 1 - (embedding <=> " + vecLiteral + ") AS score, metadata "
                + "FROM chunks "
                + "WHERE " + kbFilter + " "
                + "AND 1 - (embedding <=> " + vecLiteral + ") >= ? "
                + "ORDER BY embedding <=> " + vecLiteral + " "
                + "LIMIT ?";
        List<Map<String, Object>> vectorHits;
        try {
            vectorHits = jdbc.queryForList(vectorSql, vectorThreshold, overFetch);
        } catch (Exception e) {
            log.warn("[HybridRetrieve] 向量检索失败（库未就绪?）: {}", e.getMessage());
            vectorHits = List.of();
        }

        // 2. 关键词检索（PostgreSQL FTS）
        //    query 先用 HanLP 在 Java 端预分词，再交 websearch_to_tsquery('simple', ?)。
        //    simple config 不做语干还原，靠「Java 已切好词 + 整词等值」命中中文。
        String tsQuery = TsVectorGenerator.toTsQuery(qText);
        String kwSql = "SELECT id, content, "
                + "ts_rank_cd(tsv, websearch_to_tsquery('simple', ?)) AS score, metadata "
                + "FROM chunks "
                + "WHERE " + kbFilter + " "
                + "AND tsv @@ websearch_to_tsquery('simple', ?) "
                + "ORDER BY score DESC "
                + "LIMIT ?";
        List<Map<String, Object>> kwHits;
        try {
            // ★ 关键词路按 keywordThreshold 过滤弱命中：ts_rank_cd 没有分数下限，高频词
            // （公司/员工）反复出现的长 chunk 会刷出虚高分，混入融合会污染排序
            // （与 Milvus BM25 的 drop_ratio_search 同思路：弱命中直接砍掉）。
            double kwThreshold = keywordThresholdOverride != null
                    ? keywordThresholdOverride : props.getRetrieval().getKeywordThreshold();
            List<Map<String, Object>> kwRaw = jdbc.queryForList(kwSql, tsQuery, tsQuery, overFetch);
            kwHits = filterByScore(kwRaw, kwThreshold);
        } catch (Exception e) {
            log.warn("[HybridRetrieve] 关键词检索失败（库未就绪?）: {}", e.getMessage());
            kwHits = List.of();
        }

        // 3. 加权融合（min-max 归一化 + 加权求和），替代 RRF。
        //    RRF 只看排名不看分数，中文 RAG 场景下关键词路 ts_rank_cd 会被高频词
        //    反复出现的长 chunk 刷出虚高排名，与真正语义相关的向量结果"双路命中"
        //    后反而压过只命中向量路的正确答案。归一化消除两路量纲差异（向量余弦 0~1，
        //    关键词 ts_rank_cd 0~∞），再按向量路权重（默认 0.7）加权求和。
        double vectorWeight = clampWeight(props.getRetrieval().getHybridVectorWeight());
        List<Map<String, Object>> fused = weightedFuse(vectorHits, kwHits, vectorWeight);

        log.info("[HybridRetrieve] query=\"{}\" vector={} kw={} fused={}",
                qText, vectorHits.size(), kwHits.size(), fused.size());

        // 4. 问题切片回溯：命中的若是问题切片（metadata.type=question），
        //    按 metadata.source_chunk_id 批量查出原文切片，用原文 content 替换问题文本进上下文，
        //    避免把孤立的问题文本喂给 LLM（无答案无上下文，会误导生成）。
        //    同时按原文 id 去重：多个问题指向同一原文时只保留得分最高的一条。
        List<Map<String, Object>> topFused = fused.stream().limit(topK).toList();

        // 4.1 收集需要回溯的 source_chunk_id（仅问题切片）
        Set<String> sourceIds = new LinkedHashSet<>();
        for (Map<String, Object> row : topFused) {
            if ("question".equals(readMetaField(row.get("metadata"), "type"))) {
                String sid = readMetaField(row.get("metadata"), "source_chunk_id");
                if (sid != null && !sid.isBlank()) {
                    sourceIds.add(sid);
                }
            }
        }
        // 4.2 批量查原文切片 content
        Map<String, String> originalContent = new HashMap<>();
        if (!sourceIds.isEmpty()) {
            try {
                for (ChunkEntity c : chunkMapper.selectByIds(new ArrayList<>(sourceIds))) {
                    if (c.getContent() != null) {
                        originalContent.put(c.getId(), c.getContent());
                    }
                }
            } catch (Exception e) {
                log.warn("[HybridRetrieve] 问题回溯原文失败: {}", e.getMessage());
            }
        }

        // 4.3 拼上下文：问题切片用原文 content 替换；原文切片保持自身 content；按原文 id 去重
        Set<String> seenOriginal = new LinkedHashSet<>();
        List<Content> result = new ArrayList<>(topFused.size());
        for (Map<String, Object> row : topFused) {
            String type = readMetaField(row.get("metadata"), "type");
            String content;
            String dedupKey;
            if ("question".equals(type)) {
                String sid = readMetaField(row.get("metadata"), "source_chunk_id");
                content = originalContent.getOrDefault(sid, (String) row.get("content"));
                dedupKey = "q:" + sid;   // 同一原文的多个问题只保留一条
            } else {
                content = (String) row.get("content");
                dedupKey = "c:" + row.get("id");
            }
            if (content == null || content.isBlank()) continue;
            if (!seenOriginal.add(dedupKey)) continue;   // 已出现过，跳过
            result.add(Content.from(TextSegment.from(content)));
        }
        return result;
    }

    /**
     * 从 chunks.metadata（jsonb 文本或已序列化字符串）中提取指定字段值。
     * metadata 可能是 String（jdbc 查出的是 PGObject/jsonb 字符串）。
     * 任意异常返回 null（旧数据 metadata 为空或非对象时降级）。
     */
    private static String readMetaField(Object metadata, String field) {
        if (metadata == null) return null;
        String json = metadata.toString();
        if (json.isBlank() || !json.startsWith("{")) return null;
        try {
            JsonNode root = META_MAPPER.readTree(json);
            JsonNode node = root.path(field);
            if (node.isMissingNode() || node.isNull()) return null;
            return node.asText();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 加权融合（min-max 归一化 + 加权求和），替代原 RRF。
     *
     * <p>背景：RRF 只看排名不看分数。中文 RAG 场景下关键词路 {@code ts_rank_cd} 会被
     * 「公司/员工」等高频词反复出现的长 chunk 刷出虚高排名，与真正语义相关的向量结果
     * 「双路命中」后，在 RRF 下反而压过只命中向量路的正确答案。
     *
     * <p>做法：两路各自 min-max 归一化到 [0,1]（消除量纲差异：向量路是余弦相似度 0~1，
     * 关键词路是 ts_rank_cd 0~∞），再按 {@code vectorWeight}（向量）+ {@code 1-vectorWeight}
     * （关键词）加权求和。向量默认 0.7：语义相关比字面命中更可靠。
     *
     * @param vectorWeight 向量路权重，取值 [0,1]，建议 0.6~0.8
     */
    private List<Map<String, Object>> weightedFuse(List<Map<String, Object>> vectorHits,
                                                    List<Map<String, Object>> kwHits,
                                                    double vectorWeight) {
        double kwWeight = 1.0 - vectorWeight;
        // 1. 各路原始 score 归一化（min-max 到 [0,1]）
        Map<String, Double> vecNorm = normalizeScores(vectorHits);
        Map<String, Double> kwNorm = normalizeScores(kwHits);

        // 2. 加权求和
        Map<String, Double> fusedScore = new HashMap<>();
        Set<String> allIds = new LinkedHashSet<>();
        for (Map<String, Object> row : vectorHits) {
            allIds.add(String.valueOf(row.get("id")));
        }
        for (Map<String, Object> row : kwHits) {
            allIds.add(String.valueOf(row.get("id")));
        }
        for (String id : allIds) {
            double v = vecNorm.getOrDefault(id, 0.0);
            double k = kwNorm.getOrDefault(id, 0.0);
            fusedScore.put(id, v * vectorWeight + k * kwWeight);
        }

        // 3. 合并 payload（向量路优先，保留其 content/metadata），按融合分降序
        Map<String, Map<String, Object>> payload = new HashMap<>();
        for (Map<String, Object> row : vectorHits) {
            payload.putIfAbsent(String.valueOf(row.get("id")), row);
        }
        for (Map<String, Object> row : kwHits) {
            payload.putIfAbsent(String.valueOf(row.get("id")), row);
        }

        return fusedScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> row = new HashMap<>(payload.get(e.getKey()));
                    row.put("rrf_score", e.getValue());
                    return row;
                })
                .collect(Collectors.toList());
    }

    /** 对一路结果按原始 score 做 min-max 归一化到 [0,1]，返回 id -> 归一化分。 */
    private Map<String, Double> normalizeScores(List<Map<String, Object>> hits) {
        Map<String, Double> raw = new LinkedHashMap<>();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Map<String, Object> row : hits) {
            double s = toDouble(row.get("score"));
            if (s < 0) s = 0;
            String id = String.valueOf(row.get("id"));
            raw.put(id, s);
            if (s < min) min = s;
            if (s > max) max = s;
        }
        Map<String, Double> out = new HashMap<>();
        double range = max - min;
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            // max==min（只有一条或分数全相同）时归一化为 max（1.0 若有值，否则 0）
            double norm = range > 0 ? (e.getValue() - min) / range : (max > 0 ? 1.0 : 0.0);
            out.put(e.getKey(), norm);
        }
        return out;
    }

    private double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** 仅返回命中数量（供 RetrieveStage 判断是否触发扩写） */
    public int retrieveCount(Query query) {
        return retrieve(query).size();
    }

    /** 把权重钳制到 [0,1]，防止 yml 配错超出范围导致负权重或溢出。 */
    private static double clampWeight(double w) {
        if (w < 0.0 || Double.isNaN(w)) return 0.0;
        if (w > 1.0) return 1.0;
        return w;
    }

    /**
     * 按原始 score 过滤：丢弃 score 小于 threshold 的命中。
     * 用于关键词路的弱命中过滤（ts_rank_cd 无下限，高频词会刷虚高分）。
     * 阈值 ≤0 时不过滤（保留原行为）。
     */
    private List<Map<String, Object>> filterByScore(List<Map<String, Object>> hits, double threshold) {
        if (threshold <= 0 || hits == null || hits.isEmpty()) {
            return hits == null ? List.of() : hits;
        }
        List<Map<String, Object>> out = new ArrayList<>(hits.size());
        for (Map<String, Object> row : hits) {
            double s = toDouble(row.get("score"));
            if (s >= threshold) {
                out.add(row);
            }
        }
        return out;
    }

    private static String embToArray(Embedding emb) {
        // pgvector 接受 '[0.1,0.2,...]' 格式
        float[] vec = emb.vector();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.6f", vec[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    /** 提供给外部使用的转换（避免重复代码） */
    public static String toPgVector(Embedding emb) {
        float[] vec = emb.vector();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.6f", vec[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
