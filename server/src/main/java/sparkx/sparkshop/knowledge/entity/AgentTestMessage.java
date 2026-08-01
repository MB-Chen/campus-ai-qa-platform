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
 * 智能体测试对话消息。表 t_agent_test_message。
 *
 * <p>★ 独立于业务对话表 {@link ChatMessage}：仅用于智能体配置页的「测试对话」调试。
 * 流式回答结束后由前端落库：user 问题 + 完整 assistant 回答（含引用来源 / RAG 各阶段
 * 上下文 / 各阶段耗时 / 总耗时）。
 *
 * <p>references(列名 refs) / stageData / stageTimings 三个 jsonb 列统一用 String 映射
 * （项目既有范式，见 {@link ChatMessage}），写入前归一，读取 VO 用 {@code @JsonRawValue}。
 */
@Data
@TableName("t_agent_test_message")
public class AgentTestMessage implements Serializable {

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

    /** 引用来源 JSON（assistant，序列化后的字符串，jsonb 列）。
     *  ★ 列名用 refs 而非 references：references 是 PG 保留字，MyBatis-Plus 拼 INSERT 时不加引号会报
     *    syntax error at or near "references"。Java 字段名保留 references（语义清晰），仅 DB 列名改 refs。 */
    @TableField(value = "refs")
    private String references;

    /** RAG 各阶段上下文 JSON（assistant，jsonb 列） */
    @TableField(value = "stage_data")
    private String stageData;

    /** RAG 各阶段耗时 JSON（assistant，jsonb 列） */
    @TableField(value = "stage_timings")
    private String stageTimings;

    /** 总耗时（毫秒） */
    @TableField(value = "total_cost")
    private Long totalCost;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;
}
