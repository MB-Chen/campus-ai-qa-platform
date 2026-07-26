// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RAG 会话话题摘要实体（移植自 sparkxV2）。表 t_conversation_summary。
 *
 * 每个会话一份摘要（conversationId 为主键）。lastMessageId 为增量压缩下界：
 * 下次压缩取 (lastMessageId, cutoffId] 区间的新消息。
 *
 * ★ 核心设计——"只记话题不记答案"：摘要仅作历史讨论的话题索引，
 * 不含具体答案（避免与实时检索的最新文档内容冲突）。
 */
@Data
@TableName("t_conversation_summary")
public class ConversationSummaryEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "conversation_id", type = IdType.INPUT)
    private String conversationId;

    @TableField(value = "user_id")
    private String userId;

    @TableField(value = "summary")
    private String summary;

    /** 增量压缩下界：已摘要的最后一条消息 id */
    @TableField(value = "last_message_id")
    private Long lastMessageId;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;

    public ConversationSummaryEntity() {}

    public ConversationSummaryEntity(String conversationId, String userId, String summary, Long lastMessageId) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.summary = summary;
        this.lastMessageId = lastMessageId;
    }
}
