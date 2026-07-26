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
 * 知识图谱全局配置实体（固定 id=1 单条）。
 *
 * <p>抽取 LLM + 实体向量化模型 + 相似度阈值 / 子图跳数 等参数。整个 KG 功能共用一条。
 *
 * <p>⚠️ 全局开关 {@link #enabled} 与 {@code app.rag.knowledge-graph.enabled} 是「双闸」关系：
 * yml 总闸控制 KG 模块 Bean 是否装配（启动期），本表 enabled 控制运行期是否实际工作（可热改）。
 */
@Data
@TableName("kg_config")
public class KgConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 固定 1（整个功能共用一条配置） */
    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;

    /** 关联 ai_model.id（type=1 对话模型，用于抽实体/关系） */
    @TableField(value = "extract_model_id")
    private Integer extractModelId;

    /** 冗余快照：抽取用的具体模型名 */
    @TableField(value = "extract_model_name")
    private String extractModelName;

    /** 关联 ai_model.id（type=2 向量模型，用于实体向量化） */
    @TableField(value = "embedding_model_id")
    private Integer embeddingModelId;

    /** 冗余快照：实体向量化用的具体模型名 */
    @TableField(value = "embedding_model_name")
    private String embeddingModelName;

    /** 全局运行期开关 1=启用 2=禁用（与 yml 总闸双闸） */
    @TableField(value = "enabled")
    private Integer enabled;

    /** 实体向量召回相似度阈值（0~1），高于此值才视为命中 */
    @TableField(value = "similarity_threshold")
    private BigDecimal similarityThreshold;

    /** 单次 LLM 调用合并抽取的父块数 */
    @TableField(value = "extract_batch_size")
    private Integer extractBatchSize;

    /** 子图跳数 1=一跳 2=二跳（带 second_hop_weight 衰减） */
    @TableField(value = "hop_depth")
    private Integer hopDepth;

    /** 二跳关系衰减权重（0~1） */
    @TableField(value = "second_hop_weight")
    private BigDecimal secondHopWeight;

    /**
     * ★ 实体 embedding 合并阈值（0~1，默认 0.88）—— 第三期 EntityDisambiguator 用。
     * 在字符串 canonical_name 消歧之后，对实体的 name+description embedding 算余弦相似度，
     * ≥ 此值的实体合并为一个（解决 LLM 跨父块输出"北京"vs"北京市"无法合并的问题）。
     * 调高→合并更保守（少误合）；调低→合并更激进（多召回但可能误合）。
     */
    @TableField(value = "entity_merge_threshold")
    private BigDecimal entityMergeThreshold;

    /**
     * ★ 第四期：图谱检索模式。
     * <ul>
     *   <li>{@code local}（默认）：原行为，向量召回实体 → 子图 1/2 跳扩展取 chunk</li>
     *   <li>{@code global}：社区摘要召回（需先跑社区检测 + 摘要生成），适合"宏观/全局性问题"</li>
     *   <li>{@code hybrid}：local + global 双路并行，证据并集送入融合</li>
     * </ul>
     */
    @TableField(value = "retrieval_mode")
    private String retrievalMode;

    /** ★ 第四期：是否启用社区检测（global/hybrid 模式前置条件） */
    @TableField(value = "community_enabled")
    private Integer communityEnabled;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
