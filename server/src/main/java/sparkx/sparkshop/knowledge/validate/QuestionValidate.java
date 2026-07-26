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
import lombok.Data;

import java.io.Serializable;

/**
 * 问题新增/编辑参数
 */
@Data
@Schema(description = "问题新增/编辑参数")
public class QuestionValidate implements Serializable {

    @Schema(description = "问题 id（编辑时必填）")
    private Long id;

    @Schema(description = "知识库 id（新增时必填）")
    @NotBlank(message = "知识库 id 不能为空")
    private String kbId;

    @Schema(description = "问题文本")
    @NotBlank(message = "问题内容不能为空")
    private String content;
}
