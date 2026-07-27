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
 * 知识库子块实体（移植自 sparkxV2 ChunkEntity）。
 *
 * 注意：embedding / tsv 列为 pgvector/tsvector 类型，MyBatis-Plus 不自动映射，
 * 向量入库与检索走原生 SQL（见 {@link sparkx.sparkshop.knowledge.mapper.ChunkMapper}）。
 * metadata 用 JSONB，这里用 String 承载 JSON 文本。
 */
@Data
@TableName("chunks")
public class ChunkEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField(value = "kb_id")
    private String kbId;

    @TableField(value = "content")
    private String content;

    /** JSON 文本：parentId / chunkRole / breadcrumb / file_name 等 */
    @TableField(value = "metadata")
    private String metadata;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    public ChunkEntity() {}

    public ChunkEntity(String id, String kbId, String content, String metadata) {
        this.id = id;
        this.kbId = kbId;
        this.content = content;
        this.metadata = metadata;
    }
}
