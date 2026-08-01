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
 * 跨会话持久记忆实体。表 t_persistent_memory。
 *
 * 每个会话族（agentId + adminId）一份记忆，memoryKey 为主键。
 * memoryJson 为结构化分区 JSON（userProfile/longTermConstraints/confirmedFacts/preferences）。
 * lastExtractedMessageId 为增量抽取下界：下次抽取取 (lastExtractedMessageId, cutoffId] 区间的新消息。
 *
 * ★ 设计对标 Claude Code 的 CLAUDE.md 持久记忆 + wU2 结构化分区摘要：
 *   只沉淀「跨多次对话仍有价值」的长期事实，不记具体答案（避免与实时 RAG 检索冲突）。
 */
@Data
@TableName("t_persistent_memory")
public class PersistentMemoryEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键：pmem:{agentId}:{adminId} */
    @TableId(value = "memory_key", type = IdType.INPUT)
    private String memoryKey;

    /** 结构化分区 JSON（userProfile/longTermConstraints/confirmedFacts/preferences） */
    @TableField(value = "memory_json")
    private String memoryJson;

    /** 版本号（每次抽取自增） */
    @TableField(value = "version")
    private Integer version;

    /** 增量抽取下界：已抽取的最后一条消息 id */
    @TableField(value = "last_extracted_message_id")
    private Long lastExtractedMessageId;

    /** 该会话族累计用户消息数（抽取闸门用，避免每次都 count 全表） */
    @TableField(value = "message_count")
    private Integer messageCount;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;

    public PersistentMemoryEntity() {}

    public PersistentMemoryEntity(String memoryKey, String memoryJson, Integer version,
                                  Long lastExtractedMessageId, Integer messageCount) {
        this.memoryKey = memoryKey;
        this.memoryJson = memoryJson;
        this.version = version;
        this.lastExtractedMessageId = lastExtractedMessageId;
        this.messageCount = messageCount;
    }
}
