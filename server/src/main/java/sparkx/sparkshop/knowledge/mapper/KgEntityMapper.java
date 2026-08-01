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
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import sparkx.sparkshop.knowledge.entity.KgEntity;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱实体 Mapper（MyBatis-Plus BaseMapper + 原生 SQL）。
 *
 * <p>★ 向量 / tsv 列是 pgvector / tsvector 类型，MyBatis-Plus 不自动映射，
 * 向量化与检索走原生 SQL（范式完全对齐 {@link SampleQueryMapper}）：
 * <ul>
 *   <li>{@link #insertWithEmbedding}：INSERT 含 embedding / tsv / jsonb 元数据</li>
 *   <li>{@link #updateEmbeddingAndTsv}：回填向量（消歧后重新向量化用）</li>
 *   <li>{@link #updateNeo4jElementId}：写入 Neo4j 节点 elementId（可视化联动）</li>
 *   <li>{@link #vectorSearch}：1 - (embedding &lt;=&gt; ?::vector) AS score，按 kb_id 过滤</li>
 *   <li>{@link #keywordSearch}：ts_rank_cd 实体名/描述关键词检索</li>
 * </ul>
 *
 * <p>embedding 参数以 pgvector 文本格式传入 '[0.1,0.2,...]'，
 * 由 {@link sparkx.sparkshop.knowledge.ingest.SampleQueryIndexer#toPgVector} 构造（public static 可复用）。
 */
public interface KgEntityMapper extends BaseMapper<KgEntity> {

    /**
     * 插入实体向量记录（含 embedding + tsv + jsonb 元数据）。
     * metadata 形如 '{"aliases":["北京"],"source_doc_ids":["doc_xxx"]}'。
     */
    @Insert("INSERT INTO kg_entity (kb_id, name, canonical_name, entity_type, description, " +
            "aliases, source_doc_ids, source_parent_ids, neo4j_element_id, " +
            "embedding, tsv, vectorized, status, created_at, updated_at) VALUES (" +
            "#{kbId}, #{name}, #{canonicalName}, #{entityType}, #{description}, " +
            "#{aliases}, #{sourceDocIds}, #{sourceParentIds}, #{neo4jElementId}, " +
            "CAST(#{emb} AS vector), CAST(#{tsv} AS tsvector), #{vectorized}, #{status}, now(), now())")
    int insertWithEmbedding(@Param("kbId") String kbId,
                            @Param("name") String name,
                            @Param("canonicalName") String canonicalName,
                            @Param("entityType") String entityType,
                            @Param("description") String description,
                            @Param("aliases") String aliases,
                            @Param("sourceDocIds") String sourceDocIds,
                            @Param("sourceParentIds") String sourceParentIds,
                            @Param("neo4jElementId") String neo4jElementId,
                            @Param("emb") String embedding,
                            @Param("tsv") String tsv,
                            @Param("vectorized") int vectorized,
                            @Param("status") int status);

    /**
     * 回填向量 + tsv + 置 vectorized=1（重新向量化时调用）。
     */
    @Update("UPDATE kg_entity SET " +
            "embedding = CAST(#{emb} AS vector), " +
            "tsv = CAST(#{tsv} AS tsvector), " +
            "vectorized = 1, " +
            "updated_at = now() " +
            "WHERE id = #{id}")
    int updateEmbeddingAndTsv(@Param("id") Long id,
                              @Param("emb") String embedding,
                              @Param("tsv") String tsv);

    /**
     * 写入 Neo4j 节点 elementId（写图后回填，便于可视化联动）。
     */
    @Update("UPDATE kg_entity SET neo4j_element_id = #{elementId}, updated_at = now() WHERE id = #{id}")
    int updateNeo4jElementId(@Param("id") Long id, @Param("elementId") String elementId);

    /**
     * 向量检索：pgvector 余弦相似度（1 - cosine_distance）。
     * 按 kb_id 过滤，只在「已向量化 + 启用」的实体中检索，score ≥ threshold 才返回。
     *
     * @return [{id, name, canonical_name, entity_type, description, source_doc_ids, source_parent_ids, neo4j_element_id, score}]
     */
    @Select("SELECT id, name, canonical_name, entity_type, description, " +
            "source_doc_ids, source_parent_ids, neo4j_element_id, " +
            "1 - (embedding <=> CAST(#{emb} AS vector)) AS score " +
            "FROM kg_entity " +
            "WHERE kb_id = #{kbId} AND vectorized = 1 AND status = 1 " +
            "AND 1 - (embedding <=> CAST(#{emb} AS vector)) >= #{threshold} " +
            "ORDER BY embedding <=> CAST(#{emb} AS vector) " +
            "LIMIT #{limit}")
    List<Map<String, Object>> vectorSearch(@Param("kbId") String kbId,
                                            @Param("emb") String embedding,
                                            @Param("threshold") double threshold,
                                            @Param("limit") int limit);

    /**
     * 向量检索（文档级）：在 vectorSearch 基础上限定 doc_id（文档级隔离下同名实体跨文档各自独立）。
     *
     * @param documentId 文档 id，只召回该文档的实体
     */
    @Select("SELECT id, name, canonical_name, entity_type, description, " +
            "source_doc_ids, source_parent_ids, neo4j_element_id, " +
            "1 - (embedding <=> CAST(#{emb} AS vector)) AS score " +
            "FROM kg_entity " +
            "WHERE kb_id = #{kbId} AND doc_id = #{documentId} " +
            "AND vectorized = 1 AND status = 1 " +
            "AND 1 - (embedding <=> CAST(#{emb} AS vector)) >= #{threshold} " +
            "ORDER BY embedding <=> CAST(#{emb} AS vector) " +
            "LIMIT #{limit}")
    List<Map<String, Object>> vectorSearchByDoc(@Param("kbId") String kbId,
                                                  @Param("documentId") String documentId,
                                                  @Param("emb") String embedding,
                                                  @Param("threshold") double threshold,
                                                  @Param("limit") int limit);

    /**
     * 关键词兜底检索：向量漏网实体的「按名补链」。
     *
     * <p>★ 不用 tsvector —— PostgreSQL {@code simple} 全文配置不切分中文，
     * 「场景C」会被拆成只剩 "c"，导致中文短代号实体永远匹配不到。
     * 这里改用 {@code ILIKE} 直接对实体名 / 规范名 / 别名做（部分）匹配，
     * 中文可靠；精确名或别名命中提权到 0.99，部分匹配 0.90。
     *
     * @return 同 {@link #vectorSearch} 字段，score 为匹配置信度
     */
    @Select("SELECT id, name, canonical_name, entity_type, description, " +
            "source_doc_ids, source_parent_ids, neo4j_element_id, " +
            "CASE " +
            "  WHEN name = #{q} OR canonical_name = #{q} THEN 0.99 " +
            "  WHEN aliases::text ILIKE '%' || #{q} || '%' THEN 0.95 " +
            "  ELSE 0.90 END AS score " +
            "FROM kg_entity " +
            "WHERE kb_id = #{kbId} AND status = 1 " +
            "AND (name ILIKE '%' || #{q} || '%' " +
            "     OR canonical_name ILIKE '%' || #{q} || '%' " +
            "     OR aliases::text ILIKE '%' || #{q} || '%') " +
            "ORDER BY score DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> keywordSearch(@Param("kbId") String kbId,
                                             @Param("q") String queryText,
                                             @Param("limit") int limit);

    /**
     * 查询某 KB 下所有实体类型（去重），用于「列出所有X」类聚合问法的按类型兜底召回。
     */
    @Select("SELECT DISTINCT entity_type FROM kg_entity WHERE kb_id = #{kbId} AND status = 1")
    List<String> selectDistinctEntityTypes(@Param("kbId") String kbId);

    /**
     * 按实体类型全量召回（聚合 / 「列出所有X」兜底）。字段同 {@link #vectorSearch}。
     */
    @Select("SELECT id, name, canonical_name, entity_type, description, " +
            "source_doc_ids, source_parent_ids, neo4j_element_id, " +
            "1.0 AS score " +
            "FROM kg_entity " +
            "WHERE kb_id = #{kbId} AND entity_type = #{entityType} AND status = 1 " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectByType(@Param("kbId") String kbId,
                                           @Param("entityType") String entityType,
                                           @Param("limit") int limit);

    /**
     * 原子 upsert（并发安全）：INSERT，冲突时更新来源 + 时间。
     *
     * <p>解决并发抽取下 {@code upsertKgEntities} 的 TOCTOU 竞争（两线程同时判「不存在」→ 重复插入）
     * 与 {@code appendSource} 的 lost-update（read-modify-write JSON 数组）。
     * 依赖唯一约束 {@code uk_kg_entity_kb_doc_canonical (kb_id, doc_id, canonical_name)} 兜底。
     *
     * <p>冲突时把新来源 doc_id / parent_id 用 jsonb {@code ||} 原子并入现有数组（列是 text 类型，
     * 需 {@code ::jsonb} 转换后再 {@code ::text} 存回）。jsonb {@code ||} 合并不去重，
     * 重复来源元素可接受（仅来源标注多一项，不影响检索）。
     *
     * <p>{@code RETURNING id, (xmax = 0) AS inserted}：{@code xmax = 0} 是 PG 惯用法，
     * 区分本次是 INSERT（新增，需向量化）还是 UPDATE（冲突，原行可能已向量化，跳过）。
     * 无论哪种都返回行 id，供调用方判断。
     *
     * @return Map，含 {@code id}(Long) 与 {@code inserted}(Boolean：true=本次新增)
     */
    @org.apache.ibatis.annotations.Select("INSERT INTO kg_entity (" +
            "kb_id, doc_id, name, canonical_name, entity_type, description, " +
            "aliases, source_doc_ids, source_parent_ids, neo4j_element_id, " +
            "vectorized, status, created_at, updated_at" +
            ") VALUES (" +
            "#{kbId}, #{docId}, #{name}, #{canonicalName}, #{entityType}, #{description}, " +
            "#{aliases}, #{sourceDocIds}, #{sourceParentIds}, NULL, " +
            "0, 1, now(), now()" +
            ") ON CONFLICT (kb_id, doc_id, canonical_name) DO UPDATE SET " +
            // ★ SET 右值里的 source_doc_ids / source_parent_ids 必须加表名前缀：
            // DO UPDATE 作用域内，列名同时存在于「目标表已有行」和 excluded（待插入行），
            // 不限定会报 PSQLException: column reference "source_doc_ids" is ambiguous。
            "source_doc_ids = (COALESCE(kg_entity.source_doc_ids::jsonb, '[]'::jsonb) || #{sourceDocIds}::jsonb)::text, " +
            "source_parent_ids = (COALESCE(kg_entity.source_parent_ids::jsonb, '[]'::jsonb) || #{sourceParentIds}::jsonb)::text, " +
            "updated_at = now() " +
            "RETURNING id, (xmax = 0) AS inserted")
    Map<String, Object> upsertOnConflict(@Param("kbId") String kbId,
                          @Param("docId") String docId,
                          @Param("name") String name,
                          @Param("canonicalName") String canonicalName,
                          @Param("entityType") String entityType,
                          @Param("description") String description,
                          @Param("aliases") String aliases,
                          @Param("sourceDocIds") String sourceDocIds,
                          @Param("sourceParentIds") String sourceParentIds);
}
