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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模型调用 URL 解析（文档 5.10）。
 *
 * 优先级：candidate.url > ProviderConfig.url + endpoints[capability]
 * 智能拼接斜杠，避免 //chat 或 /chat 拼接错误。
 *
 * 本实现采用最简形态：直接用 ModelTarget.url（由 AiModelProperties 的 candidate.url 提供），
 * 未配置时回退到 OpenAI 默认端点。多供应商 endpoint 矩阵属于后续增强。
 */
public final class ModelUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(ModelUrlResolver.class);

    /** OpenAI 兼容 chat completions 路径 */
    private static final String CHAT_PATH = "/chat/completions";
    /** OpenAI 兼容 embeddings 路径 */
    private static final String EMBEDDING_PATH = "/embeddings";

    private ModelUrlResolver() { }

    /** 解析 chat completions URL */
    public static String resolveChatUrl(ModelTarget target) {
        return resolve(target, CHAT_PATH);
    }

    /** 解析 embeddings URL */
    public static String resolveEmbeddingUrl(ModelTarget target) {
        return resolve(target, EMBEDDING_PATH);
    }

    private static String resolve(ModelTarget target, String path) {
        String base = target.url();
        if (base == null || base.isBlank()) {
            // ★ 历史坑（langchain4j 默认 OpenAI 地址问题）：模型未配 url 时静默回退 OpenAI 默认端点，
            // 导致误打到 api.openai.com。这里保留兜底以保证可用性，但显式告警，便于发现「漏配 url」的模型。
            log.warn("[ModelUrl] 模型 id={} provider={} model={} 未配置 url，回退 OpenAI 默认端点"
                    + "（https://api.openai.com/v1{}），请检查 ai_model 表的 options.url 配置",
                    target.id(), target.provider(), target.model(), path);
            return "https://api.openai.com/v1" + path;
        }
        // ★ 设计变更：options.url 由用户填写完整接口地址（含协议路径），后端不再自动补全路径。
        // 直接以用户输入为准（仅做 trim）。
        return base.trim();
    }
}
