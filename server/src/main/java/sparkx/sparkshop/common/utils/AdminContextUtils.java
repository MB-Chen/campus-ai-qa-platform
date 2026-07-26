// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.common.utils;

import cn.hutool.core.convert.Convert;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前登录管理员信息（从拦截器写入的 request attribute 取）
 */
public class AdminContextUtils {

    private AdminContextUtils() {
    }

    /**
     * 获取当前线程的 HTTP 请求对象
     *
     * @return 当前请求
     * @throws IllegalStateException 非 web 环境（如异步线程）下无请求上下文时抛出
     */
    public static HttpServletRequest currentRequest() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr == null) {
            throw new IllegalStateException("无 HTTP 请求上下文");
        }
        return attr.getRequest();
    }

    /**
     * 获取当前登录管理员 id
     *
     * @return 管理员 id
     */
    public static Integer getAdminId() {
        return Convert.toInt(currentRequest().getAttribute("adminId"));
    }

    /**
     * 获取当前登录管理员的角色 id
     *
     * @return 角色 id
     */
    public static Integer getRoleId() {
        return Convert.toInt(currentRequest().getAttribute("roleId"));
    }
}
