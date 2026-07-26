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
 * 知识图谱文档级抽取状态记录。
 *
 * <p>每个 (kb_id, document_id) 一条（UNIQUE 约束），用于：
 * <ul>
 *   <li>增量抽取：只对 status != done 的文档补抽取</li>
 *   <li>进度展示：parent_done / parent_total 实时刷新（异步抽取时 Redis 桶配合前端轮询）</li>
 *   <li>统计：entity_count / relation_count</li>
 * </ul>
 */
@Data
@TableName("kg_extraction_record")
public class KgExtractionRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "kb_id")
    private String kbId;

    /** 关联文档（doc_xxx） */
    @TableField(value = "document_id")
    private String documentId;

    /** pending / extracting / done / failed */
    @TableField(value = "status")
    private String status;

    /** 待抽取父块总数 */
    @TableField(value = "parent_total")
    private Integer parentTotal;

    /** 已完成父块数 */
    @TableField(value = "parent_done")
    private Integer parentDone;

    /** 抽取实体数（累计） */
    @TableField(value = "entity_count")
    private Integer entityCount;

    /** 抽取关系数（累计） */
    @TableField(value = "relation_count")
    private Integer relationCount;

    /** 失败原因（status=failed 时） */
    @TableField(value = "error_msg")
    private String errorMsg;

    @TableField(value = "started_at")
    private LocalDateTime startedAt;

    @TableField(value = "finished_at")
    private LocalDateTime finishedAt;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;


    /** 知识库名称（关联 knowledge_base.name） */
    @TableField(exist = false)
    private String kbName;

    /** 文档名称（关联 document.file_name） */
    @TableField(exist = false)
    private String documentName;
}
