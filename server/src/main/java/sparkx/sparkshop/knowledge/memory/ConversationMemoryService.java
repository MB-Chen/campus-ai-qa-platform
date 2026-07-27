// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话记忆编排服务（文档 5.11.1）—— 对外门面。
 *
 * load()：加载摘要 + 滑动窗口历史，把摘要用 {@code <conversation-summary>} 标签包裹插到 history 最前面。
 * append()：写入消息，ASSISTANT 消息追加后异步触发摘要压缩。
 */
@Component
public class ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);

    private final ConversationMemoryStore store;
    private final ConversationMemorySummaryService summaryService;

    public ConversationMemoryService(ConversationMemoryStore store,
                                     ConversationMemorySummaryService summaryService) {
        this.store = store;
        this.summaryService = summaryService;
    }

    /**
     * 加载：摘要（标签包裹）+ 滑动窗口历史。
     * 摘要作为一条 system 消息插最前，紧跟主系统提示词后。
     */
    public List<ChatMessage> load(String conversationId, String userId) {
        return load(conversationId, userId, null);
    }

    /**
     * 加载历史，支持运行时覆盖轮数（智能体用）。
     *
     * @param turnsOverride 覆盖轮数；null 走全局默认
     */
    public List<ChatMessage> load(String conversationId, String userId, Integer turnsOverride) {
        List<ChatMessage> result = new ArrayList<>();

        String summary = summaryService.loadSummary(conversationId, userId);
        if (summary != null && !summary.isBlank()) {
            result.add(SystemMessage.from(
                    "<conversation-summary>\n" + summary + "\n</conversation-summary>"));
        }

        List<ChatMessage> history = store.loadHistory(conversationId, userId, turnsOverride);
        result.addAll(history);
        return result;
    }

    /**
     * 追加消息。ASSISTANT 消息追加后异步触发摘要压缩。
     */
    public void append(String conversationId, String userId, ChatMessage message) {
        store.append(conversationId, userId, message);
        // 仅 ASSISTANT 消息追加后触发摘要（一轮对话完整）
        if (isAssistant(message)) {
            summaryService.compressIfNeeded(conversationId, userId);
        }
    }

    private boolean isAssistant(ChatMessage msg) {
        return msg instanceof dev.langchain4j.data.message.AiMessage;
    }
}
