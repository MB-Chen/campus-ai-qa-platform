// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册登录拦截器，放行登录、验证码、swagger、静态资源
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private LoginInterceptor loginInterceptor;

    /**
     * 注册拦截器：拦截全部路径，放行登录、验证码、swagger、静态资源
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/error",
                        "/favicon.ico",
                        "/static/**",
                        // 在线客服访客端静态资源（chat 页 / 浮窗脚本，供第三方网站嵌入，免登录）
                        "/chat/**",
                        // 聊天表情包资源（静态图片，访客端/客服端共用，免登录）
                        "/emoji/**",
                        // 消息提示音资源（访客端新消息提示）
                        "/voice/**",
                        // 企业云盘：分享免登录查看
                        "/drive/shareView",
                        // 在线客服：访客端免登录 + WebSocket 握手（WS 鉴权由 ImHandshakeInterceptor 处理）
                        "/im/visitor/**",
                        "/im/config/visitor",
                        // 嵌入渠道访客端：exchange/issue/config 免登录（白名单+限流由 Service 层校验）
                        "/im/embed/exchange",
                        "/im/embed/issue",
                        "/im/embed/config",
                        "/ws/im",
                        "/ws/im/**"
                );
    }
}
