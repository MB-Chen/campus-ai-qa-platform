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

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sparkx.sparkshop.knowledge.mcp.McpToolService;
import sparkx.sparkshop.knowledge.mcp.NoopMcpToolService;

/**
 * MCP 模块 Bean 装配。
 *
 * 当前 WF-7 真实 {@link McpToolService} 尚未落地，这里以 {@code @Bean + @ConditionalOnMissingBean}
 * 提供 Noop 兜底实现，保证知识库管线（{@code RetrieveStage} 等）可正常注入并启动。
 *
 * <p>注意：{@code @ConditionalOnMissingBean} 必须写在 {@code @Configuration} 的 {@code @Bean} 方法上才可靠，
 * 不能放在被组件扫描的 {@code @Component} 上（后者求值时机不确定，可能导致兜底 Bean 不注册）。
 *
 * <p>WF-7 落地真实 {@code McpToolService}（建议以 {@code @Component}/{@code @Primary} 注册）后，
 * 本兜底 Bean 自动退让。
 */
@Configuration
public class McpBeansConfig {

    @Bean
    @ConditionalOnMissingBean(McpToolService.class)
    public McpToolService noopMcpToolService() {
        return new NoopMcpToolService();
    }
}
