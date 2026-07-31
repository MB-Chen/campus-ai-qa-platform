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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 生成问题参数（按文档列表）。
 *
 * <p>遍历所选文档的每个原文分块，用 LLM 生成若干问题，
 * 问题作为独立切片入库（type=question）并生成 embedding，用于增加召回率。
 */
@Data
@Schema(description = "生成问题参数（按文档列表）")
public class KbQuestionGenValidate implements Serializable {

    @Schema(description = "文档 id 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "文档 id 不能为空")
    private List<String> documentIds;

    @Schema(description = "使用的对话模型 id（可空，默认走候选链）")
    private Integer modelId;

    @Schema(description = "具体子模型名（逗号列表内才采用，否则取首项；可空）")
    private String modelName;

    @Schema(description = "每个原文分块生成的问题数（1-10，默认 3）")
    @Min(value = 1, message = "问题数至少为 1")
    @Max(value = 10, message = "问题数最多为 10")
    private Integer questionCount;
}
