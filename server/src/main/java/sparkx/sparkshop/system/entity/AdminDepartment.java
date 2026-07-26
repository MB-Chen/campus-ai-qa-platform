// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 部门
 */
@Data
@TableName("admin_department")
public class AdminDepartment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 部门 id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 父级 id，0 为根
     */
    @TableField(value = "pid")
    private Integer pid;

    /**
     * 部门名称
     */
    @TableField(value = "name")
    private String name;

    /**
     * 部门主管（管理员 id）
     */
    @TableField(value = "leader_id")
    private Integer leaderId;

    /**
     * 联系电话
     */
    @TableField(value = "phone")
    private String phone;

    /**
     * 排序（越大越靠前）
     */
    @TableField(value = "sort")
    private Integer sort;

    /**
     * 状态 1:正常 2:禁用
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;
}
