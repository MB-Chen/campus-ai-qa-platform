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
import cn.hutool.jwt.JWT;

import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具：签发 / 校验 / 解析
 * <p>
 * token 放在请求头 token 中（前端 alova 约定），失效返回 912。
 */
public class JwtUtils {

    private JwtUtils() {
    }

    /**
     * 生成 token，payload 中携带用户基础信息
     *
     * @param secret   签名密钥
     * @param expireAt 过期时间戳（秒），null 表示不过期
     */
    public static String create(String secret, Integer adminId, Integer roleId,
                                String nickname, Long expireAt) {
        Map<String, Object> payload = new HashMap<>(8);
        payload.put("adminId", adminId);
        payload.put("roleId", roleId);
        payload.put("nickname", nickname);
        if (expireAt != null) {
            payload.put("exp", expireAt);
        }
        return JWT.create()
                .addPayloads(payload)
                .setKey(secret.getBytes())
                .sign();
    }

    /**
     * 校验 token（签名 + 过期）
     */
    public static boolean verify(String token, String secret) {
        try {
            return JWT.of(token).setKey(secret.getBytes()).validate(0);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 token 中解析管理员 id
     *
     * @param token JWT
     * @return 管理员 id
     */
    public static Integer getAdminId(String token) {
        return Convert.toInt(JWT.of(token).getPayload("adminId"));
    }

    /**
     * 从 token 中解析角色 id
     *
     * @param token JWT
     * @return 角色 id
     */
    public static Integer getRoleId(String token) {
        return Convert.toInt(JWT.of(token).getPayload("roleId"));
    }

    /**
     * 从 token 中解析昵称
     *
     * @param token JWT
     * @return 昵称
     */
    public static String getNickname(String token) {
        return Convert.toStr(JWT.of(token).getPayload("nickname"));
    }
}
