// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.system.controller;

import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.system.service.ILoginService;
import sparkx.sparkshop.system.validate.LoginValidate;
import sparkx.sparkshop.system.vo.CaptchaVo;
import sparkx.sparkshop.system.vo.LoginReturnVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "登录接口")
@RestController
@RequestMapping("/login")
public class LoginController {

    @Resource
    private ILoginService loginService;

    /**
     * 获取图形验证码（base64 图片 + key）
     *
     * @return 验证码
     */
    @Operation(summary = "图形验证码")
    @GetMapping("/captcha")
    public AjaxResult<CaptchaVo> captcha() {
        return AjaxResult.success(loginService.getCaptcha());
    }

    /**
     * 执行登录：校验验证码与账号密码，返回 token、用户信息、动态菜单
     *
     * @param validate 登录参数（账号、密码、验证码、key）
     * @return 登录返回结构
     */
    @Operation(summary = "执行登录")
    @PostMapping("/doLogin")
    public AjaxResult<LoginReturnVo> doLogin(@RequestBody @Valid LoginValidate validate) {
        return AjaxResult.success(loginService.doLogin(validate));
    }
}
