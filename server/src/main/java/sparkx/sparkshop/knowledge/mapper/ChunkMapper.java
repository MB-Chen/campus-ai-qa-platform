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
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import sparkx.sparkshop.knowledge.entity.ChunkEntity;

import java.util.List;
import java.util.Map;

/**
 * 子块 Mapper（移植自 sparkxV2 ChunkRepository，改为 MyBatis-Plus）。
 *
 * 向量入库走原生 SQL（含 pgvector 列），不走 MyBatis-Plus 自动映射：
 *  - {@link #insertWithEmbedding}：INSERT ... CAST(#{emb} AS vector), CAST(#{tsv} AS tsvector), CAST(#{metadata} AS jsonb)
 *  - {@link #vectorSearch}：1 - (embedding &lt;=&gt; ?::vector) AS score
 *  - {@link #keywordSearch}：ts_rank_cd(tsv, websearch_to_tsquery('simple', ?))
 *  - {@link #countByKb}：按知识库统计子块数
 *
 * 注意：embedding 参数以 pgvector 文本格式传入，形如 '[0.1,0.2,...]'，
 * 由调用方（HybridContentRetriever.toPgVector）构造；
 * tsv 参数为 PG tsvector 文本格式 'word:1,2 word2:3'，由
 * {@link sparkx.sparkshop.knowledge.infra.TsVectorGenerator#toTsVector} 构造
 * （Java 端 HanLP 预分词，DB 端 trg_tsv 触发器已禁用）。
 */
public interface ChunkMapper extends BaseMapper<ChunkEntity> {

    /**
     * 原生插入子块（含 embedding 向量列 + tsv 全文检索列）。
     * emb 形如 '[0.1,0.2,...]'，tsv 形如 'word:1,2 word2:3'，metadata 为 JSON 文本。
     */
    @Insert("INSERT INTO chunks (id, kb_id, content, embedding, tsv, metadata) " +
            "VALUES (#{id}, #{kbId}, #{content}, CAST(#{emb} AS vector), CAST(#{tsv} AS tsvector), CAST(#{metadata} AS jsonb))")
    int insertWithEmbedding(@Param("id") String id,
                            @Param("kbId") String kbId,
                            @Param("content") String content,
                            @Param("emb") String embedding,
                            @Param("tsv") String tsv,
                            @Param("metadata") String metadata);

    /** 按知识库统计子块数 */
    @Select("SELECT COUNT(*) FROM chunks WHERE kb_id = #{kbId}")
    long countByKb(@Param("kbId") String kbId);

    /**
     * 向量检索：pgvector 余弦距离（1 - cosine_distance）。
     * emb 为 pgvector 文本 '[0.1,0.2,...]'。返回字段：id/content/score/metadata。
     * ★ 必须按 kb_id 过滤，否则会跨知识库串数据（多租户隔离）。
     * ★ documentId 可选：非空时只在指定文档内检索（用于「按文档做召回测试」）。
     */
    @Select("<script>" +
            "SELECT id, content, 1 - (embedding &lt;=&gt; CAST(#{emb} AS vector)) AS score, metadata " +
            "FROM chunks " +
            "WHERE kb_id = #{kbId} " +
            "<if test='documentId != null and documentId != \"\"'>" +
            "AND metadata-&gt;&gt;'document_id' = #{documentId} " +
            "</if>" +
            "AND 1 - (embedding &lt;=&gt; CAST(#{emb} AS vector)) &gt;= #{threshold} " +
            "ORDER BY embedding &lt;=&gt; CAST(#{emb} AS vector) " +
            "LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> vectorSearch(@Param("kbId") String kbId,
                                           @Param("documentId") String documentId,
                                           @Param("emb") String embedding,
                                           @Param("threshold") double threshold,
                                           @Param("limit") int limit);

    /**
     * 关键词检索：PostgreSQL FTS（ts_rank_cd）。
     * 查询串由调用方先用 {@link sparkx.sparkshop.knowledge.infra.TsVectorGenerator#toTsQuery}
     * 在 Java 端 HanLP 预分词，再交 websearch_to_tsquery('simple', ?) 做整词等值匹配。
     * 返回字段：id/content/score/metadata。
     * ★ 必须按 kb_id 过滤，否则会跨知识库串数据（多租户隔离）。
     * ★ documentId 可选：非空时只在指定文档内检索（用于「按文档做召回测试」）。
     */
    @Select("<script>" +
            "SELECT id, content, ts_rank_cd(tsv, websearch_to_tsquery('simple', #{q})) AS score, metadata " +
            "FROM chunks " +
            "WHERE kb_id = #{kbId} " +
            "<if test='documentId != null and documentId != \"\"'>" +
            "AND metadata-&gt;&gt;'document_id' = #{documentId} " +
            "</if>" +
            "AND tsv @@ websearch_to_tsquery('simple', #{q}) " +
            "ORDER BY score DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> keywordSearch(@Param("kbId") String kbId,
                                            @Param("documentId") String documentId,
                                            @Param("q") String queryText,
                                            @Param("limit") int limit);

    /**
     * 按文档 id 精确查询子块（metadata->>'document_id'，JSONB 提取）。
     * 用于「按文档查看切片」的分页列表。按 created_at 升序保持切片顺序。
     *
     * <p>★ 排除问题切片（metadata.type='question'）：问题切片是为提升召回写入的辅助数据，
     * 不在切片管理列表中展示，只参与检索。旧数据无 type 字段，按原文切片处理（IS NULL）。
     */
    @Select("SELECT id, kb_id, content, metadata, created_at FROM chunks " +
            "WHERE metadata->>'document_id' = #{documentId} " +
            "AND (metadata->>'type' IS NULL OR metadata->>'type' <> 'question') " +
            "ORDER BY created_at ASC " +
            "LIMIT #{size} OFFSET #{offset}")
    List<ChunkEntity> selectByDocumentId(@Param("documentId") String documentId,
                                         @Param("offset") long offset,
                                         @Param("size") int size);

    /**
     * 按文档 id 统计原文子块数（排除问题切片，与 {@link #selectByDocumentId} 口径一致）。
     */
    @Select("SELECT COUNT(*) FROM chunks " +
            "WHERE metadata->>'document_id' = #{documentId} " +
            "AND (metadata->>'type' IS NULL OR metadata->>'type' <> 'question')")
    long countByDocumentId(@Param("documentId") String documentId);

    /**
     * 按文档 id 查询全部原文切片（不含问题切片），用于「生成问题」时遍历每个原文分块。
     * 不分页，调用方按需控制文档大小。
     */
    @Select("SELECT id, kb_id, content, metadata, created_at FROM chunks " +
            "WHERE metadata->>'document_id' = #{documentId} " +
            "AND (metadata->>'type' IS NULL OR metadata->>'type' <> 'question') " +
            "ORDER BY created_at ASC")
    List<ChunkEntity> selectContentByDocument(@Param("documentId") String documentId);

    /**
     * 按 id 列表批量查询切片（用于检索命中问题切片后回溯原文切片）。
     * 仅返回 id/content/metadata，向量/tsv 列由 DB 端裁剪。
     */
    @Select("<script>" +
            "SELECT id, kb_id, content, metadata, created_at FROM chunks " +
            "WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<ChunkEntity> selectByIds(@Param("ids") List<String> ids);

    /**
     * 只插入文本切片（不含 embedding 向量，但带 tsv 全文检索列）。
     * 用于「保存切片结果」阶段：先存文本，向量留给用户主动向量化时补。
     * tsv 由调用方 {@link sparkx.sparkshop.knowledge.infra.TsVectorGenerator#toTsVector}
     * 生成，保证即便没有向量也能被关键词检索命中。
     * embedding 列允许为空（由调用方保证表结构允许）。
     */
    @Insert("INSERT INTO chunks (id, kb_id, content, tsv, metadata) " +
            "VALUES (#{id}, #{kbId}, #{content}, CAST(#{tsv} AS tsvector), CAST(#{metadata} AS jsonb))")
    int insertTextOnly(@Param("id") String id,
                       @Param("kbId") String kbId,
                       @Param("content") String content,
                       @Param("tsv") String tsv,
                       @Param("metadata") String metadata);

    /**
     * 回填向量到已存在的切片（按 id 更新 embedding 列）。
     * 用于「主动向量化」阶段对已落库的文本切片补向量。
     * 不动 content/tsv：重新向量化只补向量，文本/关键词索引保持不变。
     */
    @org.apache.ibatis.annotations.Update("UPDATE chunks SET embedding = CAST(#{emb} AS vector) " +
            "WHERE id = #{id}")
    int updateEmbedding(@Param("id") String id, @Param("emb") String embedding);

    /**
     * 同步更新 content 和 tsv（按 id）。
     * 用于「段落编辑」：trigger 已禁用，编辑文本后必须同步刷新 tsv，否则关键词索引陈旧。
     * tsv 由调用方 {@link sparkx.sparkshop.knowledge.infra.TsVectorGenerator#toTsVector} 生成。
     */
    @org.apache.ibatis.annotations.Update("UPDATE chunks SET content = #{content}, tsv = CAST(#{tsv} AS tsvector) " +
            "WHERE id = #{id}")
    int updateContentAndTsv(@Param("id") String id,
                            @Param("content") String content,
                            @Param("tsv") String tsv);

    /**
     * 清空某文档全部切片的旧向量（embedding 置 NULL，保留 content）。
     * 用于「重新向量化」前清理上一次失败的/陈旧的向量，保证先清后建，
     * 避免新旧维度混杂或残留坏向量污染检索。
     */
    @org.apache.ibatis.annotations.Update("UPDATE chunks SET embedding = NULL " +
            "WHERE metadata->>'document_id' = #{documentId}")
    int clearEmbeddingByDocumentId(@Param("documentId") String documentId);

    /**
     * 按知识库 + metadata.file_name 删除子块（jsonb 列，需用 ->> 提取，不能 LIKE varchar）。
     * fileNamePattern 可带 % 通配（默认模糊匹配）。
     */
    @Delete("DELETE FROM chunks WHERE kb_id = #{kbId} AND metadata->>'file_name' LIKE #{fileNamePattern}")
    int deleteByKbAndFileName(@Param("kbId") String kbId,
                              @Param("fileNamePattern") String fileNamePattern);

    /**
     * 按文档 id 删除该文档的全部子块（metadata->>'document_id' 精确匹配）。
     * 用于「重新向量化」前清理旧切片，保证先删后建。
     */
    @Delete("DELETE FROM chunks WHERE metadata->>'document_id' = #{documentId}")
    int deleteByDocumentId(@Param("documentId") String documentId);

    /**
     * 按文档 id 查询该文档的全部子块 id（metadata->>'document_id' 精确匹配）。
     * 用于删除/重传入库前收集 chunk id 列表，供图谱清理时精确移除该文档在实体节点 chunk_ids 中的贡献。
     */
    @Select("SELECT id FROM chunks WHERE metadata->>'document_id' = #{documentId}")
    List<String> selectIdsByDocumentId(@Param("documentId") String documentId);

    /**
     * 按知识库 + metadata.file_name 查询原文子块（排除问题切片，按 created_at 升序保持切片顺序）。
     * 用于「按文档查看切片」列表。fileNamePattern 可带 % 通配。
     */
    @Select("SELECT id, kb_id, content, metadata, created_at FROM chunks " +
            "WHERE kb_id = #{kbId} AND metadata->>'file_name' LIKE #{fileNamePattern} " +
            "AND (metadata->>'type' IS NULL OR metadata->>'type' <> 'question') " +
            "ORDER BY created_at ASC")
    List<ChunkEntity> selectByKbAndFileName(@Param("kbId") String kbId,
                                            @Param("fileNamePattern") String fileNamePattern);

    /**
     * 按父块 id 查询其下所有子块 id 列表（metadata->>'parentId' 匹配，驼峰写入）。
     * 用于「知识图谱抽取」时关联实体到子块（Entity.chunk_ids）。
     */
    @Select("SELECT id FROM chunks WHERE metadata->>'parentId' = #{parentId}")
    List<String> selectIdsByParentId(@Param("parentId") String parentId);
}
