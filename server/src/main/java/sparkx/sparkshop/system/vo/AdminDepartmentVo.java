// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 部门列表/树项（含主管名，字段统一驼峰）
 */
@Data
public class AdminDepartmentVo implements Serializable {

    @Schema(description = "部门 id")
    private Integer id;

    @Schema(description = "父级 id，0 为根")
    private Integer pid;

    @Schema(description = "部门名称")
    private String name;

    @Schema(description = "部门主管（管理员 id）")
    private Integer leaderId;

    @Schema(description = "主管名")
    private String leaderName;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态 1:正常 2:禁用")
    private Integer status;

    @Schema(description = "创建时间")
    private String createTime;

    @Schema(description = "更新时间")
    private String updateTime;

    @Schema(description = "子部门")
    private List<AdminDepartmentVo> children;
}
