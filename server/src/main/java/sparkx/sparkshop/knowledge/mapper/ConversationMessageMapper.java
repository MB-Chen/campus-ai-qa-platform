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
import sparkx.sparkshop.knowledge.entity.ConversationMessageEntity;

import java.util.List;

/**
 * RAG 会话消息 Mapper（移植自 sparkxV2 ConversationMessageRepository，改为 MyBatis-Plus）。
 * 提供滑动窗口历史、增量摘要区间查询等原生 SQL。
 */
public interface ConversationMessageMapper extends BaseMapper<ConversationMessageEntity> {

    /** 最近 N 条消息（按 id 倒序） */
    @Select("SELECT * FROM t_conversation_message " +
            "WHERE conversation_id = #{conversationId} AND user_id = #{userId} " +
            "ORDER BY id DESC LIMIT #{limit}")
    List<ConversationMessageEntity> findRecent(@Param("conversationId") String conversationId,
                                               @Param("userId") String userId,
                                               @Param("limit") int limit);

    /** 统计某会话的用户消息数（判断是否触发摘要压缩） */
    @Select("SELECT COUNT(*) FROM t_conversation_message " +
            "WHERE conversation_id = #{conversationId} AND user_id = #{userId} AND role = 'user'")
    long countUserMessages(@Param("conversationId") String conversationId,
                           @Param("userId") String userId);

    /** 增量摘要区间查询：(lastMessageId, cutoffId] 的消息 */
    @Select("SELECT * FROM t_conversation_message " +
            "WHERE conversation_id = #{conversationId} AND user_id = #{userId} " +
            "  AND id > #{afterId} AND id <= #{cutoffId} ORDER BY id ASC")
    List<ConversationMessageEntity> findBetweenIds(@Param("conversationId") String conversationId,
                                                   @Param("userId") String userId,
                                                   @Param("afterId") long afterId,
                                                   @Param("cutoffId") long cutoffId);
}
