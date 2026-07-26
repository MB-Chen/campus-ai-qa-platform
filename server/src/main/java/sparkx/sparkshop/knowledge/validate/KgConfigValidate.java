// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.validate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 知识图谱全局配置保存参数（对齐 SampleQueryConfigValidate 范式）。
 */
@Data
@Schema(description = "知识图谱全局配置保存参数")
public class KgConfigValidate implements Serializable {

    @Schema(description = "抽取用 LLM 模型 id（ai_model 表 type=1）")
    private Integer extractModelId;

    @Schema(description = "冗余快照：抽取用具体模型名")
    private String extractModelName;

    @Schema(description = "实体向量化模型 id（ai_model 表 type=2）")
    private Integer embeddingModelId;

    @Schema(description = "冗余快照：向量化用具体模型名")
    private String embeddingModelName;

    @Schema(description = "全局运行期开关：1=启用 2=禁用")
    @Min(value = 1, message = "开关值必须是 1 或 2")
    @Max(value = 2, message = "开关值必须是 1 或 2")
    private Integer enabled;

    @Schema(description = "实体向量召回相似度阈值（0~1）")
    @DecimalMin(value = "0.0", message = "阈值不能小于 0")
    @DecimalMax(value = "1.0", message = "阈值不能大于 1")
    private BigDecimal similarityThreshold;

    @Schema(description = "单次 LLM 调用合并抽取的父块数（5~100）")
    @Min(value = 5, message = "批量大小不能小于 5")
    @Max(value = 100, message = "批量大小不能大于 100")
    private Integer extractBatchSize;

    @Schema(description = "子图跳数：1=一跳 2=二跳")
    @Min(value = 1, message = "跳数不能小于 1")
    @Max(value = 2, message = "跳数不能大于 2")
    private Integer hopDepth;

    @Schema(description = "二跳关系衰减权重（0~1）")
    @DecimalMin(value = "0.0", message = "权重不能小于 0")
    @DecimalMax(value = "1.0", message = "权重不能大于 1")
    private BigDecimal secondHopWeight;

    @Schema(description = "实体 embedding 合并阈值（0~1，默认 0.88）：字符串消歧后，对实体 name+description 算余弦相似度，>= 此值合并")
    @DecimalMin(value = "0.5", message = "合并阈值不能小于 0.5（过低会大量误合）")
    @DecimalMax(value = "1.0", message = "合并阈值不能大于 1")
    private BigDecimal entityMergeThreshold;

    @Schema(description = "图谱检索模式 local/global/hybrid")
    private String retrievalMode;

    @Schema(description = "社区检测开关 1=启用 2=禁用")
    @Min(value = 1, message = "开关值必须是 1 或 2")
    @Max(value = 2, message = "开关值必须是 1 或 2")
    private Integer communityEnabled;
}
