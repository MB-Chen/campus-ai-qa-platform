// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import sparkx.sparkshop.knowledge.entity.SampleQuery;

import java.util.List;
import java.util.Map;

/**
 * 样例查询 Mapper（MyBatis-Plus BaseMapper + 原生 SQL）。
 *
 * <p>★ 向量 / tsv 列是 pgvector / tsvector 类型，MyBatis-Plus 不自动映射，
 * 向量化与检索走原生 SQL：
 * <ul>
 *   <li>{@link #updateEmbeddingAndTsv}：UPDATE embedding / tsv / vectorized / updated_at</li>
 *   <li>{@link #clearEmbedding}：置空 embedding + vectorized=0（重新向量化前的清理）</li>
 *   <li>{@link #vectorSearch}：1 - (embedding &lt;=&gt; ?::vector) AS score，只查已向量化 + 启用</li>
 *   <li>{@link #keywordSearch}：ts_rank_cd(tsv, websearch_to_tsquery('simple', ?))</li>
 * </ul>
 *
 * <p>embedding 参数以 pgvector 文本格式传入，形如 {@code '[0.1,0.2,...]'}，由
 * {@link sparkx.sparkshop.knowledge.ingest.SampleQueryIndexer#toPgVector} 构造；
 * tsv 参数为 PG tsvector 文本格式，由
 * {@link sparkx.sparkshop.knowledge.infra.TsVectorGenerator#toTsVector} 构造。
 */
public interface SampleQueryMapper extends BaseMapper<SampleQuery> {

    /**
     * 回填向量 + tsv + 置 vectorized=1（向量化后调用）。
     * emb 形如 '[0.1,0.2,...]'，tsv 形如 'word:1,2 word2:3'。
     */
    @Update("UPDATE sample_query SET " +
            "embedding = CAST(#{emb} AS vector), " +
            "tsv = CAST(#{tsv} AS tsvector), " +
            "vectorized = 1, " +
            "updated_at = now() " +
            "WHERE id = #{id}")
    int updateEmbeddingAndTsv(@Param("id") Long id,
                              @Param("emb") String embedding,
                              @Param("tsv") String tsv);

    /**
     * 清空单条向量：embedding 置 NULL，vectorized=0（删除/重新向量化前用）。
     * 不动 question / answer / tsv（tsv 仍可用于关键词检索）。
     */
    @Update("UPDATE sample_query SET embedding = NULL, vectorized = 0, updated_at = now() WHERE id = #{id}")
    int clearEmbedding(@Param("id") Long id);

    /**
     * 向量检索：pgvector 余弦距离（1 - cosine_distance）。
     * emb 为 pgvector 文本 '[0.1,0.2,...]'。
     * 只在「已向量化 + 启用」的记录中检索，score 高于 threshold 才返回。
     *
     * @return [{id, question, answer, score}]
     */
    @Select("SELECT id, question, answer, " +
            "1 - (embedding <=> CAST(#{emb} AS vector)) AS score " +
            "FROM sample_query " +
            "WHERE vectorized = 1 AND status = 1 " +
            "AND 1 - (embedding <=> CAST(#{emb} AS vector)) >= #{threshold} " +
            "ORDER BY embedding <=> CAST(#{emb} AS vector) " +
            "LIMIT #{limit}")
    List<Map<String, Object>> vectorSearch(@Param("emb") String embedding,
                                            @Param("threshold") double threshold,
                                            @Param("limit") int limit);

    /**
     * 关键词检索：PostgreSQL FTS（ts_rank_cd）。
     * 查询串由调用方先用 {@link sparkx.sparkshop.knowledge.infra.TsVectorGenerator#toTsQuery}
     * 在 Java 端预分词，再交 websearch_to_tsquery('simple', ?) 做整词等值匹配。
     *
     * @return [{id, question, answer, score}]
     */
    @Select("SELECT id, question, answer, " +
            "ts_rank_cd(tsv, websearch_to_tsquery('simple', #{q})) AS score " +
            "FROM sample_query " +
            "WHERE status = 1 AND tsv @@ websearch_to_tsquery('simple', #{q}) " +
            "ORDER BY score DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> keywordSearch(@Param("q") String queryText,
                                             @Param("limit") int limit);
}
