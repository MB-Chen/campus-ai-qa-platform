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
 * 知识库问题实体。
 *
 * 存储与知识库文档/分块对应的问题，用于增加召回成功率（非 QA 对，无答案）。
 * 由"生成问题"功能自动产生或页面手动录入，关联到具体问题 chunk（chunk_id）。
 */
@Data
@TableName("knowledge_question")
public class KnowledgeQuestion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "kb_id")
    private String kbId;

    /** 来源文档 */
    @TableField(value = "document_id")
    private String documentId;

    /** 关联问题 chunk chunks.id（与 chunks 表中 type=question 的记录对应） */
    @TableField(value = "chunk_id")
    private String chunkId;

    /** 问题文本 */
    @TableField(value = "content")
    private String content;

    /** manual 手动录入 / ai 生成 */
    @TableField(value = "source")
    private String source;

    /** 1正常 2禁用 */
    @TableField(value = "status")
    private Integer status;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
