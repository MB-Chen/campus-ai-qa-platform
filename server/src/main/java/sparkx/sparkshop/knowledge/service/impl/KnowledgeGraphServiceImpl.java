// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.entity.ChunkEntity;
import sparkx.sparkshop.knowledge.entity.KgConfig;
import sparkx.sparkshop.knowledge.entity.KgEntity;
import sparkx.sparkshop.knowledge.entity.KgExtractionRecord;
import sparkx.sparkshop.knowledge.entity.KnowledgeBase;
import sparkx.sparkshop.knowledge.entity.KnowledgeDocument;
import sparkx.sparkshop.knowledge.graph.GraphExtractionService;
import sparkx.sparkshop.knowledge.graph.GraphQueryEntityExtractor;
import sparkx.sparkshop.knowledge.graph.GraphRepository;
import sparkx.sparkshop.knowledge.ingest.KgEntityIndexer;
import sparkx.sparkshop.knowledge.infra.EmbeddingModelProvider;
import sparkx.sparkshop.knowledge.mapper.ChunkMapper;
import sparkx.sparkshop.knowledge.mapper.KgConfigMapper;
import sparkx.sparkshop.knowledge.mapper.KgEntityMapper;
import sparkx.sparkshop.knowledge.mapper.KgExtractionRecordMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeBaseMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeDocumentMapper;
import sparkx.sparkshop.knowledge.service.KnowledgeGraphService;
import sparkx.sparkshop.knowledge.validate.KgConfigValidate;
import sparkx.sparkshop.knowledge.validate.KgExtractValidate;
import sparkx.sparkshop.knowledge.validate.KgHitTestValidate;
import sparkx.sparkshop.knowledge.validate.KgKbSettingValidate;
import sparkx.sparkshop.knowledge.validate.KgRecordListValidate;
import sparkx.sparkshop.knowledge.vo.KgExtractionProgressVo;
import sparkx.sparkshop.knowledge.vo.KgKbSettingVo;
import sparkx.sparkshop.knowledge.vo.TaskIdVo;
import sparkx.sparkshop.system.vo.PageResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 聚合问法识别关键词（优化B 用）：query 含这些词且能识别到实体类型，才走按类型兜底召回。 */
    private static final Set<String> AGG_KEYWORDS =
            Set.of("列出", "所有", "全部", "有哪些", "一共", "全部节点", "哪些");

    @Resource
    private KgConfigMapper kgConfigMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Resource
    private KgExtractionRecordMapper kgExtractionRecordMapper;

    @Resource
    private KgEntityMapper kgEntityMapper;

    /** 图谱仓库（始终非 null：已配置 Neo4j 为真实实现，未配置为 NoopGraphRepository 兜底） */
    @Resource
    private GraphRepository graphRepository;

    @Resource
    private GraphExtractionService graphExtractionService;

    /** ★ 第四期：社区检测服务（始终装配，内部判 Neo4j 未配置时短路） */
    @Resource
    private sparkx.sparkshop.knowledge.graph.CommunityService communityService;

    @Resource
    private GraphQueryEntityExtractor queryEntityExtractor;

    @Resource
    private EmbeddingModelProvider embeddingModelProvider;

    @Resource
    private ChunkMapper chunkMapper;


    /** 读取全局配置（kg_config 固定 id=1 单行）。 */
    @Override
    public KgConfig getConfig() {
        return kgConfigMapper.selectById(1);
    }

    /** 保存全局配置：非空字段才覆盖（局部更新），upsert 保证首次保存自动建行。 */
    @Override
    public void saveConfig(KgConfigValidate v) {
        KgConfig config = kgConfigMapper.selectById(1);
        if (config == null) {
            config = new KgConfig();
            config.setId(1);
            config.setCreatedAt(LocalDateTime.now());
        }
        if (v.getExtractModelId() != null) config.setExtractModelId(v.getExtractModelId());
        if (v.getExtractModelName() != null) config.setExtractModelName(v.getExtractModelName());
        if (v.getEmbeddingModelId() != null) config.setEmbeddingModelId(v.getEmbeddingModelId());
        if (v.getEmbeddingModelName() != null) config.setEmbeddingModelName(v.getEmbeddingModelName());
        if (v.getEnabled() != null) config.setEnabled(v.getEnabled());
        if (v.getSimilarityThreshold() != null) config.setSimilarityThreshold(v.getSimilarityThreshold());
        if (v.getExtractBatchSize() != null) config.setExtractBatchSize(v.getExtractBatchSize());
        if (v.getHopDepth() != null) config.setHopDepth(v.getHopDepth());
        if (v.getSecondHopWeight() != null) config.setSecondHopWeight(v.getSecondHopWeight());
        if (v.getEntityMergeThreshold() != null) config.setEntityMergeThreshold(v.getEntityMergeThreshold());
        if (v.getRetrievalMode() != null) config.setRetrievalMode(v.getRetrievalMode());
        if (v.getCommunityEnabled() != null) config.setCommunityEnabled(v.getCommunityEnabled());
        config.setUpdatedAt(LocalDateTime.now());
        kgConfigMapper.insertOrUpdate(config);
    }


    /**
     * 测试 Neo4j 连通性：调 GraphRepository.getSchema()。
     * NoopGraphRepository（未配置）返回 null → 约定信号 "UNCONFIGURED"，前端据此显示「未配置」。
     */
    @Override
    public String testConnect() {
        // NoopGraphRepository.getSchema() 返回 null = 未配置 Neo4j
        // 前端据此显示「未配置」状态（约定信号 UNCONFIGURED）
        String schema;
        try {
            schema = graphRepository.getSchema();
        } catch (Exception e) {
            throw new BusinessException("Neo4j 连接失败: " + e.getMessage());
        }
        return schema != null ? schema : "UNCONFIGURED";
    }


    /** 查询 KB 级 KG 开关（knowledge_base.kg_enabled），1=启用 2=禁用，组装成回执。 */
    @Override
    public KgKbSettingVo getKbSetting(String kbId) {
        Integer kgEnabled = null;
        if (kbId != null && !kbId.isBlank()) {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
            kgEnabled = kb == null ? null : kb.getKgEnabled();
        }
        return new KgKbSettingVo(kbId, kgEnabled);
    }

    /** 设置 KB 级 KG 开关（旧接口保留，实际开关以文档级 document.kg_enabled 为准）。 */
    @Override
    public void saveKbSetting(KgKbSettingValidate v) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(v.getKbId());
        if (kb == null) throw new BusinessException("知识库不存在: " + v.getKbId());
        kb.setKgEnabled(v.getKgEnabled());
        knowledgeBaseMapper.updateById(kb);
    }


    /**
     * 触发图谱抽取（异步，返回 taskId 供前端轮询）。
     *
     * <p>★ 文档级开关改造后：
     * <ul>
     *   <li>documentIds 非空：对指定文档抽取，逐一校验文档级 kg_enabled=1</li>
     *   <li>documentIds 为空：补抽取——查该 KB 下所有 active=1 且 kg_enabled=1 的文档，
     *       减去已 done 的抽取记录，对未完成的文档批量抽取</li>
     * </ul>
     * 全局开关 kg_config.enabled=1 是前置条件。
     */
    @Override
    public TaskIdVo triggerExtract(KgExtractValidate v) {
        String taskId = doTriggerExtract(v);
        return new TaskIdVo(taskId);
    }

    /** 抽取触发实际逻辑（返回裸 taskId，由 {@link #triggerExtract} 包装）。 */
    private String doTriggerExtract(KgExtractValidate v) {
        if (graphExtractionService == null) {
            throw new BusinessException("知识图谱模块未启用");
        }
        // 全局开关校验
        KgConfig config = kgConfigMapper.selectById(1);
        if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException("知识图谱全局开关未启用");
        }
        // 知识库存在性校验（不再校验 KB 级 kg_enabled，已改为文档级开关）
        KnowledgeBase kb = knowledgeBaseMapper.selectById(v.getKbId());
        if (kb == null) throw new BusinessException("知识库不存在: " + v.getKbId());

        if (v.getDocumentIds() != null && !v.getDocumentIds().isEmpty()) {
            // 指定文档补抽取：校验文档级开关开启
            for (String docId : v.getDocumentIds()) {
                KnowledgeDocument doc = knowledgeDocumentMapper.selectById(docId);
                if (doc == null) throw new BusinessException("文档不存在: " + docId);
                if (doc.getKgEnabled() == null || doc.getKgEnabled() != 1) {
                    throw new BusinessException("文档「" + doc.getFileName() + "」未启用知识图谱");
                }
            }
            return graphExtractionService.triggerBatchExtraction(v.getDocumentIds(), v.getKbId());
        } else {
            // ★ 补抽取以 document 表为基准（而非 kg_extraction_record）：
            // document 表反映 KB 下真实存在的文档，kg_extraction_record 只反映"已跑过 KG 抽取"的文档。
            // 场景：文档先上传入库、KG 开关后开启时，入库钩子不会创建 record，
            // 此时按 record 查会误报"没有需要补抽取的文档"。改为：文档级 KG 开启且启用的文档 减去 已 done 的 record。
            List<KnowledgeDocument> docs = knowledgeDocumentMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeDocument>()
                            .eq(KnowledgeDocument::getKbId, v.getKbId())
                            .eq(KnowledgeDocument::getActive, 1)
                            .eq(KnowledgeDocument::getKgEnabled, 1));
            if (docs.isEmpty()) {
                throw new BusinessException("该知识库下没有启用知识图谱的文档");
            }

            // 查该 KB 下已成功抽取（status=done）的文档 id，这些跳过
            List<KgExtractionRecord> doneRecords = kgExtractionRecordMapper.selectList(
                    new LambdaQueryWrapper<KgExtractionRecord>()
                            .eq(KgExtractionRecord::getKbId, v.getKbId())
                            .eq(KgExtractionRecord::getStatus, "done"));
            Set<String> doneDocIds = doneRecords.stream()
                    .map(KgExtractionRecord::getDocumentId)
                    .collect(Collectors.toSet());

            List<String> docIds = docs.stream()
                    .map(KnowledgeDocument::getId)
                    .filter(id -> !doneDocIds.contains(id))
                    .collect(Collectors.toList());
            if (docIds.isEmpty()) {
                throw new BusinessException("没有需要补抽取的文档");
            }
            return graphExtractionService.triggerBatchExtraction(docIds, v.getKbId());
        }
    }


    /** 查询抽取进度：从 Redis 进度桶读取（由 GraphExtractionService 维护）。 */
    @Override
    public KgExtractionProgressVo getExtractProgress(String taskId) {
        if (graphExtractionService == null) return null;
        return graphExtractionService.getProgress(taskId);
    }


    /**
     * 抽取记录分页列表（支持按 kbId / documentId / status 过滤）。
     * 批量回填知识库名、文档名，避免前端显示 UUID。
     */
    @Override
    public PageResult<KgExtractionRecord> getRecords(KgRecordListValidate query) {
        int page = (query.getPage() == null || query.getPage() < 1) ? 1 : query.getPage();
        int size = (query.getSize() == null || query.getSize() < 1) ? 20 : Math.min(query.getSize(), 100);

        LambdaQueryWrapper<KgExtractionRecord> wrapper = new LambdaQueryWrapper<>();
        if (query.getKbId() != null && !query.getKbId().isBlank()) wrapper.eq(KgExtractionRecord::getKbId, query.getKbId());
        if (query.getDocumentId() != null && !query.getDocumentId().isBlank()) wrapper.eq(KgExtractionRecord::getDocumentId, query.getDocumentId());
        if (query.getStatus() != null && !query.getStatus().isBlank()) wrapper.eq(KgExtractionRecord::getStatus, query.getStatus());
        wrapper.orderByDesc(KgExtractionRecord::getCreatedAt);

        var pageObj = kgExtractionRecordMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
        var records = pageObj.getRecords();

        // ★ 批量填充知识库名称和文档名称（避免前端显示 UUID）
        if (!records.isEmpty()) {
            Set<String> kbIds = records.stream().map(KgExtractionRecord::getKbId)
                    .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            Set<String> docIds = records.stream().map(KgExtractionRecord::getDocumentId)
                    .filter(java.util.Objects::nonNull).collect(Collectors.toSet());

            // 知识库 id→name
            Map<String, String> kbNameMap = new HashMap<>();
            if (!kbIds.isEmpty()) {
                knowledgeBaseMapper.selectBatchIds(kbIds).forEach(
                        kb -> kbNameMap.put(kb.getId(), kb.getName()));
            }
            // 文档 id→fileName
            Map<String, String> docNameMap = new HashMap<>();
            if (!docIds.isEmpty()) {
                knowledgeDocumentMapper.selectBatchIds(docIds).forEach(
                        doc -> docNameMap.put(doc.getId(), doc.getFileName()));
            }

            for (KgExtractionRecord r : records) {
                r.setKbName(kbNameMap.getOrDefault(r.getKbId(), r.getKbId()));
                r.setDocumentName(docNameMap.getOrDefault(r.getDocumentId(), r.getDocumentId()));
            }
        }

        return new PageResult<>(records, pageObj.getTotal());
    }


    /**
     * 图谱检索测试（全链路调试）：复现 {@code KnowledgeGraphChannel.retrieve} 的检索过程。
     *
     * <p>★ 按 {@code kg_config.retrieval_mode}（或入参 retrievalMode 临时覆盖）分发，与真实 RAG 通道行为对齐：
     * <ul>
     *   <li>local：向量召回实体 → Neo4j 子图扩展取 chunk（受 documentId 限定文档作用域）</li>
     *   <li>global：社区摘要召回（KB 级，documentId 不参与；前置需 community_enabled=1 + 已跑社区检测）</li>
     *   <li>hybrid：local + global 双路并行</li>
     * </ul>
     *
     * <p>返回每一步的中间结果供前端展示，便于定位召回问题。
     */
    @Override
    public Map<String, Object> hitTest(KgHitTestValidate v) {
        KgConfig config = kgConfigMapper.selectById(1);
        if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException("知识图谱全局开关未启用");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("kbId", v.getKbId());
        result.put("query", v.getQuery());

        int topK = (v.getTopK() != null && v.getTopK() > 0) ? v.getTopK() : 10;
        double threshold = config.getSimilarityThreshold() != null
                ? config.getSimilarityThreshold().doubleValue() : 0.65;
        int hopDepth = config.getHopDepth() != null ? config.getHopDepth() : 2;  // 默认 2 跳，与 kg_config 建表默认一致
        double secondHopWeight = config.getSecondHopWeight() != null
                ? config.getSecondHopWeight().doubleValue() : 0.5;

        // ★ 检索模式：入参优先，其次读全局配置，默认 local（与 KnowledgeGraphChannel.retrieve 一致）
        String mode = v.getRetrievalMode();
        if (mode == null || mode.isBlank()) mode = config.getRetrievalMode();
        if (mode == null || mode.isBlank()) mode = "local";
        result.put("mode", mode);

        // 1. LLM 抽 query 实体
        List<String> entityNames = queryEntityExtractor.extract(v.getQuery(), v.getKbId());
        result.put("extractedEntities", entityNames);

        if (entityNames.isEmpty()) {
            result.put("matchedEntities", List.of());
            result.put("relatedChunks", List.of());
            result.put("relatedCommunities", List.of());
            result.put("message", "query 中未抽取到实体");
            return result;
        }

        // 2. 向量召回 kg_entity（global-only 模式下也跑——产出 matchedEntities 供前端观察实体链接情况，
        //    且 global 路的 findRelatedCommunities 也依赖 query 抽出的 entityNames 做社区成员命中）
        EmbeddingModel embModel = embeddingModelProvider.resolveByModelId(
                config.getEmbeddingModelId(), config.getEmbeddingModelName());
        if (embModel == null) {
            result.put("message", "embedding 模型未配置");
            return result;
        }

        // 文档级检索：documentId 非空时限定到单文档子图（实体向量召回按 doc_id 过滤，子图扩展按 doc_id 过滤）
        String docId = (v.getDocumentId() != null && !v.getDocumentId().isBlank()) ? v.getDocumentId() : null;

        Map<Long, Map<String, Object>> matchedEntities = new LinkedHashMap<>();
        for (String entityName : entityNames) {
            boolean linkedByVector = false;
            try {
                Embedding emb = embModel.embed(TextSegment.from(entityName)).content();
                String embPg = KgEntityIndexer.toPgVector(emb);
                List<Map<String, Object>> hits = (docId != null)
                        ? kgEntityMapper.vectorSearchByDoc(v.getKbId(), docId, embPg, threshold, topK)
                        : kgEntityMapper.vectorSearch(v.getKbId(), embPg, threshold, topK);
                for (Map<String, Object> hit : hits) {
                    Long id = ((Number) hit.get("id")).longValue();
                    matchedEntities.putIfAbsent(id, hit);
                    linkedByVector = true;
                }
            } catch (Exception e) {
                // 单个实体失败不阻断
            }
            // ★ 优化A：该实体向量未命中（典型如「场景C/场景D」短代号实体，向量相似度低于阈值），
            //   用关键词(ILIKE 中文可靠匹配)兜底，精确名/别名直接提权到 0.99，避免「抽得出却链不上」。
            //   逐实体兜底（而非整体兜底）：多实体查询中部分实体已链上时，其余实体仍有机会补链——
            //   例如「DLP 是否导致场景D」中 DLP 已链上，场景D 仍可借此补链，避免被整体跳过。
            if (!linkedByVector) {
                try {
                    List<Map<String, Object>> kwHits = kgEntityMapper.keywordSearch(v.getKbId(), entityName, topK);
                    for (Map<String, Object> hit : kwHits) {
                        Long id = ((Number) hit.get("id")).longValue();
                        if (!matchedEntities.containsKey(id)) {
                            hit.put("score", 0.99);
                            matchedEntities.put(id, hit);
                        }
                    }
                } catch (Exception e) {
                    // 单个实体失败不阻断
                }
            }
        }

        // ★ 优化B：所有实体向量+关键词均无命中（如「列出所有阶段节点」聚合问法，query 抽不出具体实体），
        //   按实体类型全量兜底召回，再走子图扩展。
        if (matchedEntities.isEmpty()) {
            String type = resolveTypeFromQuery(v.getQuery(), entityNames, v.getKbId());
            if (type != null) {
                List<Map<String, Object>> typeHits =
                        kgEntityMapper.selectByType(v.getKbId(), type, topK * 5);
                for (Map<String, Object> hit : typeHits) {
                    Long id = ((Number) hit.get("id")).longValue();
                    matchedEntities.putIfAbsent(id, hit);
                }
            }
        }

        result.put("matchedEntities", matchedEntities.values().stream().toList());

        if (matchedEntities.isEmpty()) {
            result.put("relatedChunks", List.of());
            result.put("relatedCommunities", List.of());
            result.put("message", "无匹配实体（向量/关键词/类型兜底均未命中）");
            return result;
        }

        // 收集命中实体的 canonical_names（local 子图扩展 / global 社区成员命中 共用）
        List<String> canonicalNames = matchedEntities.values().stream()
                .map(m -> (String) m.get("canonical_name"))
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .toList();

        // ★ 按 mode 分发
        List<Map<String, Object>> relatedChunks = new ArrayList<>();
        List<Map<String, Object>> relatedCommunities = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        // local 路（mode != global 都跑，受 documentId 作用域）
        if (!"global".equals(mode)) {
            runLocalHitTest(result, v.getKbId(), docId, canonicalNames, hopDepth, secondHopWeight, topK, relatedChunks, notes);
        }
        // global 路（mode == global / hybrid，前置 community_enabled + Neo4j 已配置）
        if ("global".equals(mode) || "hybrid".equals(mode)) {
            runGlobalHitTest(v.getKbId(), entityNames, topK, config, relatedCommunities, notes);
        }

        result.put("relatedChunks", relatedChunks);
        result.put("relatedCommunities", relatedCommunities);
        if (!notes.isEmpty()) {
            result.put("message", String.join("；", notes));
        }
        return result;
    }

    /**
     * ★ local 检索路（抽出以便 global-only 时不跑）：向量召回实体 → Neo4j 子图扩展取关联 chunk → 取原文。
     * 结果写入传入的 {@code relatedChunks}（按 graphScore 降序）；前置不满足（Neo4j 未配置）时写 notes 提示。
     */
    private void runLocalHitTest(Map<String, Object> result, String kbId, String docId,
                                 List<String> canonicalNames, int hopDepth, double secondHopWeight,
                                 int topK, List<Map<String, Object>> relatedChunks, List<String> notes) {
        // Neo4j 子图扩展取关联 chunk_ids（未配置 Neo4j 时跳过，仅返回实体匹配结果）
        boolean neo4jConfigured = graphRepository.getSchema() != null;
        if (!neo4jConfigured) {
            notes.add("Neo4j 未连接，local 路仅返回实体匹配结果");
            return;
        }
        if (canonicalNames.isEmpty()) {
            return;
        }

        // ★ 跳数严格遵循后台配置（kg_config.hop_depth：1=一跳 / 2=二跳）。
        //   二跳降权由 findRelatedChunkIds 内部处理：hopDepth>=2 时二跳邻居 chunk 按
        //   secondHopWeight（配置项 second_hop_weight，默认 0.5）打分，而非 1.0。
        //   不再强制 Math.max(hopDepth, 2)，以免架空用户在 9811 后台的 一跳/二跳 开关。
        Map<String, Double> chunkScores = graphRepository.findRelatedChunkIds(
                kbId, docId, canonicalNames, hopDepth, secondHopWeight);

        // 批量查 chunks 取原文
        List<String> chunkIds = new ArrayList<>(chunkScores.keySet());
        if (chunkIds.size() > topK * 3) {
            chunkIds = chunkIds.stream()
                    .sorted((a, b) -> Double.compare(
                            chunkScores.getOrDefault(b, 0.0), chunkScores.getOrDefault(a, 0.0)))
                    .limit(topK * 3L)
                    .toList();
        }

        if (!chunkIds.isEmpty()) {
            List<ChunkEntity> chunks = chunkMapper.selectByIds(chunkIds);
            for (ChunkEntity c : chunks) {
                Map<String, Object> chunkInfo = new LinkedHashMap<>();
                chunkInfo.put("id", c.getId());
                chunkInfo.put("content", c.getContent() != null
                        ? c.getContent().substring(0, Math.min(200, c.getContent().length())) + "..."
                        : "");
                chunkInfo.put("graphScore", chunkScores.getOrDefault(c.getId(), 0.0));
                relatedChunks.add(chunkInfo);
            }
        }
        // ★ 优化D：按图谱得分降序，金标准 chunk 排前，降低噪声干扰
        relatedChunks.sort((a, b) -> Double.compare(
                ((Number) b.getOrDefault("graphScore", 0.0)).doubleValue(),
                ((Number) a.getOrDefault("graphScore", 0.0)).doubleValue()));
        result.put("chunkCount", chunkScores.size());
    }

    /**
     * ★ global 检索路（第四期）：社区摘要召回。对齐 {@code KnowledgeGraphChannel.retrieveGlobal}。
     * KB 级（documentId 不参与，社区天然跨文档聚合）；前置需 community_enabled=1 + Neo4j 已配置。
     * 结果写入传入的 {@code relatedCommunities}（按 hitEntities 降序）；前置不满足时写 notes 提示。
     */
    private void runGlobalHitTest(String kbId, List<String> entityNames, int topK,
                                  KgConfig config, List<Map<String, Object>> relatedCommunities,
                                  List<String> notes) {
        // 前置校验：社区检测开关
        if (config.getCommunityEnabled() == null || config.getCommunityEnabled() != 1) {
            notes.add("global 模式需先在「知识图谱」配置页启用社区检测并运行社区检测");
            return;
        }
        // 前置校验：Neo4j 已配置（社区数据存于 Neo4j Entity 节点的 community_id/community_summary 属性）
        if (graphRepository.getSchema() == null) {
            notes.add("global 模式需 Neo4j 已连接");
            return;
        }
        if (entityNames == null || entityNames.isEmpty()) {
            return;
        }

        // findRelatedCommunities：按社区成员命中 query 实体数排序，取 topK 个社区摘要
        List<Map<String, Object>> communities = graphRepository.findRelatedCommunities(kbId, entityNames, topK);
        // ★ 过滤掉空摘要（社区检测跑了但 LLM 摘要未生成 / 失败的社区）
        for (Map<String, Object> c : communities) {
            Object summaryObj = c.get("summary");
            String summary = summaryObj == null ? "" : summaryObj.toString();
            if (summary.isBlank()) continue;

            Map<String, Object> ci = new LinkedHashMap<>();
            ci.put("communityId", c.get("communityId"));
            ci.put("hitEntities", c.get("hitEntities"));
            ci.put("summary", summary);
            relatedCommunities.add(ci);
        }
        // 按 hitEntities 降序（命中实体多的社区排前）
        relatedCommunities.sort((a, b) -> {
            int ha = a.get("hitEntities") instanceof Number ? ((Number) a.get("hitEntities")).intValue() : 0;
            int hb = b.get("hitEntities") instanceof Number ? ((Number) b.get("hitEntities")).intValue() : 0;
            return Integer.compare(hb, ha);
        });
    }

    /**
     * ★ 优化B 辅助：从 query / 抽取实体中识别「列出所有X」类的实体类型。
     * 仅当 query 带有聚合意图词（列出/所有/全部/…）且能在该 KB 的实体类型集合里匹配到类型词时才返回，
     * 避免普通问法被误判为聚合。
     */
    private String resolveTypeFromQuery(String query, List<String> entityNames, String kbId) {
        String q = (query == null ? "" : query).toLowerCase();
        boolean aggIntent = AGG_KEYWORDS.stream().anyMatch(q::contains);
        if (!aggIntent) return null;
        List<String> types;
        try {
            types = kgEntityMapper.selectDistinctEntityTypes(kbId);
        } catch (Exception e) {
            return null;
        }
        if (types == null || types.isEmpty()) return null;
        String haystack = (query + " " + String.join(" ", entityNames)).toLowerCase();
        for (String t : types) {
            if (t != null && !t.isBlank() && haystack.contains(t.toLowerCase())) return t;
        }
        return null;
    }


    /**
     * 图谱可视化数据（nodes/edges）。
     * documentId 非空时只查该文档的子图（文档级隔离，按 doc_id 过滤）；否则查整个 KB 的子图。
     * 未配置 Neo4j 时返回空结构 + 提示。
     */
    @Override
    public Map<String, Object> visualization(String kbId, String documentId) {
        if (graphRepository.getSchema() == null) {
            // 未配置 Neo4j：返回空结构 + 提示，前端展示「未配置」状态
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("nodes", List.of());
            empty.put("edges", List.of());
            empty.put("message", "未配置 Neo4j，请先在「外部服务配置」页添加并启用 Neo4j 连接");
            return empty;
        }
        // 指定文档：只查该文档贡献的子图
        if (documentId != null && !documentId.isBlank()) {
            return graphRepository.findDocumentSubgraph(kbId, documentId, 200);
        }
        return graphRepository.findSubgraph(kbId, 200);
    }


    /**
     * 删除某文档的全部图谱数据（文档级隔离，删除干净利落）：
     * ① Neo4j 按 doc_id 删该文档全部节点 + 关系；
     * ② kg_entity 按 (kb_id, doc_id) 删该文档全部实体行；
     * ③ kg_extraction_record 删该文档抽取记录。
     */
    @Override
    public void deleteByDocument(String kbId, String documentId) {
        if (kbId == null || documentId == null) {
            throw new BusinessException("参数不能为空");
        }
        // 1. 删 Neo4j 图谱数据（文档级隔离：直接删该文档全部节点 + 关系；未配置时 Noop 返回 0，无害）
        graphRepository.deleteByDocument(kbId, documentId, null);
        // 2. 删 kg_entity（文档级隔离：直接按 kb_id + doc_id 删该文档全部实体行）
        kgEntityMapper.delete(new LambdaQueryWrapper<KgEntity>()
                .eq(KgEntity::getKbId, kbId)
                .eq(KgEntity::getDocId, documentId));
        // 3. 删抽取记录
        kgExtractionRecordMapper.delete(
                new LambdaQueryWrapper<KgExtractionRecord>()
                        .eq(KgExtractionRecord::getKbId, kbId)
                        .eq(KgExtractionRecord::getDocumentId, documentId));
    }

    /**
     * ★ 第四期：触发社区检测 + 社区摘要生成。
     * global/hybrid 检索模式的前置数据准备。建议在 KB 文档抽取完成后手动触发。
     * 内部异步执行，不阻塞 controller 返回。
     */
    @Override
    public void triggerCommunityDetect(String kbId) {
        if (kbId == null || kbId.isBlank()) {
            throw new BusinessException("kbId 不能为空");
        }
        if (graphRepository.getSchema() == null) {
            throw new BusinessException("未配置 Neo4j，请先在「外部服务配置」页添加并启用 Neo4j 连接后再运行社区检测");
        }
        communityService.triggerDetect(kbId);
    }
}
