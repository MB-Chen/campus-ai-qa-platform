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
import sparkx.sparkshop.knowledge.graph.GraphChannel;
import sparkx.sparkshop.knowledge.graph.NoopGraphChannel;

/**
 * 知识图谱模块兜底 Bean 装配。
 *
 * <p>★ 当 {@code app.rag.knowledge-graph.enabled=false}（默认）时，
 * {@link KnowledgeGraphConfig} 整个不装配 → 没有真实的 {@link GraphChannel} Bean →
 * 这里通过 {@code @ConditionalOnMissingBean(GraphChannel.class)} 提供 {@link NoopGraphChannel} 兜底，
 * 保证 {@code NoopGraphChannel.isEnabled()} 恒为 false，本轮检索自动跳过 KG 通道。
 *
 * <p>⚠️ 用独立的 {@link GraphChannel} 标记接口（而非 {@code ConditionalRetrievalChannel}）做缺失判断，
 * 否则当系统已有 {@code IntentDirectedChannel}/{@code VectorKeywordHybridChannel} 等其他通道时，
 * {@code @ConditionalOnMissingBean(ConditionalRetrievalChannel.class)} 会误判为"已存在"而不装配兜底。
 *
 * <p>⚠️ {@code @ConditionalOnMissingBean} 必须写在 {@code @Configuration} 的 {@code @Bean} 方法上才可靠
 * （对齐 {@link McpBeansConfig} 范式）。
 */
@Configuration
public class GraphNoopConfig {

    @Bean
    @ConditionalOnMissingBean(GraphChannel.class)
    public GraphChannel noopGraphChannel() {
        return new NoopGraphChannel();
    }
}
