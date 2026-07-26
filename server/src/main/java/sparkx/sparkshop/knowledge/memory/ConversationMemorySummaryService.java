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

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import sparkx.sparkshop.knowledge.config.RagProperties;
import sparkx.sparkshop.knowledge.entity.ConversationMessageEntity;
import sparkx.sparkshop.knowledge.entity.ConversationSummaryEntity;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.mapper.ConversationMessageMapper;
import sparkx.sparkshop.knowledge.mapper.ConversationSummaryMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 话题导向摘要压缩服务（文档 5.11.2）。
 *
 * 触发时机：ASSISTANT 消息追加后异步触发，用户消息总数 ≥ summaryStartTurns(5) 才压缩。
 * 增量合并：取上一份摘要的 lastMessageId 为下界，最近 4 轮为上界，拉增量消息，
 *          旧摘要作 assistant 消息注入 + "以本轮为准"约束前缀。
 * ★ Redisson 分布式锁防并发重复摘要。
 * ★ 核心约束"只记话题不记答案"由 conversation-summary 模板强制。
 */
@Component
public class ConversationMemorySummaryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemorySummaryService.class);

    /**
     * 摘要提示词模板（原 conversation-summary.st，跨集群 PromptTemplateLoader 暂未移植，内联）。
     * TODO: PromptTemplateLoader 落地后改回 templateLoader.render("conversation-summary.st", ...)。
     * {summary_max_chars} 占位符与原模板一致。
     */
    private static final String SUMMARY_TEMPLATE =
            "# 角色\n" +
            "你是会话记忆摘要器，将多轮对话浓缩为话题导向的摘要，用于帮助问答助手理解上下文。\n\n" +
            "# 核心约束\n" +
            "1. 长度≤{summary_max_chars}字符（含标点），单行输出\n" +
            "2. 话题需具体到子项（❌\"咨询了人事制度\" ✅\"咨询了年假天数计算、报销单据填写\"）\n" +
            "3. ✅保留：具体话题、处理状态、用户明确提出的约束（时间范围/地点/预算/设备型号）\n" +
            "   ❌忽略：具体数据、详细规则、完整流程、精确步骤、最终结论、长段解释\n" +
            "4. 字数超限：优先保留【话题+状态】>【关键约束】>【关键词】，同类合并，最多5-8个话题\n\n" +
            "# 状态标注\n" +
            "已解答 | 当时无记录 | 部分解答 | 待确认\n\n" +
            "# ★ 重要说明（防与实时检索冲突）\n" +
            "⚠️ 绝对禁止记录具体答案！原因：\n" +
            "- 当前问答系统会实时检索最新文档内容\n" +
            "- 摘要仅用于提供\"历史讨论的话题索引\"，不替代文档内容\n" +
            "- 若摘要包含答案，会与最新文档内容冲突，导致问答助手困惑\n\n" +
            "# 输出格式\n" +
            "用户咨询了【具体话题1】（状态）、【具体话题2】（状态）。关键词：词1, 词2";

    private final LLMService llmService;
    private final ConversationMessageMapper msgRepo;
    private final ConversationSummaryMapper summaryRepo;
    private final RedissonClient redisson;
    private final boolean summaryEnabled;
    private final int summaryStartTurns;
    private final int historyKeepTurns;
    private final int summaryMaxChars;

    public ConversationMemorySummaryService(LLMService llmService,
                                            ConversationMessageMapper msgRepo,
                                            ConversationSummaryMapper summaryRepo,
                                            RedissonClient redisson,
                                            RagProperties props) {
        this.llmService = llmService;
        this.msgRepo = msgRepo;
        this.summaryRepo = summaryRepo;
        this.redisson = redisson;
        this.summaryEnabled = props.getMemory().isSummaryEnabled();
        this.summaryStartTurns = Math.max(2, props.getMemory().getSummaryStartTurns());
        this.historyKeepTurns = Math.max(1, props.getMemory().getHistoryKeepTurns());
        this.summaryMaxChars = Math.max(100, props.getMemory().getSummaryMaxChars());
    }

    /** 异步触发摘要压缩（ASSISTANT 消息追加后调用） */
    @Async("memorySummaryExecutor")
    public void compressIfNeeded(String conversationId, String userId) {
        if (!summaryEnabled) return;
        try {
            // 闸门：用户消息数 < summaryStartTurns 不压缩
            long userMsgCount = msgRepo.countUserMessages(conversationId, userId);
            if (userMsgCount < summaryStartTurns) return;

            // ★ 分布式锁防并发重复摘要
            RLock lock = redisson.getLock("memory:summary:lock:" + userId + ":" + conversationId);
            if (!lock.tryLock()) return;
            try {
                doCompress(conversationId, userId);
            } finally {
                if (lock.isHeldByCurrentThread()) lock.unlock();
            }
        } catch (Exception e) {
            // 摘要失败不影响主流程，仅记录
            log.warn("[MemorySummary] 压缩失败 conv={}: {}", conversationId, e.getMessage());
        }
    }

    /** 加载已有摘要（供 ConversationMemoryService 拼到 history 最前） */
    public String loadSummary(String conversationId, String userId) {
        ConversationSummaryEntity entity = summaryRepo.selectById(conversationId);
        return entity == null ? null : entity.getSummary();
    }


    private void doCompress(String conversationId, String userId) {
        // 确定增量压缩窗口上界 cutoffId = 最近 historyKeepTurns 个 user 消息中最早的 id
        Long cutoffId = findRecentUserMsgEarliestId(conversationId, userId, historyKeepTurns);
        if (cutoffId == null) return;

        String existing = loadSummary(conversationId, userId);
        ConversationSummaryEntity prev = summaryRepo.selectById(conversationId);
        Long afterId = prev == null ? null : prev.getLastMessageId();
        if (afterId != null && afterId >= cutoffId) return;   // 无新增待压缩内容

        // 拉取待压缩消息（afterId 为 null 时下界用 0：从最早开始取）
        long lowerBound = afterId == null ? 0L : afterId;
        List<ConversationMessageEntity> toCompress = msgRepo.findBetweenIds(
                conversationId, userId, lowerBound, cutoffId);
        if (toCompress.isEmpty()) return;
        String messagesToCompress = renderMessages(toCompress);

        // 渲染摘要提示词（★ 核心约束：只记话题不记答案）
        // TODO: PromptTemplateLoader 落地后改回 templateLoader.render("conversation-summary.st", Map.of(...))
        String system = SUMMARY_TEMPLATE.replace("{summary_max_chars}",
                String.valueOf(summaryMaxChars));

        // 增量合并：旧摘要注入 + "以本轮为准"约束
        String user;
        if (existing != null && !existing.isBlank()) {
            user = "历史摘要（仅用于合并去重，不得作为事实新增来源；若与本轮对话冲突，以本轮对话为准）：\n"
                    + existing + "\n\n" + messagesToCompress
                    + "\n合并以上对话与历史摘要，去重后输出更新摘要。要求：严格≤" + summaryMaxChars + "字符；仅一行。";
        } else {
            user = messagesToCompress;
        }

        String summary = llmService.chat(system + "\n\n" + user, 0.3, 0.9, false);
        if (summary == null || summary.isBlank()) return;

        // 持久化（覆盖式：每会话一份摘要）
        ConversationSummaryEntity entity = summaryRepo.selectById(conversationId);
        if (entity == null) {
            entity = new ConversationSummaryEntity();
        }
        entity.setConversationId(conversationId);
        entity.setUserId(userId);
        entity.setSummary(summary.trim());
        entity.setLastMessageId(cutoffId);
        if (summaryRepo.selectById(conversationId) == null) {
            summaryRepo.insert(entity);
        } else {
            summaryRepo.updateById(entity);
        }
        log.debug("[MemorySummary] 已压缩 conv={} summaryLen={}", conversationId, summary.length());
    }

    /**
     * 取最近 N 个 user 消息中最早的 id（压缩上界 cutoffId）。
     * 移植自 sparkxV2 ConversationMessageRepository.findRecentUserMsgIds：取最近 keepTurns 个 user 消息的 id（DESC），
     * 再取最后一个即"最早"作为 cutoffId。这里基于现有 findRecent 取最近 keepTurns*2 条消息再筛 user 角色。
     */
    private Long findRecentUserMsgEarliestId(String conversationId, String userId, int keepTurns) {
        // 与 findRecentUserMsgIds(keepTurns) 等价：最近 keepTurns 个 user 消息；取窗口 keepTurns*2 条足够覆盖
        List<ConversationMessageEntity> rows = msgRepo.findRecent(
                conversationId, userId, keepTurns * 2);
        // findRecent 为 id DESC，倒序遍历找最后一个 user 即"最早"
        Long earliest = null;
        int userCount = 0;
        for (ConversationMessageEntity row : rows) {   // DESC 顺序：先最新
            if ("user".equalsIgnoreCase(row.getRole())) {
                userCount++;
                earliest = row.getId();   // 不断覆盖，直到凑够 keepTurns 个，保留的就是第 keepTurns 个（最早）
                if (userCount >= keepTurns) break;
            }
        }
        return earliest;
    }

    private String renderMessages(List<ConversationMessageEntity> msgs) {
        StringBuilder sb = new StringBuilder();
        for (ConversationMessageEntity m : msgs) {
            sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString().trim();
    }
}
