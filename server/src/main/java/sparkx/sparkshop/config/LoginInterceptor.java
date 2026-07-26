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
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import sparkx.sparkshop.common.constant.SparkxConstant;
import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.common.utils.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 登录鉴权拦截器：
 * <ul>
 *   <li>读取请求头 token（或 Authorization: Bearer xxx）</li>
 *   <li>校验 JWT 签名 / 过期，失败返回 code=912（前端约定）</li>
 *   <li>超管（roleId=1）直接放行；普通角色校验 URI 是否在权限白名单，否则 403</li>
 * </ul>
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${sparkx.jwt-secret}")
    private String jwtSecret;

    @Value("${sparkx.super-role-id}")
    private Integer superRoleId;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 请求前置处理：校验登录态与接口权限
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

        Integer roleId = JwtUtils.getRoleId(token);
        Integer adminId = JwtUtils.getAdminId(token);

        // 2. 超管放行
        if (superRoleId.equals(roleId)) {
            request.setAttribute("adminId", adminId);
            request.setAttribute("roleId", roleId);
            return true;
        }

        // 3. 普通角色：URI 白名单校验
        Object cached = redisTemplate.opsForValue().get(
                SparkxConstant.AUTH_USER_PREFIX + adminId);
        if (cached == null) {
            // 权限数据过期，要求重新登录
            return write(response, AjaxResult.unauthorized());
        }
        Map<String, Object> authMap = JSONUtil.parseObj(String.valueOf(cached));
        String uri = request.getRequestURI();
        if (!authMap.containsKey(uri)) {
            return write(response, AjaxResult.forbidden());
        }

        request.setAttribute("adminId", adminId);
        request.setAttribute("roleId", roleId);
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
