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
import sparkx.sparkshop.knowledge.entity.ParentChunkEntity;

import java.util.List;

/**
 * 父块 Mapper（移植自 sparkxV2 ParentChunkRepository，改为 MyBatis-Plus）。
 *
 * 注意：metadata 列为 jsonb，MyBatis-Plus 自动映射会按 varchar 传参导致 PG 拒绝，
 * 故入库走原生 SQL + CAST(#{metadata} AS jsonb)，与 {@link ChunkMapper} 保持一致。
 */
public interface ParentChunkMapper extends BaseMapper<ParentChunkEntity> {

    /**
     * 原生插入父块（metadata 为 jsonb 列，需显式 CAST，不能用 BaseMapper.insert）。
     * metadata 参数为 JSON 文本。
     */
    @Insert("INSERT INTO parent_chunks (id, kb_id, content, metadata) " +
            "VALUES (#{id}, #{kbId}, #{content}, CAST(#{metadata} AS jsonb))")
    int insertJsonb(@Param("id") String id,
                    @Param("kbId") String kbId,
                    @Param("content") String content,
                    @Param("metadata") String metadata);

    /** 按多个 parentId 批量查询父块（供 ParentChildRetriever 展开用） */
    @Select({
            "<script>",
            "SELECT * FROM parent_chunks WHERE id IN ",
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>",
            "</script>"
    })
    List<ParentChunkEntity> findByIds(@Param("ids") List<String> ids);

    /**
     * 按知识库 + metadata.file_name 删除父块（jsonb 列，需用 ->> 提取，不能 LIKE varchar）。
     * fileNamePattern 可带 % 通配（默认模糊匹配）。
     */
    @Delete("DELETE FROM parent_chunks WHERE kb_id = #{kbId} AND metadata->>'file_name' LIKE #{fileNamePattern}")
    int deleteByKbAndFileName(@Param("kbId") String kbId,
                              @Param("fileNamePattern") String fileNamePattern);

    /**
     * 按文档 id 删除该文档的全部父块（metadata->>'document_id' 精确匹配）。
     * 用于「重新向量化」前清理旧父块，保证先删后建。
     */
    @Delete("DELETE FROM parent_chunks WHERE metadata->>'document_id' = #{documentId}")
    int deleteByDocumentId(@Param("documentId") String documentId);

    /**
     * 按文档 id 查询该文档的全部父块（metadata->>'document_id' 精确匹配）。
     * 用于「知识图谱抽取」遍历父块抽实体/关系。
     * 按创建时间升序，保证抽取顺序稳定。
     */
    @Select("SELECT id, kb_id, content, metadata, created_at FROM parent_chunks " +
            "WHERE metadata->>'document_id' = #{documentId} ORDER BY created_at ASC")
    List<ParentChunkEntity> selectByDocumentId(@Param("documentId") String documentId);
}
