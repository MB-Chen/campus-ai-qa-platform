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

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.common.trace.RagTraceNode;
import sparkx.sparkshop.knowledge.entity.ChunkEntity;
import sparkx.sparkshop.knowledge.entity.KgConfig;
import sparkx.sparkshop.knowledge.ingest.KgEntityIndexer;
import sparkx.sparkshop.knowledge.infra.EmbeddingModelProvider;
import sparkx.sparkshop.knowledge.mapper.ChunkMapper;
import sparkx.sparkshop.knowledge.mapper.KgConfigMapper;
import sparkx.sparkshop.knowledge.mapper.KgEntityMapper;
import sparkx.sparkshop.knowledge.retrieval.RetrievalContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识图谱检索通道（实现 {@link GraphChannel}，被 {@code RetrieveStage} 自动收集）。
 *
 * <p>检索流程：
 * <ol>
 *   <li>{@link GraphQueryEntityExtractor} 从 query 抽实体名（LLM + Redis 缓存）</li>
 *   <li>实体名 → embedding（复用 {@link EmbeddingModelProvider}，与 kg_entity 同模型）</li>
 *   <li>pgvector 向量召回 kg_entity（topK，阈值过滤）</li>
 *   <li>{@link GraphRepository#findRelatedChunkIds} Neo4j 子图扩展取关联 chunk_ids</li>
 *   <li>批量查 chunks 表取原文，包装 {@link Content}（带 metadata: source=graph, entity_names, graph_score）</li>
 * </ol>
 *
 * <p>★ priority=5（介于 IntentDirectedChannel=1 和 VectorKeywordHybridChannel=10 之间）。
 * ★ isEnabled：kg_config.enabled=1 且至少一个 KB 启用 KG。
 * ★ Content metadata 带 {@code source=graph}，供 {@link GraphExpansionPostProcessor} 识别和扩展。
 *
 * <p>★ 不改动 HybridContentRetriever：本通道自己包装 Content，下游 Dedup/Rerank 只读 text 不受影响。
 */
@Component
public class KnowledgeGraphChannel implements GraphChannel {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphChannel.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Resource
    private GraphQueryEntityExtractor queryEntityExtractor;

    @Resource
    private KgConfigMapper kgConfigMapper;

    @Resource
    private KgEntityMapper kgEntityMapper;

    @Resource
    private sparkx.sparkshop.knowledge.mapper.KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Resource
    private ChunkMapper chunkMapper;

    @Resource
    private EmbeddingModelProvider embeddingModelProvider;

    @Resource
    private GraphRepository graphRepository;

    @Override
    public String getName() { return "knowledge-graph"; }

    @Override
    public int getPriority() { return 5; }

    /**
     * ★ RRF 融合权重 0.5（降权）。
     *
     * <p>原因：图谱通道跑在全局图上、命中实体后只做子图扩展，证据质量不如向量直接召回稳定。
     * 0.5 让图谱证据在 RRF 融合时占半权，避免抢占向量证据的 topK 位次。
     *
     * <p>★ 调参依据：观察 {@link sparkx.sparkshop.knowledge.pipeline.stages.RerankStage}
     * 的归因日志 graphIn/graphOut——若图谱证据 rerank 存活率长期高，可上调到 0.7~1.0；
     * 长期为 0 说明当前是纯成本，应考虑下调到 0.3 或关通道。
     */
    @Override
    public double getWeight() { return 0.5; }

    @Override
    public ChannelType getType() { return ChannelType.KNOWLEDGE_GRAPH; }

    @Override
    public boolean isEnabled(RetrievalContext ctx) {
        // ★ 基础设施前置：Neo4j 未配置（NoopGraphRepository 兜底）时直接禁用通道，
        // 避免后续 retrieve 空跑 LLM 抽 query 实体。
        // 性能考虑：此处在每条消息检索路径调用，用 instanceof 做廉价判断
        // （而非 graphRepository.getSchema()，那会触发一次 Neo4j schema 查询）。
        if (graphRepository instanceof NoopGraphRepository) return false;

        // 全局开关检查
        KgConfig config = kgConfigMapper.selectById(1);
        if (config == null || config.getEnabled() == null || config.getEnabled() != 1) return false;

        // 至少一个 KB 下存在启用了 KG 的文档（文档级开关）
        List<String> kbIds = ctx.getKnowledgeBaseIds();
        if (kbIds == null || kbIds.isEmpty()) return false;
        for (String kbId : kbIds) {
            Long cnt = knowledgeDocumentMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                            sparkx.sparkshop.knowledge.entity.KnowledgeDocument>()
                            .eq(sparkx.sparkshop.knowledge.entity.KnowledgeDocument::getKbId, kbId)
                            .eq(sparkx.sparkshop.knowledge.entity.KnowledgeDocument::getActive, 1)
                            .eq(sparkx.sparkshop.knowledge.entity.KnowledgeDocument::getKgEnabled, 1));
            if (cnt != null && cnt > 0) return true;
        }
        return false;
    }

    @Override
    @RagTraceNode(name = "kg-channel-retrieve", type = "KG_SEARCH")
    public List<Content> retrieve(Query query, RetrievalContext ctx) {
        String queryText = query.text();
        List<String> kbIds = ctx.getKnowledgeBaseIds();
        if (queryText == null || queryText.isBlank() || kbIds == null || kbIds.isEmpty()) return List.of();

        KgConfig config = kgConfigMapper.selectById(1);
        if (config == null) return List.of();

        int topK = 10;
        double threshold = config.getSimilarityThreshold() != null
                ? config.getSimilarityThreshold().doubleValue() : 0.65;
        int hopDepth = config.getHopDepth() != null ? config.getHopDepth() : 2;  // 默认 2 跳，与 kg_config 建表默认一致
        double secondHopWeight = config.getSecondHopWeight() != null
                ? config.getSecondHopWeight().doubleValue() : 0.5;
        // ★ 第四期：检索模式（local/global/hybrid，默认 local 保持向后兼容）
        String mode = config.getRetrievalMode();
        if (mode == null || mode.isBlank()) mode = "local";

        List<Content> allResults = new ArrayList<>();

        for (String kbId : kbIds) {
            // 1. LLM 抽 query 实体
            List<String> entityNames = queryEntityExtractor.extract(queryText, kbId);
            if (entityNames.isEmpty()) {
                log.debug("[KgChannel] query 无实体 kbId={} query={}", kbId, queryText);
                continue;
            }

            // local 路（local/hybrid 模式都跑）：向量召回 → 子图扩展
            if (!"global".equals(mode)) {
                List<Content> localResults = retrieveLocal(kbId, entityNames, config,
                        threshold, topK, hopDepth, secondHopWeight);
                allResults.addAll(localResults);
            }

            // global 路（global/hybrid 模式跑）：社区摘要召回
            // 前置条件：kg_config.community_enabled=1 且 GraphRepository 支持社区查询
            if (("global".equals(mode) || "hybrid".equals(mode)) && isCommunityEnabled(config)) {
                List<Content> globalResults = retrieveGlobal(kbId, entityNames, topK);
                allResults.addAll(globalResults);
            }
        }

        return allResults;
    }

    /**
     * ★ 第四期：local 检索路径（原 retrieve 主流程抽出，保持行为等价）。
     * 向量召回 kg_entity → Neo4j 子图扩展取关联 chunk → 包装 Content。
     */
    private List<Content> retrieveLocal(String kbId, List<String> entityNames, KgConfig config,
                                         double threshold, int topK, int hopDepth, double secondHopWeight) {
        List<Content> results = new ArrayList<>();
        EmbeddingModel embModel = embeddingModelProvider.resolveByModelId(
                config.getEmbeddingModelId(), config.getEmbeddingModelName());
        if (embModel == null) {
            log.warn("[KgChannel] embedding 模型未配置，跳过 kbId={}", kbId);
            return results;
        }

        // 对每个实体名做向量检索，合并去重
        Map<Long, Map<String, Object>> matchedEntities = new LinkedHashMap<>();
        for (String entityName : entityNames) {
            try {
                Embedding emb = embModel.embed(TextSegment.from(entityName)).content();
                String embPg = KgEntityIndexer.toPgVector(emb);
                List<Map<String, Object>> hits = kgEntityMapper.vectorSearch(kbId, embPg, threshold, topK);
                for (Map<String, Object> hit : hits) {
                    Long id = ((Number) hit.get("id")).longValue();
                    matchedEntities.putIfAbsent(id, hit);
                }
            } catch (Exception e) {
                log.warn("[KgChannel] 实体向量检索失败 name={}: {}", entityName, e.getMessage());
            }
        }

        if (matchedEntities.isEmpty()) {
            log.debug("[KgChannel] 无匹配实体 kbId={} entities={}", kbId, entityNames);
            return results;
        }

        // 收集 canonical_names，查 Neo4j 子图取关联 chunk_ids
        List<String> canonicalNames = matchedEntities.values().stream()
                .map(m -> (String) m.get("canonical_name"))
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .toList();

        Map<String, Double> chunkScores = graphRepository.findRelatedChunkIds(
                kbId, null, canonicalNames, hopDepth, secondHopWeight);

        if (chunkScores.isEmpty()) {
            log.debug("[KgChannel] 子图无关联 chunk kbId={}", kbId);
            return results;
        }

        // 批量查 chunks 表取原文
        List<String> chunkIds = new ArrayList<>(chunkScores.keySet());
        if (chunkIds.size() > 50) {
            chunkIds = chunkIds.stream()
                    .sorted((a, b) -> Double.compare(
                            chunkScores.getOrDefault(b, 0.0), chunkScores.getOrDefault(a, 0.0)))
                    .limit(50)
                    .toList();
        }

        List<ChunkEntity> chunks = chunkMapper.selectByIds(chunkIds);
        Map<String, String> chunkContentMap = new HashMap<>();
        for (ChunkEntity c : chunks) {
            if (c.getContent() != null && !c.getContent().isBlank()) {
                chunkContentMap.put(c.getId(), c.getContent());
            }
        }

        // 包装 Content（带 metadata: source=graph, entity_names, graph_score, chunk_id）
        // ★ entity_names 用逗号串存：LangChain4j Metadata 不支持 List 类型，只能存 String/数字/UUID。
        //   下游 GraphExpansionPostProcessor 按 String 拆逗号还原。
        String entityNamesCsv = canonicalNames == null ? "" : String.join(",", canonicalNames);
        for (Map.Entry<String, Double> entry : chunkScores.entrySet()) {
            String chunkId = entry.getKey();
            double score = entry.getValue();
            String content = chunkContentMap.get(chunkId);
            if (content == null || content.isBlank()) continue;

            Map<String, Object> meta = new HashMap<>();
            meta.put("source", "graph");
            meta.put("chunk_id", chunkId);
            meta.put("graph_score", score);
            meta.put("entity_names", entityNamesCsv);
            meta.put("kb_id", kbId);

            results.add(Content.from(TextSegment.from(content, Metadata.from(meta))));
        }

        log.info("[KgChannel:local] kbId={} queryEntities={} matchedEntities={} chunks={}",
                kbId, entityNames.size(), matchedEntities.size(), chunkScores.size());
        return results;
    }

    /**
     * ★ 第四期：global 检索路径——社区摘要召回。
     * 把召回的社区摘要包装成 Content（source=graph_global），作为「宏观/全局性证据」补充进结果集。
     */
    private List<Content> retrieveGlobal(String kbId, List<String> entityNames, int topK) {
        List<Content> results = new ArrayList<>();
        try {
            List<Map<String, Object>> communities = graphRepository.findRelatedCommunities(
                    kbId, entityNames, topK);
            // ★ entity_names 用逗号串存（LangChain4j Metadata 不支持 List），与 local 路径一致
            String entityNamesCsv = entityNames == null ? "" : String.join(",", entityNames);
            for (Map<String, Object> c : communities) {
                Object summaryObj = c.get("summary");
                if (summaryObj == null) continue;
                String summary = summaryObj.toString();
                if (summary.isBlank()) continue;

                Map<String, Object> meta = new HashMap<>();
                meta.put("source", "graph_global");
                meta.put("community_id", c.get("communityId"));
                // hitEntities 可能是 List/String/Number，统一 toString 兜住（Metadata 不支持 List）
                meta.put("hit_entities", String.valueOf(c.get("hitEntities")));
                meta.put("entity_names", entityNamesCsv);
                meta.put("kb_id", kbId);

                results.add(Content.from(TextSegment.from(summary, Metadata.from(meta))));
            }
            if (!results.isEmpty()) {
                log.info("[KgChannel:global] kbId={} queryEntities={} communities={}",
                        kbId, entityNames.size(), results.size());
            }
        } catch (Exception e) {
            log.warn("[KgChannel:global] 社区摘要召回失败 kbId={}: {}", kbId, e.getMessage());
        }
        return results;
    }

    /** 社区检测开关（kg_config.community_enabled=1） */
    private static boolean isCommunityEnabled(KgConfig config) {
        return config.getCommunityEnabled() != null && config.getCommunityEnabled() == 1;
    }
}
