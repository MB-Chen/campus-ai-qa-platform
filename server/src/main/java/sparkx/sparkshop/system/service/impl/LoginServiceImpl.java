// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.system.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import sparkx.sparkshop.common.constant.SparkxConstant;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.common.utils.ClickCaptchaResult;
import sparkx.sparkshop.common.utils.JwtUtils;
import sparkx.sparkshop.system.util.MenuTreeUtils;
import sparkx.sparkshop.common.utils.ToolUtils;
import sparkx.sparkshop.system.entity.AdminMenu;
import sparkx.sparkshop.system.entity.AdminRole;
import sparkx.sparkshop.system.entity.AdminUser;
import sparkx.sparkshop.system.mapper.AdminMenuMapper;
import sparkx.sparkshop.system.mapper.AdminRoleMapper;
import sparkx.sparkshop.system.mapper.AdminUserMapper;
import sparkx.sparkshop.system.service.ILoginService;
import sparkx.sparkshop.system.validate.LoginValidate;
import sparkx.sparkshop.system.vo.CaptchaVo;
import sparkx.sparkshop.system.vo.LoginReturnVo;
import sparkx.sparkshop.system.vo.MenuNodeVo;
import sparkx.sparkshop.system.vo.SystemUserVo;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 登录服务：验证码、账号密码校验、签发 token、生成动态菜单
 */
@Slf4j
@Service
public class LoginServiceImpl implements ILoginService {

    @Resource
    private AdminUserMapper adminUserMapper;

    @Resource
    private AdminRoleMapper adminRoleMapper;

    @Resource
    private AdminMenuMapper adminMenuMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private HttpServletRequest request;

    @Value("${sparkx.jwt-secret}")
    private String jwtSecret;

    @Value("${sparkx.jwt-expire}")
    private Long jwtExpireHours;

    @Value("${sparkx.super-role-id}")
    private Integer superRoleId;

    /**
     * 生成汉字点选验证码，存入 Redis（10 分钟有效），返回 base64 图片、key 和提示文字
     *
     * @return 验证码
     */
    @Override
    public CaptchaVo getCaptcha() {
        ClickCaptchaResult captcha = ToolUtils.createClickCaptcha();
        String key = IdUtil.simpleUUID();

        // 将汉字位置信息存入 Redis（10 分钟有效），供登录时校验
        String positionsJson = JSONUtil.toJsonStr(captcha.getCharPositions());
        redisTemplate.opsForValue().set(
                SparkxConstant.CAPTCHA_PREFIX + key,
                positionsJson,
                10, TimeUnit.MINUTES);

        CaptchaVo vo = new CaptchaVo();
        vo.setKey(key);
        vo.setImg("data:image/png;base64," + ToolUtils.imageToBase64(captcha.getImage()));
        vo.setTipChars(captcha.getTargetChars());
        return vo;
    }

    /**
     * 执行登录：校验验证码 → 查用户 → 校验密码与状态 → 生成 token →
     * 按角色计算动态菜单 → 缓存权限白名单 → 返回 token、用户信息、菜单树
     *
     * @param validate 登录参数（账号、密码、验证码、key）
     * @return 登录返回结构
     */
    @Override
    public LoginReturnVo doLogin(LoginValidate validate) {
        // 1. 校验汉字点选验证码
        String captchaKey = SparkxConstant.CAPTCHA_PREFIX + validate.getKey();
        Object cached = redisTemplate.opsForValue().get(captchaKey);
        if (cached == null) {
            throw new BusinessException("验证码已失效");
        }
        // 用后即删，防止重放
        redisTemplate.delete(captchaKey);

        // 解析后端保存的汉字位置（正确坐标 + 顺序）
        List<ClickCaptchaResult.CharPosition> correctPositions;
        try {
            String json = String.valueOf(cached);
            // Redis 的 JSON 序列化可能带类型信息或双重 JSON，统一提取 JSON 数组字符串
            // 情况1: 直接是 JSON 数组 [{...},{...}]
            // 情况2: 被 Jackson 序列化成了带引号的字符串 "[{...}]"
            if (json.startsWith("\"")) {
                json = JSONUtil.parse(json).toString();
            }
            correctPositions = JSONUtil.toList(json, ClickCaptchaResult.CharPosition.class);
        } catch (Exception e) {
            throw new BusinessException("验证码已失效");
        }

        if (!validateClickCaptcha(correctPositions, validate.getCaptcha())) {
            throw new BusinessException("验证码错误");
        }

        // 2. 查用户
        AdminUser user = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getAccount, validate.getUsername())
                .last("limit 1"));
        if (user == null) {
            throw new BusinessException("账号密码错误");
        }
        // 3. 校验密码
        if (!ToolUtils.verifyPassword(user.getPassword(), validate.getPassword(), user.getSalt())) {
            throw new BusinessException("账号密码错误");
        }
        // 4. 校验状态
        if (user.getStatus() != null && user.getStatus() == 2) {
            throw new BusinessException("该账号已被禁用");
        }

        // 5. 更新登录信息
        AdminUser update = new AdminUser();
        update.setId(user.getId());
        update.setLastLoginIp(getClientIp());
        update.setLastLoginTime(LocalDateTime.now());
        adminUserMapper.updateById(update);

        // 6. 查角色
        AdminRole role = adminRoleMapper.selectById(user.getRoleId());
        String roleName = role != null ? role.getName() : "";

        // 7. 计算菜单（仅 type=1），超管取全部，普通角色按 role.menu 过滤
        List<AdminMenu> menuList = loadMenus(user.getRoleId(), role);

        // 8. 构建权限 URI 集合并缓存（超管免校验）
        cacheAuth(user.getRoleId(), user.getId(), menuList);

        // 9. 组装返回
        SystemUserVo userInfo = new SystemUserVo(
                user.getNickname(), user.getAccount(), user.getId(),
                user.getRoleId(), roleName, user.getAvatar());

        long expireAt = System.currentTimeMillis() / 1000 + jwtExpireHours * 3600;
        String token = JwtUtils.create(jwtSecret, user.getId(), user.getRoleId(),
                user.getNickname(), expireAt);

        // 登录返回的动态路由树：仅菜单(type=1)，按钮节点不参与前端路由渲染
        List<MenuNodeVo> menuTree = MenuTreeUtils.build(menuList, true);

        LoginReturnVo vo = new LoginReturnVo();
        vo.setToken(token);
        vo.setUserInfo(userInfo);
        vo.setMenu(menuTree);
        return vo;
    }

    /**
     * 按角色加载菜单（sort DESC）。
     * 超管：仅 type=1 菜单（前端只需要菜单做路由，超管接口直接放行）；
     * 普通角色：role.menu 中的 id，菜单(type=1) 用于渲染，按钮(type=2) 用于接口鉴权白名单。
     */
    private List<AdminMenu> loadMenus(Integer roleId, AdminRole role) {
        boolean isSuper = superRoleId.equals(roleId);
        QueryWrapper<AdminMenu> qw = new QueryWrapper<AdminMenu>()
                .orderByDesc("sort");
        if (isSuper) {
            qw.eq("type", 1);
        } else {
            if (role == null || StrUtil.isBlank(role.getMenu())) {
                return List.of();
            }
            List<Integer> ids = Arrays.stream(role.getMenu().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
            if (ids.isEmpty()) {
                return List.of();
            }
            // 菜单和按钮都拉：菜单用于渲染路由，按钮 auth 用于鉴权白名单
            qw.in("id", ids);
        }
        return adminMenuMapper.selectList(qw);
    }

    /**
     * 缓存权限：超管不缓存（拦截器直接放行）；普通角色缓存其可见菜单的 auth。
     * 这里从全部 type=1 菜单里取 auth 作为接口白名单。
     */
    private void cacheAuth(Integer roleId, Integer adminId, List<AdminMenu> menuList) {
        if (superRoleId.equals(roleId)) {
            return;
        }
        Map<String, Integer> authMap = new HashMap<>();
        for (AdminMenu m : menuList) {
            if (StrUtil.isNotBlank(m.getAuth())) {
                authMap.put("/" + m.getAuth(), 1);
            }
        }
        redisTemplate.opsForValue().set(
                SparkxConstant.AUTH_USER_PREFIX + adminId,
                cn.hutool.json.JSONUtil.toJsonStr(authMap),
                30, TimeUnit.DAYS);
    }

    /**
     * 校验汉字点选验证码
     * 前端提交的 captcha 是 JSON 字符串：[{x,y},{x,y},{x,y},{x,y}]（按点击顺序）
     * 后端按 order 升序排列正确位置，逐个比对坐标是否在容差范围内
     *
     * @param correctPositions 后端保存的正确汉字位置
     * @param captcha          前端提交的点击坐标 JSON
     * @return 是否通过
     */
    private boolean validateClickCaptcha(List<ClickCaptchaResult.CharPosition> correctPositions, String captcha) {
        if (StrUtil.isBlank(captcha) || correctPositions == null || correctPositions.isEmpty()) {
            return false;
        }

        try {
            // 解析前端提交的点击坐标
            cn.hutool.json.JSONArray jsonArray = JSONUtil.parseArray(captcha);
            if (jsonArray.size() != correctPositions.stream().filter(p -> p.getOrder() >= 0).count()) {
                return false;
            }

            // 按 order 升序排列目标汉字（order >= 0 的才是目标字）
            List<ClickCaptchaResult.CharPosition> targets = correctPositions.stream()
                    .filter(p -> p.getOrder() >= 0)
                    .sorted(Comparator.comparingInt(ClickCaptchaResult.CharPosition::getOrder))
                    .toList();

            // 逐个比对：第 N 次点击应该命中第 N 个目标汉字
            for (int i = 0; i < targets.size(); i++) {
                ClickCaptchaResult.CharPosition target = targets.get(i);
                cn.hutool.json.JSONObject point = jsonArray.getJSONObject(i);

                int clickX = point.getInt("x");
                int clickY = point.getInt("y");

                // 判定点击坐标是否在目标汉字的容差范围内
                if (Math.abs(clickX - target.getX()) > ToolUtils.CLICK_TOLERANCE
                        || Math.abs(clickY - target.getY()) > ToolUtils.CLICK_TOLERANCE) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.error("验证码校验异常", e);
            return false;
        }
    }

    /**
     * 获取客户端真实 IP，依次取 X-Forwarded-For、X-Real-IP、RemoteAddr
     *
     * @return 客户端 IP
     */
    private String getClientIp() {
        if (request == null) {
            return "";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
