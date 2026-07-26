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
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 管理员列表查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserSearchValidate extends PageValidate implements Serializable {

    /**
     * 昵称（模糊搜索）
     */
    @Schema(description = "昵称（模糊搜索）")
    private String nickname;

    /**
     * 账号（模糊搜索）
     */
    @Schema(description = "账号（模糊搜索）")
    private String account;

    /**
     * 部门 id（精确过滤）
     */
    @Schema(description = "部门 id（精确过滤）")
    private Integer deptId;
}
