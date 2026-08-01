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
import sparkx.sparkshop.knowledge.entity.AgentTestMessage;

/**
 * 智能体测试对话消息 Mapper。
 *
 * <p>★ jsonb 列（refs/stage_data/stage_timings）不能用 BaseMapper.insert 直接写 String：
 * PG JDBC 把 String 参数绑定为 varchar，不会隐式转 jsonb，会报
 * "column is of type jsonb but expression is of type character varying"。
 * 故用 {@link #insertJsonb} 原生 SQL + {@code CAST(? AS jsonb)}，对齐
 * {@code KnowledgeDocumentMapper.updateIngestionSummary} 范式。
 */
public interface AgentTestMessageMapper extends BaseMapper<AgentTestMessage> {

    /**
     * 插入一条测试消息（jsonb 列显式 CAST）。
     * refs/stageData/stageTimings 为 null 时写 NULL（CAST(NULL AS jsonb) 仍是 NULL）。
     */
    @Insert("INSERT INTO t_agent_test_message (session_id, role, content, refs, stage_data, stage_timings, total_cost, created_at) " +
            "VALUES (#{sessionId}, #{role}, #{content}, " +
            "CAST(#{refs} AS jsonb), CAST(#{stageData} AS jsonb), CAST(#{stageTimings} AS jsonb), " +
            "#{totalCost}, #{createdAt})")
    int insertJsonb(@Param("sessionId") String sessionId,
                    @Param("role") String role,
                    @Param("content") String content,
                    @Param("refs") String refs,
                    @Param("stageData") String stageData,
                    @Param("stageTimings") String stageTimings,
                    @Param("totalCost") Long totalCost,
                    @Param("createdAt") java.time.LocalDateTime createdAt);
}
