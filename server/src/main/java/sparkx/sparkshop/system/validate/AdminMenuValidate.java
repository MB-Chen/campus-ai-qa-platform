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
import lombok.Data;

import java.io.Serializable;

/**
 * 菜单新增/编辑参数
 */
@Data
public class AdminMenuValidate implements Serializable {

    /**
     * 菜单 id（编辑时必填）
     */
    @Schema(description = "菜单 id（编辑时必填）")
    private Integer id;

    /**
     * 父级 id，0 为根
     */
    @Schema(description = "父级 id，0 为根")
    @NotNull(message = "请选择父级菜单")
    private Integer pid;

    /**
     * 菜单名称
     */
    @Schema(description = "菜单名称")
    @NotEmpty(message = "菜单名称不能为空")
    private String name;

    /**
     * 类型 1:菜单 2:功能按钮
     */
    @Schema(description = "类型 1:菜单 2:功能按钮")
    @NotNull(message = "请选择类型")
    private Integer type;

    /**
     * 路由标识（拼接前端路由 name 用）
     */
    @Schema(description = "路由标识（拼接前端路由 name 用）")
    private String flag;

    /**
     * 前端路径
     */
    @Schema(description = "前端路径")
    private String path;

    /**
     * 前端组件地址
     */
    @Schema(description = "前端组件地址")
    private String component;

    /**
     * 权限标识（接口鉴权用，如 role/add）
     */
    @Schema(description = "权限标识（接口鉴权用，如 role/add）")
    private String auth;

    /**
     * 图标
     */
    @Schema(description = "图标")
    private String icon;

    /**
     * 排序，越大越靠前
     */
    @Schema(description = "排序，越大越靠前")
    private Integer sort;

    /**
     * 是否在侧边栏隐藏：0-显示（默认）; 1-隐藏
     */
    @Schema(description = "是否隐藏: 0-显示 1-隐藏")
    private Integer hidden;

    /**
     * 状态 1:正常 2:禁用
     */
    @Schema(description = "状态 1:正常 2:禁用")
    private Integer status;
}
