// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.entity.McpServer;
import sparkx.sparkshop.knowledge.entity.McpTool;
import sparkx.sparkshop.knowledge.mapper.McpServerMapper;
import sparkx.sparkshop.knowledge.mapper.McpToolMapper;
import sparkx.sparkshop.knowledge.mcp.McpClientManager;
import sparkx.sparkshop.knowledge.service.IMcpServerService;
import sparkx.sparkshop.knowledge.validate.McpServerSaveValidate;
import sparkx.sparkshop.knowledge.vo.McpServerVo;
import sparkx.sparkshop.knowledge.vo.McpTestResultVo;
import sparkx.sparkshop.knowledge.vo.McpToolVo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务管理业务实现。
 *
 * <h2>auth_config JSON schema</h2>
 * <ul>
 *   <li>{@code none}：忽略</li>
 *   <li>{@code api_key}：{@code {"apiKey":"...","apiKeyHeader":"X-API-Key"}}</li>
 *   <li>{@code bearer}：{@code {"token":"..."}}</li>
 * </ul>
 *
 * <h2>密钥脱敏</h2>
 * 列表/详情返回 {@link McpServerVo}，不含 apiKey/token 明文，仅返回 {@code hasApiKey}/{@code hasToken} 布尔。
 * 编辑时若前端未回填密钥（保留原值），需做"空则保留旧值"合并，避免掩码覆盖真实密钥。
 *
 * <p>⚠️ 本类内 {@link #MAPPER} 为 private static final 字段（非 Bean），
 * 避免暴露 @Bean ObjectMapper 压制 Spring Boot Jackson 自动配置。
 */
@Slf4j
@Service
public class McpServerServiceImpl implements IMcpServerService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** full_id 前缀格式："svc{serverId}__{toolName}" */
    private static final String FULL_ID_PREFIX = "svc";
    private static final String FULL_ID_SEP = "__";

    @Resource
    private McpServerMapper mcpServerMapper;
    @Resource
    private McpToolMapper mcpToolMapper;
    @Resource
    private McpClientManager mcpClientManager;

    // CRUD

    /**
     * 服务列表（可选 enabled 过滤，按 sort ASC）。
     *
     * <p>批量统计每个服务下的工具数（冗余字段 toolCount），避免前端逐个查。
     * 时间字段由全局 Jackson 按 {@code spring.jackson.date-format} 序列化
     * （与 IAiModelService.list 处理一致）。
     *
     * @param enabled 启停过滤，null = 不过滤
     * @return 密钥脱敏后的 VO 列表
     */
    @Override
    public List<McpServerVo> list(Boolean enabled) {
        LambdaQueryWrapper<McpServer> wrapper = new LambdaQueryWrapper<>();
        if (enabled != null) {
            wrapper.eq(McpServer::getEnabled, enabled);
        }
        wrapper.orderByAsc(McpServer::getSort).orderByAsc(McpServer::getId);
        List<McpServer> rows = mcpServerMapper.selectList(wrapper);
        // 批量查工具数（避免 N+1，一次 in 查询）
        Map<Integer, Integer> toolCounts = countToolsByServer(rows);
        List<McpServerVo> result = new ArrayList<>(rows.size());
        for (McpServer row : rows) {
            result.add(toVo(row, toolCounts.getOrDefault(row.getId(), 0)));
        }
        return result;
    }

    /**
     * 服务详情（密钥脱敏）。
     *
     * @param id 服务 id
     * @return VO，不存在返回 null
     */
    @Override
    public McpServerVo info(Integer id) {
        McpServer row = getById(id);
        if (row == null) return null;
        int toolCount = Math.toIntExact(mcpToolMapper.selectCount(
                new LambdaQueryWrapper<McpTool>().eq(McpTool::getServerId, id)));
        return toVo(row, toolCount);
    }

    /**
     * 新增服务。
     *
     * <p>保存后立即尝试测试连接并同步工具快照（{@link #trySyncTools}），
     * 让用户保存完就能在意图树选工具，无需手动点「刷新工具」。
     * 测试/同步失败不阻断保存（可能 MCP Server 暂未就绪，用户可后续再刷新）。
     */
    @Override
    public void add(McpServerSaveValidate v) {
        McpServer c = new McpServer();
        applyToEntity(v, c, null);
        LocalDateTime now = LocalDateTime.now();
        c.setCreateTime(now);
        c.setUpdateTime(now);
        mcpServerMapper.insert(c);
        // 保存后尝试测试连接 + 同步工具快照（失败静默，不阻断保存）
        trySyncTools(c);
    }

    /**
     * 编辑服务。
     *
     * <p>两处关键逻辑：
     * <ol>
     *   <li>{@link #configChanged} 检测连接相关配置（transport/url/auth/headers/timeout）是否变化，
     *       变化则调 {@code mcpClientManager.close} 关闭缓存连接，强制下次重建。</li>
     *   <li>{@link #mergeAuthConfig} 合并密钥：前端编辑态不回显密钥（后端脱敏），
     *       提交时密钥字段为空/掩码则保留 DB 旧值，避免掩码覆盖真实密钥。</li>
     * </ol>
     * 保存后同样尝试同步工具快照。
     */
    @Override
    public void edit(McpServerSaveValidate v) {
        if (v.getId() == null) {
            throw new BusinessException("服务 id 不能为空");
        }
        McpServer existing = getById(v.getId());
        if (existing == null) {
            throw new BusinessException("服务不存在");
        }
        // 配置变更检测：transport/url/authType/authConfig/headers 任一变化 → 关闭缓存连接强制重连
        boolean configChanged = configChanged(existing, v);
        // 密钥合并：api_key/bearer 下若前端未回填密钥（保留旧值）
        String mergedAuthConfig = mergeAuthConfig(existing, v);
        applyToEntity(v, existing, mergedAuthConfig);
        existing.setUpdateTime(LocalDateTime.now());
        mcpServerMapper.updateById(existing);
        if (configChanged) {
            mcpClientManager.close(existing.getId());
        }
        trySyncTools(existing);
    }

    /**
     * 删除服务。
     *
     * <p>顺序：先关闭连接 → 删工具快照 → 删服务配置。
     * 表上有 {@code ON DELETE CASCADE}，工具快照删两次保险。
     */
    @Override
    public void remove(Integer id) {
        mcpClientManager.close(id);
        // 级联删工具快照（表有 ON DELETE CASCADE，这里显式删一次保险）
        mcpToolMapper.delete(new LambdaQueryWrapper<McpTool>().eq(McpTool::getServerId, id));
        mcpServerMapper.deleteById(id);
    }

    /**
     * 切换启停。
     *
     * <p>禁用时主动关闭缓存连接（释放长连接资源）；启用时不主动建连，懒加载（首次调用时建）。
     */
    @Override
    public void switchStatus(Integer id, Boolean enabled) {
        McpServer c = getById(id);
        if (c == null) {
            throw new BusinessException("服务不存在");
        }
        c.setEnabled(enabled);
        c.setUpdateTime(LocalDateTime.now());
        mcpServerMapper.updateById(c);
        // 禁用时关闭连接（启用时不主动建，懒加载）
        if (Boolean.FALSE.equals(enabled)) {
            mcpClientManager.close(id);
        }
    }

    // 测试连接 / 工具发现

    /**
     * 测试已保存配置的连通性（按 id）。
     *
     * <p>调 {@link McpClientManager#testConnection} 连一次 → listTools + listResources，
     * 返回工具/资源列表。正式服务（id>0）的测试连接会复用连接池缓存。
     */
    @Override
    public McpTestResultVo test(Integer id) {
        McpServer c = getById(id);
        if (c == null) {
            throw new BusinessException("服务不存在");
        }
        return mcpClientManager.testConnection(c);
    }

    /**
     * 测试连通性（按表单参数，无需先保存；新建态用）。
     *
     * <p>构造临时实体（id=-1），{@link McpClientManager#testConnection} 的 finally 块会
     * 识别 id≤0 并关闭移出池，不污染连接池。
     */
    @Override
    public McpTestResultVo testConnect(McpServerSaveValidate v) {
        McpServer tmp = new McpServer();
        applyToEntity(v, tmp, v.getAuthConfig());
        tmp.setId(-1); // 临时配置，不进连接池
        return mcpClientManager.testConnection(tmp);
    }

    /**
     * 获取某服务的工具列表（从 mcp_tool 子表读，供「MCP服务」详情页查看）。
     */
    @Override
    public List<McpToolVo> tools(Integer serverId) {
        List<McpTool> rows = mcpToolMapper.selectList(new LambdaQueryWrapper<McpTool>()
                .eq(McpTool::getServerId, serverId)
                .orderByAsc(McpTool::getId));
        List<McpToolVo> result = new ArrayList<>(rows.size());
        for (McpTool t : rows) {
            result.add(toToolVo(t));
        }
        return result;
    }

    /**
     * 获取全部已启用服务的工具（供意图树下拉）。
     *
     * <p>仅返回已启用（enabled=true）服务的工具快照，并在 description 前拼 {@code [服务名]} 前缀，
     * 方便用户在意图树下拉里辨识工具来源（不同服务可能有同名工具）。
     */
    @Override
    public List<McpToolVo> allEnabledTools() {
        // 仅返回已启用服务的工具（供意图树下拉）
        List<McpServer> enabledServers = mcpServerMapper.selectList(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getEnabled, true)
                .orderByAsc(McpServer::getSort).orderByAsc(McpServer::getId));
        if (enabledServers.isEmpty()) return List.of();
        List<Integer> ids = enabledServers.stream().map(McpServer::getId).toList();
        List<McpTool> rows = mcpToolMapper.selectList(new LambdaQueryWrapper<McpTool>()
                .in(McpTool::getServerId, ids)
                .orderByAsc(McpTool::getServerId)
                .orderByAsc(McpTool::getId));
        // 服务名映射，拼到 description 前缀方便用户在下拉里辨识来源
        Map<Integer, String> serverNameMap = new HashMap<>();
        for (McpServer s : enabledServers) {
            serverNameMap.put(s.getId(), s.getName());
        }
        List<McpToolVo> result = new ArrayList<>(rows.size());
        for (McpTool t : rows) {
            McpToolVo vo = toToolVo(t);
            String svcName = serverNameMap.getOrDefault(t.getServerId(), "svc" + t.getServerId());
            vo.setDescription("[" + svcName + "] " + (vo.getDescription() == null ? "" : vo.getDescription()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 重新拉取工具并刷新快照（手动刷新）。
     *
     * <p>先 {@code close(id)} 关闭缓存连接（强制下次用新连接拉最新工具列表，避免连接复用导致
     * 工具列表过期），再 testConnection 拉取，成功则 {@link #syncTools} 全量替换 mcp_tool 表。
     */
    @Override
    public McpTestResultVo refresh(Integer id) {
        McpServer c = getById(id);
        if (c == null) {
            throw new BusinessException("服务不存在");
        }
        mcpClientManager.close(id); // 强制重连拉最新工具列表
        McpTestResultVo vo = mcpClientManager.testConnection(c);
        if (Boolean.TRUE.equals(vo.getSuccess())) {
            syncTools(c, vo.getTools());
        }
        return vo;
    }

    // 工具同步（listTools → mcp_tool 快照）

    /** 保存/编辑后尝试同步工具（失败静默，仅日志） */
    private void trySyncTools(McpServer server) {
        try {
            McpTestResultVo vo = mcpClientManager.testConnection(server);
            if (Boolean.TRUE.equals(vo.getSuccess()) && vo.getTools() != null) {
                syncTools(server, vo.getTools());
            }
        } catch (Exception e) {
            log.debug("[MCP] 保存后同步工具失败 server={}({}): {}", server.getName(), server.getId(), e.getMessage());
        }
    }

    /**
     * 把测试结果里的工具列表 upsert 到 mcp_tool 表。
     * 策略：先删该 server 的全部旧工具，再批量插入新工具（简单可靠）。
     */
    private void syncTools(McpServer server, List<McpTestResultVo.Tool> tools) {
        Integer serverId = server.getId();
        mcpToolMapper.delete(new LambdaQueryWrapper<McpTool>().eq(McpTool::getServerId, serverId));
        if (tools == null || tools.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (McpTestResultVo.Tool t : tools) {
            if (t.getName() == null || t.getName().isBlank()) continue;
            McpTool entity = new McpTool();
            entity.setServerId(serverId);
            entity.setFullId(buildFullId(serverId, t.getName()));
            entity.setToolName(t.getName());
            entity.setDescription(t.getDescription());
            entity.setInputSchema(t.getInputSchema());
            entity.setLastSyncedAt(now);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            mcpToolMapper.insert(entity);
        }
        log.info("[MCP] 已同步 {} 个工具：server={}({})",
                tools.size(), server.getName(), serverId);
    }

    // 工具方法

    private McpServer getById(Integer id) {
        if (id == null) return null;
        return mcpServerMapper.selectById(id);
    }

    /** validate → 实体字段赋值（enabled/timeout/retry/sort 缺省） */
    private void applyToEntity(McpServerSaveValidate v, McpServer c, String authConfig) {
        c.setName(v.getName());
        c.setDescription(v.getDescription());
        c.setEnabled(v.getEnabled() == null ? Boolean.TRUE : v.getEnabled());
        c.setTransportType(v.getTransportType());
        c.setUrl(v.getUrl());
        c.setAuthType(v.getAuthType());
        // authConfig：编辑时用合并后的值（保留旧密钥），新增/测试时用原始值
        c.setAuthConfig(authConfig != null ? authConfig : v.getAuthConfig());
        c.setHeaders(v.getHeaders());
        c.setTimeoutSec(v.getTimeoutSec() == null ? 30 : v.getTimeoutSec());
        c.setRetryCount(v.getRetryCount() == null ? 1 : v.getRetryCount());
        c.setRemark(v.getRemark());
        c.setSort(v.getSort() == null ? 100 : v.getSort());
    }

    /**
     * 配置变更检测（决定是否关闭缓存连接重连）。
     */
    private boolean configChanged(McpServer existing, McpServerSaveValidate v) {
        return !strEq(existing.getTransportType(), v.getTransportType())
                || !strEq(existing.getUrl(), v.getUrl())
                || !strEq(existing.getAuthType(), v.getAuthType())
                || !strEq(existing.getAuthConfig(), v.getAuthConfig())
                || !strEq(existing.getHeaders(), v.getHeaders())
                || !intEq(existing.getTimeoutSec(), v.getTimeoutSec());
    }

    /**
     * 密钥合并：编辑时若前端未回填 apiKey/token（值为空/掩码），保留 DB 旧值。
     * 仅对 api_key / bearer 生效。
     */
    private String mergeAuthConfig(McpServer existing, McpServerSaveValidate v) {
        String oldJson = existing.getAuthConfig();
        String newJson = v.getAuthConfig();
        if (newJson == null || newJson.isBlank()) {
            return oldJson; // 前端没传 authConfig，保留旧值
        }
        String authType = v.getAuthType() == null ? "none" : v.getAuthType();
        if ("none".equals(authType)) {
            return newJson;
        }
        try {
            JsonNode oldNode = oldJson == null || oldJson.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(oldJson);
            JsonNode newNode = MAPPER.readTree(newJson);
            // com.fasterxml.jackson.databind.node.ObjectNode 可变
            com.fasterxml.jackson.databind.node.ObjectNode merged = MAPPER.createObjectNode();
            // 先拷旧值
            if (oldNode.isObject()) {
                oldNode.fields().forEachRemaining(e -> merged.set(e.getKey(), e.getValue()));
            }
            // 再用新值覆盖（前端传了的字段）
            if (newNode.isObject()) {
                newNode.fields().forEachRemaining(e -> {
                    JsonNode val = e.getValue();
                    if (val != null && !val.isNull()) {
                        String textVal = val.asText();
                        // 空/掩码（如 "******"）则不覆盖，保留旧值
                        if (textVal != null && !textVal.isBlank() && !isMask(textVal)) {
                            merged.put(e.getKey(), textVal);
                        }
                    }
                });
            }
            return MAPPER.writeValueAsString(merged);
        } catch (Exception e) {
            log.warn("[MCP] authConfig 合并失败，回退用前端值: {}", e.getMessage());
            return newJson;
        }
    }

    /** 是否掩码占位（前端未回填密钥时的占位符） */
    private boolean isMask(String s) {
        return "******".equals(s) || "****".equals(s) || "***".equals(s);
    }

    /** 实体 → VO（密钥脱敏） */
    private McpServerVo toVo(McpServer c, int toolCount) {
        McpServerVo vo = new McpServerVo();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setDescription(c.getDescription());
        vo.setEnabled(c.getEnabled());
        vo.setTransportType(c.getTransportType());
        vo.setUrl(c.getUrl());
        vo.setAuthType(c.getAuthType());
        vo.setHeaders(c.getHeaders());
        // 解析 auth_config 提取是否已配置密钥 + apiKeyHeader（不回显明文）
        JsonNode authCfg = parseJson(c.getAuthConfig());
        String apiKey = textOf(authCfg, "apiKey");
        String token = textOf(authCfg, "token");
        vo.setHasApiKey(apiKey != null && !apiKey.isBlank());
        vo.setHasToken(token != null && !token.isBlank());
        vo.setApiKeyHeader(textOr(authCfg, "apiKeyHeader", "X-API-Key"));
        vo.setTimeoutSec(c.getTimeoutSec());
        vo.setRetryCount(c.getRetryCount());
        vo.setRemark(c.getRemark());
        vo.setSort(c.getSort());
        vo.setToolCount(toolCount);
        vo.setCreateTime(c.getCreateTime());
        vo.setUpdateTime(c.getUpdateTime());
        return vo;
    }

    /** 工具实体 → VO */
    private McpToolVo toToolVo(McpTool t) {
        McpToolVo vo = new McpToolVo();
        vo.setId(t.getId());
        vo.setServerId(t.getServerId());
        vo.setFullId(t.getFullId());
        vo.setToolName(t.getToolName());
        vo.setDescription(t.getDescription());
        vo.setInputSchema(t.getInputSchema());
        vo.setLastSyncedAt(t.getLastSyncedAt());
        return vo;
    }

    /**
     * 批量统计每个服务的工具数（列表页冗余字段用，避免 N+1 查询）。
     * 单次 in 查询 + 内存分组。
     */
    private Map<Integer, Integer> countToolsByServer(List<McpServer> rows) {
        Map<Integer, Integer> map = new java.util.HashMap<>();
        if (rows.isEmpty()) return map;
        List<Integer> ids = rows.stream().map(McpServer::getId).toList();
        List<McpTool> allTools = mcpToolMapper.selectList(new LambdaQueryWrapper<McpTool>().in(McpTool::getServerId, ids));
        for (McpTool t : allTools) {
            map.merge(t.getServerId(), 1, Integer::sum);
        }
        return map;
    }

    /**
     * 构造工具的全局唯一 fullId：{@code "svc{serverId}__{toolName}"}。
     * 该值作为意图节点 {@code t_intent_node.mcp_tool_id} 的值，{@link DefaultMcpToolService} 解析后定位工具。
     */
    public static String buildFullId(Integer serverId, String toolName) {
        return FULL_ID_PREFIX + serverId + FULL_ID_SEP + toolName;
    }

    /**
     * 解析 fullId → [serverId, toolName]。
     *
     * @return {@code {serverId(字符串), toolName}}，格式非法返回 null
     */
    public static String[] parseFullId(String fullId) {
        if (fullId == null || fullId.isBlank()) return null;
        int sepIdx = fullId.indexOf(FULL_ID_SEP);
        if (sepIdx <= FULL_ID_PREFIX.length()) return null;
        String prefixPart = fullId.substring(0, sepIdx);
        if (!prefixPart.startsWith(FULL_ID_PREFIX)) return null;
        String serverIdStr = prefixPart.substring(FULL_ID_PREFIX.length());
        try {
            Integer.parseInt(serverIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
        String toolName = fullId.substring(sepIdx + FULL_ID_SEP.length());
        if (toolName.isEmpty()) return null;
        return new String[]{serverIdStr, toolName};
    }

    /** 解析 JSON 文本；空/非法返回空对象节点（不抛异常，由调用方判断字段）。 */
    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return MAPPER.createObjectNode();
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }

    /** 取 JsonNode 文本字段（null/缺失返回 null）。 */
    private static String textOf(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) return null;
        return v.asText();
    }

    /** 取 JsonNode 文本字段，空/缺失返回默认值。 */
    private static String textOr(JsonNode node, String field, String def) {
        String v = textOf(node, field);
        return v == null || v.isBlank() ? def : v;
    }

    /** 字符串相等（null 安全）。 */
    private static boolean strEq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** 整数相等（null 安全）。 */
    private static boolean intEq(Integer a, Integer b) {
        return a == null ? b == null : a.equals(b);
    }

    /** 应用关闭时关闭所有 MCP 连接，释放长连接资源（SSE 是持久连接）。 */
    @PreDestroy
    public void onDestroy() {
        mcpClientManager.closeAll();
    }
}
