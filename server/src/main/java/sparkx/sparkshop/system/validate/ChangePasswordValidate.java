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
import lombok.Data;

import java.io.Serializable;

/**
 * 修改密码参数
 */
@Data
public class ChangePasswordValidate implements Serializable {

    /**
     * 原密码
     */
    @Schema(description = "原密码")
    @NotEmpty(message = "请输入原密码")
    private String oldPwd;

    /**
     * 新密码
     */
    @Schema(description = "新密码")
    @NotEmpty(message = "请输入新密码")
    private String newPwd;
}
