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
 * 验证码返回
 */
@Data
public class CaptchaVo implements Serializable {

    /**
     * 验证码标识，登录时带回
     */
    @Schema(description = "验证码标识，登录时带回")
    private String key;

    /**
     * base64 图片
     */
    @Schema(description = "base64 图片")
    private String img;

    /**
     * 需要按顺序点击的目标汉字提示（按顺序）
     */
    @Schema(description = "需要按顺序点击的目标汉字")
    private List<String> tipChars;
}
