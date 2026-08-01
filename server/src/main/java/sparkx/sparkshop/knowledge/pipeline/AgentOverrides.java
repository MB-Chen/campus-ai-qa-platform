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

import java.util.List;

/**
 * 智能体运行时参数覆盖载体。
 * <p>
 * 由 {@code AgentChatService} 从智能体配置读出，塞入 {@link PipelineContext#setAgentOverrides}。
 * 各 RAG 阶段读取时遵循「覆盖值非空则用，否则回退 {@code RagProperties} 默认值」原则。
 * <p>
 * ★ 全部为包装类型：{@code null} 表示该参数不覆盖（保持全局默认）。这样 IM 客服链路
 * （不设 agentOverrides）行为完全不变。
 */
public class AgentOverrides {

    /** 知识库模式：all 全部 / selected 指定 / none 不使用（控制检索是否执行） */
    private String kbMode;

    /** 限定文档 id 列表（kb_mode=selected 时可选，null/空=整库） */
    private List<String> documentIds;

    /**
     * 主对话模型 ai_model.id（type=1）。
     * <p>★ 非空时 GenerateStage 强制路由到该模型（仍享熔断降级骨架），让「智能体配置的对话模型」真正生效；
     * 为空时走全局默认候选链（保持 IM 客服链路行为不变）。
     */
    private Integer chatModelId;
    /** 冗余：主对话模型显示名（诊断/展示用） */
    private String chatModelName;

    /** 温度（覆盖 GenerateStage 硬编码 temp） */
    private Double temperature;
    /** top_p（覆盖 GenerateStage 硬编码 topP） */
    private Double topP;
    /** 最大生成 token（覆盖 GenerateStage 的 maxTokens=-1） */
    private Integer maxTokens;
    /** 历史记忆轮数（覆盖 ConversationMemoryStore 的 historyKeepTurns） */
    private Integer historyTurns;
    /** 向量召回 topK（覆盖 HybridContentRetriever 的 embeddingTopK） */
    private Integer embeddingTopK;
    /** 向量相似度阈值（覆盖 HybridContentRetriever 的 vectorThreshold） */
    private Double vectorThreshold;
    /** 关键词阈值（覆盖 HybridContentRetriever 的 keywordThreshold） */
    private Double keywordThreshold;
    /**
     * 检索方式 embedding纯向量/mix混合/text纯关键词（覆盖 HybridContentRetriever 的 mode）。
     * null 时回退 mix（向后兼容，与改造前行为一致）。
     */
    private String retrievalMode;
    /** 重排模型 ai_model.id（type=3）；非空时 RerankStage 用它调真实 rerank API，否则用全局默认 ScoringModel */
    private Integer rerankModelId;
    /** ★ 重排具体模型名（从 ai_model.models 逗号拆分中指定）；非空时 AgentRerankClient 用它而非首项 */
    private String rerankModelName;
    /** 是否启用重排（false 时 RerankStage 整阶段跳过） */
    private Boolean rerankEnabled;
    /** 重排 topK（覆盖 RerankStage） */
    private Integer rerankTopK;
    /** 重排阈值（覆盖 RerankStage） */
    private Double rerankThreshold;
    /**
     * 意图/改写专用模型 ai_model.id（type=1，对话模型）。
     * 非空时 MultiQuestionRewriteService / IntentClassifier 用它做意图分类与查询改写，
     * 否则走全局默认对话模型（主大模型）。借鉴 WeKnora QueryUnderstandModelID：辅助任务可用小快模型降本提速。
     */
    private Integer rewriteModelId;
    /** 冗余：意图/改写专用模型显示名（前端展示用） */
    private String rewriteModelName;
    /** 自定义系统提示词（非空时覆盖 GenerateStage 的场景模板） */
    private String systemPrompt;
    /** 兜底策略 model/fixed（覆盖全局 app.rag.fallback.strategy） */
    private String fallbackStrategy;
    /** 兜底固定话术（strategy=fixed 时用） */
    private String fallbackResponse;
    /**
     * 是否启用样例查询短路（true 时 SampleQueryStage 用原始 query 向量匹配样例库，
     * 命中阈值直接返回样例答案，跳过整条 RAG 链路）。
     */
    private Boolean sampleQueryEnabled;
    /** 样例匹配相似度阈值覆盖（null 时回退全局 sample_query_config.similarity_threshold） */
    private Double sampleQueryThreshold;
    /**
     * 是否启用跨会话持久记忆（null=不覆盖，回退全局 app.rag.memory.persistent-enabled）。
     * 启用后：GenerateStage 注入持久记忆 SystemMessage；每轮后异步抽取沉淀长期事实。
     */
    private Boolean persistentMemoryEnabled;

    public String getKbMode() { return kbMode; }
    public void setKbMode(String kbMode) { this.kbMode = kbMode; }
    public List<String> getDocumentIds() { return documentIds; }
    public void setDocumentIds(List<String> documentIds) { this.documentIds = documentIds; }
    public Integer getChatModelId() { return chatModelId; }
    public void setChatModelId(Integer chatModelId) { this.chatModelId = chatModelId; }
    public String getChatModelName() { return chatModelName; }
    public void setChatModelName(String chatModelName) { this.chatModelName = chatModelName; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Integer getHistoryTurns() { return historyTurns; }
    public void setHistoryTurns(Integer historyTurns) { this.historyTurns = historyTurns; }
    public Integer getEmbeddingTopK() { return embeddingTopK; }
    public void setEmbeddingTopK(Integer embeddingTopK) { this.embeddingTopK = embeddingTopK; }
    public Double getVectorThreshold() { return vectorThreshold; }
    public void setVectorThreshold(Double vectorThreshold) { this.vectorThreshold = vectorThreshold; }
    public Double getKeywordThreshold() { return keywordThreshold; }
    public void setKeywordThreshold(Double keywordThreshold) { this.keywordThreshold = keywordThreshold; }
    public String getRetrievalMode() { return retrievalMode; }
    public void setRetrievalMode(String retrievalMode) { this.retrievalMode = retrievalMode; }
    public Integer getRerankModelId() { return rerankModelId; }
    public void setRerankModelId(Integer rerankModelId) { this.rerankModelId = rerankModelId; }
    public String getRerankModelName() { return rerankModelName; }
    public void setRerankModelName(String rerankModelName) { this.rerankModelName = rerankModelName; }
    public Boolean getRerankEnabled() { return rerankEnabled; }
    public void setRerankEnabled(Boolean rerankEnabled) { this.rerankEnabled = rerankEnabled; }
    public Integer getRerankTopK() { return rerankTopK; }
    public void setRerankTopK(Integer rerankTopK) { this.rerankTopK = rerankTopK; }
    public Double getRerankThreshold() { return rerankThreshold; }
    public void setRerankThreshold(Double rerankThreshold) { this.rerankThreshold = rerankThreshold; }
    public Integer getRewriteModelId() { return rewriteModelId; }
    public void setRewriteModelId(Integer rewriteModelId) { this.rewriteModelId = rewriteModelId; }
    public String getRewriteModelName() { return rewriteModelName; }
    public void setRewriteModelName(String rewriteModelName) { this.rewriteModelName = rewriteModelName; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getFallbackStrategy() { return fallbackStrategy; }
    public void setFallbackStrategy(String fallbackStrategy) { this.fallbackStrategy = fallbackStrategy; }
    public String getFallbackResponse() { return fallbackResponse; }
    public void setFallbackResponse(String fallbackResponse) { this.fallbackResponse = fallbackResponse; }
    public Boolean getSampleQueryEnabled() { return sampleQueryEnabled; }
    public void setSampleQueryEnabled(Boolean sampleQueryEnabled) { this.sampleQueryEnabled = sampleQueryEnabled; }
    public Double getSampleQueryThreshold() { return sampleQueryThreshold; }
    public void setSampleQueryThreshold(Double sampleQueryThreshold) { this.sampleQueryThreshold = sampleQueryThreshold; }
    public Boolean getPersistentMemoryEnabled() { return persistentMemoryEnabled; }
    public void setPersistentMemoryEnabled(Boolean persistentMemoryEnabled) { this.persistentMemoryEnabled = persistentMemoryEnabled; }
}
