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

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档表（移植自 sparkxV2 document，扩展 question_status/active）。
 */
@Data
@TableName("document")
public class KnowledgeDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（UUID，由业务生成） */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField(value = "kb_id")
    private String kbId;

    @TableField(value = "file_name")
    private String fileName;

    @TableField(value = "file_size")
    private Long fileSize;

    /** MinIO 对象 key */
    @TableField(value = "storage_url")
    private String storageUrl;

    /**
     * 入库耗时统计 JSON（jsonb 列）。
     * 存时为 JSON 字符串，MyBatis-Plus 按 varchar 传参会被 PG 拒绝，
     * 故落库走原生 SQL + CAST（见 KnowledgeDocumentMapper.updateIngestionSummary）。
     * 读时 PG JDBC 会以字符串形式返回，业务层再反序列化成 IngestionSummary。
     *
     * ⚠️ updateById 时必须跳过本字段：selectById 取出的 ingestionSummary 是 jsonb 序列化后的
     * 字符串，若 updateById 原样回写会被 PG 当 varchar 拒绝（"column is of type jsonb but
     * expression is of type character varying"）。用 insertStrategy=NEVER + updateStrategy=NEVER
     * 强制该字段不参与 BaseMapper 的 insert/updateById，写入一律走 updateIngestionSummary。
     */
    @TableField(value = "ingestion_summary",
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private String ingestionSummary;

    /** 向量化状态: pending|processing|done|failed */
    @TableField(value = "status")
    private String status;

    @TableField(value = "chunk_count")
    private Integer chunkCount;

    /** 问题生成状态: 1待生成 2生成中 3已生成（对标 spark-x） */
    @TableField(value = "question_status")
    private Integer questionStatus;

    /** 是否启用：1正常 2禁用 */
    @TableField(value = "active")
    private Integer active;

    /** 知识图谱开关（文档级）：1=启用 2=禁用，决定该文档是否参与图谱抽取/召回 */
    @TableField(value = "kg_enabled")
    private Integer kgEnabled;

    @TableField(value = "created_at", update = "now()")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
