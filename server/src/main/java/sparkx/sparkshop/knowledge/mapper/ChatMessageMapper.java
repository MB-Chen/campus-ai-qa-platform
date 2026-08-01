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
import sparkx.sparkshop.knowledge.entity.ChatMessage;

import java.util.List;

/**
 * 聊天消息 Mapper。
 *
 * <p>★ jsonb 列（refs/stage_data/workflow_steps）的写：不能用 BaseMapper.insert（PG JDBC 把 String
 * 参数绑定为 varchar，不会隐式转 jsonb，报 "column is of type jsonb but expression is of type character
 * varying"），改用 {@link #insertJsonb} 原生 SQL + {@code CAST(? AS jsonb)}。
 *
 * <p>★ refs 列名（原 references）：references 是 PG 保留字，SELECT/INSERT 不加引号会报语法错，
 * 故 DB 列名改为 refs（实体字段名仍为 references）。
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /** 插入一条聊天消息（jsonb 列显式 CAST）。null 写 NULL。 */
    @Insert("INSERT INTO t_chat_message (session_id, role, content, refs, stage_data, workflow_steps, total_cost, total_tokens, created_at) " +
            "VALUES (#{sessionId}, #{role}, #{content}, " +
            "CAST(#{refs} AS jsonb), CAST(#{stageData} AS jsonb), CAST(#{workflowSteps} AS jsonb), " +
            "#{totalCost}, #{totalTokens}, #{createdAt})")
    int insertJsonb(@Param("sessionId") String sessionId,
                    @Param("role") String role,
                    @Param("content") String content,
                    @Param("refs") String refs,
                    @Param("stageData") String stageData,
                    @Param("workflowSteps") String workflowSteps,
                    @Param("totalCost") Long totalCost,
                    @Param("totalTokens") Integer totalTokens,
                    @Param("createdAt") java.time.LocalDateTime createdAt);

    /** 按会话查消息（按 id 升序）。refs 列是保留字别名，用 AS 映射到 references 字段。 */
    @Select("SELECT id, session_id, role, content, refs AS references, stage_data, workflow_steps, total_cost, total_tokens, created_at " +
            "FROM t_chat_message WHERE session_id = #{sessionId} ORDER BY id ASC")
    List<ChatMessage> selectBySession(@Param("sessionId") String sessionId);
}
