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
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统管理员
 */
@Data
@TableName("admin_user")
public class AdminUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 管理员 id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 登录账号
     */
    @TableField(value = "account")
    private String account;

    /**
     * 昵称
     */
    @TableField(value = "nickname")
    private String nickname;

    /**
     * 密码（md5 双重加密）
     */
    @JsonIgnore
    @TableField(value = "password")
    private String password;

    /**
     * 加密盐（每用户独立）
     */
    @JsonIgnore
    @TableField(value = "salt")
    private String salt;

    /**
     * 头像
     */
    @TableField(value = "avatar")
    private String avatar;

    /**
     * 状态 1:正常 2:禁用
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 最近登录 ip
     */
    @TableField(value = "last_login_ip")
    private String lastLoginIp;

    /**
     * 最近登录时间
     */
    @TableField(value = "last_login_time")
    private LocalDateTime lastLoginTime;

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
