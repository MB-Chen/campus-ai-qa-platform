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
 * 智能体测试对话会话。表 t_agent_test_session。
 *
 * <p>★ 独立于业务对话表 {@link ChatSession}：仅用于智能体配置页的「测试对话」调试，
 * 不与正式聊天数据混用，避免污染业务表。按 adminId 隔离当前登录用户。
 * 消息明细见 {@link AgentTestMessage}。
 */
@Data
@TableName("t_agent_test_session")
public class AgentTestSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（UUID hex，业务生成） */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 所属管理员 id（登录用户） */
    @TableField(value = "admin_id")
    private Long adminId;

    /** 测试的智能体 id */
    @TableField(value = "agent_id")
    private String agentId;

    /** 会话标题 */
    @TableField(value = "title")
    private String title;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
