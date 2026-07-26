// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.entity.KnowledgeBase;
import sparkx.sparkshop.knowledge.infra.EmbeddingModelProvider;
import sparkx.sparkshop.knowledge.infra.TsVectorGenerator;
import sparkx.sparkshop.knowledge.mapper.ChunkMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeBaseMapper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 问题向量化入库器。
 *
 * <p>封装「问题文本 → embedding 向量 + tsv + 写 chunks 表(type=question)」的完整逻辑，
 * 让手动新增 / 编辑 / Excel 批量导入三条路径与「AI 生成问题」
 * （{@code KnowledgeDocumentServiceImpl#doGenerateKbQuestions}）入库效果一致，
 * 保证手动录入的问题也能被向量 / 关键词检索召回。
 *
 * <p>★ 注意：问题切片的 {@code type} 字段写在 chunks 表的 metadata JSON 里
 * （{@code metadata.type = "question"}），不是独立列。所有「原文切片列表」查询
 * 都显式排除 {@code type='question'}，所以问题切片不会污染切片管理列表，只参与检索。
 *
 * <p>★ 当前 AI 生成路径未走本类（已有实现已验证可用，最小改动原则不动它），
 * 后续如需统一可让其改为调用本类。
 */
@Component
public class QuestionIndexer {

    private static final Logger log = LoggerFactory.getLogger(QuestionIndexer.class);

    @Resource
    private EmbeddingModelProvider embeddingModelProvider;

    @Resource
    private ChunkMapper chunkMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    /** 局部 ObjectMapper（不暴露为 Bean，避免破坏全局 Jackson 自动配置） */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 新增问题：embedding + 写 chunks 表（type=question）。
     *
     * @param kbId          知识库 id（必填，决定 embedding 模型）
     * @param content       问题文本（必填）
     * @param documentId    来源文档 id（手动 / 导入可传 null）
     * @param sourceChunkId 来源原文 chunk id（手动 / 导入可传 null）
     * @return 新生成的 chunkId（{@code c_<uuid>}）
     */
    public String index(String kbId, String content, String documentId, String sourceChunkId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException("知识库不存在: " + kbId);
        }
        assertDimensionConsistent(kb);

        EmbeddingModel embModel = embeddingModelProvider.resolve(kbId);
        String qChunkId = "c_" + UUID.randomUUID().toString().replace("-", "");

        // 问题向量：失败不阻断，仍入库（embPg=null），关键词检索可命中
        String embPg = null;
        try {
            Embedding emb = embModel.embed(TextSegment.from(content)).content();
            embPg = toPgVector(emb);
        } catch (Exception ee) {
            log.warn("[QuestionIndexer] 问题向量生成失败 kb={} chunk={} : {}", kbId, qChunkId, ee.getMessage());
        }

        Map<String, Object> metaMap = new LinkedHashMap<>();
        metaMap.put("type", "question");
        if (sourceChunkId != null && !sourceChunkId.isBlank()) {
            metaMap.put("source_chunk_id", sourceChunkId);
        }
        if (documentId != null && !documentId.isBlank()) {
            metaMap.put("document_id", documentId);
        }
        metaMap.put("kb_id", kbId);
        metaMap.put("file_name", "");
        String meta = metadataToJson(metaMap);

        chunkMapper.insertWithEmbedding(qChunkId, kbId, content, embPg,
                TsVectorGenerator.toTsVector(content), meta);
        return qChunkId;
    }

    /**
     * 编辑问题：重算 content 的向量 + tsv，更新已存在的 chunk。
     * 不改 metadata（来源 / 文档归属保持不变）。
     */
    public void reindex(String chunkId, String kbId, String newContent) {
        if (chunkId == null || chunkId.isBlank()) {
            return;
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb != null) {
            assertDimensionConsistent(kb);
        }
        EmbeddingModel embModel = embeddingModelProvider.resolve(kbId);

        // 1) 先刷新文本 + tsv（保证关键词索引即时生效）
        chunkMapper.updateContentAndTsv(chunkId, newContent, TsVectorGenerator.toTsVector(newContent));
        // 2) 再补向量（失败则保留 NULL 向量，关键词检索仍可命中）
        try {
            Embedding emb = embModel.embed(TextSegment.from(newContent)).content();
            chunkMapper.updateEmbedding(chunkId, toPgVector(emb));
        } catch (Exception ee) {
            log.warn("[QuestionIndexer] 问题向量更新失败 chunk={} : {}", chunkId, ee.getMessage());
            // embedding 列置 NULL，避免陈旧向量污染检索（保留文本可被关键词命中）
            chunkMapper.updateEmbedding(chunkId, null);
        }
    }

    /**
     * 删除问题：删 chunk（向量 / tsv / 文本随之消失）。
     */
    public void unindex(String chunkId) {
        if (chunkId == null || chunkId.isBlank()) {
            return;
        }
        chunkMapper.deleteById(chunkId);
    }


    /** 维度一致性校验：与 AI 生成路径保持一致，防止换过 embedding 模型后写坏维度向量。 */
    private void assertDimensionConsistent(KnowledgeBase kb) {
        if (kb == null || kb.getEmbeddingModelId() == null) {
            return;
        }
        int actual = embeddingModelProvider.probeDimension(
                kb.getEmbeddingModelId(), kb.getEmbeddingModelName());
        if (actual <= 0) {
            return;
        }
        Integer recorded = kb.getDimension();
        if (recorded != null && recorded > 0 && actual != recorded) {
            throw new BusinessException("向量化模型维度(" + actual
                    + ")与知识库维度(" + recorded + ")不一致，请检查嵌入模型配置");
        }
    }

    /** metadata Map → JSON 文本（失败兜底为 "{}"）。 */
    private String metadataToJson(Map<String, Object> metaMap) {
        try {
            return mapper.writeValueAsString(metaMap);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * LangChain4j Embedding → pgvector 文本格式 {@code [0.123456,...]}。
     * Locale.ROOT 避免小数点变逗号导致 SQL 解析失败。
     */
    private static String toPgVector(Embedding embedding) {
        float[] vec = embedding.vector();
        StringBuilder sb = new StringBuilder(vec.length * 9);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.6f", vec[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
