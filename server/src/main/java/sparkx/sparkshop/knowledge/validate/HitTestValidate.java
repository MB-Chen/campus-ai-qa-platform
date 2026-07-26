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
 * 命中测试参数
 */
@Data
@Schema(description = "命中测试参数")
public class HitTestValidate implements Serializable {

    @Schema(description = "知识库 id")
    @NotBlank(message = "知识库 id 不能为空")
    private String kbId;

    @Schema(description = "文档 id（可选，指定后只在单个文档内做召回测试；为空则查整个知识库）")
    private String documentId;

    @Schema(description = "查询文本")
    @NotBlank(message = "查询内容不能为空")
    private String query;

    @Schema(description = "检索模式: embedding/text/mix")
    private String mode;

    @Schema(description = "相似度阈值")
    private Double similarity;

    @Schema(description = "返回条数")
    private Integer topRank;
}
