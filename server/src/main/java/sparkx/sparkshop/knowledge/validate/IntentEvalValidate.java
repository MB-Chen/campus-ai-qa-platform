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
import lombok.Data;
import sparkx.sparkshop.knowledge.vo.IntentEvalCaseVo;

import java.io.Serializable;
import java.util.List;

/**
 * 意图分类批量评估入参（替代旧控制器内嵌的 IntentEvalRequest）。
 */
@Data
@Schema(description = "意图分类批量评估入参")
public class IntentEvalValidate implements Serializable {

    @Schema(description = "标注用例列表")
    private List<IntentEvalCaseVo> cases;

    @Schema(description = "每条保留前 N 候选（不传默认 3）")
    private Integer topN;

    @Schema(description = "最小分数阈值（不传默认 0.35）")
    private Double minScore;
}
