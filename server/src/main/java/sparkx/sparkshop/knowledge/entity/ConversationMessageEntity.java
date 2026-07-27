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
 * RAG 会话消息实体（移植自 sparkxV2）。表 t_conversation_message。
 *
 * 滑动窗口策略：每个会话只保留最近 historyKeepTurns*2 条用于喂模型，
 * 历史更长部分由 {@link ConversationSummaryEntity} 摘要压缩。
 */
@Data
@TableName("t_conversation_message")
public class ConversationMessageEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "conversation_id")
    private String conversationId;

    @TableField(value = "user_id")
    private String userId;

    /** 角色：user / assistant / system */
    @TableField(value = "role")
    private String role;

    @TableField(value = "content")
    private String content;

    /** 思考过程内容（reasoning，部分模型支持），可为 null */
    @TableField(value = "thinking_content")
    private String thinkingContent;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    public ConversationMessageEntity() {}

    public ConversationMessageEntity(String conversationId, String userId, String role,
                                     String content, String thinkingContent) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.thinkingContent = thinkingContent;
    }
}
