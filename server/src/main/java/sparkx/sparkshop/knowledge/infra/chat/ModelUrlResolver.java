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
            return "https://api.openai.com/v1" + path;
        }
        base = base.trim();
        // 已是完整路径（含 /chat/completions）直接返回
        if (base.contains("/chat/completions") || base.contains("/embeddings")) {
            return base;
        }
        // 去尾斜杠再拼路径
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + path;
    }
}
