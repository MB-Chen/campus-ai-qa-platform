// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.fallback;

import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.knowledge.infra.chat.StreamCallback;
import sparkx.sparkshop.knowledge.prompt.PromptTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 模型兜底
 * 引导模型基于通用知识回答，并明确告知"非来自知识库"。
 * 注意 1.17.0：ChatModel.chat(String) 返回 String。
 *
 * ★ 流式：{@link #fallbackStream} 走 {@link LLMService#streamChat} 逐 token 推送，
 *   避免 {@link #fallback}（同步 chat）「干等全文才一次性蹦出字」的体验。
 *   两者共用同一套 prompt 渲染 + RoutingLLMService 容错层（熔断/降级）。
 */
public class ModelFallbackProvider implements FallbackProvider {

    private static final Logger log = LoggerFactory.getLogger(ModelFallbackProvider.class);

    /** model 兜底采样参数（与原同步实现一致：稍高温度保证自然） */
    private static final double TEMPERATURE = 0.7;
    private static final double TOP_P = 0.9;

    private final LLMService llmService;
    private final PromptTemplateManager promptManager;

    public ModelFallbackProvider(LLMService llmService, PromptTemplateManager promptManager) {
        this.llmService = llmService;
        this.promptManager = promptManager;
    }

    @Override
    public String fallback(String query, String rewriteQuery, String language) {
        String prompt = buildPrompt(query, rewriteQuery, language);
        if (prompt == null) {
            return new FixedFallbackProvider().fallback(query, rewriteQuery, language);
        }
        try {
            // ★ 走 RoutingLLMService（ai_model 表路由 + 30s connect 超时 + 熔断降级），
            //   与意图识别/正式生成共用同一套可达的模型客户端，避免再怼 yml 默认 OpenAI 地址。
            return llmService.chat(prompt, TEMPERATURE, TOP_P, false);
        } catch (Exception e) {
            log.warn("[Fallback-Model] 同步调用失败，退化为固定话术: {}", e.getMessage());
            return new FixedFallbackProvider().fallback(query, rewriteQuery, language);
        }
    }

    @Override
    public String fallbackStream(String query, String rewriteQuery, String language,
                                 Consumer<String> tokenConsumer) {
        String prompt = buildPrompt(query, rewriteQuery, language);
        if (prompt == null) {
            return new FixedFallbackProvider().fallbackStream(query, rewriteQuery, language, tokenConsumer);
        }
        StringBuilder full = new StringBuilder();
        // streamChat 内部把读取异步提交到流式线程池，主调用只阻塞到首包探测成功即返回；
        // 因此用 future 在 onComplete/onError 时 release，这里阻塞等流真正结束再返回全文。
        java.util.concurrent.CompletableFuture<Void> done = new java.util.concurrent.CompletableFuture<>();
        try {
            LlmChatRequest request = LlmChatRequest.ofUser(prompt, TEMPERATURE);
            request = new LlmChatRequest(request.messages(), TEMPERATURE, TOP_P,
                    request.topK(), request.maxTokens(), false);
            // ★ 逐 token 经容错层流式生成（模型故障自动降级，首包探测无感切换）
            llmService.streamChat(request, new StreamCallback() {
                @Override
                public void onContent(String token) {
                    full.append(token);
                    if (tokenConsumer != null) tokenConsumer.accept(token);
                }

                @Override
                public void onComplete() {
                    done.complete(null);
                }

                @Override
                public void onError(Throwable e) {
                    log.warn("[Fallback-Model] 流式生成失败: {}", e.getMessage());
                    done.completeExceptionally(e);
                }
            }, false);
            // 阻塞等待流结束（流式线程池内 doStream 会在末尾回调 onComplete/onError）
            done.join();
            String answer = full.toString();
            if (answer.isEmpty()) {
                // 容错层全候选失败 → 退化固定话术（整段回调）
                return new FixedFallbackProvider().fallbackStream(query, rewriteQuery, language, tokenConsumer);
            }
            return answer;
        } catch (java.util.concurrent.CompletionException ce) {
            log.warn("[Fallback-Model] 流式调用失败，退化为固定话术: {}", ce.getMessage());
            return new FixedFallbackProvider().fallbackStream(query, rewriteQuery, language, tokenConsumer);
        } catch (Exception e) {
            log.warn("[Fallback-Model] 流式调用失败，退化为固定话术: {}", e.getMessage());
            return new FixedFallbackProvider().fallbackStream(query, rewriteQuery, language, tokenConsumer);
        }
    }

    /** 渲染 fallback.model 模板；模板缺失返回 null（调用方走固定话术） */
    private String buildPrompt(String query, String rewriteQuery, String language) {
        String q = (rewriteQuery != null && !rewriteQuery.isBlank()) ? rewriteQuery : query;
        String lang = (language == null || language.isBlank()) ? "中文" : language;
        try {
            return promptManager.render("fallback.model", Map.of("query", q, "language", lang));
        } catch (Exception e) {
            log.warn("[Fallback-Model] 模板渲染失败，走固定话术: {}", e.getMessage());
            return null;
        }
    }
}
