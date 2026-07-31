// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.entity.AiModel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 智能体重排客户端 —— 按 {@code ai_model.id}（type=3 rerank）调真实 rerank API。
 * <p>
 * 与全局 {@code ScoringModel}（基于 embedding 的假重排）不同，这里走真实
 * SiliconFlow / Cohere / Jina 通用 rerank 协议：
 * <pre>
 *   POST {baseUrl}/rerank   Authorization: Bearer {apiKey}
 *   body = { model, query, documents:[...] }
 *   resp = { results:[ { index, relevance_score }, ... ] }
 * </pre>
 * <p>
 * 失败时抛异常，由 {@code RerankStage} 决定是否回退全局 ScoringModel。
 */
@Slf4j
@Component
public class AgentRerankClient {

    /** ★ 用字段而非 Bean：遵守项目「不暴露 ObjectMapper Bean」约定（Jackson 自动配置不被压制） */
    private final ObjectMapper mapper = new ObjectMapper();
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    @Resource
    private sparkx.sparkshop.knowledge.mapper.AiModelMapper aiModelMapper;

    /**
     * 调真实 rerank API，返回与 passages 等长、同序的分数列表。
     *
     * @param rerankModelId     ai_model.id（type=3）
     * @param modelNameOverride 具体重排模型名（从 ai_model.models 逗号拆分中指定）；为空则取 models 首项
     * @param query             查询
     * @param passages          候选段落（顺序与返回分数一一对应）
     * @return 分数列表（0~1，值越大越相关）
     * @throws Exception 模型不存在 / 调用失败
     */
    public List<Double> rerank(Integer rerankModelId, String modelNameOverride,
                               String query, List<String> passages) throws Exception {
        if (rerankModelId == null) {
            throw new IllegalArgumentException("rerankModelId 为空");
        }
        if (passages == null || passages.isEmpty()) {
            return List.of();
        }

        // 1. 读 ai_model 配置（type=3 rerank）
        AiModel model = aiModelMapper.selectById(rerankModelId);
        if (model == null) {
            throw new IllegalStateException("重排模型不存在 id=" + rerankModelId);
        }
        String baseUrl = extractField(model.getOptions(), "url");
        String apiKey = extractField(model.getCredential(), "apiKey");
        // ★ 优先用前端指定的具体模型名，否则回退 models 首项
        String modelName = resolveModelName(model, modelNameOverride);
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalStateException("重排模型未配置可用模型名");
        }
        String url = resolveRerankUrl(baseUrl);

        // 2. 构造请求体
        ObjectNode body = mapper.createObjectNode();
        body.put("model", modelName);
        body.put("query", query);
        ArrayNode docs = body.putArray("documents");
        for (String p : passages) {
            docs.add(p);
        }

        // 3. 发请求（与 AiModelServiceImpl.doTest 同款 OkHttp 配置）
        Request.Builder rb = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), JSON_MEDIA));
        if (apiKey != null && !apiKey.isBlank()) {
            rb.header("Authorization", "Bearer " + apiKey);
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15).toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(Duration.ofSeconds(60).toMillis(), TimeUnit.MILLISECONDS)
                .build();

        // 4. 解析响应：results:[{index, relevance_score}]，按 index 映射回原序
        double[] scores = new double[passages.size()];
        try (Response resp = client.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                throw new IllegalStateException("rerank HTTP " + resp.code() + " " + resp.message());
            }
            try (ResponseBody respBody = resp.body()) {
                String respText = respBody == null ? "" : respBody.string();
                JsonNode root = mapper.readTree(respText);
                JsonNode err = root.path("error");
                if (!err.isMissingNode()) {
                    throw new IllegalStateException("重排供应商返回错误: " + err.path("message").asText());
                }
                JsonNode results = root.path("results");
                if (!results.isArray() || results.isEmpty()) {
                    throw new IllegalStateException("重排响应无 results");
                }
                for (JsonNode r : results) {
                    int idx = r.path("index").asInt(-1);
                    double score = r.path("relevance_score").asDouble(0.0);
                    if (idx >= 0 && idx < scores.length) {
                        scores[idx] = score;
                    }
                }
            }
        }
        List<Double> out = new ArrayList<>(scores.length);
        for (double s : scores) out.add(s);
        return out;
    }


    /**
     * rerank URL 解析：{@code options.url} 由用户填写<b>完整接口地址</b>（含 /rerank 路径），
     * 后端不再自动补全，直接以用户输入为准（仅做 trim）。
     * 仅在 url 完全缺失时回退 SiliconFlow 默认端点。
     */
    private String resolveRerankUrl(String base) {
        if (base == null || base.isBlank()) {
            return "https://api.siliconflow.cn/v1/rerank";
        }
        return base.trim();
    }

    /** 从 options/credential 的 [{field,value}] JSON 数组中取字段值 */
    private String extractField(String json, String field) {
        if (json == null || json.isBlank() || field == null) return null;
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isArray()) return null;
            for (JsonNode node : root) {
                JsonNode f = node.path("field");
                JsonNode v = node.path("value");
                if (field.equals(f.asText())) {
                    return v != null && !v.isNull() && !v.isMissingNode() ? v.asText() : null;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 取 models 逗号分隔首项 */
    private String firstModel(String models) {
        if (models == null || models.isBlank()) return null;
        String[] arr = models.split(",");
        for (String s : arr) {
            if (s != null && !s.isBlank()) return s.trim();
        }
        return null;
    }

    /**
     * ★ 解析实际调用的模型名：优先用 override（前端从 models 列表里选的具体模型名），
     *   校验其属于该 ai_model 的 models 列表后采用；否则回退首项。
     */
    private String resolveModelName(AiModel model, String override) {
        String models = model.getModels();
        if (override != null && !override.isBlank() && models != null && !models.isBlank()) {
            String trimmed = override.trim();
            for (String part : models.split(",")) {
                if (trimmed.equals(part.trim())) {
                    return trimmed;
                }
            }
        }
        return firstModel(models);
    }
}
