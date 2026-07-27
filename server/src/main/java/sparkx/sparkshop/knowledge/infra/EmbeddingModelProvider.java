// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.entity.AiModel;
import sparkx.sparkshop.knowledge.entity.KnowledgeBase;
import sparkx.sparkshop.knowledge.mapper.AiModelMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeBaseMapper;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按知识库动态解析 embedding 模型。
 *
 * <p>背景：原架构下所有 embedding 调用点都注入 yml 兜底的 {@code @Primary OpenAiEmbeddingModel}
 * 单例（baseUrl 指向 {@code api.openai.com}），导致用户在 {@code ai_model} 表里配的
 * ollama / 通义 / bge 等模型完全失效，且文档向量化维度（如 bge-m3 1024）与查询向量化维度
 * （OpenAI 1536）不一致时 pgvector 会抛维度不匹配异常（被吞成空结果，检索哑火）。
 *
 * <p>本类按 {@code kb.embedding_model_id + kb.embedding_model_name} 精确定位模型实例：
 * <pre>
 * kbId → knowledge_base(embedding_model_id, embedding_model_name)
 *      → ai_model(provider/url/apiKey)
 *      → OpenAiEmbeddingModel(modelName = embedding_model_name)
 * </pre>
 *
 * <p>★ 为什么同时要 modelId + modelName：
 * 一条 ai_model 记录的 {@code models} 字段可配多个模型（逗号分隔，共用同一服务地址/凭证），
 * 不同模型维度可能不同。知识库必须绑定到「具体模型名」，否则运行时 {@code firstModel} 粗暴取首项，
 * 用户后续改 models 顺序/内容就会换模型导致维度失配。
 *
 * <p>构造出的实例按 {@code modelId:modelName} 复合 key 缓存（OkHttp 客户端复用），
 * 解析失败回退到注入的默认 {@code @Primary EmbeddingModel}（yml 兜底），保证可用性。
 *
 * <p>ollama 走 OpenAI 兼容的 {@code /v1/embeddings}，统一用 {@link OpenAiEmbeddingModel} 构造。
 */
@Component
public class EmbeddingModelProvider {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingModelProvider.class);

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AiModelMapper aiModelMapper;
    /** 默认兜底模型（yml app.rag.embedding 配置的 @Primary 单例） */
    private final EmbeddingModel defaultModel;

    /** 局部 ObjectMapper（不暴露为 Bean，避免破坏全局自动配置） */
    private final ObjectMapper mapper = new ObjectMapper();

    /** 按 "modelId:modelName" 复合 key 缓存的实例 */
    private final ConcurrentHashMap<String, EmbeddingModel> cache = new ConcurrentHashMap<>();

    public EmbeddingModelProvider(KnowledgeBaseMapper knowledgeBaseMapper,
                                   AiModelMapper aiModelMapper,
                                   EmbeddingModel defaultModel) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.aiModelMapper = aiModelMapper;
        this.defaultModel = defaultModel;
    }

    /**
     * 按知识库 id 解析 embedding 模型。
     *
     * <p>★ 快照优先：知识库创建时已把 url/apiKey/modelName 完整快照进 KB 表，
     * 本方法优先用 KB 自身字段构造模型，<b>不再回查 ai_model 表</b>。这样即便后续在
     * /ai/model 页面改 url/apiKey/删模型，已存在的知识库向量化/检索行为完全不变
     * （已落库向量与查询向量始终来自同一快照，维度/服务地址一致）。
     *
     * <p>★ 老数据兼容：若 KB 快照字段缺失（升级前已建的库），回退到
     * {@link #resolveByModelId} 按 embeddingModelId 实时查 ai_model 的老链路，保证可用。
     *
     * @param kbId 知识库 id；为空或链路任意一环失败时回退默认模型
     * @return 对应的 {@link EmbeddingModel}，解析失败回退 {@link #defaultModel}
     */
    public EmbeddingModel resolve(String kbId) {
        if (kbId == null || kbId.isBlank()) {
            return defaultModel;
        }
        try {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
            if (kb == null || kb.getEmbeddingModelId() == null) {
                return defaultModel;
            }
            // 快照完整 → 走 KB 自带配置（推荐路径，与 ai_model 表解耦）
            String snapshotUrl = kb.getEmbeddingModelUrl();
            String modelName = kb.getEmbeddingModelName();
            if (snapshotUrl != null && !snapshotUrl.isBlank()
                    && modelName != null && !modelName.isBlank()) {
                String cacheKey = "kb:" + kbId;
                return cache.computeIfAbsent(cacheKey, k -> buildFromSnapshot(
                        snapshotUrl, kb.getEmbeddingModelApiKey(), modelName));
            }
            // 老数据缺快照：回退按 modelId 实时查 ai_model（兼容路径）
            // embedding_model_name 为空时回退取 models 首项
            String effModelName = (modelName == null || modelName.isBlank()) ? null : modelName;
            return resolveByModelId(kb.getEmbeddingModelId(), effModelName);
        } catch (Exception e) {
            log.warn("[EmbeddingProvider] 解析 kb={} 的 embedding 模型失败，回退默认: {}", kbId, e.getMessage());
            return defaultModel;
        }
    }

    /**
     * 用 KB 快照字段构造 OpenAI 兼容 EmbeddingModel（与 {@link #build} 逻辑等价，
     * 只是数据源从 ai_model 行换成 KB 自带的 url/apiKey/modelName）。
     * url/model 必填；apiKey 为空时给占位（ollama 等无 key 服务）。
     */
    private EmbeddingModel buildFromSnapshot(String url, String apiKey, String modelName) {
        var b = OpenAiEmbeddingModel.builder()
                .baseUrl(url)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60));
        if (apiKey != null && !apiKey.isBlank()) {
            b.apiKey(apiKey);
        } else {
            // ollama 等无 key 服务需要一个占位 apiKey，OpenAiEmbeddingModel 会原样发送
            b.apiKey("ollama");
        }
        log.info("[EmbeddingProvider] 构造 embedding 模型(snapshot) model={} url={}", modelName, url);
        return b.build();
    }

    /**
     * 按 ai_model.id + 具体模型名 解析（带实例缓存）。
     *
     * @param embeddingModelId ai_model 主键（确定 url/apiKey）
     * @param modelName        具体模型名；为空时回退取该记录 models 首项（兼容老数据）
     */
    public EmbeddingModel resolveByModelId(Integer embeddingModelId, String modelName) {
        if (embeddingModelId == null) {
            return defaultModel;
        }
        String cacheKey = embeddingModelId + ":" + (modelName == null ? "" : modelName);
        return cache.computeIfAbsent(cacheKey, k -> build(embeddingModelId, modelName));
    }

    /**
     * 失效单个 ai_model 记录的所有缓存实例（ modelName 维度全部清除）。
     * <p>供 {@code IAiModelService} 在 edit/remove/switchStatus 后调用，
     * 让 url/apiKey/model 名等变更立即生效。
     */
    public void invalidate(Integer embeddingModelId) {
        if (embeddingModelId == null) return;
        String prefix = embeddingModelId + ":";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** 失效全部缓存实例（保险用）。 */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * 按 ai_model 记录 + 具体模型名构造 OpenAI 兼容 EmbeddingModel。
     * 从 {@code options}/{@code credential} JSON 数组提取 url/apiKey。
     * modelName 为空时取 models 首项；构造失败（缺 url 或 model）返回默认模型。
     */
    private EmbeddingModel build(Integer embeddingModelId, String modelName) {
        try {
            AiModel model = aiModelMapper.selectById(embeddingModelId);
            if (model == null) {
                log.warn("[EmbeddingProvider] ai_model={} 不存在，回退默认", embeddingModelId);
                return defaultModel;
            }
            String url = extractField(model.getOptions(), "url");
            String apiKey = extractField(model.getCredential(), "apiKey");
            // 优先用传入的 modelName，为空回退 models 首项（兼容老数据）
            String effModelName = (modelName != null && !modelName.isBlank())
                    ? modelName.trim() : firstModel(model.getModels());
            if (url == null || url.isBlank() || effModelName == null || effModelName.isBlank()) {
                log.warn("[EmbeddingProvider] ai_model={} 配置不完整(url/model)，回退默认", embeddingModelId);
                return defaultModel;
            }
            // 注意：1.17.0 OpenAiEmbeddingModel.builder() 返回的 Builder 类型不是公开嵌套类，
            // 不能显式声明为 OpenAiEmbeddingModel.Builder，用 var 接收。
            var b = OpenAiEmbeddingModel.builder()
                    .baseUrl(url)
                    .modelName(effModelName)
                    .timeout(Duration.ofSeconds(60));
            if (apiKey != null && !apiKey.isBlank()) {
                b.apiKey(apiKey);
            } else {
                // ollama 等无 key 服务需要一个占位 apiKey，OpenAiEmbeddingModel 会原样发送
                b.apiKey("ollama");
            }
            log.info("[EmbeddingProvider] 构造 embedding 模型 id={} name={} url={}",
                    embeddingModelId, effModelName, url);
            return b.build();
        } catch (Exception e) {
            log.warn("[EmbeddingProvider] 构造 ai_model={} name={} 失败，回退默认: {}",
                    embeddingModelId, modelName, e.getMessage());
            return defaultModel;
        }
    }

    /** 默认兜底模型（yml app.rag.embedding 配置的单例），供非活跃链路或解析失败使用 */
    public EmbeddingModel defaultModel() {
        return defaultModel;
    }

    /**
     * 探测某 embedding 模型 id + 模型名 的实际输出维度（探针 embed 一个短词）。
     *
     * <p>用途：知识库创建时用实际维度回填 {@code kb.dimension}（不依赖用户手填），
     * 以及向量化前校验「模型实际维度 == kb.dimension」，防止模型被改后维度失配
     * 导致 pgvector 报错被吞成静默哑火。
     *
     * @return 实际维度；探测失败（模型不可达/配置错误）返回 -1
     */
    public int probeDimension(Integer embeddingModelId, String modelName) {
        if (embeddingModelId == null) return -1;
        try {
            EmbeddingModel m = resolveByModelId(embeddingModelId, modelName);
            Embedding emb = m.embed(TextSegment.from("probe")).content();
            return emb.vector().length;
        } catch (Exception e) {
            log.warn("[EmbeddingProvider] 探测维度失败 modelId={} name={}: {}",
                    embeddingModelId, modelName, e.getMessage());
            return -1;
        }
    }

    /**
     * 从 [{"field":"xxx","value":"yyy"}] 形态 JSON 数组中找 field 对应 value。
     * 任意异常（null/空/非数组/解析失败/缺字段）返回 null。
     * 与 {@code IAiModelService#extractField} 同形态，但那个是 private，这里内聚一份避免依赖。
     */
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
        for (String p : models.split(",")) {
            if (p != null && !p.isBlank()) return p.trim();
        }
        return null;
    }
}
