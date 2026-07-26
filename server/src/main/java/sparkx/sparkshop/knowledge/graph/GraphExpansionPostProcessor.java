// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.graph;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.entity.ChunkEntity;
import sparkx.sparkshop.knowledge.entity.KgConfig;
import sparkx.sparkshop.knowledge.mapper.ChunkMapper;
import sparkx.sparkshop.knowledge.mapper.KgConfigMapper;
import sparkx.sparkshop.knowledge.retrieval.RetrievalContext;
import sparkx.sparkshop.knowledge.retrieval.SearchResultPostProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图谱邻居 chunk 扩展后处理器（order=5，去重后 / 重排前）。
 *
 * <p>在去重（order=1）之后、重排（order=10）之前执行：
 * 读取 {@link KnowledgeGraphChannel} 产出的 Content（metadata.source=graph），
 * 提取其中的 entity_names → 查 Neo4j 一跳邻居 → 取邻居 chunk → 去重后补充进结果集。
 *
 * <p>★ 对 {@code HybridContentRetriever} 产出的 Content（无 source=graph metadata）天然跳过，
 * 不影响现有检索链路。
 *
 * <p>★ 补充的 chunk 带 metadata {@code source=graph_expansion}，供后续识别。
 */
@Component
public class GraphExpansionPostProcessor implements SearchResultPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(GraphExpansionPostProcessor.class);

    /** 最多从已有 graph 结果中提取多少个实体名做邻居扩展 */
    private static final int MAX_EXPANSION_ENTITIES = 10;
    /** 邻居扩展最多补充多少个 chunk */
    private static final int MAX_EXPANSION_CHUNKS = 20;

    @Resource
    private KgConfigMapper kgConfigMapper;

    @Resource
    private ChunkMapper chunkMapper;

    @Resource
    private GraphRepository graphRepository;

    @Override
    public int getOrder() { return 5; }

    @Override
    public boolean isEnabled(RetrievalContext ctx) {
        // 恒启用（process 内部判断是否有 graph 来源的 Content）
        return true;
    }

    @Override
    public List<Content> process(List<Content> chunks, Query query, RetrievalContext ctx) {
        if (chunks == null || chunks.isEmpty()) return chunks;

        // 1. 从已有结果中提取 graph 来源的 entity_names 和已知 chunk_id
        Set<String> knownChunkIds = new HashSet<>();
        Set<String> entityNames = new HashSet<>();

        for (Content c : chunks) {
            Metadata meta = c.textSegment().metadata();
            if (meta == null) continue;

            String source = meta.getString("source");
            String chunkId = meta.getString("chunk_id");
            if (chunkId != null) knownChunkIds.add(chunkId);

            if ("graph".equals(source)) {
                // entity_names 现为逗号分隔字符串（KnowledgeGraphChannel 写入时已转，
                // 因 LangChain4j Metadata 不支持 List 类型）
                String namesCsv = meta.getString("entity_names");
                if (namesCsv != null && !namesCsv.isBlank()) {
                    for (String name : namesCsv.split(",")) {
                        if (entityNames.size() >= MAX_EXPANSION_ENTITIES) break;
                        String trimmed = name.trim();
                        if (!trimmed.isEmpty()) entityNames.add(trimmed);
                    }
                }
            }
        }

        if (entityNames.isEmpty()) return chunks;  // 无 graph 来源，跳过

        // 2. Neo4j 一跳邻居扩展
        KgConfig config = kgConfigMapper.selectById(1);
        if (config == null) return chunks;

        // 取第一个 graph 来源的 kb_id
        String kbId = null;
        for (Content c : chunks) {
            Metadata meta = c.textSegment().metadata();
            if (meta != null && "graph".equals(meta.getString("source"))) {
                kbId = meta.getString("kb_id");
                break;
            }
        }
        if (kbId == null) return chunks;

        Map<String, Double> expansionChunks = graphRepository.findRelatedChunkIds(
                kbId, null, new ArrayList<>(entityNames), 1, 1.0);

        // 3. 过滤已知 chunk，取 top N
        List<String> newChunkIds = expansionChunks.entrySet().stream()
                .filter(e -> !knownChunkIds.contains(e.getKey()))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(MAX_EXPANSION_CHUNKS)
                .map(Map.Entry::getKey)
                .toList();

        if (newChunkIds.isEmpty()) return chunks;

        // 4. 批量查 chunks 取原文，包装 Content
        List<ChunkEntity> newChunks = chunkMapper.selectByIds(newChunkIds);
        List<Content> expanded = new ArrayList<>(chunks);
        for (ChunkEntity nc : newChunks) {
            if (nc.getContent() == null || nc.getContent().isBlank()) continue;
            Map<String, Object> meta = new HashMap<>();
            meta.put("source", "graph_expansion");
            meta.put("chunk_id", nc.getId());
            meta.put("graph_score", expansionChunks.getOrDefault(nc.getId(), 0.0));
            meta.put("kb_id", kbId);
            expanded.add(Content.from(TextSegment.from(nc.getContent(), Metadata.from(meta))));
        }

        log.debug("[GraphExpansion] 已有={} 扩展={}", chunks.size(), expanded.size() - chunks.size());
        return expanded;
    }
}
