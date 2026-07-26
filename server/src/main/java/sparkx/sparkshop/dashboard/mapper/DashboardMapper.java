// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 首页统计 Mapper（原生 SQL 聚合）
 */
@Mapper
public interface DashboardMapper {

    /**
     * 会话数趋势（按时段分组计数）
     */
    @Select("""
            SELECT to_char(create_time, #{fmt}) AS bucket, count(*) AS cnt
            FROM im_conversation
            WHERE create_time >= #{start} AND create_time < #{end}
            GROUP BY bucket ORDER BY bucket
            """)
    List<Map<String, Object>> sessionTrend(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end,
                                           @Param("fmt") String fmt);

    /**
     * 消息数趋势
     */
    @Select("""
            SELECT to_char(create_time, #{fmt}) AS bucket, count(*) AS cnt
            FROM im_message
            WHERE create_time >= #{start} AND create_time < #{end}
            GROUP BY bucket ORDER BY bucket
            """)
    List<Map<String, Object>> messageTrend(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end,
                                           @Param("fmt") String fmt);

    /**
     * 活跃用户趋势（去重 visitor_id）
     */
    @Select("""
            SELECT to_char(create_time, #{fmt}) AS bucket, count(DISTINCT visitor_id) AS cnt
            FROM im_message
            WHERE create_time >= #{start} AND create_time < #{end} AND sender_type = 1
            GROUP BY bucket ORDER BY bucket
            """)
    List<Map<String, Object>> activeUserTrend(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              @Param("fmt") String fmt);

    /**
     * 知识库聊天消息平均间隔（user → 下一条 assistant 的时间差，作为响应耗时近似）
     */
    @Select("""
            WITH pairs AS (
                SELECT user_msg.id AS uid, user_msg.conversation_id AS cid, user_msg.created_at AS ut,
                       (SELECT min(created_at) FROM t_conversation_message
                        WHERE conversation_id = user_msg.conversation_id AND role = 'assistant'
                          AND created_at > user_msg.created_at) AS at
                FROM t_conversation_message user_msg
                WHERE user_msg.role = 'user'
                  AND user_msg.created_at >= #{start} AND user_msg.created_at < #{end}
            )
            SELECT COALESCE(avg(extract(epoch FROM (pairs.at - pairs.ut)) * 1000), 0) AS avg_ms,
                   COALESCE(percentile_cont(0.95) WITHIN GROUP (ORDER BY extract(epoch FROM (pairs.at - pairs.ut)) * 1000), 0) AS p95_ms
            FROM pairs WHERE pairs.at IS NOT NULL
            """)
    Map<String, Object> latencyStats(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /**
     * 知识库 assistant 消息总数（无知识率分母）
     */
    @Select("""
            SELECT count(*) FROM t_conversation_message
            WHERE role = 'assistant' AND created_at >= #{start} AND created_at < #{end}
            """)
    long countAssistant(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 知识库命中兜底文案的 assistant 消息数（无知识率分子）
     */
    @Select("""
            SELECT count(*) FROM t_conversation_message
            WHERE role = 'assistant' AND created_at >= #{start} AND created_at < #{end}
              AND (content LIKE '抱歉，我在当前知识库中暂未找到%'
                   OR content LIKE '知识库中未检索到%'
                   OR content LIKE '以下内容未来自企业知识库%')
            """)
    long countNoDoc(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
