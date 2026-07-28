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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import sparkx.sparkshop.knowledge.config.RagProperties;
import sparkx.sparkshop.knowledge.entity.ConversationMessageEntity;
import sparkx.sparkshop.knowledge.mapper.ConversationMessageMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话记忆存储（文档 5.11.1）—— 滑动窗口策略。
 *
 * historyKeepTurns=4 → 保留最近 4 轮（= 8 条 USER/ASSISTANT 消息）。
 * normalizeHistory：开头连续 ASSISTANT 裁掉，保证历史从 USER 开始（避免半截对话）。
 */
@Component
public class ConversationMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryStore.class);

    private final ConversationMessageMapper repo;
    private final int historyKeepTurns;

    public ConversationMemoryStore(ConversationMessageMapper repo,
                                   RagProperties props) {
        this.repo = repo;
        this.historyKeepTurns = Math.max(1, props.getMemory().getHistoryKeepTurns());
    }

    /** 加载最近 historyKeepTurns*2 条消息（时序正序，已 normalize） */
    public List<ChatMessage> loadHistory(String conversationId, String userId) {
        return loadHistory(conversationId, userId, null);
    }

    /**
     * 加载最近 N 轮历史，支持运行时覆盖轮数（智能体用）。
     *
     * @param turnsOverride 覆盖轮数；null 走全局 historyKeepTurns
     */
    public List<ChatMessage> loadHistory(String conversationId, String userId, Integer turnsOverride) {
        int turns = turnsOverride != null && turnsOverride > 0 ? turnsOverride : historyKeepTurns;
        int limit = turns * 2;   // 一轮 = user + assistant
        List<ConversationMessageEntity> rows = repo.findRecent(
                conversationId, userId, limit);
        // findRecent 是 id DESC，反转为时序正序
        Collections.reverse(rows);
        List<ChatMessage> msgs = new ArrayList<>(rows.size());
        for (ConversationMessageEntity row : rows) {
            ChatMessage msg = toChatMessage(row);
            if (msg != null) msgs.add(msg);
        }
        return normalizeHistory(msgs);
    }

    /** 追加一条消息 */
    public void append(String conversationId, String userId, ChatMessage message) {
        append(conversationId, userId, message, null);
    }

    /**
     * 追加一条消息（可携带 RAG 上下文）。
     *
     * @param ragContextJson RAG 调用流程上下文 JSON 字符串；仅 assistant 消息传入，其余传 null
     */
    public void append(String conversationId, String userId, ChatMessage message, String ragContextJson) {
        String role = roleOf(message);
        String content = textOf(message);
        repo.insert(new ConversationMessageEntity(conversationId, userId, role, content, null, ragContextJson));
    }

    /** 统计用户消息数（摘要闸门用） */
    public long countUserMessages(String conversationId, String userId) {
        return repo.countUserMessages(conversationId, userId);
    }


    /** 裁掉开头连续的 ASSISTANT（保证历史从 USER 开始） */
    private List<ChatMessage> normalizeHistory(List<ChatMessage> msgs) {
        int start = 0;
        while (start < msgs.size() && isAssistant(msgs.get(start))) {
            start++;
        }
        return start == 0 ? msgs : new ArrayList<>(msgs.subList(start, msgs.size()));
    }

    private ChatMessage toChatMessage(ConversationMessageEntity row) {
        return switch (row.getRole().toLowerCase()) {
            case "user" -> UserMessage.from(row.getContent());
            case "assistant" -> AiMessage.from(row.getContent());
            case "system" -> SystemMessage.from(row.getContent());
            default -> null;
        };
    }

    private String roleOf(ChatMessage msg) {
        if (msg instanceof SystemMessage) return "system";
        if (msg instanceof UserMessage) return "user";
        if (msg instanceof AiMessage) return "assistant";
        return "user";
    }

    private String textOf(ChatMessage msg) {
        if (msg instanceof SystemMessage sm) return sm.text();
        if (msg instanceof UserMessage um) return um.singleText();
        if (msg instanceof AiMessage am) return am.text() != null ? am.text() : "";
        return "";
    }

    private boolean isAssistant(ChatMessage msg) {
        return msg instanceof AiMessage;
    }
}
