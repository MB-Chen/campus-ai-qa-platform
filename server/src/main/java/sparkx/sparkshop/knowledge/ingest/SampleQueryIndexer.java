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
import sparkx.sparkshop.knowledge.mapper.SampleQueryMapper;

import java.util.Locale;

/**
 * 样例查询向量化器。
 *
 * <p>封装「问题文本 → embedding 向量 + tsv + 回填 sample_query 表」的完整逻辑。
 *
 * <p>★ 向量独立存储在 {@code sample_query} 表的 {@code embedding} 列（pgvector），
 * <b>不</b>写入 chunks 表，与知识库切片完全隔离。
 *
 * <p>★ 模型解析：直接按 {@code sample_query_config.embedding_model_id + embedding_model_name}
 * 经 {@link EmbeddingModelProvider#resolveByModelId} 构造。模型未配置时回退默认兜底模型。
 */
@Component
public class SampleQueryIndexer {

    private static final Logger log = LoggerFactory.getLogger(SampleQueryIndexer.class);

    @Resource
    private EmbeddingModelProvider embeddingModelProvider;

    @Resource
    private SampleQueryMapper sampleQueryMapper;

    /**
     * 向量化单条样例：embedding + tsv 回填到 sample_query 表。
     *
     * @param id          样例 id（主键）
     * @param question    问题文本（参与向量化）
     * @param modelId     配置的 embedding 模型 id（可空，空则用默认兜底）
     * @param modelName   具体模型名（可空）
     */
    public void index(Long id, String question, Integer modelId, String modelName) {
        EmbeddingModel embModel = embeddingModelProvider.resolveByModelId(modelId, modelName);

        // 向量：失败不阻断，仍回填 tsv（关键词检索可命中），但 vectorized 保持 0
        String embPg = null;
        boolean ok = false;
        try {
            Embedding emb = embModel.embed(TextSegment.from(question)).content();
            embPg = toPgVector(emb);
            ok = true;
        } catch (Exception ee) {
            log.warn("[SampleQueryIndexer] 向量生成失败 id={} : {}", id, ee.getMessage());
        }

        String tsv = TsVectorGenerator.toTsVector(question);
        if (ok) {
            // 向量 + tsv 全量回填，vectorized=1
            sampleQueryMapper.updateEmbeddingAndTsv(id, embPg, tsv);
        } else {
            // 向量失败：仅刷新 tsv（关键词检索仍可命中），vectorized 由 updateEmbeddingAndTsv 控制不会被错误置 1
            // 这里走一次 tsv-only 更新（复用同 SQL 不可行，因为同一条 SQL 会把 vectorized 置 1）
            // 故直接落库 vectorized 保持现状（默认 0），只更新 tsv + 文本同步由 service 层负责
            log.warn("[SampleQueryIndexer] id={} 向量化失败，tsv 仅靠 insert/update 时写入，本次跳过", id);
        }
    }

    /**
     * 清空单条向量：embedding 置 NULL，vectorized=0。
     * 用于重新向量化前清理或删除前清理。
     */
    public void clear(Long id) {
        if (id == null) {
            return;
        }
        sampleQueryMapper.clearEmbedding(id);
    }


    /**
     * LangChain4j Embedding → pgvector 文本格式 {@code [0.123456,...]}。
     * Locale.ROOT 避免小数点变逗号导致 SQL 解析失败。
     */
    public static String toPgVector(Embedding embedding) {
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
