// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Set;

/**
 * MinerU 基础设施装配。
 *
 * 与 LLM 容错层客户端（{@link InfraAiConfig}）隔离，单独注册：
 *  - {@code mineruHttpClient}：MinerU 专用 HTTP 客户端。读超时较长（PDF 解析耗时），
 *    与模型 SSE 流式客户端互不影响。
 *
 * ⚠️ 注意：此处**不要**再声明任何 {@code @Bean ObjectMapper}。Spring Boot 的
 * {@code JacksonAutoConfiguration} 通过 {@code @ConditionalOnMissingBean} 注册全局
 * {@link com.fasterxml.jackson.databind.ObjectMapper}（含 jsr310/JavaTimeModule + 应用
 * {@code spring.jackson.*} 配置）。一旦本项目暴露任意 {@code ObjectMapper} Bean，
 * 该自动配置 Bean 即被压制，导致 Spring MVC 回退到无 jsr310 的裸 ObjectMapper，所有
 * {@code java.time.LocalDateTime} 等日期字段序列化都会报
 * "Java 8 date/time type not supported by default"。
 * MinerU 专用的 ObjectMapper 由 {@code MinerUClient} 内部以 private 字段持有，
 * 既能隔离全局配置，又不会污染 MVC 的 ObjectMapper。
 *
 * 同时提供轻量 SSRF 校验（{@link #validateOutboundUrl}）：拒绝指向明显私网/回环地址的自建 endpoint。
 */
@Configuration
public class MinerUConfig {

    /** MinerU HTTP 客户端：连接 30s，读 1200s（自建同步解析可达 1000s+），写 120s */
    @Bean("mineruHttpClient")
    public OkHttpClient mineruHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(1200))
                .writeTimeout(Duration.ofSeconds(120))
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * 本机回环白名单：自建 MinerU 与 Java 后端同机部署时，endpoint 常为
     * {@code http://127.0.0.1:8600}。自建 endpoint 来源于管理员后台配置（可信来源），
     * 本机互访是设计目标，故对回环字面量放行。私网段（10.* / 172.16-31.* / 192.168.*）
     * 仍走 DNS 解析判定，保持对非本机内网地址的防护。
     */
    private static final Set<String> LOOPBACK_WHITELIST =
            Set.of("localhost", "127.0.0.1", "[::1]", "::1", "0.0.0.0", "[::]");

    /**
     * 轻量 SSRF 校验：拒绝指向私有 / 链路本地 / 元数据地址的出站 URL；
     * 对本机回环字面量（localhost / 127.0.0.1 / ::1）放行，适配自建同机部署。
     *
     * @param url 待校验的完整 URL
     * @throws IllegalArgumentException 当 host 解析到内网/元数据地址时
     */
    public static void validateOutboundUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("MinerU endpoint 为空");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("MinerU endpoint 格式非法: " + url);
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("MinerU endpoint 缺少 host: " + url);
        }
        // 主机名字面量直接判定
        String h = host.toLowerCase();
        // 云厂商元数据地址：始终拒绝（防止实例被诱导拉取临时凭证）
        if (h.equals("metadata.google.internal")) {
            throw new IllegalArgumentException("MinerU endpoint 指向云元数据地址，疑似 SSRF: " + url);
        }
        // 本机回环白名单：自建同机部署场景放行（含 IPv6 回环与通配）
        if (LOOPBACK_WHITELIST.contains(h) || h.endsWith(".localhost")) {
            return;
        }
        // 解析所有 A/AAAA 记录，任一落内网即拒绝（防止 DNS rebinding）
        try {
            InetAddress[] all = InetAddress.getAllByName(host);
            for (InetAddress addr : all) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                        || addr.isMulticastAddress()) {
                    throw new IllegalArgumentException(
                            "MinerU endpoint host=[" + host + "] 解析到内网/回环地址 " + addr.getHostAddress()
                                    + "，疑似 SSRF；如确需访问内网 MinerU，请扩展白名单。");
                }
            }
        } catch (UnknownHostException e) {
            // 解析失败不阻断（让真实请求阶段报错，保留对主机名形式 endpoint 的支持）
        }
    }
}
