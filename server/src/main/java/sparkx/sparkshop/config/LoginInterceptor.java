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

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.common.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录鉴权拦截器：
 * <ul>
 *   <li>读取请求头 token（或 Authorization: Bearer xxx）</li>
 *   <li>校验 JWT 签名 / 过期，失败返回 code=912（前端约定）</li>
 *   <li>校验通过即放行（角色体系已移除，所有登录用户权限一致）</li>
 * </ul>
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Value("${sparkx.jwt-secret}")
    private String jwtSecret;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 请求前置处理：校验登录态
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @return true 放行，false 拦截
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 1. 取 token：优先 token 头，兼容 Authorization: Bearer xxx
        String token = request.getHeader("token");
        if (StrUtil.isBlank(token)) {
            String auth = request.getHeader("Authorization");
            if (StrUtil.isNotBlank(auth) && auth.startsWith("Bearer ")) {
                token = auth.substring(7);
            }
        }

        if (StrUtil.isBlank(token) || !JwtUtils.verify(token, jwtSecret)) {
            return write(response, AjaxResult.unauthorized());
        }

        // 2. 校验通过，记录 adminId（roleId 已无角色体系，置 null 兼容旧读取处）
        Integer adminId = JwtUtils.getAdminId(token);
        request.setAttribute("adminId", adminId);
        request.setAttribute("roleId", null);
        return true;
    }

    /**
     * 向响应写入 JSON 并拦截请求
     *
     * @param response 响应
     * @param result   统一响应体
     * @return 固定 false（拦截）
     */
    private boolean write(HttpServletResponse response, AjaxResult<?> result) throws Exception {
        response.setStatus(200);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(mapper.writeValueAsString(result));
        return false;
    }
}
