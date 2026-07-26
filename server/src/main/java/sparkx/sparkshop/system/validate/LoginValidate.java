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
 * 登录参数
 */
@Data
public class LoginValidate implements Serializable {

    /**
     * 账号
     */
    @Schema(description = "账号")
    @NotEmpty(message = "账号不能为空")
    @Size(min = 2, max = 30, message = "账号或密码错误")
    private String username;

    /**
     * 密码
     */
    @Schema(description = "密码")
    @NotEmpty(message = "密码不能为空")
    @Size(min = 4, max = 64, message = "账号或密码错误")
    private String password;

    /**
     * 验证码
     */
    @Schema(description = "验证码")
    @NotEmpty(message = "验证码不能为空")
    private String captcha;

    /**
     * 验证码标识
     */
    @Schema(description = "验证码标识")
    @NotEmpty(message = "验证码已失效")
    private String key;
}
