// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.system.validate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询基类
 */
@Data
public class PageValidate implements Serializable {

    /**
     * 页码，默认 1
     */
    @Schema(description = "页码，默认 1")
    private Integer page = 1;

    /**
     * 每页条数，默认 10
     */
    @Schema(description = "每页条数，默认 10")
    private Integer limit = 10;
}
