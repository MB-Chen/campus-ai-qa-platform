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
 * 模型调用目标 —— 携带 provider + model 信息，上层不关心 URL/API Key（文档 5.10.1）。
 *
 * @param id               模型唯一标识（熔断器 key，缺省为 "provider::model"）
 * @param model            供应商模型名（如 qwen-plus / gpt-4o-mini）
 * @param provider         供应商 id（openai / ollama / bailian / siliconflow）
 * @param url              调用 URL（candidate.url > provider.url + endpoints[chat]）
 * @param apiKey           API Key（Ollama 等本地模型为 null）
 * @param supportsThinking 该模型是否声明支持深度思考（对应 ai_model.supports_thinking）。
 *                         ★ 用于精确控制 enable_thinking 字段下发：仅在模型支持 thinking
 *                         且本次请求 thinking=false 时显式下发 enable_thinking=false，
 *                         避免供应商默认开启 reasoning_content 导致辅助任务（改写/分类/澄清）白白耗秒。
 */
public record ModelTarget(
        String id,
        String model,
        String provider,
        String url,
        String apiKey,
        boolean supportsThinking
) {
}
