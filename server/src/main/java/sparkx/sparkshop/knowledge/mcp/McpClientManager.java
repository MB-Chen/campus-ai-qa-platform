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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.entity.McpServer;
import sparkx.sparkshop.knowledge.vo.McpTestResultVo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 客户端连接池管理（langchain4j-mcp）。
 *
 * <p>按 {@code serverId} 缓存 {@link McpClient}，避免每次调用重建连接（SSE 是长连接）。
 * 配置/凭据/启停变更时调 {@link #close(Integer)} 强制关闭重连。
 *
 * <h2>传输类型</h2>
 * 两种传输配置在底层统一用 {@link StreamableHttpMcpTransport}（它是 legacy HttpMcpTransport 的扩展，
 * 也是 langchain4j 官方唯一推荐的 HTTP 传输；legacy SSE 专用的 HttpMcpTransport 已标记 forRemoval，
 * 这里不再使用）。区别仅在日志标识，便于用户在管理页区分：
 * <ul>
 *   <li>{@code http_streamable} → 标准 Streamable HTTP（新规范，推荐）</li>
 *   <li>{@code sse} → 同样走 {@link StreamableHttpMcpTransport}（向后兼容标签，
 *       服务端需同时支持 Streamable HTTP 才能连上；纯 legacy SSE server 建议改选 http_streamable 类型）</li>
 * </ul>
 *
 * <h2>认证头注入</h2>
 * {@code none} 忽略；{@code api_key} 注入 {@code apiKeyHeader}（默认 {@code X-API-Key}）；
 * {@code bearer} 注入 {@code Authorization: Bearer {token}}；最后叠加自定义请求头。
 *
 * <p>⚠️ 本类内 {@link #MAPPER} 为 private static final 字段（非 Bean），
 * 故意不依赖 Spring 全局 ObjectMapper，避免暴露 @Bean ObjectMapper 压制
 * Spring Boot Jackson 自动配置（那会导致 MVC 序列化 java.time.* 失败）。
 */
@Component
public class McpClientManager {

    private static final Logger log = LoggerFactory.getLogger(McpClientManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 连接池：serverId → McpClient */
    private final Map<Integer, McpClient> clients = new ConcurrentHashMap<>();

    /**
     * 获取或创建某服务的 MCP 客户端（双重检查锁）。
     *
     * @param server MCP 服务配置
     * @return 已连接的客户端
     * @throws Exception 连接/初始化失败
     */
    public McpClient getOrCreate(McpServer server) throws Exception {
        if (server == null || server.getId() == null) {
            throw new IllegalArgumentException("MCP 服务配置为空");
        }
        Integer id = server.getId();
        McpClient cached = clients.get(id);
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = clients.get(id);
            if (cached != null) {
                return cached;
            }
            McpClient client = buildClient(server);
            clients.put(id, client);
            log.info("[MCP] 已建立连接：server={}({}) transport={} url={}",
                    server.getName(), id, server.getTransportType(), server.getUrl());
            return client;
        }
    }

    /**
     * 构造一个新客户端。
     *
     * <p>两种传输配置（{@code sse} / {@code http_streamable}）底层统一用
     * {@link StreamableHttpMcpTransport}（legacy SSE 专用的 HttpMcpTransport 已标记 forRemoval，不再使用；
     * StreamableHttpMcpTransport 是它的扩展，也是官方推荐传输）。区别仅作日志标识，
     * 便于用户在管理页区分来源。认证头 + 自定义头统一通过 {@code customHeaders} 注入。
     */
    private McpClient buildClient(McpServer server) throws Exception {
        Map<String, String> headers = resolveHeaders(server);
        int timeoutSec = server.getTimeoutSec() == null || server.getTimeoutSec() <= 0 ? 30 : server.getTimeoutSec();
        Duration timeout = Duration.ofSeconds(timeoutSec);

        String transport = server.getTransportType() == null ? "" : server.getTransportType();
        if (!"http_streamable".equalsIgnoreCase(transport) && !"sse".equalsIgnoreCase(transport)) {
            throw new IllegalArgumentException("不支持的传输类型: " + transport + "（仅支持 sse / http_streamable）");
        }

        StreamableHttpMcpTransport.Builder b = StreamableHttpMcpTransport.builder()
                .url(server.getUrl())
                .timeout(timeout);
        if (!headers.isEmpty()) {
            b.customHeaders(headers);
        }
        return new DefaultMcpClient.Builder()
                .key("mcp-" + server.getId())
                .transport(b.build())
                .cacheToolList(false)
                .build();
    }

    /**
     * 关闭某服务的所有缓存连接（配置/凭据/启停变更时调用，强制下次重建）。
     */
    public void close(Integer serverId) {
        if (serverId == null) return;
        McpClient client = clients.remove(serverId);
        if (client != null) {
            try {
                client.close();
                log.info("[MCP] 已关闭连接：serverId={}", serverId);
            } catch (Exception e) {
                log.warn("[MCP] 关闭连接异常 serverId={}: {}", serverId, e.getMessage());
            }
        }
    }

    /**
     * 关闭所有连接（应用关闭时调用）。
     */
    public void closeAll() {
        for (Integer id : new ArrayList<>(clients.keySet())) {
            close(id);
        }
    }

    /**
     * 测试连接：连一次 → listTools + listResources → 返回结构化结果。
     * 测试完主动关闭，不污染连接池。
     */
    public McpTestResultVo testConnection(McpServer server) {
        McpTestResultVo vo = new McpTestResultVo();
        long start = System.currentTimeMillis();
        McpClient client = null;
        try {
            client = getOrCreate(server);
            // listTools
            List<ToolSpecification> tools = client.listTools();
            List<McpTestResultVo.Tool> toolList = new ArrayList<>();
            if (tools != null) {
                for (ToolSpecification t : tools) {
                    McpTestResultVo.Tool item = new McpTestResultVo.Tool();
                    item.setName(t.name());
                    item.setDescription(t.description());
                    // 注意：不能直接 MAPPER.writeValueAsString(t.parameters()) ——
                    // langchain4j 的 JsonObjectSchema 等模型类没有 JavaBean getter，Jackson 无法序列化，
                    // 会抛 "No serializer found for class ...JsonObjectSchema ... no properties discovered"。
                    // 改用 schemaToJson 递归重建为标准 JSON Schema 字符串（同时把 type 关键词补回来）。
                    item.setInputSchema(schemaToJson(t.parameters()));
                    toolList.add(item);
                }
            }
            vo.setTools(toolList);
            // listResources（部分 server 不支持资源，失败忽略）
            List<McpResource> resources = null;
            try {
                resources = client.listResources();
            } catch (Exception e) {
                log.debug("[MCP] listResources 不支持或失败（忽略）: {}", e.getMessage());
            }
            List<McpTestResultVo.Resource> resList = new ArrayList<>();
            if (resources != null) {
                for (McpResource r : resources) {
                    McpTestResultVo.Resource item = new McpTestResultVo.Resource();
                    item.setUri(r.uri());
                    item.setName(r.name());
                    item.setDescription(r.description());
                    item.setMimeType(r.mimeType());
                    resList.add(item);
                }
            }
            vo.setResources(resList);
            vo.setSuccess(true);
            vo.setMessage("连接成功，发现 " + toolList.size() + " 个工具" + (resList.isEmpty() ? "" : "，" + resList.size() + " 个资源"));
        } catch (Exception e) {
            vo.setSuccess(false);
            vo.setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            log.warn("[MCP] 测试连接失败 server={}({}): {}",
                    server.getName(), server.getId(), e.getMessage());
        } finally {
            // 临时配置（testConnect 时 id=-1，尚未落库）测试完必须关闭并移出池，避免污染连接池。
            // 已正式保存的服务（id>0）测试完保留复用（后续真实调用直接命中缓存）。
            Integer sid = server.getId();
            if (sid != null && sid <= 0) {
                close(sid);
            }
            vo.setLatencyMs(System.currentTimeMillis() - start);
        }
        return vo;
    }

    /**
     * 调用工具（供 DefaultMcpToolService 用）。
     *
     * <p>{@link McpClient#executeTool} 返回 {@link ToolExecutionResult}（非 String），
     * 这里把 {@code resultContents()}（{@code List<Content>}）拍平成文本：
     * <ul>
     *   <li>{@link TextContent} → 直接取 {@code text()}</li>
     *   <li>{@link ImageContent} → 占位 {@code [image: mime=xxx]}（图片 base64 不塞进 LLM 文本上下文）</li>
     *   <li>其他 Content → 取 {@code type()} 简要标注</li>
     * </ul>
     * 多个 content 用 {@code \n} 拼接。若 {@code result.isError()} 为真，前缀加 {@code [ERROR]}。
     *
     * @param server   服务配置
     * @param toolName MCP 原始工具名
     * @param argsJson 入参 JSON 字符串
     * @return 工具结果文本（含 [ERROR] 前缀表示工具执行报错）
     */
    public String executeTool(McpServer server, String toolName, String argsJson) throws Exception {
        McpClient client = getOrCreate(server);
        ToolExecutionRequest req = ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(argsJson == null ? "{}" : argsJson)
                .build();
        ToolExecutionResult result = client.executeTool(req);
        return renderToolResult(result);
    }

    /**
     * 把 {@link ToolExecutionResult} 的 contents 拍平成文本。
     *
     * <p>工具结果内容（{@code resultContents()} 返回 {@code List<Content>}）可能是文本、图片、音频等：
     * <ul>
     *   <li>{@link TextContent} → 取 {@code text()} 直接拼</li>
     *   <li>其他（图片/音频等）→ 用 {@code [内容类型: xxx]} 占位（二进制 base64 不塞进 LLM 文本上下文）</li>
     * </ul>
     * 多个 content 用 {@code \n} 拼接；{@code isError()=true} 时前缀加 {@code [ERROR]}。
     */
    private String renderToolResult(ToolExecutionResult result) {
        if (result == null) return "";
        List<Content> contents = result.resultContents();
        boolean isError = Boolean.TRUE.equals(result.isError());
        if (contents == null || contents.isEmpty()) {
            return isError ? "[ERROR] (空结果)" : "";
        }
        StringBuilder sb = new StringBuilder();
        if (isError) {
            sb.append("[ERROR] ");
        }
        boolean first = true;
        for (Content c : contents) {
            if (!first) sb.append("\n");
            first = false;
            if (c instanceof TextContent tc) {
                sb.append(tc.text());
            } else {
                // 非文本内容（图片/音频等），用类型占位，不输出 base64
                sb.append("[内容类型: ").append(c.type()).append("]");
            }
        }
        return sb.toString();
    }

    /**
     * 列出工具规格（供 Service 刷新工具快照用）。
     */
    public List<ToolSpecification> listTools(McpServer server) throws Exception {
        McpClient client = getOrCreate(server);
        return client.listTools();
    }

    // 工具方法

    /**
     * 解析请求头（认证头 + 自定义头）。认证头按 authType 注入，自定义头最后叠加（优先级更高）。
     */
    private Map<String, String> resolveHeaders(McpServer server) {
        Map<String, String> headers = new LinkedHashMap<>();
        String authType = server.getAuthType() == null ? "none" : server.getAuthType();
        JsonNode authCfg = parseJson(server.getAuthConfig());
        switch (authType) {
            case "api_key" -> {
                String apiKey = textOf(authCfg, "apiKey");
                if (apiKey != null && !apiKey.isBlank()) {
                    String headerName = textOr(authCfg, "apiKeyHeader", "X-API-Key");
                    headers.put(headerName, apiKey);
                }
            }
            case "bearer" -> {
                String token = textOf(authCfg, "token");
                if (token != null && !token.isBlank()) {
                    headers.put("Authorization", "Bearer " + token);
                }
            }
            default -> {
                // none：不注入认证头
            }
        }
        // 叠加自定义请求头
        JsonNode custom = parseJson(server.getHeaders());
        if (custom != null && custom.isObject()) {
            custom.fields().forEachRemaining(e -> {
                if (e.getValue() != null && !e.getValue().isNull()) {
                    headers.put(e.getKey(), e.getValue().asText());
                }
            });
        }
        return headers;
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return MAPPER.createObjectNode();
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            log.warn("[MCP] JSON 解析失败，按空对象处理: {}", e.getMessage());
            return MAPPER.createObjectNode();
        }
    }

    private static String textOf(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) return null;
        return v.asText();
    }

    private static String textOr(JsonNode node, String field, String def) {
        String v = textOf(node, field);
        return v == null || v.isBlank() ? def : v;
    }

    // JSON Schema 转换（langchain4j JsonObjectSchema → 标准 JSON Schema 字符串）

    /**
     * 把 langchain4j 的 {@link JsonSchemaElement} 转换为标准 JSON Schema 字符串。
     *
     * <p>⚠️ 不能直接 {@code MAPPER.writeValueAsString(t.parameters())}：
     * langchain4j 的 {@code JsonObjectSchema} 等模型类没有 JavaBean getter（方法名为
     * {@code description()} 而非 {@code getDescription()}），也没有 {@code @JsonProperty} 注解，
     * Jackson 无法序列化，会抛
     * "No serializer found for class ...JsonObjectSchema ... no properties discovered"。
     *
     * <p>此外 langchain4j 的模型是有损的（如 {@code JsonStringSchema} 仅保留 description，
     * 丢弃 type/minLength 等），这里按具体子类型递归重建，把 {@code type} 关键词补回来，
     * 输出下游 {@code DefaultMcpToolService#buildToolDefinition} 期望的标准 JSON Schema
     * （{@code properties} 下每个参数含 {@code type}/{@code description}，配 {@code required} 数组）。
     *
     * @param schema langchain4j schema 元素（可为 null）
     * @return JSON Schema 字符串；null/异常时返回 null
     */
    private static String schemaToJson(JsonSchemaElement schema) {
        if (schema == null) return null;
        try {
            return MAPPER.writeValueAsString(schemaToNode(schema));
        } catch (Exception e) {
            log.warn("[MCP] 输入 schema 转 JSON 失败（降级为 null）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 递归把 {@link JsonSchemaElement} 转为 Jackson {@link JsonNode}（标准 JSON Schema 结构）。
     * 按具体子类型映射出 {@code type} 关键词，覆盖 langchain4j 模型丢失的 type 信息。
     */
    private static JsonNode schemaToNode(JsonSchemaElement el) {
        if (el == null) return MAPPER.nullNode();
        ObjectNode node = MAPPER.createObjectNode();
        if (el instanceof JsonObjectSchema o) {
            node.put("type", "object");
            if (o.description() != null) node.put("description", o.description());
            if (o.additionalProperties() != null) node.put("additionalProperties", o.additionalProperties());
            ObjectNode props = MAPPER.createObjectNode();
            if (o.properties() != null) {
                o.properties().forEach((k, v) -> props.set(k, schemaToNode(v)));
            }
            node.set("properties", props);
            if (o.required() != null && !o.required().isEmpty()) {
                ArrayNode req = MAPPER.createArrayNode();
                o.required().forEach(req::add);
                node.set("required", req);
            }
            if (o.definitions() != null && !o.definitions().isEmpty()) {
                ObjectNode defs = MAPPER.createObjectNode();
                o.definitions().forEach((k, v) -> defs.set(k, schemaToNode(v)));
                node.set("definitions", defs);
            }
            return node;
        }
        if (el instanceof JsonStringSchema s) {
            node.put("type", "string");
            if (s.description() != null) node.put("description", s.description());
            return node;
        }
        if (el instanceof JsonNumberSchema n) {
            node.put("type", "number");
            if (n.description() != null) node.put("description", n.description());
            return node;
        }
        if (el instanceof JsonIntegerSchema n) {
            node.put("type", "integer");
            if (n.description() != null) node.put("description", n.description());
            return node;
        }
        if (el instanceof JsonBooleanSchema b) {
            node.put("type", "boolean");
            if (b.description() != null) node.put("description", b.description());
            return node;
        }
        if (el instanceof JsonNullSchema) {
            node.put("type", "null");
            return node;
        }
        if (el instanceof JsonEnumSchema en) {
            node.put("type", "string");
            if (en.description() != null) node.put("description", en.description());
            if (en.enumValues() != null) {
                ArrayNode arr = MAPPER.createArrayNode();
                en.enumValues().forEach(arr::add);
                node.set("enum", arr);
            }
            return node;
        }
        if (el instanceof JsonArraySchema a) {
            node.put("type", "array");
            if (a.description() != null) node.put("description", a.description());
            if (a.items() != null) node.set("items", schemaToNode(a.items()));
            return node;
        }
        if (el instanceof JsonReferenceSchema r) {
            if (r.reference() != null) node.put("$ref", r.reference());
            if (r.description() != null) node.put("description", r.description());
            return node;
        }
        if (el instanceof JsonAnyOfSchema any) {
            if (any.description() != null) node.put("description", any.description());
            if (any.anyOf() != null) {
                ArrayNode arr = MAPPER.createArrayNode();
                any.anyOf().forEach(e -> arr.add(schemaToNode(e)));
                node.set("anyOf", arr);
            }
            return node;
        }
        if (el instanceof JsonRawSchema raw) {
            if (raw.schema() != null) {
                try {
                    JsonNode parsed = MAPPER.readTree(raw.schema());
                    if (raw.description() != null && parsed.isObject() && !parsed.has("description")) {
                        ((ObjectNode) parsed).put("description", raw.description());
                    }
                    return parsed;
                } catch (Exception ignored) {
                    // 解析失败则降级为带 description 的空对象
                }
            }
            if (raw.description() != null) node.put("description", raw.description());
            return node;
        }
        if (el instanceof JsonSchema s) {
            return s.rootElement() != null ? schemaToNode(s.rootElement()) : node;
        }
        // 未知类型兜底：空对象
        return node;
    }
}
