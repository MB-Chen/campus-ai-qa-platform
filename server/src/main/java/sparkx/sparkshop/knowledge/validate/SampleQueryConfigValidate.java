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
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 样例查询全局配置保存参数
 */
@Data
@Schema(description = "样例查询全局配置保存参数")
public class SampleQueryConfigValidate implements Serializable {

    @Schema(description = "embedding 模型 id（ai_model 表 type=2）；为空表示用默认兜底模型")
    private Integer embeddingModelId;

    @Schema(description = "具体模型名（ai_model.models 中的某一项，冗余快照）")
    private String embeddingModelName;

    @Schema(description = "命中相似度阈值（0~1）")
    @DecimalMin(value = "0.0", message = "相似度阈值不能小于 0")
    @DecimalMax(value = "1.0", message = "相似度阈值不能大于 1")
    private BigDecimal similarityThreshold;
}
