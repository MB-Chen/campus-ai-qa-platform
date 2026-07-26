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
 * 菜单 / 权限节点
 */
@Data
@TableName("admin_menu")
public class AdminMenu implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 节点 id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 父级 id，0 为根
     */
    @TableField(value = "pid")
    private Integer pid;

    /**
     * 菜单名称（显示标题）
     */
    @TableField(value = "name")
    private String name;

    /**
     * 类型 1:菜单 2:功能按钮
     */
    @TableField(value = "type")
    private Integer type;

    /**
     * 路由标识（拼接前端路由 name 用）
     */
    @TableField(value = "flag")
    private String flag;

    /**
     * 前端路径
     */
    @TableField(value = "path")
    private String path;

    /**
     * 前端组件地址
     */
    @TableField(value = "component")
    private String component;

    /**
     * 权限标识（接口鉴权用，如 role/add）
     */
    @TableField(value = "auth")
    private String auth;

    /**
     * 图标
     */
    @TableField(value = "icon")
    private String icon;

    /**
     * 排序，越大越靠前
     */
    @TableField(value = "sort")
    private Integer sort;

    /**
     * 是否在侧边栏隐藏：0-显示（默认）; 1-隐藏。
     * 隐藏的菜单不渲染到侧边栏，但仍注册为路由，可通过 URL 直接访问
     * （如知识库详情页 /knowledge/detail）。
     */
    @TableField(value = "hidden")
    private Integer hidden;

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
