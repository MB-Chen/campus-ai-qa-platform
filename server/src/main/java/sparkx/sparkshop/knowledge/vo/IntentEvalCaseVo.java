// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 意图分类评估单条用例入参
 */
@Data
@Schema(description = "意图分类评估单条用例")
public class IntentEvalCaseVo implements Serializable {

    @Schema(description = "用户问题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String query;

    @Schema(description = "期望命中的节点 id", requiredMode = Schema.RequiredMode.REQUIRED)
    private String expectNodeId;

    @Schema(description = "期望命中的节点名称（仅展示用，可空）")
    private String expectNodeName;

    @Schema(description = "备注（可空）")
    private String note;
}
