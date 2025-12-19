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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 智谱AI工具调用消息序列重建器
 * 
 * 根据错误分析，LangChain4j在工具调用时生成的消息序列与智谱AI期望的不一致：
 * 1. LangChain4j：工具调用请求(text=null) + 工具执行结果
 * 2. 智谱AI期望：完整的对话上下文 + 工具调用请求(text有内容) + 工具执行结果
 * 
 * 这个类负责重建符合智谱AI要求的消息序列
 */
@Slf4j
public class ZhipuAiToolCallRebuilder {

    /**
     * 重建智谱AI工具调用消息序列
     *
     * @param originalMessages 原始消息列表
     * @return 重建后的消息列表
     */
    public static List<ChatMessage> rebuildForZhipuAi(List<ChatMessage> originalMessages) {
        if (originalMessages == null || originalMessages.isEmpty()) {
            return originalMessages;
        }

        // 检查是否包含工具调用相关的消息
        if (!containsToolCallMessages(originalMessages)) {
            return originalMessages;
        }

        log.debug("重建智谱AI工具调用消息序列，原始消息数量: {}", originalMessages.size());

        try {
            // 分析消息序列结构
            MessageSequenceAnalysis analysis = analyzeMessageSequence(originalMessages);
            
            // 直接修复消息格式，不进行复杂的重建逻辑
            return fixMessageFormat(analysis);
        } catch (Exception e) {
            log.error("重建智谱AI消息序列时发生错误: {}", e.getMessage(), e);
            // 直接返回原始消息，不进行降级处理
            return originalMessages;
        }
    }

    /**
     * 分析消息序列
     */
    private static MessageSequenceAnalysis analyzeMessageSequence(List<ChatMessage> messages) {
        MessageSequenceAnalysis analysis = new MessageSequenceAnalysis();
        
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            
            if (message instanceof UserMessage) {
                analysis.userMessages.add(message);
                analysis.orderedMessages.add(message);
            } else if (message instanceof AiMessage aiMessage) {
                if (aiMessage.hasToolExecutionRequests()) {
                    analysis.toolCallMessages.add(aiMessage);
                    analysis.orderedMessages.add(aiMessage);
                    // 检查text是否为空
                    if (aiMessage.text() == null || aiMessage.text().trim().isEmpty()) {
                        analysis.hasEmptyToolCallMessage = true;
                    }
                } else {
                    analysis.regularAiMessages.add(aiMessage);
                    analysis.orderedMessages.add(aiMessage);
                }
            } else if (message instanceof ToolExecutionResultMessage toolMessage) {
                analysis.toolResultMessages.add(toolMessage);
                analysis.orderedMessages.add(toolMessage);
            } else {
                // 其他类型的消息也加入有序列表
                analysis.orderedMessages.add(message);
            }
        }
        
        return analysis;
    }

    /**
     * 智谱AI消息格式修复方法
     */
    private static List<ChatMessage> fixMessageFormat(MessageSequenceAnalysis analysis) {
        List<ChatMessage> fixedMessages = new ArrayList<>();
        
        // 智谱AI API特殊处理：当消息序列同时包含工具调用请求和工具执行结果时，
        // 需要将工具调用AI消息转换为用户消息，因为智谱AI期望的是用户消息+工具结果
        for (ChatMessage message : analysis.orderedMessages) {
            if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                // 检查消息序列中是否还有工具执行结果
                boolean hasToolResultAfter = analysis.orderedMessages.stream()
                    .dropWhile(msg -> msg != message)
                    .anyMatch(msg -> msg instanceof ToolExecutionResultMessage);
                
                if (hasToolResultAfter) {
                    // 如果后面有工具执行结果，将工具调用AI消息转换为用户消息
                    String userContent = aiMessage.text() != null ? aiMessage.text() : "请使用工具帮我处理这个请求。";
                    dev.langchain4j.data.message.UserMessage userMessage = new dev.langchain4j.data.message.UserMessage(userContent);
                    fixedMessages.add(userMessage);
                    log.debug("将工具调用AI消息转换为用户消息: {}", userContent);
                } else {
                    // 如果后面没有工具执行结果，保持为AI消息
                    if (aiMessage.text() == null || aiMessage.text().trim().isEmpty()) {
                        log.debug("为空的工具调用AI消息添加占位符文本");
                        AiMessage fixedAiMessage = createFixedAiMessage(aiMessage);
                        fixedMessages.add(fixedAiMessage);
                    } else {
                        fixedMessages.add(aiMessage);
                    }
                }
            } else {
                // 其他消息直接添加
                fixedMessages.add(message);
            }
        }
        
        log.debug("智谱AI修复后的消息序列包含 {} 条消息", fixedMessages.size());
        return fixedMessages;
    }

    /**
     * 为空的工具调用AI消息创建修复版本
     */
    private static AiMessage createFixedAiMessage(AiMessage originalAiMessage) {
        // 创建一个描述性的文本内容，让智谱AI能够理解
        String placeholderText = "我需要使用工具来帮助回答这个问题。";
        
        // 创建新的AiMessage，保留工具调用请求但添加文本内容
        return new AiMessage(placeholderText, originalAiMessage.toolExecutionRequests());
    }

    /**
     * 检查消息列表是否包含工具调用相关的消息
     */
    private static boolean containsToolCallMessages(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                return true;
            }
            if (message instanceof ToolExecutionResultMessage) {
                return true;
            }
        }
        return false;
    }

    /**
     * 消息序列分析结果
     */
    private static class MessageSequenceAnalysis {
        List<ChatMessage> userMessages = new ArrayList<>();
        List<AiMessage> toolCallMessages = new ArrayList<>();
        List<AiMessage> regularAiMessages = new ArrayList<>();
        List<ToolExecutionResultMessage> toolResultMessages = new ArrayList<>();
        List<ChatMessage> orderedMessages = new ArrayList<>(); // 保持消息的原始顺序
        boolean hasEmptyToolCallMessage = false;
        
        boolean isIncomplete() {
            // 简化判断逻辑，只检查是否有空的工具调用消息
            return hasEmptyToolCallMessage;
        }
    }
}