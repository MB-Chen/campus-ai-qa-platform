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

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.infra.EmbeddingModelProvider;
import sparkx.sparkshop.knowledge.infra.TsVectorGenerator;
import sparkx.sparkshop.knowledge.mapper.KgEntityMapper;

/**
 * 知识图谱实体向量化器。
 *
 * <p>封装「实体名+描述 → embedding 向量 + tsv + 回填 kg_entity 表」的完整逻辑。
 * 范式完全对齐 {@link SampleQueryIndexer}。
 *
 * <p>★ 向量独立存储在 {@code kg_entity.embedding} 列（pgvector，与 chunks 同库，对齐 WeKnora 架构）。
 *
 * <p>★ 模型解析：直接按 {@code kg_config.embedding_model_id + embedding_model_name}
 * 经 {@link EmbeddingModelProvider#resolveByModelId} 构造（不绑 KB，因为实体的向量化模型由全局配置决定）。
 */
@Component
public class KgEntityIndexer {

    private static final Logger log = LoggerFactory.getLogger(KgEntityIndexer.class);

    @Resource
    private EmbeddingModelProvider embeddingModelProvider;

    @Resource
    private KgEntityMapper kgEntityMapper;

    /**
     * 向量化单条实体：embedding + tsv 回填到 kg_entity 表。
     *
     * @param id          kg_entity 主键
     * @param name        实体规范名（参与向量化）
     * @param description 实体描述（参与向量化，可空）
     * @param modelId     全局配置的 embedding 模型 id（可空，空则用默认兜底）
     * @param modelName   具体模型名（可空）
     */
    public void index(Long id, String name, String description, Integer modelId, String modelName) {
        EmbeddingModel embModel = embeddingModelProvider.resolveByModelId(modelId, modelName);

        // 向量化文本 = 规范名 + 描述（描述为空时仅用规范名）
        String embText = (description == null || description.isBlank())
                ? name
                : name + " " + description;

        String embPg = null;
        boolean ok = false;
        try {
            Embedding emb = embModel.embed(TextSegment.from(embText)).content();
            embPg = toPgVector(emb);
            ok = true;
        } catch (Exception ee) {
            log.warn("[KgEntityIndexer] 向量生成失败 id={} name={}: {}", id, name, ee.getMessage());
        }

        String tsv = TsVectorGenerator.toTsVector(name + " " + (description == null ? "" : description));
        if (ok) {
            kgEntityMapper.updateEmbeddingAndTsv(id, embPg, tsv);
        } else {
            // 向量失败：仅刷新 tsv 由调用方在 insert 时已写入，这里跳过（vectorized 保持 0，关键词检索仍可命中）
            log.warn("[KgEntityIndexer] id={} 向量化失败，tsv 已在 insert 时写入，本次跳过 embedding 回填", id);
        }
    }

    /**
     * 清空单条实体向量：embedding 置 NULL，vectorized=0（删除/重新向量化前用）。
     * 当前 kg_entity 不缓存实体向量复用，此方法预留。
     */
    public void clear(Long id) {
        // updateEmbeddingAndTsv 会置 vectorized=1，清空需单独 SQL，这里暂不实现（删除走 DB delete）
        log.debug("[KgEntityIndexer] clear 暂未实现，删除请直接走 DB delete");
    }


    /**
     * LangChain4j Embedding → pgvector 文本格式 {@code [0.123456,...]}。
     * Locale.ROOT 避免小数点变逗号导致 SQL 解析失败。
     * 与 {@link SampleQueryIndexer#toPgVector} 实现一致（两处都保留，便于独立演进）。
     */
    public static String toPgVector(Embedding embedding) {
        float[] vec = embedding.vector();
        StringBuilder sb = new StringBuilder(vec.length * 9);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(java.util.Locale.ROOT, "%.6f", vec[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
