// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.service.helper;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 智谱AI流式聊天模型包装器
 * 专门处理工具调用时的消息格式兼容性问题
 */
@Slf4j
public class ZhipuAiStreamingChatModelWrapper implements StreamingChatModel {

    private final StreamingChatModel delegate;

    public ZhipuAiStreamingChatModelWrapper(StreamingChatModel delegate) {
        this.delegate = delegate;
    }

    /**
     * 重写chat方法，在发送请求前修复消息格式
     */
    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        // 检查是否包含工具调用相关的消息
        if (hasToolCallMessages(request.messages())) {
            log.debug("检测到智谱AI工具调用，使用消息重建器修复消息序列");
            
            // 使用消息重建器修复工具调用消息，确保符合智谱AI API要求
            List<ChatMessage> fixedMessages = ZhipuAiToolCallRebuilder.rebuildForZhipuAi(request.messages());
            
            // 创建修复后的请求
            ChatRequest fixedRequest = ChatRequest.builder()
                    .messages(fixedMessages)
                    .parameters(request.parameters())
                    .build();
            
            // 创建包装的响应处理器，确保完成回调被正确调用
            StreamingChatResponseHandler wrappedHandler = new SafeCompletionHandler(handler);
            
            // 直接使用修复后的请求，不进行降级处理
            delegate.chat(fixedRequest, wrappedHandler);
        } else {
            // 没有工具调用消息，直接使用原始请求
            delegate.chat(request, handler);
        }
    }
    
    /**
     * 检查消息列表是否包含工具调用相关的消息
     */
    private boolean hasToolCallMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        
        for (ChatMessage message : messages) {
            if (message instanceof dev.langchain4j.data.message.AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                return true;
            }
            if (message instanceof dev.langchain4j.data.message.ToolExecutionResultMessage) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 简化的响应处理器包装器，移除复杂的错误处理逻辑
     */
    private static class SafeCompletionHandler implements StreamingChatResponseHandler {
        private final StreamingChatResponseHandler delegate;
        
        public SafeCompletionHandler(StreamingChatResponseHandler delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public void onPartialResponse(String partialResponse) {
            delegate.onPartialResponse(partialResponse);
        }
        
        @Override
        public void onCompleteResponse(ChatResponse response) {
            delegate.onCompleteResponse(response);
        }
        
        @Override
        public void onError(Throwable error) {
            delegate.onError(error);
        }
    }
}