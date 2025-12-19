package sparkx.service.helper;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;
import static org.mapdb.Serializer.STRING;

@Component
@Slf4j
public class MemoryBuildHelper implements ChatMemoryStore {

    private final DB db = DBMaker.fileDB("multi-user-chat-memory.db").transactionEnable().make();
    private final Map<String, String> map = db.hashMap("messages", STRING, STRING).createOrOpen();

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {

        String json = map.get(String.valueOf(memoryId));
        List<ChatMessage> messages = messagesFromJson(json);
        
        // 确保返回非空列表，避免 "messages cannot be null or empty"错误
        if (messages == null) {
            log.debug("内存中没有找到消息记录，memoryId: {}，返回空列表", memoryId);
            return new ArrayList<>();
        }
        
        // 检查消息列表是否有效（至少包含一条有效消息）
        if (messages.isEmpty()) {
            log.debug("消息列表为空，memoryId: {}，返回空列表", memoryId);
            return new ArrayList<>();
        }
        
        // 检查消息是否全部为空或无效内容
        boolean hasValidMessage = messages.stream().anyMatch(msg -> {
            if (msg == null) return false;
            
            // 检查用户消息
            if (msg instanceof dev.langchain4j.data.message.UserMessage userMsg) {
                return userMsg.singleText() != null && !userMsg.singleText().trim().isEmpty();
            }
            
            // 检查AI消息
            if (msg instanceof dev.langchain4j.data.message.AiMessage aiMsg) {
                return aiMsg.text() != null && !aiMsg.text().trim().isEmpty() || aiMsg.hasToolExecutionRequests();
            }
            
            // 其他类型消息默认认为有效
            return true;
        });
        
        if (!hasValidMessage) {
            log.debug("消息列表中没有有效消息，memoryId: {}，返回空列表", memoryId);
            return new ArrayList<>();
        }
        
        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = messagesToJson(messages);
        map.put(String.valueOf(memoryId), json);
        db.commit();
    }

    @Override
    public void deleteMessages(Object memoryId) {
        map.remove(String.valueOf(memoryId));
        db.commit();
    }
}