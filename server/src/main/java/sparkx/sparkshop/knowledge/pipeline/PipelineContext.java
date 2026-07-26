// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.pipeline;

import dev.langchain4j.rag.query.Metadata;
import sparkx.sparkshop.knowledge.intent.GuidanceDecision;
import sparkx.sparkshop.knowledge.intent.NodeScore;
import sparkx.sparkshop.knowledge.intent.QueryIntent;
import sparkx.sparkshop.knowledge.prompt.PromptScene;
import sparkx.sparkshop.knowledge.query.RewriteResult;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * RAG 管线上下文
 * 贯穿整个管线，承载请求参数与中间状态。
 */
public class PipelineContext {

    private String originalQuery;
    private String sessionId;
    private String userId;
    private List<String> knowledgeBaseIds;
    private List<String> images;                  // 图片 URL 列表
    private boolean webSearchEnabled;
    private String language = "中文";

    private QueryIntent intent;                   // 意图分类结果（扁平 fallback）
    private String rewriteQuery;                  // 改写后的问题
    private List<Query> expandedQueries;          // 扩展后的查询变体
    private List<Content> searchResult;           // 原始召回
    private List<Content> rerankResult;           // 重排后
    private List<Content> mergeResult;            // 合并后（最终上下文）
    private String renderedContexts;              // 渲染后的上下文文本
    private UserMessage augmentedMessage;         // 注入上下文后的用户消息
    private String answer;                        // 最终回答

    private RewriteResult rewriteResult;          // 改写+拆分结果（主问题+子问题）
    private List<NodeScore> subIntents;           // 意图分类结果（TreeIntentStage 产出）
    private GuidanceDecision guidance;            // 歧义引导决策
    private List<ChatMessage> history;            // 会话历史（含摘要，GenerateStage 用）
    private String kbContext;                     // 渲染后的 KB 上下文
    private String mcpContext;                    // 渲染后的 MCP 工具结果
    private PromptScene promptScene;              // ★ 场景（决定提示词模板）

    private Consumer<String> tokenConsumer;       // 逐 token 回调

    private AgentOverrides agentOverrides;

    /** ★ 各阶段耗时（name → ms），由 RagPipeline.run 写入，供前端时间线展示。有序，保留执行顺序。 */
    private final Map<String, Long> stageTimings = new LinkedHashMap<>();
    /** ★ 管线总耗时(ms)，由 RagPipeline.run 结束时写入。 */
    private long totalCost;
    /** ★ LLM 调用次数，由 RagPipeline.run 结束时写入。 */
    private int llmCallCount;

    private final Map<String, Object> attributes = new HashMap<>();

    /** 是否需要检索（由意图决定） */
    public boolean needsRetrieval() {
        if (intent == null) return true;          // 默认走检索
        return intent.needsRetrieval();
    }

    public boolean isCacheHit() {
        return Boolean.TRUE.equals(attributes.get("cacheHit"));
    }

    public <T> T getAttr(String key, Class<T> type) {
        return type.cast(attributes.get(key));
    }

    public void setAttr(String key, Object value) {
        attributes.put(key, value);
    }

    public boolean hasAttr(String key) {
        return attributes.containsKey(key);
    }

    public Query toQuery() {
        String q = rewriteQuery != null ? rewriteQuery : originalQuery;
        // ⚠️ langchain4j 1.17.0 起 Query.from 的 metadata 不允许传 null（会抛 IllegalArgumentException），
        // 必须传非 null 的空 Metadata。旧注释"metadata 可为 null"已失效。
        return Query.from(q, Metadata.from(UserMessage.from(""), null, null));
    }

    public String getOriginalQuery() { return originalQuery; }
    public void setOriginalQuery(String originalQuery) { this.originalQuery = originalQuery; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public List<String> getKnowledgeBaseIds() { return knowledgeBaseIds; }
    public void setKnowledgeBaseIds(List<String> knowledgeBaseIds) { this.knowledgeBaseIds = knowledgeBaseIds; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
    public boolean isWebSearchEnabled() { return webSearchEnabled; }
    public void setWebSearchEnabled(boolean webSearchEnabled) { this.webSearchEnabled = webSearchEnabled; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public QueryIntent getIntent() { return intent; }
    public void setIntent(QueryIntent intent) { this.intent = intent; }
    public String getRewriteQuery() { return rewriteQuery; }
    public void setRewriteQuery(String rewriteQuery) { this.rewriteQuery = rewriteQuery; }
    public List<Query> getExpandedQueries() { return expandedQueries; }
    public void setExpandedQueries(List<Query> expandedQueries) { this.expandedQueries = expandedQueries; }
    public List<Content> getSearchResult() { return searchResult; }
    public void setSearchResult(List<Content> searchResult) { this.searchResult = searchResult; }
    public List<Content> getRerankResult() { return rerankResult; }
    public void setRerankResult(List<Content> rerankResult) { this.rerankResult = rerankResult; }
    public List<Content> getMergeResult() { return mergeResult; }
    public void setMergeResult(List<Content> mergeResult) { this.mergeResult = mergeResult; }
    public String getRenderedContexts() { return renderedContexts; }
    public void setRenderedContexts(String renderedContexts) { this.renderedContexts = renderedContexts; }
    public UserMessage getAugmentedMessage() { return augmentedMessage; }
    public void setAugmentedMessage(UserMessage augmentedMessage) { this.augmentedMessage = augmentedMessage; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public Consumer<String> getTokenConsumer() { return tokenConsumer; }
    public void setTokenConsumer(Consumer<String> tokenConsumer) { this.tokenConsumer = tokenConsumer; }

    public AgentOverrides getAgentOverrides() { return agentOverrides; }
    public void setAgentOverrides(AgentOverrides agentOverrides) { this.agentOverrides = agentOverrides; }

    public RewriteResult getRewriteResult() { return rewriteResult; }
    public void setRewriteResult(RewriteResult rewriteResult) { this.rewriteResult = rewriteResult; }
    public List<NodeScore> getSubIntents() { return subIntents; }
    public void setSubIntents(List<NodeScore> subIntents) { this.subIntents = subIntents; }
    public GuidanceDecision getGuidance() { return guidance; }
    public void setGuidance(GuidanceDecision guidance) { this.guidance = guidance; }
    public List<ChatMessage> getHistory() { return history; }
    public void setHistory(List<ChatMessage> history) { this.history = history; }
    public String getKbContext() { return kbContext; }
    public void setKbContext(String kbContext) { this.kbContext = kbContext; }
    public String getMcpContext() { return mcpContext; }
    public void setMcpContext(String mcpContext) { this.mcpContext = mcpContext; }
    public PromptScene getPromptScene() { return promptScene; }
    public void setPromptScene(PromptScene promptScene) { this.promptScene = promptScene; }

    /** 主检索问题：优先用改写后的问题 */
    public String getMainQuery() {
        return rewriteResult != null && rewriteResult.rewrittenQuestion() != null
                ? rewriteResult.rewrittenQuestion() : getMainQueryOriginal();
    }

    /** 兜底主问题（无改写结果时） */
    private String getMainQueryOriginal() {
        return rewriteQuery != null ? rewriteQuery : originalQuery;
    }

    /** 是否存在意图分类候选 */
    public boolean hasSubIntents() {
        return subIntents != null && !subIntents.isEmpty();
    }

    /** 是否纯系统意图（闲聊，不走检索） */
    public boolean isSystemOnly() {
        return subIntents != null && !subIntents.isEmpty()
                && subIntents.stream().allMatch(s -> s.node().isSystem());
    }

    /** 是否触发歧义引导短路 */
    public boolean isGuidancePrompt() {
        return guidance != null && guidance.isPrompt();
    }

    /** ★ 各阶段耗时（name → ms，有序），供 RagPipeline.run 写入、SSE complete 事件回传前端 */
    public Map<String, Long> getStageTimings() { return stageTimings; }

    public long getTotalCost() { return totalCost; }
    public void setTotalCost(long totalCost) { this.totalCost = totalCost; }

    public int getLlmCallCount() { return llmCallCount; }
    public void setLlmCallCount(int llmCallCount) { this.llmCallCount = llmCallCount; }
}
