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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 新增/编辑 AI 模型参数
 */
@Data
@Schema(description = "新增/编辑 AI 模型参数")
public class AiModelValidate implements Serializable {

    @Schema(description = "模型 id（编辑时必填）")
    private Integer id;

    @Schema(description = "厂家/名称")
    @NotBlank(message = "模型名称不能为空")
    private String name;

    @Schema(description = "类型 1对话 2向量 3重排 4视觉")
    @NotNull(message = "模型类型不能为空")
    private Integer type;

    @Schema(description = "供应商 openai/ollama")
    @NotBlank(message = "供应商不能为空")
    private String provider;

    @Schema(description = "凭证 JSON 数组")
    private String credential;

    @Schema(description = "可用模型名（逗号分隔）")
    private String models;

    @Schema(description = "函数调用能力（逗号分隔）")
    private String functionCalling;

    @Schema(description = "选项 JSON 数组")
    private String options;

    @Schema(description = "1启用 2禁用")
    private Integer status;

    @Schema(description = "优先级（数值小者优先）")
    private Integer priority;

    @Schema(description = "是否支持深度思考 0否 1是")
    private Integer supportsThinking;
}
