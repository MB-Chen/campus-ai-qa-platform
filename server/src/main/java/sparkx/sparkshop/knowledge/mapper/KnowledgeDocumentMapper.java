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
import org.apache.ibatis.annotations.Update;
import sparkx.sparkshop.knowledge.entity.KnowledgeDocument;

/**
 * 知识库文档 Mapper（MyBatis-Plus BaseMapper）。
 *
 * <p>{@code ingestion_summary} 为 jsonb 列，BaseMapper.updateById 按 varchar 传参会被 PG 拒绝，
 * 故单独提供原生 SQL + CAST（与 {@link ParentChunkMapper#insertJsonb} 同款范式）。
 */
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    /**
     * 更新文档的入库耗时统计（jsonb 列，显式 CAST）。
     *
     * @param id     文档 id
     * @param summary JSON 文本
     * @return 影响行数
     */
    @Update("UPDATE document SET ingestion_summary = CAST(#{summary} AS jsonb), updated_at = now() WHERE id = #{id}")
    int updateIngestionSummary(@Param("id") String id, @Param("summary") String summary);
}
