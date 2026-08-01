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
 * 聊天消息。表 t_chat_message。
 * <p>
 * 对应管理端聊天页（admin/views/chat）会话内的单条消息。
 * 流式回答结束后落库：user 问题 + 完整 assistant 回答（含引用来源 / RAG 各阶段上下文 / 编排步骤 / 耗时与 token）。
 * 与 RAG 管线内部记忆表 {@code t_conversation_message} 无关，不复用。
 */
@Data
@TableName("t_chat_message")
public class ChatMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属会话 id */
    @TableField(value = "session_id")
    private String sessionId;

    /** 角色：user / assistant */
    @TableField(value = "role")
    private String role;

    /** 消息内容 */
    @TableField(value = "content")
    private String content;

    /** 引用来源 JSON（assistant，序列化后的字符串）。
     *  ★ 列名用 refs 而非 references：references 是 PG 保留字，MyBatis-Plus 拼参时不加引号会报
     *    syntax error at or near "references"。Java 字段名保留 references（语义清晰），仅 DB 列名改 refs。 */
    @TableField(value = "refs")
    private String references;

    /** RAG 各阶段上下文 JSON（assistant，序列化后的字符串） */
    @TableField(value = "stage_data")
    private String stageData;

    /** 编排智能体步骤 JSON（assistant，序列化后的字符串） */
    @TableField(value = "workflow_steps")
    private String workflowSteps;

    /** 总耗时（毫秒） */
    @TableField(value = "total_cost")
    private Long totalCost;

    /** 总 token 数 */
    @TableField(value = "total_tokens")
    private Integer totalTokens;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;
}
