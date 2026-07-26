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

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.config.MinerUConfig;
import sparkx.sparkshop.knowledge.entity.ExtServiceConfig;
import sparkx.sparkshop.knowledge.ingest.mineru.MinerUOptions;
import sparkx.sparkshop.knowledge.mapper.ExtServiceConfigMapper;
import sparkx.sparkshop.knowledge.service.IExtServiceConfigService;
import sparkx.sparkshop.knowledge.validate.ExtServiceConfigValidate;
import sparkx.sparkshop.knowledge.vo.ModelTestVo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 外部服务配置业务实现（MinerU 等非 LLM 外部服务）。
 *
 * <p>与 IAiModelService 区分：
 * <ul>
 *   <li>IAiModelService 管 LLM 模型（对话/向量/重排/视觉），统一结构 + 容错降级链。</li>
 *   <li>本类管非 LLM 的外部服务（PDF 解析引擎 MinerU 等），按 category 分类，
 *       具体字段存 config JSON。</li>
 * </ul>
 *
 * <h2>支持的 category 与 config JSON schema</h2>
 * <ul>
 *   <li><b>mineru_self</b>（自建 MinerU，engine=mineru）：
 *     <pre>{@code
 *       {
 *         "endpoint": "http://127.0.0.1:8000",
 *         "model": "pipeline",            // backend: pipeline / vlm-* / hybrid-*
 *         "vlmServerUrl": "",             // 仅 vlm-http-client/hybrid-http-client 后端需要
 *         "enableFormula": true, "enableTable": true, "enableOcr": true,
 *         "language": "ch",               // OCR 语言 ch / en / ...
 *         "timeoutSec": 1000              // 自建同步解析读超时（秒）
 *       }
 *     }</pre></li>
 *   <li><b>mineru_cloud</b>（云端 MinerU，engine=mineru_cloud）：
 *     <pre>{@code
 *       {
 *         "apiKey": "eyJ...",             // mineru.net 令牌（apiManage/token）
 *         "model": "pipeline",            // model_version: pipeline / vlm / MinerU-HTML
 *         "enableFormula": true, "enableTable": true, "enableOcr": true,
 *         "language": "ch",
 *         "pollIntervalSec": 3,           // 轮询间隔（秒）
 *         "timeoutSec": 600               // 单任务总超时（秒）
 *       }
 *     }</pre></li>
 * </ul>
 *
 * <p>运行时 MinerUParserFactory 按 engine=mineru/mineru_cloud 查本表对应 category +
 * status=1 的记录，构造 MinerUOptions（替代原 application.yml 的 app.rag.mineru.*）。
 *
 * <p>⚠️ 注意：本类内 {@link #MAPPER} 为 private static final 字段（非 Bean），
 * 故意不依赖 Spring 全局 ObjectMapper，避免暴露 @Bean ObjectMapper 压制
 * Spring Boot Jackson 自动配置（那会导致 MVC 序列化 java.time.* 失败）。
 */
@Slf4j
@Service
public class ExtServiceConfigServiceImpl implements IExtServiceConfigService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** status：启用 */
    private static final int STATUS_ENABLED = 1;

    /** MinerU 云端 API 根地址（hardcoded，对标 MinerUClient.CLOUD_BASE） */
    private static final String MINERU_CLOUD_BASE = "https://mineru.net/api/v4";

    @Resource
    private ExtServiceConfigMapper extServiceConfigMapper;

    /** MinerU 专用 HTTP 客户端（长超时，由 MinerUConfig 装配），连通性测试复用 */
    @Resource
    @Qualifier("mineruHttpClient")
    private OkHttpClient mineruHttpClient;

    // 后台 CRUD

    /**
     * 配置列表（按 category 可选筛选，按 sort ASC）。
     * 时间字段手动格式化，避免 Jackson 直接序列化 LocalDateTime 报错
     * （与 IAiModelService.list 处理一致）。
     */
    @Override
    public List<Map<String, Object>> list(String category, Integer status) {
        LambdaQueryWrapper<ExtServiceConfig> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) {
            wrapper.eq(ExtServiceConfig::getCategory, category);
        }
        if (status != null && status > 0) {
            wrapper.eq(ExtServiceConfig::getStatus, status);
        }
        wrapper.orderByAsc(ExtServiceConfig::getSort)
                .orderByAsc(ExtServiceConfig::getId);
        List<Map<String, Object>> data = new ArrayList<>();
        for (ExtServiceConfig c : extServiceConfigMapper.selectList(wrapper)) {
            data.add(toMap(c));
        }
        return data;
    }

    /** 配置详情。 */
    @Override
    public Map<String, Object> info(Integer id) {
        return toMap(getById(id));
    }

    /** 新增配置。 */
    @Override
    public void add(ExtServiceConfigValidate v) {
        ExtServiceConfig c = new ExtServiceConfig();
        applyToEntity(v, c);
        LocalDateTime now = LocalDateTime.now();
        c.setCreateTime(now);
        c.setUpdateTime(now);
        extServiceConfigMapper.insert(c);
    }

    /** 编辑配置。 */
    @Override
    public void edit(ExtServiceConfigValidate v) {
        if (v.getId() == null) {
            throw new BusinessException("配置 id 不能为空");
        }
        ExtServiceConfig c = extServiceConfigMapper.selectById(v.getId());
        if (c == null) {
            throw new BusinessException("配置不存在");
        }
        applyToEntity(v, c);
        c.setUpdateTime(LocalDateTime.now());
        extServiceConfigMapper.updateById(c);
    }

    /** 删除配置。 */
    @Override
    public void remove(Integer id) {
        extServiceConfigMapper.deleteById(id);
    }

    /** 切换启停。 */
    @Override
    public void switchStatus(Integer id, Integer status) {
        ExtServiceConfig c = extServiceConfigMapper.selectById(id);
        if (c == null) {
            throw new BusinessException("配置不存在");
        }
        c.setStatus(status);
        c.setUpdateTime(LocalDateTime.now());
        extServiceConfigMapper.updateById(c);
    }

    // MinerU 配置读取（供 MinerUParserFactory 调用，核心改造点）

    /**
     * 按 MinerU 引擎名查启用的配置记录，构造 MinerUOptions。
     * engine 到 category 映射：mineru → mineru_self；mineru_cloud → mineru_cloud。
     */
    @Override
    public MinerUOptions getMineruConfig(String engine) {
        String category;
        boolean self;
        switch (engine == null ? "" : engine) {
            case "mineru" -> {
                category = "mineru_self";
                self = true;
            }
            case "mineru_cloud" -> {
                category = "mineru_cloud";
                self = false;
            }
            default -> throw new IllegalArgumentException("未知 MinerU 引擎: " + engine);
        }
        ExtServiceConfig row = findEnabled(category);
        if (row == null) {
            throw new IllegalArgumentException(
                    (self ? "自建" : "云端") + " MinerU 未配置或未启用"
                            + "（请在「知识库 → 外部服务配置」新增并启用 category=" + category + " 的记录）");
        }
        JsonNode cfg = parseConfig(row.getConfig());
        if (self) {
            return buildSelfOptions(cfg);
        }
        return buildCloudOptions(cfg);
    }

    /** 查某 category 下 status=1 的首条记录（按 sort ASC）。 */
    private ExtServiceConfig findEnabled(String category) {
        return extServiceConfigMapper.selectOne(new LambdaQueryWrapper<ExtServiceConfig>()
                .eq(ExtServiceConfig::getCategory, category)
                .eq(ExtServiceConfig::getStatus, STATUS_ENABLED)
                .orderByAsc(ExtServiceConfig::getSort)
                .orderByAsc(ExtServiceConfig::getId)
                .last("LIMIT 1"));
    }

    /** 自建 MinerU 配置 → MinerUOptions(SELF)。校验 endpoint 非空。 */
    private MinerUOptions buildSelfOptions(JsonNode cfg) {
        String endpoint = textOf(cfg, "endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException(
                    "自建 MinerU 配置缺 endpoint（config.endpoint），请在「外部服务配置」补全");
        }
        return new MinerUOptions(
                MinerUOptions.Mode.SELF,
                endpoint,
                null,
                textOr(cfg, "model", "pipeline"),
                textOf(cfg, "vlmServerUrl"),
                boolOr(cfg, "enableFormula", true),
                boolOr(cfg, "enableTable", true),
                boolOr(cfg, "enableOcr", true),
                textOr(cfg, "language", "ch"),
                0,
                intOr(cfg, "timeoutSec", 1000));
    }

    /** 云端 MinerU 配置 → MinerUOptions(CLOUD)。校验 apiKey 非空。 */
    private MinerUOptions buildCloudOptions(JsonNode cfg) {
        String apiKey = textOf(cfg, "apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "云端 MinerU 配置缺 apiKey（config.apiKey），请在「外部服务配置」补全");
        }
        return new MinerUOptions(
                MinerUOptions.Mode.CLOUD,
                null,
                apiKey,
                textOr(cfg, "model", "pipeline"),
                null,
                boolOr(cfg, "enableFormula", true),
                boolOr(cfg, "enableTable", true),
                boolOr(cfg, "enableOcr", true),
                textOr(cfg, "language", "ch"),
                intOr(cfg, "pollIntervalSec", 3),
                intOr(cfg, "timeoutSec", 600));
    }

    // 连通性测试

    /** 测试已保存配置的连通性（按 id）。 */
    @Override
    public ModelTestVo test(Integer id) {
        ExtServiceConfig c = extServiceConfigMapper.selectById(id);
        if (c == null) {
            throw new BusinessException("配置不存在");
        }
        return doTest(c.getCategory(), c.getConfig());
    }

    /** 测试连通性（按表单参数，无需先保存；新建态用）。 */
    @Override
    public ModelTestVo testConnect(ExtServiceConfigValidate v) {
        return doTest(v.getCategory(), v.getConfig());
    }

    /**
     * 按 category 分支做轻量探测：
     * - mineru_self：SSRF 校验 endpoint → GET {endpoint}/ 探测可达
     *   （fastapi 根路径通常 200/404，都算可达；连接失败/超时算失败）。
     *   不触发实际 /file_parse，避免无谓解析开销。
     * - mineru_cloud：GET mineru.net/api/v4/extract-results/batch/__probe__
     *   带 Bearer apiKey → HTTP 401 = key 无效；404/其他 = 鉴权通过。
     */
    private ModelTestVo doTest(String category, String configJson) {
        ModelTestVo vo = new ModelTestVo();
        long start = System.currentTimeMillis();
        try {
            JsonNode cfg = parseConfig(configJson);
            String probeDesc;
            int okCode;
            if ("mineru_self".equals(category)) {
                String endpoint = textOf(cfg, "endpoint");
                if (endpoint == null || endpoint.isBlank()) {
                    throw new IllegalArgumentException("endpoint 未配置");
                }
                MinerUConfig.validateOutboundUrl(endpoint);
                String url = stripTrailingSlash(endpoint) + "/";
                probeDesc = "GET " + url;
                try (Response resp = mineruHttpClient.newCall(
                        new Request.Builder().url(url).get().build()).execute()) {
                    // fastapi 根路径 200 或 404 都算 endpoint 可达
                    okCode = resp.code();
                }
            } else if ("mineru_cloud".equals(category)) {
                String apiKey = textOf(cfg, "apiKey");
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalArgumentException("apiKey 未配置");
                }
                String url = MINERU_CLOUD_BASE + "/extract-results/batch/__probe__";
                probeDesc = "GET " + url + " (Bearer)";
                try (Response resp = mineruHttpClient.newCall(
                        new Request.Builder().url(url)
                                .header("Authorization", "Bearer " + apiKey)
                                .get().build()).execute()) {
                    okCode = resp.code();
                    if (okCode == 401) {
                        throw new IllegalStateException("apiKey 无效或已过期（HTTP 401）");
                    }
                }
            } else if ("neo4j_self".equals(category)) {
                String uri = textOf(cfg, "uri");
                if (uri == null || uri.isBlank()) {
                    throw new IllegalArgumentException("uri 未配置");
                }
                String username = textOr(cfg, "username", "neo4j");
                String password = textOf(cfg, "password");
                if (password == null) password = "";
                probeDesc = "Neo4j verifyConnectivity " + uri;
                Driver tmpDriver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
                try {
                    tmpDriver.verifyConnectivity();
                } finally {
                    tmpDriver.close();
                }
                okCode = 0; // verifyConnectivity 成功即通过，无 HTTP 状态码
            } else {
                throw new IllegalArgumentException("暂不支持该服务类别的连通性测试: " + category);
            }
            vo.setSuccess(true);
            if (probeDesc.startsWith("Neo4j")) {
                vo.setMessage(probeDesc + " 连接成功");
            } else {
                vo.setMessage(probeDesc + " 探测通过（HTTP " + okCode + "）");
            }
        } catch (Exception e) {
            vo.setSuccess(false);
            vo.setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        vo.setLatencyMs(System.currentTimeMillis() - start);
        return vo;
    }

    // Neo4j 连接配置读取（供 KnowledgeGraphConfig 调用）

    /**
     * 读取启用的 Neo4j 连接配置（仅 neo4j_self）。
     * @return {uri, username, password}，未配置返回 null
     */
    @Override
    public String[] getNeo4jConnection() {
        ExtServiceConfig row = findEnabled("neo4j_self");
        if (row == null) return null;
        JsonNode cfg = parseConfig(row.getConfig());
        String uri = textOf(cfg, "uri");
        if (uri == null || uri.isBlank()) return null;
        String username = textOr(cfg, "username", "neo4j");
        String password = textOf(cfg, "password");
        if (password == null) password = "";
        return new String[]{uri, username, password};
    }

    // 工具

    private ExtServiceConfig getById(Integer id) {
        if (id == null) return null;
        return extServiceConfigMapper.selectById(id);
    }

    /** validate → 实体字段赋值（status/sort 缺省）。 */
    private void applyToEntity(ExtServiceConfigValidate v, ExtServiceConfig c) {
        c.setName(v.getName());
        c.setCategory(v.getCategory());
        c.setConfig(v.getConfig());
        c.setRemark(v.getRemark());
        c.setStatus(v.getStatus() == null ? STATUS_ENABLED : v.getStatus());
        c.setSort(v.getSort() == null ? 100 : v.getSort());
    }

    /**
     * 实体 → Map（时间字段手动格式化，避免 Jackson LocalDateTime 报错）。
     * config 原样返回（前端解析）。
     */
    private Map<String, Object> toMap(ExtServiceConfig c) {
        if (c == null) return null;
        Map<String, Object> m = BeanUtil.beanToMap(c, false, false);
        if (c.getCreateTime() != null) m.put("createTime", c.getCreateTime().format(FMT));
        if (c.getUpdateTime() != null) m.put("updateTime", c.getUpdateTime().format(FMT));
        return m;
    }

    /** 解析 config JSON；空/非法返回空对象节点（不抛异常，由调用方判断字段）。 */
    private JsonNode parseConfig(String config) {
        if (config == null || config.isBlank()) return MAPPER.createObjectNode();
        try {
            return MAPPER.readTree(config);
        } catch (Exception e) {
            log.warn("[ext-service] config JSON 解析失败，按空对象处理: {}", e.getMessage());
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

    private static boolean boolOr(JsonNode node, String field, boolean def) {
        if (node == null) return def;
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) return def;
        if (v.isBoolean()) return v.asBoolean();
        if (v.isTextual()) {
            String s = v.asText();
            return "true".equalsIgnoreCase(s) || "1".equals(s);
        }
        return v.asBoolean(def);
    }

    private static int intOr(JsonNode node, String field, int def) {
        if (node == null) return def;
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) return def;
        if (v.isNumber()) return v.asInt();
        try {
            return Integer.parseInt(v.asText());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
