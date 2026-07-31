// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.infra.chat;

import sparkx.sparkshop.knowledge.agent.AgentRerankClient;
import sparkx.sparkshop.knowledge.config.AiModelProperties;
import sparkx.sparkshop.knowledge.config.CohereScoringModelConfig;
import sparkx.sparkshop.knowledge.infra.EmbeddingModelProvider;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.model.ModelHealthStore;
import sparkx.sparkshop.knowledge.infra.model.ModelRoutingExecutor;
import sparkx.sparkshop.knowledge.infra.model.ModelSelector;
import sparkx.sparkshop.knowledge.service.IAiModelService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 流式降级编排（文档 5.10.5）—— 降级链指挥者。
 *
 * 流式路径（独立实现降级循环，因 ModelRoutingExecutor 是同步的）：
 * <pre>
 * for target in candidates:
 *   if !healthStore.allowCall(target): continue       // 二次熔断准入
 *   bridge = new ProbeStreamBridge(callback)          // 包装业务 callback
 *   handle = client.streamChat(request, bridge, target)
 *   result = firstPacketProbe.awaitFirstPacket(bridge, 60s)   // ★ 阻塞等首包
 *   if result.success: markSuccess; return handle     // 成功，bridge 自动 commit
 *   else: markFailure; handle.cancel(); continue      // 失败换下一个候选
 * throw allFailed
 * </pre>
 *
 * 同步路径（chat/embed/rerank）走 {@link ModelRoutingExecutor#executeWithFallback} 复用同一套降级骨架。
 *
 * TODO WF-7: sparkxV2 在 chat/streamChat 方法上使用 @RagTraceNode(name=..., type="LLM_ROUTING")
 * 采集链路追踪节点。当前 WF-7 未完成（RagTraceNode 注解尚未移植），此处先去掉注解保留方法逻辑，
 * 待 WF-7 完成后再补回 @RagTraceNode 注解。
 */
@Service
@Primary
public class RoutingLLMService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(RoutingLLMService.class);

    private final ModelSelector selector;
    private final ModelHealthStore healthStore;
    private final ModelRoutingExecutor executor;
    private final LlmFirstPacketProbe firstPacketProbe;
    private final EmbeddingModelProvider embeddingModelProvider;
    private final AgentRerankClient agentRerankClient;
    /** rerank 回退用的 embedding 余弦打分模型（懒构建，基于 ai_model 默认 embedding，非 yml OpenAI 默认） */
    private volatile ScoringModel defaultScoringModel;
    private final ChatModelBridge chatModelBridge;
    private final VlmModelBridge vlmModelBridge;
    private final IAiModelService aiModelService;
    private final Map<String, ChatClient> clientsByProvider;
    private final long firstPacketTimeoutSeconds;

    public RoutingLLMService(ModelSelector selector,
                             ModelHealthStore healthStore,
                             ModelRoutingExecutor executor,
                             LlmFirstPacketProbe firstPacketProbe,
                             EmbeddingModelProvider embeddingModelProvider,
                             AgentRerankClient agentRerankClient,
                             List<ChatClient> clients,
                             ChatModelBridge chatModelBridge,
                             VlmModelBridge vlmModelBridge,
                             IAiModelService aiModelService,
                             AiModelProperties aiProps) {
        this.selector = selector;
        this.healthStore = healthStore;
        this.executor = executor;
        this.firstPacketProbe = firstPacketProbe;
        this.embeddingModelProvider = embeddingModelProvider;
        this.agentRerankClient = agentRerankClient;
        this.chatModelBridge = chatModelBridge;
        this.vlmModelBridge = vlmModelBridge;
        this.aiModelService = aiModelService;
        this.firstPacketTimeoutSeconds = aiProps.getSelection().getFirstPacketTimeoutSeconds();
        this.clientsByProvider = new HashMap<>();
        for (ChatClient c : clients) {
            this.clientsByProvider.put(c.provider(), c);
        }
        log.info("[Routing] 已加载 {} 个 ChatClient: {}", clientsByProvider.size(), clientsByProvider.keySet());
    }


    @Override
    public String chat(String prompt, double temperature, double topP, boolean thinking) {
        // ★ 修正：原实现丢弃了 thinking 参数（ofUser 写死 false），导致显式请求 thinking 的调用被静默吞掉。
        //   对辅助任务（改写/分类/澄清，传 false）恰好结果不变，但 chat(prompt,...,true) 会被误关 thinking。
        //   这里显式构造 LlmChatRequest 透传 thinking，保证语义正确。
        return chat(new LlmChatRequest(
                List.of(dev.langchain4j.data.message.UserMessage.from(prompt)),
                temperature, topP, -1, -1, thinking));
    }

    @Override
    public String chat(LlmChatRequest request) {
        // 若无注册 ChatClient（如仅配单模型未起适配层），降级用现有 LangChain4j ChatModel
        if (clientsByProvider.isEmpty()) {
            return chatModelBridge.chat(request);
        }
        List<ModelTarget> targets = selector.selectChatCandidates(request.thinking());
        // 候选全部熔断时降级到现有 ChatModel（保证可用性优先于韧性）
        if (targets.isEmpty()) {
            log.warn("[Routing] 所有对话候选熔断，降级到默认 ChatModel");
            return chatModelBridge.chat(request);
        }
        return executor.executeWithFallback(
                "chat", targets,
                this::resolveClientByProvider,
                (client, target) -> client.chat(request, target));
    }

    /**
     * 指定模型 id 同步对话。
     *
     * <p>modelId 非空时，按 {@code ai_model.id} 解析出单一 ModelTarget 强制路由，
     * 仍复用 {@link ModelRoutingExecutor#executeWithFallback} 享受熔断/降级保护；
     * 该模型未注册 ChatClient / 不存在 / 非对话类型 / modelId 为空 时，回退默认候选链。
     */
    @Override
    public String chat(LlmChatRequest request, Integer modelId) {
        return chat(request, modelId, null);
    }

    /**
     * 同步对话（指定模型 id + 可选具体子模型名）。
     *
     * <p>与 {@link #chat(LlmChatRequest, Integer)} 一致，区别仅在于解析 ModelTarget 时
     * 用 {@link IAiModelService#getChatTarget(Integer, String)} 覆盖为前端选定的子模型名。
     */
    @Override
    public String chat(LlmChatRequest request, Integer modelId, String modelName) {
        if (modelId == null) {
            return chat(request);
        }
        if (clientsByProvider.isEmpty()) {
            return chatModelBridge.chat(request);
        }
        ModelTarget target = aiModelService.getChatTarget(modelId, modelName);
        if (target == null) {
            log.warn("[Routing] 指定 modelId={} 解析失败，回退默认候选链", modelId);
            return chat(request);
        }
        if (resolveClientByProvider(target.provider()) == null) {
            log.warn("[Routing] 指定 modelId={} 的 provider={} 无可用 ChatClient，回退默认候选链",
                    modelId, target.provider());
            return chat(request);
        }
        return executor.executeWithFallback(
                "chat", List.of(target),
                this::resolveClientByProvider,
                (client, t) -> client.chat(request, t));
    }


    @Override
    public StreamCancellationHandle streamChat(LlmChatRequest request, StreamCallback callback, boolean deepThinking) {
        return streamChat(request, callback, deepThinking, null);
    }

    /**
     * 流式对话（可指定模型）。
     *
     * <p>★ modelId 非空时：解析出该模型作为<strong>首选</strong>排到候选链最前，其余默认候选跟后。
     * 这样「用户/智能体指定的对话模型」真正生效，且该模型熔断/首包失败时仍能平滑降级到其他候选，
     * 不牺牲可用性。modelId 为空 / 解析失败 / 未注册 ChatClient 时，等同走默认候选链。
     */
    @Override
    public StreamCancellationHandle streamChat(LlmChatRequest request, StreamCallback callback, boolean deepThinking, Integer modelId) {
        return streamChat(request, callback, deepThinking, modelId, null);
    }

    /**
     * 流式对话（可指定模型 + 可选具体子模型名）。
     *
     * <p>★ modelId 非空时：解析出该模型作为<strong>首选</strong>排到候选链最前，其余默认候选跟后。
     * 这样「用户/智能体指定的对话模型」真正生效，且该模型熔断/首包失败时仍能平滑降级到其他候选，
     * 不牺牲可用性。modelName 用于覆盖为前端选定的具体子模型（逗号列表内才采用，否则回退首项）。
     */
    @Override
    public StreamCancellationHandle streamChat(LlmChatRequest request, StreamCallback callback, boolean deepThinking, Integer modelId, String modelName) {
        // 无注册 ChatClient 时降级用现有 StreamingChatModel
        if (clientsByProvider.isEmpty()) {
            return chatModelBridge.streamChat(request, callback);
        }

        // ★ 指定模型：解析成功则提到候选链首位（首选优先 + 默认候选兜底降级）
        ModelTarget pinned = (modelId != null) ? aiModelService.getChatTarget(modelId, modelName) : null;
        if (modelId != null && pinned == null) {
            log.warn("[Routing] 指定 modelId={} 解析失败，回退默认候选链", modelId);
        } else if (pinned != null && resolveClientByProvider(pinned.provider()) == null) {
            log.warn("[Routing] 指定 modelId={} 的 provider={} 无可用 ChatClient，回退默认候选链",
                    modelId, pinned.provider());
            pinned = null;
        }

        List<ModelTarget> targets = selector.selectChatCandidates(deepThinking);
        if (pinned != null) {
            // pinned 置首，去重其余候选里的同 id
            List<ModelTarget> merged = new java.util.ArrayList<>();
            merged.add(pinned);
            for (ModelTarget t : targets) {
                if (!t.id().equals(pinned.id())) merged.add(t);
            }
            targets = merged;
        }
        // 候选全部熔断时降级到现有 StreamingChatModel
        if (targets.isEmpty()) {
            log.warn("[Routing] 所有流式候选熔断，降级到默认 StreamingChatModel");
            return chatModelBridge.streamChat(request, callback);
        }

        Throwable lastError = null;
        int idx = 0;
        int total = targets.size();
        for (ModelTarget target : targets) {
            idx++;
            ChatClient client = resolveClientByProvider(target.provider());
            if (client == null) continue;
            // ★ 二次熔断准入（应对选择后状态变化）
            if (!healthStore.allowCall(target.id())) {
                log.info("[Routing] stream 候选 {}/{} model={}({}) thinking={} 熔断中，跳过",
                        idx, total, target.id(), target.model(), request.thinking());
                continue;
            }

            ProbeStreamBridge bridge = new ProbeStreamBridge(callback);
            StreamCancellationHandle handle = client.streamChat(request, bridge, target);

            // ★ 阻塞等首包（独立 Bean 以便 AOP 采集 TTFT 指标）
            long tProbe = System.currentTimeMillis();
            ProbeStreamBridge.ProbeResult result = firstPacketProbe.awaitFirstPacket(
                    bridge, firstPacketTimeoutSeconds, TimeUnit.SECONDS);
            long probeCost = System.currentTimeMillis() - tProbe;

            if (result.isSuccess()) {
                healthStore.markSuccess(target.id());
                // ★ INFO 级诊断：定位慢模型用。一眼看出命中了哪个模型、首包耗时多少
                //   pinned 标记位便于看出「指定模型是否被命中」vs「降级到了其他候选」
                boolean viaPin = pinned != null && pinned.id().equals(target.id());
                log.info("[Routing] stream 候选 {}/{} model={}({}) thinking={} 首包成功 耗时={}ms{}",
                        idx, total, target.id(), target.model(), request.thinking(), probeCost,
                        viaPin ? " [指定模型]" : "");
                return handle;   // 成功，bridge 已 commit，后续 token 直通
            }

            // 失败：计入熔断，取消这次流，换下一个候选
            healthStore.markFailure(target.id());
            handle.cancel();
            lastError = buildLastError(result, target);
            log.warn("[Routing] stream 候选 {}/{} model={}({}) thinking={} 首包失败={} 耗时={}ms，尝试下一个候选",
                    idx, total, target.id(), target.model(), request.thinking(), result.type(), probeCost);
        }

        // 全挂了：降级到默认 StreamingChatModel（可用性优先），仍失败才统一报错
        log.warn("[Routing] 所有流式候选首包失败，降级到默认 StreamingChatModel");
        try {
            return chatModelBridge.streamChat(request, callback);
        } catch (Exception fallbackErr) {
            callback.onError(lastError != null ? lastError
                    : new ModelClientException(ModelClientErrorType.PROVIDER_ERROR,
                            "所有对话模型不可用", fallbackErr));
            return StreamCancellationHandle.noop();
        }
    }


    @Override
    public float[] embed(String text) {
        // 默认兜底模型（kbId=null 时 provider.resolve 返回 @Primary 单例）
        return embed(text, null);
    }

    @Override
    public float[] embed(String text, String kbId) {
        // 按 kb.embedding_model_id 解析对应 embedding 模型（查询向量与入库向量维度一致），
        // 解析失败回退默认兜底模型
        EmbeddingModel model = embeddingModelProvider.resolve(kbId);
        Embedding emb = model.embed(text).content();
        return emb.vector();
    }

    @Override
    public List<Float> rerank(String query, List<String> passages) {
        return rerank(query, passages, null);
    }

    /**
     * 重排打分（指定 rerank 模型 id）。
     *
     * <p>★ 修复（对齐智能体 RerankStage，解决「langchain4j 默认 OpenAI 地址」问题）：
     * 原实现直接用 yml {@code @Primary OpenAiEmbeddingModel}（base-url 默认 {@code https://api.openai.com/v1}）
     * 做 embedding 余弦打分，导致 workflow 的 rerank 误打到 OpenAI 默认地址 → ConnectException →
     * 重试阻塞数十秒 → SSE emitter 提前 completed → 前端卡「生成中」。
     *
     * <p>新逻辑：指定了 rerank 模型 → 优先走 {@link AgentRerankClient} 真实 rerank API（URL 取自 ai_model 表）；
     * 否则（或未指定/调用失败）回退用 {@link EmbeddingModelProvider#resolveDefault()} 解析的
     * ai_model 表默认 embedding 模型做余弦相似度（同样不再用 yml OpenAI 默认地址）。
     */
    @Override
    public List<Float> rerank(String query, List<String> passages, Integer rerankModelId) {
        return rerank(query, passages, rerankModelId, null);
    }

    @Override
    public List<Float> rerank(String query, List<String> passages, Integer rerankModelId, String rerankModelName) {
        // 1) 指定 rerank 模型 → 真实 rerank API（与智能体 RerankStage 一致），透传子模型名
        if (rerankModelId != null) {
            try {
                List<Double> scores = agentRerankClient.rerank(rerankModelId, rerankModelName, query, passages);
                if (scores != null) {
                    return scores.stream().map(Double::floatValue).toList();
                }
            } catch (Exception e) {
                log.warn("[Routing] 指定 rerank 模型 id={} 调用失败，回退 embedding 余弦: {}",
                        rerankModelId, e.getMessage());
            }
        }
        // 2) 回退：ai_model 表默认 embedding 模型余弦相似度（非 yml OpenAI 默认地址）
        ScoringModel sm = resolveDefaultScoring();
        List<TextSegment> passageSegments = passages.stream().map(TextSegment::from).toList();
        return sm.scoreAll(passageSegments, query).content()
                .stream().map(Double::floatValue).toList();
    }

    /** 懒构建 embedding 余弦打分模型：基于 ai_model 表默认 embedding 模型（缓存，避免每次重建 HTTP 客户端） */
    private ScoringModel resolveDefaultScoring() {
        ScoringModel sm = defaultScoringModel;
        if (sm == null) {
            synchronized (this) {
                sm = defaultScoringModel;
                if (sm == null) {
                    EmbeddingModel emb = embeddingModelProvider.resolveDefault();
                    defaultScoringModel = new CohereScoringModelConfig(null, emb).scoringModel();
                    sm = defaultScoringModel;
                }
            }
        }
        return sm;
    }

    /**
     * 按 provider 取 ChatClient；未注册时，对 OpenAI 兼容协议（非 ollama）兜底复用通用 "openai" ChatClient。
     *
     * <p>★ 修复（对齐 rerank 的 langchain4j 默认地址问题）：若 ai_model 里某模型的 provider 字段
     * 填成了 "xiaomi"/"mimo"/"deepseek" 等与 OpenAI 协议兼容、但不是字面值 "openai" 的值，
     * 原逻辑会因找不到对应 ChatClient 直接跳过该候选，最终降级到 langchain4j 的 yml OpenAI 默认地址。
     * 这里对 OpenAI 兼容协议统一兜底到已注册的 "openai" ChatClient（它会用 target 自带的 url，
     * 不会落到 OpenAI 默认地址）；仅 ollama（非 OpenAI 协议）不做此兜底。
     */
    private ChatClient resolveClientByProvider(String provider) {
        ChatClient client = clientsByProvider.get(provider);
        if (client != null) {
            return client;
        }
        if (!"ollama".equals(provider) && clientsByProvider.containsKey("openai")) {
            return clientsByProvider.get("openai");
        }
        return null;
    }

    @Override
    public String describeImage(byte[] content, String mime, String prompt, int maxTokens) {
        // VLM 是入库侧非热路径，不做降级
        return vlmModelBridge.describeImage(content, mime, prompt, maxTokens);
    }


    private Throwable buildLastError(ProbeStreamBridge.ProbeResult result, ModelTarget target) {
        return switch (result.type()) {
            case TIMEOUT -> new ModelClientException(ModelClientErrorType.NETWORK_ERROR,
                    "模型 " + target.id() + " 首包超时");
            case NO_CONTENT -> new ModelClientException(ModelClientErrorType.INVALID_RESPONSE,
                    "模型 " + target.id() + " 流式响应无内容");
            case ERROR -> result.error() != null ? result.error()
                    : new ModelClientException(ModelClientErrorType.SERVER_ERROR,
                            "模型 " + target.id() + " 流式调用错误");
            default -> new ModelClientException(ModelClientErrorType.PROVIDER_ERROR,
                    "模型 " + target.id() + " 未知失败");
        };
    }
}
