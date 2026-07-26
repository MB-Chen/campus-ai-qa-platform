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

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 现有 LangChain4j ChatModel 桥接（降级路径）。
 *
 * 当 {@code RoutingLLMService} 检测到无注册 ChatClient（例如仅配单模型、未启用适配层）时，
 * 降级使用现有 {@code ChatModel}/{@code StreamingChatModel} Bean，
 * 保证"无显式多候选配置也能跑"。
 *
 * 对齐 LangChain4j 1.17.0 接口：
 *  - {@code ChatModel.chat(ChatRequest)} 返回 {@code ChatResponse}
 *  - {@code StreamingChatModel.chat(ChatRequest, StreamingChatResponseHandler)}
 */
@Component
public class ChatModelBridge {

    private static final Logger log = LoggerFactory.getLogger(ChatModelBridge.class);

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;

    public ChatModelBridge(ChatModel chatModel, StreamingChatModel streamingChatModel) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }

    /** 同步对话：复用现有 ChatModel，经 ChatRequest 返回 ChatResponse */
    public String chat(LlmChatRequest request) {
        ChatRequest lcReq = ChatRequest.builder()
                .messages(request.messages())
                .build();
        ChatResponse response = chatModel.chat(lcReq);
        return response.aiMessage().text();
    }

    /** 流式对话：复用现有 StreamingChatModel，适配到本模块 StreamCallback */
    public StreamCancellationHandle streamChat(LlmChatRequest request, StreamCallback callback) {
        ChatRequest lcReq = ChatRequest.builder()
                .messages(request.messages())
                .build();
        streamingChatModel.chat(lcReq, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                callback.onContent(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                callback.onComplete();
            }

            @Override
            public void onError(Throwable error) {
                callback.onError(error);
            }
        });
        return StreamCancellationHandle.noop();
    }
}
