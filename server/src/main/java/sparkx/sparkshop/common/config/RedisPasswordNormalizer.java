// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redis 密码归一化（解决 Redisson 在「无密码」场景下连接失败的问题）。
 *
 * <p>背景：本项目用 redisson-spring-boot-starter 接管 Redis 连接（且 {@code ConversationMemorySummaryService}
 * 硬依赖 {@link org.redisson.api.RedissonClient} 分布式锁，无法移除 Redisson）。Redisson 装配单机配置时，
 * 把 {@code spring.data.redis.password} 原样喂给 {@code SingleServerConfig.setPassword(...)}，<b>不做 null/空串判断</b>：
 * <ul>
 *   <li>password = {@code null}（yml 完全不写该字段）→ 不发送 AUTH，无密码 Redis 可直连 ✅</li>
 *   <li>password = 空串 {@code ""}（如 {@code password: ${REDIS_PASSWORD:}} 在未设环境变量时解析为 ""）
 *       → Redisson 仍发送 {@code AUTH ""}，无密码 Redis 报
 *       {@code ERR Client sent AUTH, but no password is set} ❌</li>
 * </ul>
 *
 * <p>而 yml 占位符 {@code ${REDIS_PASSWORD:}} 无法产出 null（默认值只能是字符串），所以这里在
 * {@link RedisProperties} 绑定完成后，把空白 password 改回 null，让「留空=无密码」成立、
 * 「填值=带密码」也成立。
 *
 * <p>注册方式：以 {@code static @Bean} 暴露 {@link BeanPostProcessor}，这是 Spring 官方推荐的早注册写法——
 * 在 {@code registerBeanPostProcessors()} 阶段（所有普通 bean 实例化之前）即激活，
 * 确保对 {@link RedisProperties} 的修改必然先于 Redisson 读取。
 *
 * <p>注：早期方案曾用 {@code EnvironmentPostProcessor} 在属性源塞 null，但 Spring 属性解析器把
 * PropertySource 返回的 null 视作「本源无此 key」继续向下查找，导致塞 null 无效，已弃用。
 *
 * @author NickBai
 */
@Configuration
public class RedisPasswordNormalizer {

    private static final Logger log = LoggerFactory.getLogger(RedisPasswordNormalizer.class);

    /**
     * 以 static @Bean 注册 BeanPostProcessor（不依赖外层 @Configuration 实例，可被尽早注册），
     * 在 {@link RedisProperties} 初始化后把空白 password 归一化为 null。
     *
     * <p>时机保证：Spring 保证被依赖的 bean 先完整初始化（含全部 BeanPostProcessor 回调）再注入，
     * 而 Redisson 的 {@code redisson()} bean 依赖 {@link RedisProperties}，故本回调必然先于 Redisson 读取执行。
     *
     * @return RedisProperties 专用的 BeanPostProcessor
     */
    @Bean
    static BeanPostProcessor redisPasswordNormalizingPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof RedisProperties props) {
                    String password = props.getPassword();
                    // 仅当 password 为空白时归一化为 null（Redisson 随之不发 AUTH）；
                    // 非空（真实密码）保持原值不动。
                    if (!StringUtils.hasText(password)) {
                        props.setPassword(null);
                        log.info("[Redis] 未配置密码，已将空白 password 归一化为 null（Redisson 将不发送 AUTH）");
                    } else {
                        log.info("[Redis] 检测到已配置密码，保持原值用于 AUTH");
                    }
                }
                return bean;
            }
        };
    }
}
