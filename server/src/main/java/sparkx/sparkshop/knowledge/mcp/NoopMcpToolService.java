// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.mcp;

import sparkx.sparkshop.knowledge.intent.NodeScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * {@link McpToolService} 的空实现 —— 仅为让管线在 WF-7（真实 MCP 落地）前可启动、可运行。
 *
 * <p>本类不再带 {@code @Component}：兜底注册改由 {@link sparkx.sparkshop.knowledge.config.McpBeansConfig}
 * 以 {@code @Bean + @ConditionalOnMissingBean(McpToolService.class)} 完成。在 {@code @Component} 上使用
 * {@code @ConditionalOnMissingBean} 求值时机不确定，会导致兜底 Bean 不被注册、启动报
 * "required a bean of type 'McpToolService' that could not be found"。
 *
 * TODO WF-7: WF-7 移植真实 McpToolService 后，删除本类（真实实现以 @Primary/@Component 注册后，
 * 上述 {@code @ConditionalOnMissingBean} 兜底自动退让）。
 *
 * 当前行为：所有 MCP 工具调用直接返回 null（等价"无 MCP 证据"），
 * RetrieveStage 会在仅有 MCP 意图、无 KB 召回时走兜底，行为安全。
 */
public class NoopMcpToolService implements McpToolService {

    private static final Logger log = LoggerFactory.getLogger(NoopMcpToolService.class);

    @Override
    public String executeTools(String question, List<NodeScore> mcpIntents) {
        log.debug("[MCP:noop] MCP 工具尚未落地（WF-7），忽略 {} 个 MCP 意图", mcpIntents.size());
        return null;
    }
}
