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
 * 登录返回结构
 * <pre>
 * { token, userInfo, menu }
 * </pre>
 */
@Data
public class LoginReturnVo implements Serializable {

    /**
     * 访问令牌
     */
    @Schema(description = "访问令牌")
    private String token;

    /**
     * 用户信息
     */
    @Schema(description = "用户信息")
    private SystemUserVo userInfo;

    /**
     * 动态菜单树
     */
    @Schema(description = "动态菜单树")
    private List<MenuNodeVo> menu;
}
