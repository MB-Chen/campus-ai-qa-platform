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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI 协议统一模板方法（文档 5.10.1）。
 *
 * 通用逻辑封进 {@link #chat} / {@link #streamChat}，子类（OpenAI/Ollama/BaiLian...）
 * 只覆写钩子 {@link #provider()} / {@link #customizeRequestBody} / {@link #requiresApiKey}。
 *
 * 同步：构造 OpenAI 格式 body → POST → 解析 choices[0].message.content。
 * 流式：stream=true + SSE Accept → 逐行读取 {@link OpenAIStyleSseParser} 解析 → 回调。
 */
public abstract class AbstractOpenAIChatClient implements ChatClient {

    private static final Logger log = LoggerFactory.getLogger(AbstractOpenAIChatClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    protected final ObjectMapper mapper = new ObjectMapper();

    /** 同步 HTTP 客户端（较短超时） */
    protected abstract OkHttpClient syncHttpClient();
    /** 流式 HTTP 客户端（长读超时，流式逐 token） */
    protected abstract OkHttpClient streamingHttpClient();
    /** 流式读取线程池 */
    protected abstract ExecutorService streamExecutor();


    /**
     * 供应商特有请求字段（thinking 精确控制，按供应商适配）。
     *
     * <p>★ 不同供应商关闭思维链的字段不同，字段名不对会被供应商忽略，模型照样跑 reasoning_content，
     * 导致辅助任务（改写/分类/澄清）白白耗时数秒甚至数十秒。当前主流 reasoning 模型关闭字段：
     * <ul>
     *   <li><b>DeepSeek / 小米 MiMo（官方端点）</b>：用 {@code {"thinking":{"type":"disabled"}}}。
     *       这两家默认开启 thinking mode，且不认 enable_thinking 字段。
     *       参考 https://api-docs.deepseek.com/zh-cn/guides/thinking_mode 、
     *       https://mimo.mi.com/docs/zh-CN/quick-start/usage-guide/text-generation/deep-thinking</li>
     *   <li><b>Qwen/通义（阿里云百炼 DashScope 兼容端点）</b>：用 {@code enable_thinking=false}。
     *       仅当 url 含 dashscope/bailian 才走这个分支。</li>
     *   <li>其他不支持 thinking 的供应商：不下发任何字段</li>
     * </ul>
     *
     * <p>仅在模型声明 supports_thinking=1 时下发，避免给标准 OpenAI 协议端点发脏字段。
     * ★ 辅助任务（改写/意图分类/模糊澄清/歧义检查）一律传 thinking=false，无论用哪个模型都不开思考。
     */
    protected ObjectNode customizeRequestBody(ObjectNode body, LlmChatRequest request, ModelTarget target) {
        // 模型不支持 thinking → 不下发任何字段（标准 OpenAI 协议无此字段）
        if (!target.supportsThinking()) {
            return body;
        }
        boolean wantThinking = request.thinking();
        String url = target.url() == null ? "" : target.url().toLowerCase();

        // ★ 阿里云百炼 DashScope 兼容端点：enable_thinking 字段（url 含 dashscope/bailian）
        if (url.contains("dashscope") || url.contains("bailian")) {
            body.put("enable_thinking", wantThinking);
            return body;
        }

        // ★ DeepSeek / 小米 MiMo（官方端点）：thinking.type 字段
        //   这两家默认开启 thinking mode，必须显式 disabled 才能关闭，否则照样跑思维链。
        //   enable_thinking 对它们无效，会被忽略。
        com.fasterxml.jackson.databind.node.ObjectNode thinkingNode = body.putObject("thinking");
        thinkingNode.put("type", wantThinking ? "enabled" : "disabled");
        return body;
    }

    /** 是否需要 API Key（Ollama 覆写为 false） */
    protected boolean requiresApiKey() { return true; }


    @Override
    public String chat(LlmChatRequest request, ModelTarget target) {
        String url = ModelUrlResolver.resolveChatUrl(target);
        ObjectNode body = buildRequestBody(request, target);
        body = customizeRequestBody(body, request, target);
        body.put("stream", false);

        Request.Builder rb = buildRequest(url, body, target);
        try (Response resp = syncHttpClient().newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                int code = resp.code();
                throw new ModelClientException(ModelClientErrorType.fromHttpStatus(code),
                        "模型同步调用失败: HTTP " + code + " " + resp.message());
            }
            try (ResponseBody respBody = resp.body()) {
                if (respBody == null) {
                    throw new ModelClientException(ModelClientErrorType.INVALID_RESPONSE, "空响应体");
                }
                return extractChatContent(respBody.string());
            }
        } catch (ModelClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelClientException(ModelClientErrorType.NETWORK_ERROR,
                    "模型同步调用网络异常: " + e.getMessage(), e);
        }
    }


    @Override
    public StreamCancellationHandle streamChat(LlmChatRequest request, StreamCallback callback, ModelTarget target) {
        String url = ModelUrlResolver.resolveChatUrl(target);
        ObjectNode body = buildRequestBody(request, target);
        body = customizeRequestBody(body, request, target);
        body.put("stream", true);

        Request.Builder rb = buildRequest(url, body, target);
        rb.header("Accept", "text/event-stream");

        AtomicBoolean cancelled = new AtomicBoolean(false);
        StreamCancellationHandle.Simple handle = new StreamCancellationHandle.Simple(null);

        // 提交到流式读取线程池异步读 SSE
        try {
            streamExecutor().submit(() -> doStream(rb.build(), callback, handle, cancelled, target));
        } catch (java.util.concurrent.RejectedExecutionException ree) {
            // 线程池满：直接同步降级读，避免任务丢失
            doStream(rb.build(), callback, handle, cancelled, target);
        }
        return handle;
    }

    /** SSE 读取循环（后台线程执行） */
    private void doStream(Request req, StreamCallback callback,
                          StreamCancellationHandle.Simple handle,
                          AtomicBoolean cancelled, ModelTarget target) {
        // 绑定 cancel 动作：置标志 + 关闭 OkHttp Call（通过 abort Response）
        okhttp3.Call call = streamingHttpClient().newCall(req);
        Response resp = null;
        try {
            resp = call.execute();
            if (!resp.isSuccessful()) {
                callback.onError(new ModelClientException(
                        ModelClientErrorType.fromHttpStatus(resp.code()),
                        "模型流式调用失败: HTTP " + resp.code() + " " + resp.message()));
                return;
            }
            try (ResponseBody respBody = resp.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         respBody != null ? respBody.byteStream() : new java.io.ByteArrayInputStream(new byte[0])))) {
                String line;
                boolean gotAnyContent = false;
                while (!cancelled.get() && (line = reader.readLine()) != null) {
                    OpenAIStyleSseParser.ParsedEvent ev = OpenAIStyleSseParser.parseLine(line);
                    if (ev.isEmpty()) continue;
                    if (ev.content() != null && !ev.content().isEmpty()) {
                        gotAnyContent = true;
                        callback.onContent(ev.content());
                    }
                    if (ev.reasoning() != null && !ev.reasoning().isEmpty()) {
                        gotAnyContent = true;
                        callback.onThinking(ev.reasoning());
                    }
                    if (ev.finished()) {
                        callback.onComplete();
                        return;
                    }
                }
                // 流读完未收到 finished 标记
                if (!cancelled.get() && !gotAnyContent) {
                    callback.onError(new ModelClientException(ModelClientErrorType.INVALID_RESPONSE,
                            "模型流式响应结束但无任何内容"));
                }
            }
        } catch (java.io.IOException e) {
            if (!cancelled.get()) {
                callback.onError(new ModelClientException(ModelClientErrorType.NETWORK_ERROR,
                        "模型流式读取异常: " + e.getMessage(), e));
            }
        } finally {
            if (resp != null) try { resp.close(); } catch (Exception ignored) { }
        }
    }


    /** 构造 OpenAI 格式请求体（model/messages/temperature/top_p/max_tokens） */
    protected ObjectNode buildRequestBody(LlmChatRequest request, ModelTarget target) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", target.model());
        ArrayNode messages = body.putArray("messages");
        for (ChatMessage msg : request.messages()) {
            ObjectNode m = messages.addObject();
            m.put("role", roleOf(msg));
            m.put("content", textOf(msg));
        }
        body.put("temperature", request.temperature());
        if (request.topP() > 0 && request.topP() < 1) body.put("top_p", request.topP());
        if (request.topK() > 0) body.put("top_k", request.topK());
        if (request.maxTokens() > 0) body.put("max_tokens", request.maxTokens());
        return body;
    }

    private Request.Builder buildRequest(String url, ObjectNode body, ModelTarget target) {
        RequestBody reqBody = RequestBody.create(body.toString(), JSON);
        Request.Builder rb = new Request.Builder().url(url).post(reqBody);
        if (requiresApiKey() && target.apiKey() != null && !target.apiKey().isBlank()) {
            rb.header("Authorization", "Bearer " + target.apiKey());
        }
        return rb;
    }

    /** 提取同步响应的 choices[0].message.content */
    protected String extractChatContent(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                // 部分供应商直接返回 error 对象
                JsonNode err = root.path("error");
                if (!err.isMissingNode()) {
                    throw new ModelClientException(ModelClientErrorType.PROVIDER_ERROR,
                            "供应商返回错误: " + err.path("message").asText());
                }
                throw new ModelClientException(ModelClientErrorType.INVALID_RESPONSE, "响应无 choices: " + json);
            }
            JsonNode content = choices.get(0).path("message").path("content");
            return content.asText();
        } catch (ModelClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelClientException(ModelClientErrorType.INVALID_RESPONSE,
                    "解析模型响应失败: " + e.getMessage(), e);
        }
    }

    private String roleOf(ChatMessage msg) {
        if (msg instanceof SystemMessage) return "system";
        if (msg instanceof UserMessage) return "user";
        if (msg instanceof AiMessage) return "assistant";
        return "user";
    }

    private String textOf(ChatMessage msg) {
        // LangChain4j 1.x：ChatMessage 无统一 text() 接口，按类型提取
        if (msg instanceof SystemMessage sm) return sm.text();
        if (msg instanceof UserMessage um) return um.singleText();
        if (msg instanceof AiMessage am) return am.text() != null ? am.text() : "";
        return "";
    }

    /** 便捷：构造默认同步 OkHttpClient */
    protected static OkHttpClient buildSyncClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }

    /** 便捷：构造默认流式 OkHttpClient（长读超时） */
    protected static OkHttpClient buildStreamingClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(300))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }
}
