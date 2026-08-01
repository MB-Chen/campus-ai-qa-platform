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
import sparkx.sparkshop.knowledge.entity.PersistentMemoryEntity;

import java.util.List;

/**
 * 跨会话持久记忆 Mapper。
 *
 * 持久记忆实体本身按 memoryKey（pmem:{agentId}:{adminId}）单表 CRUD；
 * 增量抽取时需跨多个 conversationId 拉取该会话族的消息（conversation_id 以 agent:{agentId}: 开头，
 * user_id 固定为 agent:operator）。
 * 注意：所有查询均显式排除 conversation_id LIKE '%:eval:%' 的评估探针会话，防止评估数据污染正式记忆。
 */
public interface PersistentMemoryMapper extends BaseMapper<PersistentMemoryEntity> {

    /**
     * 统计某会话族（agentId 维度，跨所有 conversationId）的用户消息总数。
     * 会话消息的 conversation_id 格式为 "agent:{agentId}:{conversationId}"，
     * 用 LIKE 前缀匹配该智能体下所有会话。
     */
    @Select("SELECT COUNT(*) FROM t_conversation_message " +
            "WHERE conversation_id LIKE #{convPrefix} AND user_id = #{userId} " +
            "  AND conversation_id NOT LIKE '%:eval:%' AND role = 'user'")
    long countUserMessagesByAgent(@Param("convPrefix") String conversationIdPrefix,
                                  @Param("userId") String userId);

    /**
     * 取某会话族最近 N 条消息（跨所有 conversationId，按 id DESC）。
     * 用于增量抽取时拉取待压缩的新消息。
     */
    @Select("SELECT * FROM t_conversation_message " +
            "WHERE conversation_id LIKE #{convPrefix} AND user_id = #{userId} " +
            "  AND conversation_id NOT LIKE '%:eval:%' " +
            "ORDER BY id DESC LIMIT #{limit}")
    List<ConversationMessageEntity> findRecentByAgent(@Param("convPrefix") String conversationIdPrefix,
                                                      @Param("userId") String userId,
                                                      @Param("limit") int limit);

    /**
     * 增量抽取区间查询：某会话族内 id > afterId 的消息（跨 conversationId，按 id ASC）。
     * afterId 为 0 时取该会话族全部（首次抽取）。
     */
    @Select("SELECT * FROM t_conversation_message " +
            "WHERE conversation_id LIKE #{convPrefix} AND user_id = #{userId} " +
            "  AND conversation_id NOT LIKE '%:eval:%' AND id > #{afterId} " +
            "ORDER BY id ASC LIMIT #{limit}")
    List<ConversationMessageEntity> findIncrementalByAgent(@Param("convPrefix") String conversationIdPrefix,
                                                           @Param("userId") String userId,
                                                           @Param("afterId") long afterId,
                                                           @Param("limit") int limit);
}
