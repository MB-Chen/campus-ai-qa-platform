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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理员新增/编辑参数
 */
@Data
public class AdminUserValidate implements Serializable {

    /**
     * 管理员 id（编辑时必填）
     */
    @Schema(description = "管理员 id（编辑时必填）")
    private Integer id;

    /**
     * 昵称
     */
    @Schema(description = "昵称")
    @NotEmpty(message = "昵称不能为空")
    @Size(max = 50, message = "昵称过长")
    private String nickname;

    /**
     * 登录账号
     */
    @Schema(description = "登录账号")
    @NotEmpty(message = "账号不能为空")
    @Size(min = 2, max = 30, message = "账号长度 2-30")
    private String account;

    /**
     * 角色 id
     */
    @Schema(description = "角色 id")
    @NotNull(message = "请选择角色")
    private Integer roleId;

    /**
     * 部门 id
     */
    @Schema(description = "部门 id")
    private Integer deptId;

    /**
     * 密码（新增时必填；编辑时为空表示不改密码）
     */
    @Schema(description = "密码（新增时必填；编辑时为空表示不改密码）")
    private String password;

    /**
     * 状态 1:正常 2:禁用
     */
    @Schema(description = "状态 1:正常 2:禁用")
    private Integer status;

    /**
     * 头像
     */
    @Schema(description = "头像")
    private String avatar;
}
