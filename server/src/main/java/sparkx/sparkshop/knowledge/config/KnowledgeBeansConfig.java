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

import dev.langchain4j.data.document.DocumentSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import sparkx.sparkshop.knowledge.fallback.FixedFallbackProvider;
import sparkx.sparkshop.knowledge.fallback.FallbackProvider;
import sparkx.sparkshop.knowledge.fallback.ModelFallbackProvider;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.ingest.AdaptiveDocumentSplitter;
import sparkx.sparkshop.knowledge.ingest.ParentChildSplitter;
import sparkx.sparkshop.knowledge.prompt.PromptTemplateManager;

/**
 * 知识库分块器 + 兜底策略 Bean 装配（移植自 sparkxV2 LangChain4jConfig 的后半部分）。
 *
 * 这些 Bean 依赖 ingest / fallback 包，独立成配置类避免 LangChain4jConfig（模型装配）
 * 依赖尚未创建的业务类。当 PromptTemplateManager 完整实现落地（WF-6）后，
 * ModelFallbackProvider 可正常工作；当前若 app.rag.fallback.strategy=model 且
 * PromptTemplateManager 仅为占位接口，启动会失败——可先把 strategy 设为 fixed。
 */
@Configuration
public class KnowledgeBeansConfig {


    /**
     * 自适应文档分块器（实现 LangChain4j DocumentSplitter）。
     * 可单独注入用于非父子场景的分块。
     */
    @Bean
    public DocumentSplitter adaptiveSplitter(RagProperties props) {
        RagProperties.Chunking c = props.getChunking();
        return new AdaptiveDocumentSplitter(c.getChunkSize(), c.getChunkOverlap(), c.getStrategy());
    }

    /**
     * 父子分块器（可选，parentEnabled=true 时使用）。
     * 返回 ParentChildSplitter（而非 DocumentSplitter），因为入库服务需要调用
     * {@link ParentChildSplitter#getParentContents()} 落父块表。
     */
    @Bean
    @Primary
    public ParentChildSplitter parentChildSplitter(RagProperties props) {
        RagProperties.Chunking c = props.getChunking();
        // 父块用大窗口递归切（overlap 复用 KB 配置，对齐 WeKnora buildParentChildConfigs）；
        // 子块用配置的 child-size 切，overlap 上限 childSize/5（≈20%，对齐 WeKnora child overlap 比例）。
        // ⚠️ 此前父子 splitter 都写死 overlap=0，叠加旧 overlapTail 的 bug 会导致父块互相复制全文。
        int parentOverlap = c.getChunkOverlap();
        int childOverlap = Math.min(c.getChunkOverlap(), c.getChildSize() / 5);
        DocumentSplitter parentSplitter =
                new AdaptiveDocumentSplitter(c.getParentSize(), parentOverlap, "recursive");
        DocumentSplitter childSplitter =
                new AdaptiveDocumentSplitter(c.getChildSize(), childOverlap, "recursive");
        return new ParentChildSplitter(parentSplitter, childSplitter);
    }


    /**
     * 按配置选择兜底策略：model | fixed。
     * 注意：strategy=model 时依赖 PromptTemplateManager 完整实现（WF-6）。
     * 若 WF-6 尚未完成，请将 app.rag.fallback.strategy 设为 fixed 以保证启动。
     */
    @Bean
    public FallbackProvider fallbackProvider(RagProperties props,
                                             LLMService llmService,
                                             PromptTemplateManager promptManager) {
        String strategy = props.getFallback().getStrategy();
        if ("fixed".equalsIgnoreCase(strategy)) {
            return new FixedFallbackProvider();
        }
        return new ModelFallbackProvider(llmService, promptManager);
    }
}
