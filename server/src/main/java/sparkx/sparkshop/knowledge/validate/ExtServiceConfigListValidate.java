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
 * 外部服务配置列表入参（按 category 可选筛选）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "外部服务配置列表入参")
public class ExtServiceConfigListValidate extends PageQuery implements Serializable {

    @Schema(description = "服务类别：mineru_self / mineru_cloud / ...（不传则查全部）")
    private String category;

    @Schema(description = "状态 1启用 2禁用（>0 时生效）")
    private Integer status;
}
