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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.knowledge.service.MinioService;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.entity.AiModel;
import sparkx.sparkshop.knowledge.entity.ChunkEntity;
import sparkx.sparkshop.knowledge.entity.KnowledgeBase;
import sparkx.sparkshop.knowledge.entity.KnowledgeDocument;
import sparkx.sparkshop.knowledge.entity.KnowledgeQuestion;
import sparkx.sparkshop.knowledge.entity.ParentChunkEntity;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.TsVectorGenerator;
import sparkx.sparkshop.knowledge.mapper.AiModelMapper;
import sparkx.sparkshop.knowledge.mapper.ChunkMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeBaseMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeDocumentMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeQuestionMapper;
import sparkx.sparkshop.knowledge.mapper.ParentChunkMapper;
import sparkx.sparkshop.knowledge.config.RagProperties;
import sparkx.sparkshop.knowledge.service.IKnowledgeService;
import sparkx.sparkshop.knowledge.validate.HitTestValidate;
import sparkx.sparkshop.knowledge.validate.KnowledgeBaseEditValidate;
import sparkx.sparkshop.knowledge.validate.KnowledgeBaseValidate;
import sparkx.sparkshop.knowledge.vo.HitTestVo;
import sparkx.sparkshop.knowledge.vo.KnowledgeBaseVo;
import sparkx.sparkshop.system.vo.PageQuery;
import sparkx.sparkshop.system.vo.PageResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库业务实现（KB CRUD + 命中测试）。
 *
 * 命中测试复刻 {@code HybridContentRetriever} 的检索逻辑：
 *  - embedding：query → LLMService.embed → ChunkMapper.vectorSearch
 *  - text：ChunkMapper.keywordSearch
 *  - mix：两者 RRF 融合
 */
@Slf4j
@Service
public class KnowledgeServiceImpl implements IKnowledgeService {

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Resource
    private ChunkMapper chunkMapper;

    @Resource
    private ParentChunkMapper parentChunkMapper;

    @Resource
    private KnowledgeQuestionMapper knowledgeQuestionMapper;

    @Resource
    private AiModelMapper aiModelMapper;

    @Resource
    private LLMService llmService;

    @Resource
    private MinioService minioService;

    @Resource
    private sparkx.sparkshop.knowledge.infra.EmbeddingModelProvider embeddingModelProvider;

    @Resource
    private RagProperties ragProperties;

    /** 分页查询知识库列表（支持关键词模糊搜索）。 */
    @Override
    public PageResult<KnowledgeBaseVo> page(PageQuery query) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
                .orderByDesc(KnowledgeBase::getCreatedAt);
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(KnowledgeBase::getName, query.getKeyword());
        }
        IPage<KnowledgeBase> mpPage = new Page<>(query.safePage(), query.safeSize());
        IPage<KnowledgeBase> result = knowledgeBaseMapper.selectPage(mpPage, wrapper);

        List<KnowledgeBaseVo> vos = result.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        return new PageResult<>(vos, result.getTotal());
    }

    /**
     * 新增知识库：解析 embedding 模型名 + 探测向量维度，快照模型配置到 KB 表。
     * 创建后 ai_model 表变更不影响该知识库的向量化/检索。
     */
    @Override
    public KnowledgeBaseVo add(KnowledgeBaseValidate validate) {
        // 解析 embedding 模型 → 取 model 名 + 维度
        AiModel embeddingModel = aiModelMapper.selectById(validate.getEmbeddingModelId());
        if (embeddingModel == null) {
            throw new BusinessException("嵌入模型不存在");
        }
        // ★ 具体模型名：前端传则用前端值，否则回退取 models 首项
        String modelName = validate.getEmbeddingModelName();
        if (modelName == null || modelName.isBlank()) {
            modelName = firstModel(embeddingModel.getModels());
        }
        if (modelName == null || modelName.isBlank()) {
            throw new BusinessException("嵌入模型未配置可用模型名");
        }
        // ★ 维度只能用「真实探测」：探测失败说明模型不可达/配置错（url 错、apiKey 错、模型名错、服务未启动…），
        // 此时直接报错，让用户去换一个可用的嵌入模型，而不是默认一个不支持的维度埋雷（后续入库/检索会哑火）。
        int dimension = embeddingModelProvider.probeDimension(validate.getEmbeddingModelId(), modelName);
        if (dimension <= 0) {
            throw new BusinessException("嵌入模型不可用，无法获取向量维度，请检查模型配置或更换一个可用的嵌入模型");
        }

        // ★ 快照：把构造 EmbeddingModel 所需的 url/apiKey 也一并落到 KB 表，
        // 之后 ai_model 表怎么改都不影响该知识库的向量化/检索（配置创建即不可变）。
        // apiKey 明文落库与 ai_model.credential 现状一致，不引入新安全降级。
        String snapshotUrl = extractField(embeddingModel.getOptions(), "url");
        String snapshotApiKey = extractField(embeddingModel.getCredential(), "apiKey");

        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(UUID.randomUUID().toString().replace("-", ""));
        kb.setName(validate.getName());
        kb.setDescription(validate.getDescription());
        kb.setEmbeddingModelId(validate.getEmbeddingModelId());
        kb.setEmbeddingModelName(modelName);
        kb.setEmbeddingModel(embeddingModel.getName());
        kb.setEmbeddingModelUrl(snapshotUrl);
        kb.setEmbeddingModelApiKey(snapshotApiKey);
        kb.setDimension(dimension);
        kb.setStatus(validate.getStatus() == null ? 1 : validate.getStatus());
        kb.setDocCount(0);
        LocalDateTime now = LocalDateTime.now();
        kb.setCreatedAt(now);
        kb.setUpdatedAt(now);
        knowledgeBaseMapper.insert(kb);
        return toVo(kb);
    }

    /** 编辑知识库基本信息（名称、描述、状态），不修改 embeddingModel。 */
    @Override
    public void edit(KnowledgeBaseEditValidate validate) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(validate.getId());
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        kb.setName(validate.getName());
        if (validate.getDescription() != null) {
            kb.setDescription(validate.getDescription());
        }
        if (validate.getStatus() != null) {
            kb.setStatus(validate.getStatus());
        }
        kb.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);
    }

    /** 删除知识库：级联删除 chunks / parent_chunks / documents / questions + MinIO 对象。 */
    @Override
    public void delete(String id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            return;
        }
        // 1. 删除文档及其 MinIO 对象
        List<KnowledgeDocument> docs = knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getKbId, id));
        for (KnowledgeDocument doc : docs) {
            if (doc.getStorageUrl() != null && !doc.getStorageUrl().isBlank()) {
                minioService.remove(doc.getStorageUrl());
            }
        }
        // 2. 删除子块
        chunkMapper.delete(new LambdaQueryWrapper<ChunkEntity>().eq(ChunkEntity::getKbId, id));
        // 3. 删除父块
        parentChunkMapper.delete(new LambdaQueryWrapper<ParentChunkEntity>().eq(ParentChunkEntity::getKbId, id));
        // 4. 删除文档记录
        knowledgeDocumentMapper.delete(new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getKbId, id));
        // 5. 删除问题
        knowledgeQuestionMapper.delete(new LambdaQueryWrapper<KnowledgeQuestion>().eq(KnowledgeQuestion::getKbId, id));
        // 6. 删除知识库
        knowledgeBaseMapper.deleteById(id);
    }

    /**
     * 命中测试：复刻检索管线逻辑，支持 embedding / text / mix 三种模式。
     * mix 模式用 min-max 归一化 + 加权求和融合（非 RRF），向量默认权重 0.7。
     */
    @Override
    public List<HitTestVo> hitTest(HitTestValidate validate) {
        String mode = validate.getMode() == null || validate.getMode().isBlank() ? "mix" : validate.getMode().toLowerCase();
        double similarity = validate.getSimilarity() == null ? 0.6 : validate.getSimilarity();
        int topRank = validate.getTopRank() == null ? 5 : validate.getTopRank();

        List<Map<String, Object>> vectorHits = List.of();
        List<Map<String, Object>> kwHits = List.of();

        if ("embedding".equals(mode) || "mix".equals(mode)) {
            try {
                // 按 kb 绑定的 embedding 模型查询向量化（与入库向量维度一致，避免哑火）
                float[] vec = llmService.embed(validate.getQuery(), validate.getKbId());
                String pgVec = toPgVector(vec);
                vectorHits = chunkMapper.vectorSearch(validate.getKbId(), validate.getDocumentId(), pgVec, similarity, topRank * 3);
            } catch (Exception e) {
                log.warn("[HitTest] 向量检索失败: {}", e.getMessage());
            }
        }
        if ("text".equals(mode) || "mix".equals(mode)) {
            try {
                // query 先用 HanLP 在 Java 端预分词，再交 keywordSearch 内的 websearch_to_tsquery。
                String tsQuery = TsVectorGenerator.toTsQuery(validate.getQuery());
                List<Map<String, Object>> raw = chunkMapper.keywordSearch(validate.getKbId(), validate.getDocumentId(), tsQuery, topRank * 3);
                // ★ mix 模式下关键词路按 keywordThreshold 过滤弱命中：ts_rank_cd 没有分数下限，
                // 高频词（公司/员工）反复出现的长 chunk 会刷出虚高分，混入融合会污染排序
                // （与 Milvus BM25 的 drop_ratio_search 同思路：弱命中直接砍掉）。
                // text 单路模式不过滤，保持「全文检索不使用相似度阈值，按返回条数截断」的语义，
                // 让用户看到完整的 FTS 命中分布。
                if ("mix".equals(mode)) {
                    double kwThreshold = ragProperties.getRetrieval().getKeywordThreshold();
                    kwHits = filterByScore(raw, kwThreshold);
                } else {
                    kwHits = raw;
                }
            } catch (Exception e) {
                log.warn("[HitTest] 关键词检索失败: {}", e.getMessage());
            }
        }

        // 融合
        List<Map<String, Object>> fused;
        if ("embedding".equals(mode)) {
            fused = vectorHits;
        } else if ("text".equals(mode)) {
            fused = kwHits;
        } else {
            // ★ 不用 RRF：RRF 只看排名不看分数，中文场景下关键词路 ts_rank_cd 会被
            // 「公司」这类反复出现的长 chunk 刷出虚高排名，与向量路真实语义相关结果"双路命中"
            // 后，在 RRF 下反而压过只命中向量路的真正答案。改成 min-max 归一化 + 加权求和，
            // 向量权重高（语义比字面命中更可靠），并消除两路量纲差异。
            double vectorWeight = clampWeight(ragProperties.getRetrieval().getHybridVectorWeight());
            fused = weightedFuse(vectorHits, kwHits, vectorWeight);
        }

        return fused.stream().limit(topRank).map(row -> {
            HitTestVo vo = new HitTestVo();
            vo.setChunkId(asString(row.get("id")));
            vo.setContent(asString(row.get("content")));
            // mix 模式下 fused 按 rrf_score 排序，但 row 里残留的原始 score 来自两套不同量纲
            //（向量路是余弦相似度 0~1，关键词路是 ts_rank_cd ~0.01-0.5），直接展示会出现
            //「0.42 排在 0.36 后面」的错觉（排序对、数字错）。mix 统一返回 rrf_score，
            // 与排序口径一致；embedding/text 单路模式没有 rrf_score，回退原始 score。
            Object score = "mix".equals(mode) ? row.get("rrf_score") : row.get("score");
            if (score == null) {
                score = row.get("rrf_score");
            }
            vo.setScore(asDouble(score));
            vo.setMetadata(asString(row.get("metadata")));
            vo.setDocumentName(extractFileName(asString(row.get("metadata"))));
            return vo;
        }).collect(Collectors.toList());
    }


    /** 知识库实体 → VO 转换。 */
    private KnowledgeBaseVo toVo(KnowledgeBase kb) {
        KnowledgeBaseVo vo = new KnowledgeBaseVo();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setDescription(kb.getDescription());
        vo.setEmbeddingModel(kb.getEmbeddingModel());
        vo.setEmbeddingModelId(kb.getEmbeddingModelId());
        vo.setEmbeddingModelName(kb.getEmbeddingModelName());
        vo.setDimension(kb.getDimension());
        vo.setDocCount(kb.getDocCount());
        vo.setStatus(kb.getStatus());
        vo.setCreatedAt(kb.getCreatedAt());
        return vo;
    }

    /** 取 models 逗号分隔首项 */
    private String firstModel(String models) {
        if (models == null || models.isBlank()) return null;
        for (String p : models.split(",")) {
            if (p != null && !p.isBlank()) return p.trim();
        }
        return null;
    }

    /** 从 [{"field":"xxx","value":"yyy"}] 形态 JSON 中取值 */
    private String extractField(String json, String field) {
        if (json == null || json.isBlank() || field == null) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (!root.isArray()) return null;
            for (com.fasterxml.jackson.databind.JsonNode node : root) {
                com.fasterxml.jackson.databind.JsonNode f = node.path("field");
                com.fasterxml.jackson.databind.JsonNode v = node.path("value");
                if (field.equals(f.asText())) {
                    return v != null && !v.isNull() && !v.isMissingNode() ? v.asText() : null;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
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
     * @param vectorWeight 向量路权重，取值 [0,1]，建议 0.6~0.8，默认 0.7（yml: app.rag.retrieval.hybrid-vector-weight）
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

        List<Map<String, Object>> out = new ArrayList<>();
        fusedScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> {
                    Map<String, Object> row = new HashMap<>(payload.get(e.getKey()));
                    row.put("rrf_score", e.getValue());
                    out.add(row);
                });
        return out;
    }

    /**
     * 按原始 score 过滤：丢弃 score 小于 threshold 的命中。
     * 用于 mix 模式下关键词路的弱命中过滤（ts_rank_cd 无下限，高频词会刷虚高分）。
     * 阈值 ≤0 时不过滤（保留原行为）。
     */
    private List<Map<String, Object>> filterByScore(List<Map<String, Object>> hits, double threshold) {
        if (threshold <= 0 || hits == null || hits.isEmpty()) {
            return hits == null ? List.of() : hits;
        }
        List<Map<String, Object>> out = new ArrayList<>(hits.size());
        for (Map<String, Object> row : hits) {
            Double s = asDouble(row.get("score"));
            if (s != null && s >= threshold) {
                out.add(row);
            }
        }
        return out;
    }

    /** 对一路结果按原始 score 做 min-max 归一化到 [0,1]，返回 id -> 归一化分。 */
    private Map<String, Double> normalizeScores(List<Map<String, Object>> hits) {
        Map<String, Double> raw = new LinkedHashMap<>();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Map<String, Object> row : hits) {
            Double parsed = asDouble(row.get("score"));
            double s = parsed == null ? 0.0 : parsed;
            if (s < 0) s = 0;
            String id = String.valueOf(row.get("id"));
            // 同一 chunk 在一路内只出现一次，直接放；保留最大值防极端情况
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

    /** 从 metadata JSON 提取 file_name（展示用） */
    private String extractFileName(String metadata) {
        if (metadata == null || metadata.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(metadata);
            com.fasterxml.jackson.databind.JsonNode fn = node.path("file_name");
            return fn != null && !fn.isMissingNode() && !fn.isNull() ? fn.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** float[] → pgvector 文本格式 {@code [0.123456,...]}，Locale.ROOT 避免小数点变逗号。 */
    private static String toPgVector(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.6f", vec[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    /** Object 安全转 String，null 时返回 null。 */
    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** Object 安全转 Double，支持 Number / 字符串解析，null 或解析失败返回 null。 */
    private Double asDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    /** 把权重钳制到 [0,1]，防止 yml 配错超出范围导致负权重或溢出。 */
    private static double clampWeight(double w) {
        if (w < 0.0 || Double.isNaN(w)) return 0.0;
        if (w > 1.0) return 1.0;
        return w;
    }
}
