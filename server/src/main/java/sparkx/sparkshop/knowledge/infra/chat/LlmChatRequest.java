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

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 模型调用请求（供应商无关）。文档 5.10.1。
 *
 * ★ 命名刻意区别于 {@code dev.langchain4j.model.chat.request.ChatRequest}（LangChain4j 自有类），
 * 避免同包/同文件 import 混淆。
 *
 * @param messages     消息列表（system/user/assistant）
 * @param temperature  采样温度
 * @param topP         nucleus sampling
 * @param topK         top-k 采样（部分供应商支持，-1 表示不设置）
 * @param maxTokens    最大生成 token（-1 表示不设置）
 * @param thinking     是否启用深度思考（reasoning_content）
 */
public record LlmChatRequest(
        List<ChatMessage> messages,
        double temperature,
        double topP,
        int topK,
        int maxTokens,
        boolean thinking
) {
    /** 仅消息 + 温度的便捷构造（意图/改写等同步调用常用） */
    public static LlmChatRequest of(String systemPrompt, String userPrompt, double temperature) {
        return of(systemPrompt, userPrompt, temperature, 1.0);
    }

    /** 消息 + 温度 + topP 的便捷构造（意图分类/改写等需低 topP 的同步调用用） */
    public static LlmChatRequest of(String systemPrompt, String userPrompt, double temperature, double topP) {
        return new LlmChatRequest(
                List.of(dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                        dev.langchain4j.data.message.UserMessage.from(userPrompt)),
                temperature, topP, -1, -1, false);
    }

    /** 单条 user 消息便捷构造 */
    public static LlmChatRequest ofUser(String userPrompt, double temperature) {
        return ofUser(userPrompt, temperature, 1.0);
    }

    /** 单条 user 消息 + topP 便捷构造（意图分类/改写等把整段 prompt 作为 user 消息的同步调用用） */
    public static LlmChatRequest ofUser(String userPrompt, double temperature, double topP) {
        return new LlmChatRequest(
                List.of(dev.langchain4j.data.message.UserMessage.from(userPrompt)),
                temperature, topP, -1, -1, false);
    }
}
