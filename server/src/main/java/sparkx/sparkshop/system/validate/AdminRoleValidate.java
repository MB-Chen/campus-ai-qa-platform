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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色新增/编辑参数
 */
@Data
public class AdminRoleValidate implements Serializable {

    /**
     * 角色 id（编辑时必填）
     */
    @Schema(description = "角色 id（编辑时必填）")
    private Integer id;

    /**
     * 角色名称
     */
    @Schema(description = "角色名称")
    @NotEmpty(message = "角色名不能为空")
    @Size(min = 2, max = 50, message = "角色名长度 2-50")
    private String name;

    /**
     * 角色拥有的菜单 id（逗号分隔），超管为 *
     */
    @Schema(description = "角色拥有的菜单 id（逗号分隔），超管为 *")
    private String menu;

    /**
     * 状态 1:正常 2:禁用
     */
    @Schema(description = "状态 1:正常 2:禁用")
    private Integer status;
}
