// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.common.trace.RagTraceNode;
import sparkx.sparkshop.knowledge.entity.KgConfig;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.knowledge.mapper.KgConfigMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 检索时 query 实体抽取器。
 *
 * <p>用 LLM 从用户 query 中抽取实体名列表（如"北京天气" → ["北京"]），
 * 供 {@link KnowledgeGraphChannel} 向量召回 kg_entity 表做实体匹配。
 *
 * <p>★ Redis 缓存：相同 query+kbId 的抽取结果缓存 1 小时，避免每轮检索都调 LLM。
 * key = {@code kg:query-entity:<md5(query+kbId)>}。
 *
 * <p>★ temperature=0.1 保证确定性（同一 query 抽出相同实体）。
 */
@Component
public class GraphQueryEntityExtractor {

    private static final Logger log = LoggerFactory.getLogger(GraphQueryEntityExtractor.class);

    private static final String CACHE_KEY_PREFIX = "kg:query-entity:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private static final String EXTRACT_SYSTEM_PROMPT = """
            你是实体抽取助手。从用户问题中抽取关键实体名称（人名、组织、产品、技术、地点、概念等）。
            只输出实体名列表（JSON 数组），不要其他内容。示例：
            输入："LangChain4j 和 Neo4j 的集成方案是什么？"
            输出：["LangChain4j","Neo4j"]
            输入："北京和上海的天气怎么样？"
            输出：["北京","上海"]
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Resource
    private LLMService llmService;

    @Resource
    private KgConfigMapper kgConfigMapper;

    @Resource
    private RedissonClient redisson;

    /**
     * 从 query 中抽取实体名列表（带 Redis 缓存）。
     *
     * @param query 用户问题
     * @param kbId  知识库 id（用于缓存 key 隔离）
     * @return 实体名列表（可能为空，不会为 null）
     */
    @RagTraceNode(name = "kg-query-entity", type = "KG_SEARCH")
    public List<String> extract(String query, String kbId) {
        if (query == null || query.isBlank()) return List.of();

        // 1. 查缓存
        String cacheKey = CACHE_KEY_PREFIX + md5(query + "|" + kbId);
        RBucket<List<String>> bucket = redisson.getBucket(cacheKey);
        List<String> cached = bucket.get();
        if (cached != null) return cached;

        // 2. 调 LLM
        KgConfig config = kgConfigMapper.selectById(1);
        if (config == null || config.getExtractModelId() == null) {
            log.debug("[QueryEntity] kg_config 未配置抽取模型，跳过");
            return List.of();
        }

        try {
            LlmChatRequest req = LlmChatRequest.of(EXTRACT_SYSTEM_PROMPT,
                    "请从以下问题中抽取实体：\n" + query, 0.1);
            String output = llmService.chat(req, config.getExtractModelId());
            List<String> entities = parseEntityList(output);

            // 3. 写缓存
            if (!entities.isEmpty()) {
                bucket.set(entities, CACHE_TTL);
            }
            return entities;
        } catch (Exception e) {
            log.warn("[QueryEntity] LLM 抽取失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 解析 LLM 返回的 JSON 数组（strip markdown fence） */
    private List<String> parseEntityList(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) return List.of();
        String json = llmOutput.trim();
        // strip ```json ... ```
        if (json.startsWith("```")) {
            int nl = json.indexOf('\n');
            if (nl > 0) json = json.substring(nl + 1);
        }
        if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
        json = json.trim();

        try {
            JsonNode root = mapper.readTree(json);
            if (root.isArray()) {
                List<String> result = new ArrayList<>();
                for (JsonNode node : root) {
                    String s = node.asText(null);
                    if (s != null && !s.isBlank()) result.add(s.trim());
                }
                return result;
            }
            return List.of();
        } catch (Exception e) {
            log.warn("[QueryEntity] JSON 解析失败: {}", json.substring(0, Math.min(100, json.length())));
            return List.of();
        }
    }

    private static String md5(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
