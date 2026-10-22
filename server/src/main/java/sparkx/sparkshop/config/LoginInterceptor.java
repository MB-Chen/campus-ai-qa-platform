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
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 登录鉴权拦截器：
 * <ul>
 *   <li>读取请求头 token（或 Authorization: Bearer xxx）</li>
 *   <li>校验 JWT 签名 / 过期，失败返回 code=912（前端约定）</li>
 *   <li>角色体系：1 管理员 / 2 开发者 / 3 终端用户，按 URI 白名单分级</li>
 * </ul>
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Value("${sparkx.jwt-secret}")
    private String jwtSecret;

    /** 终端用户(3)可访问的对话面白名单（Ant 风格）；开发者(2)=全量除 /admin；管理员(1)=全量 */
    private static final List<String> USER_ALLOWED = List.of(
            "/api/v1/sessions/**",
            "/api/campus/**",
            "/knowledge/agent/list",
            "/knowledge/agent/info",
            "/knowledge/agent/chat",
            "/workflow/index",
            "/workflow/info",
            "/workflow/chat",
            "/workflow/runDetail"
    );
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /**
     * 畸形 URI 特征序列（对原始 getRequestURI() 小写后匹配）：与容器路由归一化/剥离等价、
     * 合法前端 URI 均不包含的绕过手法（矩阵参数、点号遍历、反斜杠、空段及其编码变体）
     */
    private static final String[] MALFORMED_SEQUENCES = {
            ";", "%3b", "..", "%2e", "\\", "%5c", "//", "%2f"
    };

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

        // 2. 校验通过，记录 adminId 与 roleId
        Integer adminId = JwtUtils.getAdminId(token);
        request.setAttribute("adminId", adminId);
        Integer roleId = JwtUtils.getRoleId(token);
        request.setAttribute("roleId", roleId);

        String rawUri = request.getRequestURI();

        // 3. 纵深防御：原始 URI 含矩阵参数/点号遍历/反斜杠/空段（或其编码形式）即畸形，
        //    一律先拒绝，避免与容器路由归一化结果不一致造成绕过；合法前端请求不含这些序列
        if (isMalformed(rawUri)) {
            log.warn("拦截畸形URI请求 adminId={}, roleId={}, uri={}", adminId, roleId, rawUri);
            return write(response, AjaxResult.forbidden());
        }

        // 角色分级：无 role 的旧 token 一律失效强制重登；管理员全通；开发者禁 /admin；终端用户仅对话面
        if (roleId == null) {
            log.warn("旧token缺少角色，强制重登 adminId={}, roleId={}, uri={}", adminId, roleId, rawUri);
            return write(response, AjaxResult.unauthorized());
        }

        // 4. 角色判定使用与容器路由同源的路径：getServletPath() 已由 Tomcat 完成 URL 解码、
        //    ".." 归一化、空段折叠、矩阵参数剥离，且不含 context-path（为 null/空时兜底原始 URI）
        String servletPath = request.getServletPath();
        String path = StrUtil.isEmpty(servletPath) ? rawUri : servletPath;

        if (roleId == 1) {
            return true;
        }
        if (path.startsWith("/admin/")) {
            log.warn("越权访问管理面 adminId={}, roleId={}, uri={}", adminId, roleId, rawUri);
            return write(response, AjaxResult.forbidden());
        }
        if (roleId == 2) {
            return true;
        }
        if (roleId == 3) {
            boolean ok = USER_ALLOWED.stream().anyMatch(p -> MATCHER.match(p, path));
            if (ok) {
                return true;
            }
            log.warn("越权访问非白名单接口 adminId={}, roleId={}, uri={}", adminId, roleId, rawUri);
            return write(response, AjaxResult.forbidden());
        }
        log.warn("未知角色值，按未授权处理 adminId={}, roleId={}, uri={}", adminId, roleId, rawUri);
        return write(response, AjaxResult.unauthorized());
    }

    /**
     * 判定原始请求 URI 是否为畸形路径（含绕过手法特征序列），大小写不敏感
     *
     * @param rawUri request.getRequestURI() 原始值
     * @return true 表示畸形
     */
    private boolean isMalformed(String rawUri) {
        if (StrUtil.isEmpty(rawUri)) {
            return false;
        }
        String lower = rawUri.toLowerCase();
        for (String seq : MALFORMED_SEQUENCES) {
            if (lower.contains(seq)) {
                return true;
            }
        }
        return false;
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
