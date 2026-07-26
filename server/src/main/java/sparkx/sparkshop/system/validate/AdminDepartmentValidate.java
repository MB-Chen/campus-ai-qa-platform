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
 * 部门新增/编辑参数
 */
@Data
public class AdminDepartmentValidate implements Serializable {

    /**
     * 部门 id（编辑时必填）
     */
    @Schema(description = "部门 id（编辑时必填）")
    private Integer id;

    /**
     * 父级 id，0 为根
     */
    @Schema(description = "父级 id，0 为根")
    private Integer pid;

    /**
     * 部门名称
     */
    @Schema(description = "部门名称")
    @NotEmpty(message = "部门名不能为空")
    @Size(min = 2, max = 50, message = "部门名长度 2-50")
    private String name;

    /**
     * 部门主管（管理员 id）
     */
    @Schema(description = "部门主管（管理员 id）")
    private Integer leaderId;

    /**
     * 联系电话
     */
    @Schema(description = "联系电话")
    @Size(max = 20, message = "联系电话过长")
    private String phone;

    /**
     * 排序（越大越靠前）
     */
    @Schema(description = "排序（越大越靠前）")
    private Integer sort;

    /**
     * 状态 1:正常 2:禁用
     */
    @Schema(description = "状态 1:正常 2:禁用")
    private Integer status;
}
