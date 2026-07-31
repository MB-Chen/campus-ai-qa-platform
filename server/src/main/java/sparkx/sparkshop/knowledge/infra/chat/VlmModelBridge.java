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

import sparkx.sparkshop.knowledge.common.exception.RagException;
import sparkx.sparkshop.knowledge.config.AiModelProperties;
import sparkx.sparkshop.knowledge.service.IAiModelService;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * 视觉模型桥接（文档 5.9）。
 *
 * <p>★ 配置来源：每次调用都从 {@code ai_model} 表读取第一个启用的视觉模型（{@code type=4}），
 * 动态构建 {@link OpenAiChatModel}。运营在后台改完 url/apiKey/model 立即生效，不走 yml 兜底。
 *
 * <p>★ 容错：VLM 是入库侧非热路径，不做降级。
 * 调用失败（无候选 / 连接异常 / 模型报错）直接抛 {@link RagException}，
 * 由 {@code MinerUImageDescriber} 捕获后让该图按「无描述」处理（保留原图引用）。
 *
 * <p>⚠️ 正确的多模态调用方式：构造 {@link UserMessage}（含 {@link TextContent} + {@link ImageContent}），
 *    走 {@code ChatModel.chat(ChatMessage...)}。LangChain4j 的 {@code OpenAiChatModel} 会把
 *    ImageContent 的 base64Data 自动转成 OpenAI 协议的 {@code image_url: {url: "data:mime;base64,..."}}。
 *
 * <p>历史问题：旧实现 {@code vlmModel.chat(prompt + "\n[image: " + dataUrl + "]")} 把 data URL 当纯文本拼接，
 * {@code chat(String)} 只发文本 message，VLM 实际看不到图，OCR/Caption 全是模型瞎编。
 */
@Component
public class VlmModelBridge {

    private static final Logger log = LoggerFactory.getLogger(VlmModelBridge.class);

    private final IAiModelService aiModelService;

    public VlmModelBridge(IAiModelService aiModelService) {
        this.aiModelService = aiModelService;
    }

    /**
     * 图片图生文（VLM）：描述 + OCR 二合一。
     *
     * @param content   图片字节数据
     * @param mime      图片 MIME 类型（image/png / image/jpeg）
     * @param prompt    描述/OCR 指令；为空时用默认「描述内容 + 提取文字」二合一 prompt
     * @param maxTokens 最大生成 token（预留，当前 LangChain4j ChatModel 走 yml 全局配置）
     * @return 图片描述文本
     */
    public String describeImage(byte[] content, String mime, String prompt, int maxTokens) {
        ChatModel vlmModel = resolveVlmModel();
        try {
            String detailLevel = (prompt != null && !prompt.isBlank()) ? prompt
                    : "请描述这张图片的内容（场景、对象、布局、关键细节），并提取其中所有可见文字（OCR）。用中文输出。";
            String safeMime = (mime == null || mime.isBlank()) ? "image/png" : mime;
            String base64 = Base64.getEncoder().encodeToString(content);
            UserMessage msg = UserMessage.from(
                    TextContent.from(detailLevel),
                    ImageContent.from(base64, safeMime)
            );
            ChatResponse resp = vlmModel.chat(msg);
            String text = resp.aiMessage() == null ? null : resp.aiMessage().text();
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            log.error("[VLM] 图生文失败: {}", e.getMessage());
            throw new RagException(500, "VLM 图生文失败: " + e.getMessage());
        }
    }

    /**
     * 从 {@code ai_model} 表读取第一个启用的视觉模型候选（{@code type=4}，按 priority ASC），
     * 动态构建 {@link OpenAiChatModel}。每次调用都重新读，保证「读取最新配置」。
     *
     * <p>无启用候选时抛 {@link RagException}，调用方按「VLM 不可用」处理。
     */
    private ChatModel resolveVlmModel() {
        List<AiModelProperties.ModelCandidate> candidates = aiModelService.getVlmCandidates();
        if (candidates == null || candidates.isEmpty()) {
            throw new RagException(500, "未配置启用的视觉模型（ai_model 表 type=4）");
        }
        AiModelProperties.ModelCandidate c = candidates.get(0);
        log.debug("[VLM] 使用候选 model={} provider={} url={}", c.getModel(), c.getProvider(), c.getUrl());
        // ★ 设计变更：options.url 为完整接口地址（含 /chat/completions）。
        // 但 langchain4j 的 OpenAiChatModel 会在 baseUrl 后自动追加 /chat/completions，
        // 故此处剥离已知端点后缀再传入，避免 .../chat/completions/chat/completions 双路径 404。
        return OpenAiChatModel.builder()
                .baseUrl(normalizeLangchainBaseUrl(c.getUrl()))
                .apiKey(c.getApiKey())
                .modelName(c.getModel())
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    /**
     * 归一化 langchain4j 模型 baseUrl。
     *
     * <p>options.url 由用户填写完整接口地址（如 .../v1/chat/completions），而 langchain4j 的
     * OpenAiChatModel / OpenAiEmbeddingModel 会自行追加对应协议路径（/chat/completions、/embeddings），
     * 若直接传入完整地址会变成双路径 → 供应商网关 404。故剥离已知端点后缀及尾斜杠后再交给 langchain4j。
     *
     * @param url 配置中的原始 url（可能含协议路径后缀）
     * @return 净 baseUrl；为 null/空时原样返回 null
     */
    private static String normalizeLangchainBaseUrl(String url) {
        if (url == null || url.isBlank()) return url;
        String u = url.trim();
        String[] suffixes = {"/chat/completions", "/embeddings", "/rerank"};
        for (String s : suffixes) {
            if (u.endsWith(s)) {
                u = u.substring(0, u.length() - s.length());
                break;
            }
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
