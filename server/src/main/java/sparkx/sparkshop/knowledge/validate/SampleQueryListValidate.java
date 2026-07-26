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
import lombok.EqualsAndHashCode;
import sparkx.sparkshop.system.vo.PageQuery;

import java.io.Serializable;

/**
 * 样例查询列表入参
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "样例查询列表入参")
public class SampleQueryListValidate extends PageQuery implements Serializable {

    @Schema(description = "关键词（模糊匹配 question / answer）")
    private String keyword;

    @Schema(description = "状态：1启用 2禁用")
    private Integer status;

    @Schema(description = "向量化状态过滤：1已向量化（chunk_id 非空） 2未向量化（chunk_id 为空）")
    private Integer vectorized;
}
