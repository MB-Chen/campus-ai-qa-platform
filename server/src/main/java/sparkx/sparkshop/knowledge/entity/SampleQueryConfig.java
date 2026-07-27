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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 样例查询全局配置实体。
 *
 * <p>整个「样例查询」功能共用一套配置（表内固定只有 id=1 一条）：
 * <ul>
 *   <li>{@link #embeddingModelId} + {@link #embeddingModelName}：指定向量化用的 embedding 模型</li>
 *   <li>{@link #similarityThreshold}：问答检索时的命中阈值（余弦相似度 ≥ 此值才返回预设答案）</li>
 * </ul>
 */
@Data
@TableName("sample_query_config")
public class SampleQueryConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 固定 1（整个功能共用一条配置） */
    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;

    /** 关联 ai_model.id（type=2 向量模型） */
    @TableField(value = "embedding_model_id")
    private Integer embeddingModelId;

    /** 冗余快照：具体模型名（ai_model.models 中的某一项） */
    @TableField(value = "embedding_model_name")
    private String embeddingModelName;

    /** 命中相似度阈值（0~1），高于此值直接返回答案 */
    @TableField(value = "similarity_threshold")
    private BigDecimal similarityThreshold;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
