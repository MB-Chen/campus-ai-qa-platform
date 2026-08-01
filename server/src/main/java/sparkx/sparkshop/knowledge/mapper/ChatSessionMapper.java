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
import sparkx.sparkshop.knowledge.entity.ChatSession;

/**
 * 聊天会话 Mapper。分页 / 过滤用 MyBatis-Plus 的 LambdaQueryWrapper + selectPage。
 *
 * <p>★ jsonb 列（agent_config）的写：不能用 BaseMapper.insert（PG JDBC 把 String 参数绑定为
 * varchar，不会隐式转 jsonb，报 "column is of type jsonb but expression is of type character
 * varying"），改用 {@link #insertJsonb} 原生 SQL + {@code CAST(? AS jsonb)}。与 ChatMessageMapper
 * 的 insertJsonb 同一处理模式。
 */
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /** 插入一条会话（agent_config jsonb 列显式 CAST）。null 写 NULL。 */
    @Insert("INSERT INTO t_chat_session (id, admin_id, title, description, source, kind, agent_id, agent_config, created_at, updated_at) " +
            "VALUES (#{id}, #{adminId}, #{title}, #{description}, #{source}, #{kind}, #{agentId}, " +
            "CAST(#{agentConfig} AS jsonb), #{createdAt}, #{updatedAt})")
    int insertJsonb(@Param("id") String id,
                    @Param("adminId") Long adminId,
                    @Param("title") String title,
                    @Param("description") String description,
                    @Param("source") String source,
                    @Param("kind") String kind,
                    @Param("agentId") String agentId,
                    @Param("agentConfig") String agentConfig,
                    @Param("createdAt") java.time.LocalDateTime createdAt,
                    @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
