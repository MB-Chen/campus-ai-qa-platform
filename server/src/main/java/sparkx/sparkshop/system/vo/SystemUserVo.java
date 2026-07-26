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

/**
 * 登录返回的用户信息，对齐前端 user.ts 约定
 */
@Data
public class SystemUserVo implements Serializable {

    /**
     * 昵称
     */
    @Schema(description = "昵称")
    private String username;

    /**
     * 登录账号
     */
    @Schema(description = "登录账号")
    private String account;

    /**
     * 用户 id
     */
    @Schema(description = "用户 id")
    private Integer id;

    /**
     * 角色 id
     */
    @Schema(description = "角色 id")
    private Integer roleId;

    /**
     * 角色名称
     */
    @Schema(description = "角色名称")
    private String roleName;

    /**
     * 头像
     */
    @Schema(description = "头像")
    private String avatar;

    /** 默认无参构造 */
    public SystemUserVo() {
    }

    /**
     * 全参构造
     *
     * @param username 昵称
     * @param account  登录账号
     * @param id       用户 id
     * @param roleId   角色 id
     * @param roleName 角色名
     * @param avatar   头像
     */
    public SystemUserVo(String username, String account, Integer id, Integer roleId,
                        String roleName, String avatar) {
        this.username = username;
        this.account = account;
        this.id = id;
        this.roleId = roleId;
        this.roleName = roleName;
        this.avatar = avatar;
    }
}
