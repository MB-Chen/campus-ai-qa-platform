// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.system.service;

import sparkx.sparkshop.system.validate.LoginValidate;
import sparkx.sparkshop.system.vo.CaptchaVo;
import sparkx.sparkshop.system.vo.LoginReturnVo;

public interface ILoginService {

    /**
     * 获取图形验证码
     */
    CaptchaVo getCaptcha();

    /**
     * 执行登录
     */
    LoginReturnVo doLogin(LoginValidate validate);
}
