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
 * 聊天会话。表 t_chat_session。
 * <p>
 * 对应管理端聊天页（admin/views/chat）的一组多轮对话，按 adminId 归属当前登录用户。
 * 消息明细见 {@link ChatMessage}。
 */
@Data
@TableName("t_chat_session")
public class ChatSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（UUID hex，业务生成） */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 所属管理员 id（登录用户） */
    @TableField(value = "admin_id")
    private Long adminId;

    /** 会话标题 */
    @TableField(value = "title")
    private String title;

    /** 会话描述 */
    @TableField(value = "description")
    private String description;

    /** 来源场景：chat 等 */
    @TableField(value = "source")
    private String source;

    /** 目标类型：agent 智能体 / workflow 编排智能体 */
    @TableField(value = "kind")
    private String kind;

    /** 绑定的智能体/编排 id */
    @TableField(value = "agent_id")
    private String agentId;

    /** 智能体配置 JSON（序列化后的字符串，jsonb 列） */
    @TableField(value = "agent_config")
    private String agentConfig;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
