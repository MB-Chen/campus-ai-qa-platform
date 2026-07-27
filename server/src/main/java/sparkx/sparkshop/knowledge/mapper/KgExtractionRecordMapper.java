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
import sparkx.sparkshop.knowledge.entity.KgExtractionRecord;

import java.util.Collection;
import java.util.List;

/**
 * 知识图谱抽取记录 Mapper（MyBatis-Plus BaseMapper）。
 *
 * <p>每个 (kb_id, document_id) 一条（DB 层 UNIQUE 约束）。
 */
public interface KgExtractionRecordMapper extends BaseMapper<KgExtractionRecord> {

    /**
     * 按 document_id 批量查最新抽取记录（文档列表回填抽取状态用）。
     * 原生 SQL 用 ROW_NUMBER() 取每个 document_id 最新一条，避免 LambdaQueryWrapper 的潜在映射问题。
     */
    @Select("<script>" +
            "SELECT * FROM kg_extraction_record " +
            "WHERE document_id IN " +
            "<foreach collection='documentIds' item='d' open='(' separator=',' close=')'>#{d}</foreach> " +
            "ORDER BY document_id, updated_at DESC" +
            "</script>")
    List<KgExtractionRecord> selectLatestByDocumentIds(@Param("documentIds") Collection<String> documentIds);
}
