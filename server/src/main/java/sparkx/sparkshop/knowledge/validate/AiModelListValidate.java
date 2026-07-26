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
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import sparkx.sparkshop.system.vo.PageQuery;

import java.io.Serializable;

/**
 * AI 模型列表入参（按类型筛选）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI 模型列表入参")
public class AiModelListValidate extends PageQuery implements Serializable {

    @Schema(description = "类型 1对话 2向量 3重排 4视觉", required = true)
    @NotNull(message = "模型类型不能为空")
    private Integer type;

    @Schema(description = "状态 1启用 2禁用（>0 时生效）")
    private Integer status;
}
