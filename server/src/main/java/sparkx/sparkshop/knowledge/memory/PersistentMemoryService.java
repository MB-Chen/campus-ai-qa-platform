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

import cn.hutool.core.util.StrUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.config.RagProperties;
import sparkx.sparkshop.knowledge.entity.ConversationMessageEntity;
import sparkx.sparkshop.knowledge.entity.PersistentMemoryEntity;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.knowledge.mapper.PersistentMemoryMapper;
import sparkx.sparkshop.knowledge.prompt.PromptTemplateLoader;

import java.util.List;
import java.util.Map;

/**
 * 跨会话持久记忆服务（对标 Claude Code 的 CLAUDE.md 持久记忆 + wU2 结构化分区摘要）。
 *
 * 与 {@link ConversationMemorySummaryService}（会话内话题摘要）并行、互补：
 * - 会话摘要：按 conversationId 隔离，记录本会话讨论的话题索引，会话结束即失效
 * - 持久记忆：按 agentId+adminId 隔离，沉淀跨会话的长期事实，跨会话共享
 *
 * 三大能力：
 * <ol>
 *   <li>{@link #load}：读取持久记忆 JSON，渲染成标签文本供注入 prompt</li>
 *   <li>{@link #extractIfNeeded}：每轮 assistant 回答后异步增量抽取长期事实，合并进持久记忆</li>
 *   <li>{@link #renderForPrompt}：把结构化 JSON 渲染成 {@code <persistent-memory>} 标签文本</li>
 * </ol>
 *
 * ★ 增量抽取采用「跨会话族」维度：记忆 key 是 pmem:{agentId}:{adminId}，
 * 但消息分布在多个 conversationId（"agent:{agentId}:*"）下，
 * 用 LIKE 前缀匹配该智能体下所有会话，按 id 全局时序拉取增量。
 */
@Component
public class PersistentMemoryService {

    private static final Logger log = LoggerFactory.getLogger(PersistentMemoryService.class);

    /** 持久记忆 userId（与 AgentChatService.OPERATOR_USER_ID 一致，会话消息都落这个 userId） */
    private static final String OPERATOR_USER_ID = "agent:operator";

    /** 空持久记忆 JSON（所有分区为空） */
    private static final String EMPTY_MEMORY_JSON =
            "{\"userProfile\":\"\",\"longTermConstraints\":[],\"confirmedFacts\":[],\"preferences\":[]}";

    private final LLMService llmService;
    private final PersistentMemoryMapper memoryRepo;
    private final RedissonClient redisson;
    private final PromptTemplateLoader templateLoader;
    private final boolean persistentEnabled;
    private final int persistentStartTurns;
    private final int extractInterval;
    private final int persistentMaxChars;
    private final int persistentBatchMessages;

    public PersistentMemoryService(LLMService llmService,
                                   PersistentMemoryMapper memoryRepo,
                                   RedissonClient redisson,
                                   PromptTemplateLoader templateLoader,
                                   RagProperties props) {
        this.llmService = llmService;
        this.memoryRepo = memoryRepo;
        this.redisson = redisson;
        this.templateLoader = templateLoader;
        RagProperties.Memory mem = props.getMemory();
        this.persistentEnabled = mem.isPersistentEnabled();
        this.persistentStartTurns = Math.max(2, mem.getPersistentStartTurns());
        this.extractInterval = Math.max(2, mem.getExtractInterval());
        this.persistentMaxChars = Math.max(100, mem.getPersistentMaxChars());
        this.persistentBatchMessages = Math.max(4, mem.getPersistentBatchMessages());
    }

    /**
     * 加载持久记忆并渲染成标签文本（供 GenerateStage 注入 SystemMessage）。
     *
     * @param memoryKey pmem:{agentId}:{adminId}；null 表示不启用，直接返回 null
     * @return 渲染后的 {@code <persistent-memory>} 标签文本，无记忆或为空时返回 null
     */
    public String load(String memoryKey) {
        if (StrUtil.isBlank(memoryKey)) return null;
        PersistentMemoryEntity entity = memoryRepo.selectById(memoryKey);
        if (entity == null || StrUtil.isBlank(entity.getMemoryJson())) return null;
        return renderForPrompt(entity.getMemoryJson());
    }

    /**
     * 异步增量抽取持久记忆（ASSISTANT 消息追加后调用）。
     *
     * @param memoryKey      pmem:{agentId}:{adminId}
     * @param conversationId 当前会话 id（用于推导该会话族的消息前缀）
     * @param rewriteModelId 抽取用的模型 id（复用 rewriteModelId/Name，小快模型降本）
     * @param rewriteModelName 抽取用的模型名
     */
    @Async("memorySummaryExecutor")
    public void extractIfNeeded(String memoryKey, String conversationId,
                                Integer rewriteModelId, String rewriteModelName) {
        if (!persistentEnabled || StrUtil.isBlank(memoryKey)) return;
        try {
            // 推导该会话族的消息前缀：从 conversationId "agent:{agentId}:{xxx}" 提取 "agent:{agentId}:"
            String convPrefix = extractAgentPrefix(conversationId);
            if (convPrefix == null) return;

            // 读取已有记忆（含 messageCount 缓存 + lastExtractedMessageId 增量下界）
            PersistentMemoryEntity existing = memoryRepo.selectById(memoryKey);

            // 闸门 1：该会话族用户消息总数 < persistentStartTurns 不抽取（避免早期噪音）
            // 优先用已缓存的 messageCount，避免每次都 count 全表
            int cachedCount = existing != null && existing.getMessageCount() != null
                    ? existing.getMessageCount() : -1;
            long totalUserMsgs;
            if (cachedCount >= 0) {
                // 缓存值可能滞后，追加 1（本轮刚加的 user 消息）
                totalUserMsgs = cachedCount + 1;
            } else {
                totalUserMsgs = memoryRepo.countUserMessagesByAgent(convPrefix, OPERATOR_USER_ID);
            }
            if (totalUserMsgs < persistentStartTurns) {
                // 仍更新缓存的 messageCount，供下次闸门快速判断
                upsertMessageCount(memoryKey, (int) totalUserMsgs, existing);
                return;
            }

            // ★ 闸门 2（间隔节流）：达到起步阈值后，不是每轮都抽，而是每积累 extractInterval 轮才抽一次。
            // 持久记忆是「长期事实」，大部分轮次不会产生新事实，频繁抽取纯属浪费 LLM 成本。
            // 判断方式：自上次抽取（lastExtractedMessageId）以来的新消息条数是否达到 extractInterval*2
            // （1 轮 = user + assistant 两条消息）。首次抽取（lastExtractedMessageId 为 null）不卡间隔。
            if (existing != null && existing.getLastExtractedMessageId() != null) {
                long afterId = existing.getLastExtractedMessageId();
                // 用增量查询拉新消息，只看条数（不传结果），轻量
                List<ConversationMessageEntity> pending = memoryRepo.findIncrementalByAgent(
                        convPrefix, OPERATOR_USER_ID, afterId, extractInterval * 2 + 1);
                // 待抽取的新消息不足一个间隔周期 → 跳过（仍更新 messageCount）
                if (pending.size() < extractInterval * 2) {
                    upsertMessageCount(memoryKey, (int) totalUserMsgs, existing);
                    return;
                }
            }

            // ★ Redisson 分布式锁防并发重复抽取（按会话族维度，避开 summary 的锁 key）
            RLock lock = redisson.getLock("memory:persistent:lock:" + memoryKey);
            if (!lock.tryLock()) return;
            try {
                doExtract(memoryKey, convPrefix, rewriteModelId, rewriteModelName, (int) totalUserMsgs);
            } finally {
                if (lock.isHeldByCurrentThread()) lock.unlock();
            }
        } catch (Exception e) {
            // 抽取失败不影响主流程，仅记录
            log.warn("[PersistentMemory] 抽取失败 key={}: {}", memoryKey, e.getMessage());
        }
    }

    /**
     * 把结构化 JSON 渲染成 {@code <persistent-memory>} 标签包裹的可读文本。
     * 空记忆（全分区为空）返回 null，避免注入无意义内容。
     */
    public String renderForPrompt(String memoryJson) {
        if (StrUtil.isBlank(memoryJson)) return null;
        try {
            cn.hutool.json.JSONObject mem = cn.hutool.json.JSONUtil.parseObj(memoryJson);
            StringBuilder sb = new StringBuilder();

            String profile = mem.getStr("userProfile", "");
            if (StrUtil.isNotBlank(profile)) {
                sb.append("【用户画像】").append(profile).append("\n");
            }
            String constraints = joinStrList(mem, "longTermConstraints");
            if (StrUtil.isNotBlank(constraints)) {
                sb.append("【长期约束】").append(constraints).append("\n");
            }
            String facts = joinStrList(mem, "confirmedFacts");
            if (StrUtil.isNotBlank(facts)) {
                sb.append("【已确认事实】").append(facts).append("\n");
            }
            String prefs = joinStrList(mem, "preferences");
            if (StrUtil.isNotBlank(prefs)) {
                sb.append("【交互偏好】").append(prefs).append("\n");
            }
            if (sb.isEmpty()) return null;
            return "<persistent-memory>\n" + sb.toString().strip() + "\n</persistent-memory>";
        } catch (Exception e) {
            log.debug("[PersistentMemory] 渲染失败，降级为原始 JSON: {}", e.getMessage());
            // 解析失败时降级：直接包标签返回原始 JSON
            return "<persistent-memory>\n" + memoryJson + "\n</persistent-memory>";
        }
    }

    /** 从 JSONObject 取字符串数组并拼接（分号分隔），过滤空值 */
    @SuppressWarnings("unchecked")
    private String joinStrList(cn.hutool.json.JSONObject mem, String key) {
        Object val = mem.get(key);
        if (!(val instanceof java.util.List<?> list)) return "";
        return list.stream()
                .filter(o -> o != null && !o.toString().isBlank())
                .map(Object::toString)
                .collect(java.util.stream.Collectors.joining("；"));
    }

    // ============================ 内部实现 ============================

    /** 增量抽取核心逻辑 */
    private void doExtract(String memoryKey, String convPrefix,
                           Integer rewriteModelId, String rewriteModelName,
                           int totalUserMsgs) {
        PersistentMemoryEntity prev = memoryRepo.selectById(memoryKey);
        Long afterId = prev != null ? prev.getLastExtractedMessageId() : null;
        long lowerBound = afterId == null ? 0L : afterId;

        // 拉取增量消息（跨会话族，按 id ASC），限制条数防输入过长。
        // 至少拉够一个间隔周期（extractInterval*2），避免间隔积累期间的消息被截断
        int fetchLimit = Math.max(persistentBatchMessages, extractInterval * 2);
        List<ConversationMessageEntity> toExtract = memoryRepo.findIncrementalByAgent(
                convPrefix, OPERATOR_USER_ID, lowerBound, fetchLimit);
        if (toExtract.isEmpty()) return;

        String messagesText = renderMessages(toExtract);
        String existingJson = prev != null ? prev.getMemoryJson() : null;

        // 渲染抽取提示词
        String system = renderExtractPrompt();
        String user = buildExtractUserPrompt(existingJson, messagesText);

        // 调 LLM 抽取（复用 rewriteModel 小快模型降本；modelId 为 null 时走默认候选链）
        String extracted;
        if (rewriteModelId != null) {
            LlmChatRequest req = LlmChatRequest.of(system, user, 0.2, 0.9);
            extracted = llmService.chat(req, rewriteModelId, rewriteModelName);
        } else {
            extracted = llmService.chat(system + "\n\n" + user, 0.2, 0.9, false);
        }
        if (StrUtil.isBlank(extracted)) return;

        // 清洗：LLM 可能输出 markdown 代码块包裹的 JSON，剥离
        String cleaned = cleanJsonOutput(extracted);
        if (!isValidMemoryJson(cleaned)) {
            log.debug("[PersistentMemory] 抽取结果非合法 JSON，跳过 key={}", memoryKey);
            return;
        }

        // 计算本次抽取到的最大消息 id（作为下次增量下界）
        long maxId = toExtract.stream()
                .mapToLong(ConversationMessageEntity::getId)
                .max().orElse(lowerBound);

        // 覆盖式持久化（每个会话族一份）
        int newVersion = (prev != null && prev.getVersion() != null ? prev.getVersion() : 0) + 1;
        PersistentMemoryEntity entity = prev != null ? prev : new PersistentMemoryEntity();
        entity.setMemoryKey(memoryKey);
        entity.setMemoryJson(cleaned);
        entity.setVersion(newVersion);
        entity.setLastExtractedMessageId(maxId);
        entity.setMessageCount(totalUserMsgs);
        if (prev == null) {
            memoryRepo.insert(entity);
        } else {
            memoryRepo.updateById(entity);
        }
        log.debug("[PersistentMemory] 已抽取 key={} version={} msgs={}",
                memoryKey, newVersion, totalUserMsgs);
    }

    /** 渲染抽取提示词（用 PromptTemplateLoader 加载 .st 模板） */
    private String renderExtractPrompt() {
        try {
            return templateLoader.render("persistent-memory-extract.st",
                    Map.of("persistent_max_chars", String.valueOf(persistentMaxChars)));
        } catch (Exception e) {
            // 模板加载失败时降级为内置最小 prompt
            log.warn("[PersistentMemory] 提示词模板加载失败，降级: {}", e.getMessage());
            return "你是持久记忆抽取器。从对话提取跨会话有价值的事实，输出 JSON：" +
                    "{\"userProfile\":\"\",\"longTermConstraints\":[],\"confirmedFacts\":[],\"preferences\":[]}" +
                    "。总字符数≤" + persistentMaxChars + "，不记具体答案，单行输出。";
        }
    }

    /** 构造抽取的 user 输入：历史记忆 + 本次对话（增量合并提示） */
    private String buildExtractUserPrompt(String existingJson, String messagesText) {
        if (StrUtil.isNotBlank(existingJson) && !EMPTY_MEMORY_JSON.equals(existingJson)) {
            return "历史记忆（用于增量合并去重，不作为新事实来源；与本次对话冲突以本次为准）：\n"
                    + existingJson + "\n\n本次新增对话：\n" + messagesText
                    + "\n\n合并以上记忆，输出更新后的完整 JSON。要求：严格单行 JSON，总字符≤"
                    + persistentMaxChars + "。";
        }
        return "对话内容：\n" + messagesText
                + "\n\n提取持久记忆，输出 JSON。要求：严格单行 JSON，总字符≤"
                + persistentMaxChars + "。";
    }

    /** 从 conversationId "agent:{agentId}:{xxx}" 提取 "agent:{agentId}:" 前缀 */
    private String extractAgentPrefix(String conversationId) {
        if (conversationId == null) return null;
        // 格式：agent:{agentId}:{conversationId}（3段）或评估 agent:{agentId}:eval:{uuid}（4段）
        int firstColon = conversationId.indexOf(':');
        int secondColon = conversationId.indexOf(':', firstColon + 1);
        if (firstColon < 0 || secondColon < 0) return null;
        return conversationId.substring(0, secondColon + 1);
    }

    /** 只更新缓存的 messageCount（闸门用，避免每次 count 全表） */
    private void upsertMessageCount(String memoryKey, int count, PersistentMemoryEntity existing) {
        try {
            PersistentMemoryEntity entity = existing != null ? existing : new PersistentMemoryEntity();
            entity.setMemoryKey(memoryKey);
            entity.setMessageCount(count);
            if (existing == null) {
                memoryRepo.insert(entity);
            } else {
                memoryRepo.updateById(entity);
            }
        } catch (Exception ignore) {
            // messageCount 只是优化缓存，失败无所谓
        }
    }

    /** 清洗 LLM 输出：剥离 markdown 代码块包裹 */
    private String cleanJsonOutput(String raw) {
        String s = raw.strip();
        if (s.startsWith("```")) {
            // 去掉首行 ```json 或 ```
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) s = s.substring(firstNl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
            s = s.strip();
        }
        return s;
    }

    /** 校验是否为合法的持久记忆 JSON（含 4 个分区键） */
    private boolean isValidMemoryJson(String json) {
        if (StrUtil.isBlank(json)) return false;
        try {
            Map<String, Object> m = cn.hutool.json.JSONUtil.parseObj(json);
            return m.containsKey("userProfile")
                    && m.containsKey("longTermConstraints")
                    && m.containsKey("confirmedFacts")
                    && m.containsKey("preferences");
        } catch (Exception e) {
            return false;
        }
    }

    private String renderMessages(List<ConversationMessageEntity> msgs) {
        StringBuilder sb = new StringBuilder();
        for (ConversationMessageEntity m : msgs) {
            sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString().trim();
    }
}
