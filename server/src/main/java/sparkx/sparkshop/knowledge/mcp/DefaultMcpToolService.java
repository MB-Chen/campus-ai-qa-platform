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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.entity.McpServer;
import sparkx.sparkshop.knowledge.entity.McpTool;
import sparkx.sparkshop.knowledge.intent.NodeScore;
import sparkx.sparkshop.knowledge.mapper.McpServerMapper;
import sparkx.sparkshop.knowledge.mapper.McpToolMapper;
import sparkx.sparkshop.knowledge.service.impl.McpServerServiceImpl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具服务真实实现 —— 接通「意图树 MCP 节点 → 工具调用 → GenerateStage」全链路。
 *
 * <p>以 {@code @Primary @Component} 注册，替换 {@code McpBeansConfig} 的 Noop 兜底
 * （{@code @ConditionalOnMissingBean} 兜底自动退让，无需删 Noop）。
 *
 * <h2>执行流程</h2>
 * <ol>
 *   <li>遍历 mcpIntents，取 {@code node.getMcpToolId()}（格式 {@code "svc{serverId}__{toolName}"}）</li>
 *   <li>解析出 serverId + toolName → 查 {@code mcp_server} 服务配置 + {@code mcp_tool} 工具快照</li>
 *   <li>用 {@link McpParameterExtractor} 从 question 提取参数（白名单过滤 schema 参数）</li>
 *   <li>{@link McpClientManager#executeTool} 调用工具</li>
 *   <li>结果拼成 {@code <tool-data name="...">...</tool-data>} 文本返回</li>
 * </ol>
 *
 * <p>错误降级：单工具失败拼成 {@code <tool-error>} 块，不中断其他工具。
 * 服务未启用/工具快照缺失/参数提取失败/调用异常等均降级处理。
 *
 * <p>⚠️ 本类内 {@link #MAPPER} 为 private static final 字段（非 Bean），避免压制 Jackson 自动配置。
 */
@Primary
@Component
public class DefaultMcpToolService implements McpToolService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpToolService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private McpServerMapper mcpServerMapper;
    @Resource
    private McpToolMapper mcpToolMapper;
    @Resource
    private McpClientManager mcpClientManager;
    /**
     * McpParameterExtractor 受 app.mcp.enabled=true 门控。用 ObjectProvider 可选注入，
     * 即使门控未开（Bean 不存在）也不阻断 DefaultMcpToolService 装配（此时参数提取降级为空参数）。
     */
    @Resource
    private ObjectProvider<McpParameterExtractor> parameterExtractorProvider;

    /**
     * 针对命中的 MCP 意图节点批量执行工具调用，返回拼接后的工具结果文本。
     *
     * <p>每个 MCP 意图节点的 {@code mcpToolId} 是工具的 {@code fullId}（格式
     * {@code svc{serverId}__{toolName}}），由 {@link McpServerServiceImpl#parseFullId(String)} 解析。
     * 工具结果拼成 {@code <tool-data name="...">...</tool-data>} 文本块，
     * 由 RetrieveStage 经 {@code ctx.setMcpContext} 透传给 GenerateStage，最终塞进 LLM user 消息的
     * {@code <tool-data>} 容器（参见 GenerateStage 第 192-194 行）。
     *
     * <p>错误降级：单工具失败拼成 {@code <tool-error>} 块，不中断其他工具；
     * 服务未启用/工具快照缺失/参数提取失败/调用异常等均降级处理，保证 RAG 管线不中断。
     *
     * @param question   当前子问题原文（作为工具入参提取来源）
     * @param mcpIntents 命中的 MCP 类型意图节点（{@code node.isMCP() == true}）
     * @return 工具结果文本（{@code <tool-data>}/{@code <tool-error>} 块的 \n 拼接），全失败时返回 null
     */
    @Override
    public String executeTools(String question, List<NodeScore> mcpIntents) {
        if (mcpIntents == null || mcpIntents.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (NodeScore ns : mcpIntents) {
            if (ns == null || ns.node() == null) continue;
            String fullId = ns.node().getMcpToolId();
            if (fullId == null || fullId.isBlank()) continue;
            String block = executeOne(question, fullId, ns.node().getParamPromptTemplate());
            if (block != null && !block.isBlank()) {
                sb.append(block).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 执行单个工具调用。
     */
    private String executeOne(String question, String fullId, String customPromptTemplate) {
        // 1. 解析 fullId → serverId + toolName
        String[] parsed = McpServerServiceImpl.parseFullId(fullId);
        if (parsed == null) {
            log.warn("[MCP] 工具 fullId 格式非法，跳过: {}", fullId);
            return errorBlock(fullId, "工具ID格式非法（应为 svc{id}__{toolName}）");
        }
        Integer serverId;
        String toolName;
        try {
            serverId = Integer.parseInt(parsed[0]);
            toolName = parsed[1];
        } catch (NumberFormatException e) {
            return errorBlock(fullId, "服务ID非法");
        }

        // 2. 查服务配置（校验启用）
        McpServer server = mcpServerMapper.selectById(serverId);
        if (server == null) {
            return errorBlock(fullId, "MCP服务不存在(id=" + serverId + ")");
        }
        if (Boolean.FALSE.equals(server.getEnabled())) {
            log.debug("[MCP] 服务未启用，跳过: {}({})", server.getName(), serverId);
            return null;
        }

        // 3. 查工具快照（校验存在 + 取 inputSchema 重建 ToolDefinition）
        McpTool toolSnapshot = findTool(serverId, toolName);
        if (toolSnapshot == null) {
            return errorBlock(fullId, "工具快照不存在，请在MCP服务管理页点击「刷新工具」");
        }
        ToolDefinition toolDef = buildToolDefinition(toolSnapshot);

        // 4. 提取参数（McpParameterExtractor 可选；缺失则空参数）
        Map<String, Object> params;
        try {
            params = extractParametersSafe(question, toolDef, customPromptTemplate);
        } catch (Exception e) {
            log.warn("[MCP] 参数提取失败，用空参数降级: {}", e.getMessage());
            params = Map.of();
        }

        // 5. 调用工具（带重试）
        String argsJson;
        try {
            argsJson = MAPPER.writeValueAsString(params);
        } catch (Exception e) {
            argsJson = "{}";
        }
        int retry = server.getRetryCount() == null ? 1 : server.getRetryCount();
        Exception lastErr = null;
        for (int attempt = 0; attempt <= retry; attempt++) {
            try {
                String result = mcpClientManager.executeTool(server, toolName, argsJson);
                log.info("[MCP] 工具调用成功 {} (attempt={}): {}", fullId, attempt,
                        result == null ? null : (result.length() > 100 ? result.substring(0, 100) + "..." : result));
                return dataBlock(toolName, result);
            } catch (Exception e) {
                lastErr = e;
                log.debug("[MCP] 工具调用失败 {} (attempt={}): {}", fullId, attempt, e.getMessage());
                if (attempt < retry) {
                    // 配置可能已失效，关闭重连再试
                    mcpClientManager.close(serverId);
                }
            }
        }
        return errorBlock(fullId, "工具调用失败: " + (lastErr == null ? "未知错误" : lastErr.getMessage()));
    }

    /** McpParameterExtractor 是可选 Bean（受 app.mcp.enabled 门控），用 ObjectProvider 安全取用 */
    private Map<String, Object> extractParametersSafe(String question, ToolDefinition toolDef, String customPrompt) {
        McpParameterExtractor extractor = parameterExtractorProvider.getIfAvailable();
        if (extractor == null) {
            return Map.of();
        }
        Map<String, Object> result = extractor.extractParameters(question, toolDef, customPrompt);
        return result == null ? Map.of() : result;
    }

    /** 查工具快照 */
    private McpTool findTool(Integer serverId, String toolName) {
        List<McpTool> tools = mcpToolMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<McpTool>()
                .eq(McpTool::getServerId, serverId)
                .eq(McpTool::getToolName, toolName)
                .last("LIMIT 1"));
        return tools.isEmpty() ? null : tools.get(0);
    }

    /**
     * 从工具快照的 inputSchema（JSON Schema 原文）重建 ToolDefinition（Map<String, ParamDef>）。
     * 仅取 properties 下的一层参数（MCP 工具入参通常是扁平对象）。
     */
    private ToolDefinition buildToolDefinition(McpTool tool) {
        Map<String, ToolDefinition.ParamDef> schema = new LinkedHashMap<>();
        JsonNode root = parseJson(tool.getInputSchema());
        JsonNode properties = root.get("properties");
        JsonNode requiredArr = root.get("required");
        java.util.Set<String> required = new java.util.HashSet<>();
        if (requiredArr != null && requiredArr.isArray()) {
            requiredArr.forEach(n -> required.add(n.asText()));
        }
        if (properties != null && properties.isObject()) {
            properties.fields().forEachRemaining(e -> {
                String name = e.getKey();
                JsonNode def = e.getValue();
                String type = def != null && def.has("type") ? def.get("type").asText() : "string";
                boolean req = required.contains(name);
                String desc = def != null && def.has("description") ? def.get("description").asText() : "";
                JsonNode defaultNode = def != null ? def.get("default") : null;
                String defaultVal = (defaultNode == null || defaultNode.isNull()) ? null : defaultNode.asText();
                schema.put(name, new ToolDefinition.ParamDef(type, req, desc, defaultVal));
            });
        }
        return new ToolDefinition(tool.getToolName(), tool.getDescription(), schema);
    }

    private String dataBlock(String toolName, String content) {
        return "<tool-data name=\"" + toolName + "\">\n" + (content == null ? "" : content) + "\n</tool-data>";
    }

    private String errorBlock(String fullId, String msg) {
        return "<tool-error name=\"" + fullId + "\">\n" + msg + "\n</tool-error>";
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return MAPPER.createObjectNode();
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }
}
