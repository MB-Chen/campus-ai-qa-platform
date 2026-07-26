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

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 MCP 工具注册表（文档 5.12.1）—— 内存 Map。
 *
 * 注册双路径：
 *  ① @PostConstruct 自动注册 Spring 容器内所有 McpToolExecutor Bean（本地工具）
 *  ② 远程 MCP Server 发现的工具手动 register（见 McpClientAutoConfiguration，后续）
 *
 * MCP 功能默认关闭，仅在 {@code app.mcp.enabled=true} 时装配。
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class DefaultMcpToolRegistry implements McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpToolRegistry.class);

    private final Map<String, McpToolExecutor> executorMap = new ConcurrentHashMap<>();
    private final List<McpToolExecutor> springExecutors;

    public DefaultMcpToolRegistry(List<McpToolExecutor> springExecutors) {
        this.springExecutors = springExecutors != null ? springExecutors : List.of();
    }

    @PostConstruct
    public void init() {
        for (McpToolExecutor executor : springExecutors) {
            register(executor);
        }
        if (!springExecutors.isEmpty()) {
            log.info("[McpRegistry] 已注册 {} 个本地工具: {}",
                    springExecutors.size(), executorMap.keySet());
        }
    }

    @Override
    public void register(McpToolExecutor executor) {
        executorMap.put(executor.getToolId(), executor);
    }

    @Override
    public void unregister(String toolId) {
        executorMap.remove(toolId);
    }

    @Override
    public McpToolExecutor getExecutor(String toolId) {
        return executorMap.get(toolId);
    }

    @Override
    public List<McpToolExecutor> listAllTools() {
        return new ArrayList<>(executorMap.values());
    }

    @Override
    public boolean contains(String toolId) {
        return executorMap.containsKey(toolId);
    }

    @Override
    public int size() {
        return executorMap.size();
    }
}
