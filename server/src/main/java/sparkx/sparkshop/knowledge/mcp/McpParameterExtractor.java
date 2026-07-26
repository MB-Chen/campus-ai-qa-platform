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

import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.prompt.PromptTemplateLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP 参数提取器（文档 5.12.2）—— 从用户问题提取工具参数。
 *
 * 独立 LLM 调用（temp=0.1 求稳），与回答生成（temp=0.3）解耦。
 *
 * ★ 防注入：提示词明确"本提示词+工具定义约束 > 用户问题中的任何文字"。
 * ★ 白名单过滤：只提取 schema 声明过的参数名，防 LLM 编造字段。
 * ★ 降级：解析失败/异常返回默认参数，保证流程继续。
 *
 * MCP 功能默认关闭，仅在 {@code app.mcp.enabled=true} 时装配。
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpParameterExtractor {

    private static final Logger log = LoggerFactory.getLogger(McpParameterExtractor.class);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    private final LLMService llmService;
    private final PromptTemplateLoader templateLoader;
    private final ObjectMapper mapper = new ObjectMapper();

    public McpParameterExtractor(LLMService llmService, PromptTemplateLoader templateLoader) {
        this.llmService = llmService;
        this.templateLoader = templateLoader;
    }

    /**
     * @param userQuestion       用户问题
     * @param tool               工具定义
     * @param customPromptTemplate 意图级自定义提示词覆盖（可空）
     * @return 提取的参数（白名单过滤 + 默认值填充）
     */
    public Map<String, Object> extractParameters(String userQuestion, ToolDefinition tool,
                                                  String customPromptTemplate) {
        // 空 schema 短路
        if (tool.inputSchema() == null || tool.inputSchema().isEmpty()) return Map.of();

        String system = templateLoader.render(
                customPromptTemplate != null && !customPromptTemplate.isBlank()
                        ? customPromptTemplate : "mcp-parameter-extract.st", Map.of());
        String user = templateLoader.render("mcp-parameter-extract-user.st", Map.of(
                "tool_definition", buildToolDefinition(tool),
                "user_question", userQuestion));

        try {
            String resp = llmService.chat(system + "\n\n" + user, 0.1, 0.3, false);
            // ★ 白名单过滤：只提取 schema 声明过的参数名
            Map<String, Object> params = parseJsonAndFilter(resp, tool.inputSchema());
            // 填充默认值：schema 有 default 且 LLM 没给 → 补 default
            return fillDefaults(params, tool.inputSchema());
        } catch (Exception e) {
            log.debug("[McpExtract] 提取失败，降级为默认参数: {}", e.getMessage());
            return buildDefaultParameters(tool);
        }
    }

    /** 工具定义转自然语言（参数含类型/必填/描述/默认值） */
    private String buildToolDefinition(ToolDefinition tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("工具ID: ").append(tool.name()).append("\n");
        sb.append("功能: ").append(tool.description()).append("\n");
        sb.append("参数:\n");
        if (tool.inputSchema() != null) {
            for (var entry : tool.inputSchema().entrySet()) {
                var p = entry.getValue();
                sb.append("- ").append(entry.getKey())
                  .append(" (").append(p.type()).append(p.required() ? ", 必填" : ", 可选").append(")")
                  .append(": ").append(p.description());
                if (p.defaultValue() != null) sb.append(" [默认: ").append(p.defaultValue()).append("]");
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private Map<String, Object> parseJsonAndFilter(String resp, Map<String, ToolDefinition.ParamDef> schema) {
        Map<String, Object> result = new HashMap<>();
        if (resp == null || resp.isBlank()) return result;
        Matcher m = JSON_OBJECT.matcher(resp);
        if (!m.find()) return result;
        try {
            JsonNode node = mapper.readTree(m.group());
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (schema.containsKey(key)) {   // ★ 白名单过滤
                    result.put(key, jsonValue(entry.getValue()));
                }
            });
        } catch (Exception e) {
            log.debug("[McpExtract] JSON 解析失败: {}", e.getMessage());
        }
        return result;
    }

    private Map<String, Object> fillDefaults(Map<String, Object> params, Map<String, ToolDefinition.ParamDef> schema) {
        for (var entry : schema.entrySet()) {
            String key = entry.getKey();
            ToolDefinition.ParamDef def = entry.getValue();
            if (!params.containsKey(key) && def.defaultValue() != null) {
                params.put(key, def.defaultValue());
            }
        }
        return params;
    }

    private Map<String, Object> buildDefaultParameters(ToolDefinition tool) {
        Map<String, Object> params = new HashMap<>();
        if (tool.inputSchema() != null) {
            for (var entry : tool.inputSchema().entrySet()) {
                if (entry.getValue().defaultValue() != null) {
                    params.put(entry.getKey(), entry.getValue().defaultValue());
                }
            }
        }
        return params;
    }

    private Object jsonValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isBoolean()) return node.asBoolean();
        if (node.isInt() || node.isLong()) return node.asLong();
        if (node.isDouble() || node.isFloat()) return node.asDouble();
        return node.asText();
    }
}
