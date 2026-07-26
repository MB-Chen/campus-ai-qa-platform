// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.common.constant;

/**
 * SparkX 常量
 */
public class SparkxConstant {

    /**
     * Redis key 前缀：登录验证码 captcha:{key}
     */
    public static final String CAPTCHA_PREFIX = "captcha:";

    /**
     * Redis key 前缀：用户权限列表 auth:user:{adminId}
     */
    public static final String AUTH_USER_PREFIX = "auth:user:";

    /**
     * 默认密码（新建管理员 / 重置密码用）
     */
    public static final String DEFAULT_PASSWORD = "123456";

    /**
     * 头像默认值
     */
    public static final String DEFAULT_AVATAR = "";
}
