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
 * 问题关联子块参数
 */
@Data
@Schema(description = "问题关联子块参数")
public class RelationValidate implements Serializable {

    @Schema(description = "问题 id")
    @NotNull(message = "问题 id 不能为空")
    private Long questionId;

    @Schema(description = "子块 id")
    @NotBlank(message = "子块 id 不能为空")
    private String chunkId;
}
