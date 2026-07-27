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

import sparkx.sparkshop.knowledge.entity.ParentChunkEntity;
import sparkx.sparkshop.knowledge.mapper.ParentChunkMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 父子检索器。
 *
 * 检索子块 → 命中后用 parentId 批量查父块全文 → 用父块内容替换。
 * 解决"小粒度精准检索 vs 大上下文喂模型"的矛盾。
 * 改进点 #5：父子关系由 metadata 承载，存储无关。
 *
 * <p>移植自 sparkxV2：父块批量查询原走 JdbcTemplate 原生 SQL，
 * 在 SparkX 改用 {@link ParentChunkMapper#findByIds}（已存在的 MyBatis-Plus 等价实现，
 * SELECT * FROM parent_chunks WHERE id IN (...)），逻辑等价。
 */
public class ParentChildRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(ParentChildRetriever.class);

    private final JdbcTemplate jdbc;
    private final EmbeddingModel embeddingModel;
    private final ParentChunkMapper parentChunkMapper;
    private final int maxResults;
    private final double minScore;

    public ParentChildRetriever(JdbcTemplate jdbc, EmbeddingModel embeddingModel,
                                 ParentChunkMapper parentChunkMapper,
                                 int maxResults, double minScore) {
        this.jdbc = jdbc;
        this.embeddingModel = embeddingModel;
        this.parentChunkMapper = parentChunkMapper;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    @Override
    public List<Content> retrieve(Query query) {
        Embedding qEmb = embeddingModel.embed(query.text()).content();
        String vec = emb(qEmb);

        // 1. 检索子块（向量化的是子块）
        String sql = """
                SELECT id, content, metadata->>'parentId' AS parent_id, metadata->>'chunkRole' AS role
                FROM chunks
                WHERE metadata->>'chunkRole' = 'child'
                  AND 1 - (embedding <=> ?::vector) >= ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        List<Map<String, Object>> childHits;
        try {
            childHits = jdbc.queryForList(sql, vec, minScore, vec, maxResults);
        } catch (Exception e) {
            log.warn("[ParentChild] 子块检索失败: {}", e.getMessage());
            return List.of();
        }

        if (childHits.isEmpty()) return List.of();

        // 2. 收集去重的 parentId，批量查父块全文
        Set<String> parentIds = childHits.stream()
                .map(r -> (String) r.get("parent_id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (parentIds.isEmpty()) {
            // 无父块，返回子块本身
            return childHits.stream()
                    .map(r -> Content.from(TextSegment.from((String) r.get("content"))))
                    .toList();
        }

        Map<String, String> parentContent = new HashMap<>();
        try {
            List<ParentChunkEntity> parents = parentChunkMapper.findByIds(List.copyOf(parentIds));
            if (parents != null) {
                for (ParentChunkEntity p : parents) {
                    parentContent.put(p.getId(), p.getContent());
                }
            }
        } catch (Exception e) {
            log.warn("[ParentChild] 父块查询失败: {}", e.getMessage());
        }

        // 3. 用父块内容替换子块（命中即给完整上下文）
        return childHits.stream()
                .map(r -> {
                    String pid = (String) r.get("parent_id");
                    String content = parentContent.getOrDefault(pid, (String) r.get("content"));
                    return Content.from(TextSegment.from(content));
                })
                .distinct()   // 多个子块命中同一父块去重
                .toList();
    }

    private static String emb(Embedding e) {
        float[] vec = e.vector();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.6f", vec[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
