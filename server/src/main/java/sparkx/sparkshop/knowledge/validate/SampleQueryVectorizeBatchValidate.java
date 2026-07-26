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

import java.io.Serializable;
import java.util.List;

/**
 * 样例查询批量向量化入参（ids 为空表示全部启用项）。
 */
@Data
@Schema(description = "样例查询批量向量化入参")
public class SampleQueryVectorizeBatchValidate implements Serializable {

    @Schema(description = "待向量化的样例 id 列表（为空表示全部启用项）")
    private List<Long> ids;
}
