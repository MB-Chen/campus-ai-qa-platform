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

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * LangChain4j 模型 / 组件 Bean 装配（移植自 sparkxV2）。
 *
 * 所有模型经 OpenAI 兼容接口，可指向 OpenAI 官方 / Ollama / 通义 / DeepSeek 等。
 * 用了 1.17.0 已核实的 builder API：baseUrl(String)、temperature(Double)、maxTokens(Integer)。
 *
 * 适配说明（与 sparkxV2 差异）：
 *  - 这里的 ChatModel/StreamingChatModel/EmbeddingModel 是基于 yml 兜底配置构建的"默认模型" Bean，
 *    作为 ChatModelBridge 降级时的后备（无 ChatClient 注册时使用）。
 *  - 实际生产优先使用 ai_model 表中配置的模型，经 RoutingLLMService / ChatClient 调度。
 *  - AdaptiveDocumentSplitter / ParentChildSplitter / FallbackProvider 等 Bean 装配见
 *    {@link KnowledgeBeansConfig}（独立配置类，避免本类依赖业务包）。
 */
@Configuration
public class LangChain4jConfig {


    /**
     * 同步对话模型（意图识别、改写、非流式兜底）。
     * 基于 yml app.rag.chat 兜底配置；生产优先走 ai_model 表候选。
     */
    @Bean
    @Primary
    public ChatModel chatModel(RagProperties props) {
        RagProperties.Chat c = props.getChat();
        return OpenAiChatModel.builder()
                .baseUrl(c.getBaseUrl())
                .apiKey(c.getApiKey())
                .modelName(c.getModel())
                .temperature(c.getTemperature())
                .maxTokens(c.getMaxTokens())
                .timeout(c.getTimeout())
                .build();
    }

    /**
     * 流式对话模型（最终回答生成，逐 token 推送）。
     * 基于 yml app.rag.streaming-chat 兜底配置。
     */
    @Bean
    public StreamingChatModel streamingChatModel(RagProperties props) {
        RagProperties.Chat c = props.getStreamingChat();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(c.getBaseUrl())
                .apiKey(c.getApiKey())
                .modelName(c.getModel())
                .temperature(c.getTemperature())
                .maxTokens(c.getMaxTokens())
                .timeout(c.getTimeout())
                .build();
    }

    // 视觉模型（图片 OCR / Caption）已不再在此处装配为兜底 Bean：
    // VlmModelBridge 改为每次从 ai_model 表（type=4）读取第一个启用候选动态构建，
    // 后台改 url/apiKey/model 立即生效，无需发版。


    /**
     * 嵌入模型（文档向量化、查询向量化）。
     * 基于 yml app.rag.embedding 兜底配置；知识库创建时绑定的 embedding 模型优先（走 ai_model 表）。
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel(RagProperties props) {
        RagProperties.Embedding e = props.getEmbedding();
        return OpenAiEmbeddingModel.builder()
                .baseUrl(e.getBaseUrl())
                .apiKey(e.getApiKey())
                .modelName(e.getModel())
                .timeout(e.getTimeout())
                .build();
    }


    /**
     * 重排打分模型。
     * 为保证可编译且不依赖外部 Cohere 服务在线，采用基于嵌入余弦相似度的本地实现
     * （见 {@link CohereScoringModelConfig.EmbeddingBasedScoringModel}）。
     * 若需真实 Cohere Rerank，可在此替换为 CohereScoringModel。
     */
    @Bean
    public ScoringModel scoringModel(RagProperties props, EmbeddingModel embeddingModel) {
        return new CohereScoringModelConfig(props.getRerank(), embeddingModel).scoringModel();
    }
}
