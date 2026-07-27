// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * RAG 业务配置根。
 * 对应 application.yml 的 app.rag.* 节点（移植自 sparkxV2）。
 *
 * 注意：chat/embedding/rerank/vlm 的 baseUrl/apiKey/model 在此仅为默认兜底；
 * 实际运行优先使用 ai_model 表（页面可编辑）中配置的模型。
 */
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private Embedding embedding = new Embedding();
    private Chat chat = new Chat();
    private Chat streamingChat = new Chat();
    private Vlm vlm = new Vlm();
    private Rerank rerank = new Rerank();
    private Retrieval retrieval = new Retrieval();
    private Fusion fusion = new Fusion();
    private Fallback fallback = new Fallback();
    private Chunking chunking = new Chunking();
    private Cache cache = new Cache();
    private Memory memory = new Memory();
    /**
     * MinerU 入库行为开关（不含连接配置——endpoint/apiKey 仍在 ext_service_config 表）。
     * 这里只放图片处理策略等管线级行为。
     */
    private Mineru mineru = new Mineru();
    /** 提示词语言（.st 模板按 prompt/{language}/ 加载，默认 zh） */
    private String language = "zh";

    /** 嵌入模型配置 */
    public static class Embedding {
        private String provider = "openai";
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "demo-key";
        private String model = "text-embedding-3-small";
        private int dimension = 1536;
        private Duration timeout = Duration.ofSeconds(60);

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }

    /** 对话模型配置 */
    public static class Chat {
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "demo-key";
        private String model = "gpt-4o-mini";
        private double temperature = 0.3;
        private int maxTokens = 2048;
        private Duration timeout = Duration.ofSeconds(120);

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }

    /** 视觉模型配置（图片 OCR/Caption） */
    public static class Vlm {
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "demo-key";
        private String model = "gpt-4o";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    /** 重排模型配置 */
    public static class Rerank {
        private String baseUrl = "https://api.cohere.ai";
        private String apiKey = "demo-key";
        private String model = "rerank-multilingual-v3.0";
        private double threshold = 0.3;
        private int topK = 30;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
    }

    /** 检索配置 */
    public static class Retrieval {
        private int embeddingTopK = 30;
        private double vectorThreshold = 0.2;
        /**
         * 关键词路（ts_rank_cd）弱命中过滤阈值，仅 mix 模式生效。
         * ts_rank_cd 无分数下限，高频词反复出现的长 chunk 会刷出虚高分污染融合排序。
         * 0.3 实测能砍掉大部分「公司」刷分噪声；设 0 则不过滤。
         */
        private double keywordThreshold = 0.3;
        private boolean enableQueryExpansion = true;
        private boolean enableRewrite = true;
        /** 召回低于该值触发查询扩写 */
        private int minHitsForExpansion = 5;
        /**
         * 混合检索融合时向量路的权重（关键词路权重 = 1 - 此值）。
         * 取值 [0,1]，建议 0.6~0.8。
         * <ul>
         *   <li>0.7（默认）：偏语义，适合中文 RAG 问答场景。语义相关比字面命中更可靠，
         *       且能压住「高频词（公司/员工）反复出现的长 chunk 在关键词路刷出虚高 ts_rank_cd」
         *       带来的噪声。</li>
         *   <li>0.5：双路平权，适合关键词命中性强（产品名/编号/专有名词主导）的场景。</li>
         *   <li>0.8+：几乎只信向量，关键词路只作微调，适合文档高频词噪声大、向量模型质量高的场景。</li>
         * </ul>
         * 调参依据：跑一批覆盖「语义类/关键词类/混合类」的测试 query，
         * 看哪个权重的 recall@3 最高。
         */
        private double hybridVectorWeight = 0.7;

        public int getEmbeddingTopK() { return embeddingTopK; }
        public void setEmbeddingTopK(int embeddingTopK) { this.embeddingTopK = embeddingTopK; }
        public double getVectorThreshold() { return vectorThreshold; }
        public void setVectorThreshold(double vectorThreshold) { this.vectorThreshold = vectorThreshold; }
        public double getKeywordThreshold() { return keywordThreshold; }
        public void setKeywordThreshold(double keywordThreshold) { this.keywordThreshold = keywordThreshold; }
        public boolean isEnableQueryExpansion() { return enableQueryExpansion; }
        public void setEnableQueryExpansion(boolean enableQueryExpansion) { this.enableQueryExpansion = enableQueryExpansion; }
        public boolean isEnableRewrite() { return enableRewrite; }
        public void setEnableRewrite(boolean enableRewrite) { this.enableRewrite = enableRewrite; }
        public int getMinHitsForExpansion() { return minHitsForExpansion; }
        public void setMinHitsForExpansion(int minHitsForExpansion) { this.minHitsForExpansion = minHitsForExpansion; }
        public double getHybridVectorWeight() { return hybridVectorWeight; }
        public void setHybridVectorWeight(double hybridVectorWeight) { this.hybridVectorWeight = hybridVectorWeight; }
    }

    /**
     * 多通道融合配置（RRF / 候选池截断 / 通道权重覆盖）。
     *
     * <p>由 {@code FusionPostProcessor}（order=3，去重后/重排前）消费：
     * <ul>
     *   <li>各通道返回的 {@code List<Content>} 在通道内部已按各自 score 降序，这里以「在通道内的名次」参与 RRF</li>
     *   <li>图谱等通道通过 {@link sparkx.sparkshop.knowledge.retrieval.ConditionalRetrievalChannel#getWeight()}
     *       声明默认权重，此处 {@link ChannelWeights} 可覆盖（配置优先于代码默认）</li>
     * </ul>
     */
    public static class Fusion {
        /** 是否启用 RRF 融合（false 时跳过融合，按通道 priority 顺序保留去重首批，等同旧行为） */
        private boolean enabled = true;
        /**
         * RRF 平滑常数 k。经典取 60，但本项目候选池小（每通道 topK=10~30），
         * k=60 会让名次差异被严重压缩（rank1 vs rank5 的 RRF 贡献几乎相等）。
         * 20 实测能把头部证据和长尾证据拉开差距。
         */
        private int rrfK = 20;
        /** RRF 后送入 rerank 的候选池上限（控制 rerank 调用成本） */
        private int rerankCandidateLimit = 40;
        /** 通道权重覆盖（按 ChannelType 名字配置，覆盖通道自带的 getWeight 默认值） */
        private ChannelWeights channelWeights = new ChannelWeights();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getRrfK() { return rrfK; }
        public void setRrfK(int rrfK) { this.rrfK = rrfK; }
        public int getRerankCandidateLimit() { return rerankCandidateLimit; }
        public void setRerankCandidateLimit(int rerankCandidateLimit) { this.rerankCandidateLimit = rerankCandidateLimit; }
        public ChannelWeights getChannelWeights() { return channelWeights; }
        public void setChannelWeights(ChannelWeights channelWeights) { this.channelWeights = channelWeights; }

        /**
         * 按 {@link sparkx.sparkshop.knowledge.retrieval.ConditionalRetrievalChannel.ChannelType}
         * 名字（大写下划线，如 {@code KNOWLEDGE_GRAPH}）配置权重。
         * 未配置的通道用通道自带的 getWeight() 默认值。
         */
        public static class ChannelWeights {
            private double intentDirected = 1.0;
            private double hybridGlobal = 1.0;
            /** 图谱默认降权（与 KnowledgeGraphChannel.getWeight() 一致，配置可覆盖） */
            private double knowledgeGraph = 0.5;
            private double keyword = 1.0;
            private double parentChild = 1.0;

            public double getIntentDirected() { return intentDirected; }
            public void setIntentDirected(double intentDirected) { this.intentDirected = intentDirected; }
            public double getHybridGlobal() { return hybridGlobal; }
            public void setHybridGlobal(double hybridGlobal) { this.hybridGlobal = hybridGlobal; }
            public double getKnowledgeGraph() { return knowledgeGraph; }
            public void setKnowledgeGraph(double knowledgeGraph) { this.knowledgeGraph = knowledgeGraph; }
            public double getKeyword() { return keyword; }
            public void setKeyword(double keyword) { this.keyword = keyword; }
            public double getParentChild() { return parentChild; }
            public void setParentChild(double parentChild) { this.parentChild = parentChild; }
        }
    }

    /** 兜底策略配置 */
    public static class Fallback {
        /** model | fixed */
        private String strategy = "model";

        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
    }

    /** 分块配置 */
    public static class Chunking {
        /** auto | heading | heuristic | recursive */
        private String strategy = "auto";
        private int chunkSize = 512;
        /** 相邻 chunk 重叠字符数：中文 RAG 约 100 字（200 字符）能保住一个条款的语义连续性 */
        private int chunkOverlap = 200;
        private boolean parentEnabled = true;
        private int parentSize = 4096;
        private int childSize = 384;

        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
        public boolean isParentEnabled() { return parentEnabled; }
        public void setParentEnabled(boolean parentEnabled) { this.parentEnabled = parentEnabled; }
        public int getParentSize() { return parentSize; }
        public void setParentSize(int parentSize) { this.parentSize = parentSize; }
        public int getChildSize() { return childSize; }
        public void setChildSize(int childSize) { this.childSize = childSize; }
    }

    /** 缓存配置 */
    public static class Cache {
        private boolean semanticEnabled = true;
        private long semanticTtl = 3600;
        private double semanticSimilarity = 0.92;

        public boolean isSemanticEnabled() { return semanticEnabled; }
        public void setSemanticEnabled(boolean semanticEnabled) { this.semanticEnabled = semanticEnabled; }
        public long getSemanticTtl() { return semanticTtl; }
        public void setSemanticTtl(long semanticTtl) { this.semanticTtl = semanticTtl; }
        public double getSemanticSimilarity() { return semanticSimilarity; }
        public void setSemanticSimilarity(double semanticSimilarity) { this.semanticSimilarity = semanticSimilarity; }
    }

    /** 会话记忆配置。与 app.ai.memory 镜像，便于业务层注入 RagProperties 一次取齐。 */
    public static class Memory {
        /** 滑动窗口保留轮数（1 轮 = 1 user + 1 assistant） */
        private int historyKeepTurns = 4;
        /** 是否开启话题导向摘要 */
        private boolean summaryEnabled = true;
        /** 用户消息总数达到该值才触发摘要压缩 */
        private int summaryStartTurns = 5;
        /** 摘要最大字符数 */
        private int summaryMaxChars = 200;
        /** 摘要标题最大长度 */
        private int titleMaxLength = 30;

        public int getHistoryKeepTurns() { return historyKeepTurns; }
        public void setHistoryKeepTurns(int historyKeepTurns) { this.historyKeepTurns = historyKeepTurns; }
        public boolean isSummaryEnabled() { return summaryEnabled; }
        public void setSummaryEnabled(boolean summaryEnabled) { this.summaryEnabled = summaryEnabled; }
        public int getSummaryStartTurns() { return summaryStartTurns; }
        public void setSummaryStartTurns(int summaryStartTurns) { this.summaryStartTurns = summaryStartTurns; }
        public int getSummaryMaxChars() { return summaryMaxChars; }
        public void setSummaryMaxChars(int summaryMaxChars) { this.summaryMaxChars = summaryMaxChars; }
        public int getTitleMaxLength() { return titleMaxLength; }
        public void setTitleMaxLength(int titleMaxLength) { this.titleMaxLength = titleMaxLength; }
    }

    /**
     * MinerU 入库行为开关。
     *
     * <p>⚠️ 这里只放「管线行为」，不放 MinerU 服务连接配置——endpoint/apiKey/model 等仍在
     * {@code ext_service_config} 表（页面「知识库 → 外部服务配置」可编辑）。
     */
    public static class Mineru {
        /**
         * MinerU 解析出的图片处理策略：
         * <ul>
         *   <li>{@code describe}（默认）：VLM 把每张图转成「描述 + OCR」二合一文本，原地替换 markdown 图片引用。
         *       图片字节不上 MinIO，chunk 里无 URL 噪声。</li>
         *   <li>{@code drop}：直接删 markdown 里的图片引用，不调 VLM（VLM 不可用时的退路）。</li>
         *   <li>{@code keep}：保留旧行为（上 MinIO + 改写为 7 天预签名 URL），向后兼容。</li>
         * </ul>
         */
        private String imageMode = "describe";

        public String getImageMode() { return imageMode; }
        public void setImageMode(String imageMode) { this.imageMode = imageMode; }
    }

    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding embedding) { this.embedding = embedding; }
    public Chat getChat() { return chat; }
    public void setChat(Chat chat) { this.chat = chat; }
    public Chat getStreamingChat() { return streamingChat; }
    public void setStreamingChat(Chat streamingChat) { this.streamingChat = streamingChat; }
    public Vlm getVlm() { return vlm; }
    public void setVlm(Vlm vlm) { this.vlm = vlm; }
    public Rerank getRerank() { return rerank; }
    public void setRerank(Rerank rerank) { this.rerank = rerank; }
    public Retrieval getRetrieval() { return retrieval; }
    public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }
    public Fusion getFusion() { return fusion; }
    public void setFusion(Fusion fusion) { this.fusion = fusion; }
    public Fallback getFallback() { return fallback; }
    public void setFallback(Fallback fallback) { this.fallback = fallback; }
    public Chunking getChunking() { return chunking; }
    public void setChunking(Chunking chunking) { this.chunking = chunking; }
    public Cache getCache() { return cache; }
    public void setCache(Cache cache) { this.cache = cache; }
    public Memory getMemory() { return memory; }
    public void setMemory(Memory memory) { this.memory = memory; }
    public Mineru getMineru() { return mineru; }
    public void setMineru(Mineru mineru) { this.mineru = mineru; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
