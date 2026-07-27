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

import java.util.ArrayList;
import java.util.List;

/**
 * 模型容错层配置（熔断参数 + 流式 + 会话记忆默认值）。
 * 对应 application.yml 的 app.ai.* 节点（移植自 sparkxV2）。
 *
 * 适配说明（与 sparkxV2 差异）：
 *  - sparkxV2 的 chat/embedding/rerank/vlm 候选列表（candidates）从 yml 读；
 *    本项目改为从 ai_model 表（页面可编辑）读取，由 IAiModelService 提供候选。
 *  - 本类只保留熔断策略、流式、会话记忆这些"非模型清单"的配置项。
 *  - 为了与 sparkxV2 1:1 对齐字段、便于后续 ModelSelector 复用，仍保留 ModelCandidate/ModelGroup
 *    结构（供 IAiModelService 装配候选时构造），但不再从 yml 绑定候选清单。
 *
 * 设计：业务/管线只见 {@code LLMService} 接口，多候选、熔断、降级全部由本配置 + ai_model 表驱动。
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiModelProperties {

    /** 熔断/降级策略 */
    private Selection selection = new Selection();
    /** 流式相关 */
    private Stream stream = new Stream();
    /** 会话记忆配置 */
    private Memory memory = new Memory();
    /** Ollama ChatClient 启用开关（容错层 OllamaChatClient @ConditionalOnProperty 用） */
    private Ollama ollama = new Ollama();


    public static class Selection {
        /** 连续失败多少次触发熔断（OPEN） */
        private int failureThreshold = 2;
        /** 熔断冷却时长（ms），冷却到期转 HALF_OPEN 允许单次探测 */
        private long openDurationMs = 30_000L;
        /** 流式首包探测超时（秒） */
        private long firstPacketTimeoutSeconds = 60L;

        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
        public long getOpenDurationMs() { return openDurationMs; }
        public void setOpenDurationMs(long openDurationMs) { this.openDurationMs = openDurationMs; }
        public long getFirstPacketTimeoutSeconds() { return firstPacketTimeoutSeconds; }
        public void setFirstPacketTimeoutSeconds(long firstPacketTimeoutSeconds) { this.firstPacketTimeoutSeconds = firstPacketTimeoutSeconds; }
    }

    public static class Stream {
        /** SSE 单批聚合后向下游推送的 token 粒度 */
        private int messageChunkSize = 5;

        public int getMessageChunkSize() { return messageChunkSize; }
        public void setMessageChunkSize(int messageChunkSize) { this.messageChunkSize = messageChunkSize; }
    }

    /** Ollama 容错层开关 */
    public static class Ollama {
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }


    public static class Memory {
        /** 滑动窗口保留轮数（1 轮 = 1 user + 1 assistant） */
        private int historyKeepTurns = 4;
        /** 是否开启话题导向摘要 */
        private boolean summaryEnabled = true;
        /** 用户消息总数达到该值才触发摘要压缩 */
        private int summaryStartTurns = 5;
        /** 摘要最大字符数 */
        private int summaryMaxChars = 200;
        /** 摘要标题最大长度（预留） */
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


    /** 对话模型组（支持深度思考首选） */
    public static class ChatGroup extends ModelGroup {
        /** 深度思考首选模型 id（deepThinking=true 时优先） */
        private String deepThinkingModel;

        public String getDeepThinkingModel() { return deepThinkingModel; }
        public void setDeepThinkingModel(String deepThinkingModel) { this.deepThinkingModel = deepThinkingModel; }
    }

    /** 通用模型组：一个默认模型 + 多候选（按 priority 排序） */
    public static class ModelGroup {
        private String defaultModel;
        private List<ModelCandidate> candidates = new ArrayList<>();

        public String getDefaultModel() { return defaultModel; }
        public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
        public List<ModelCandidate> getCandidates() { return candidates; }
        public void setCandidates(List<ModelCandidate> candidates) { this.candidates = candidates; }
    }

    /**
     * 模型候选项。id 缺省时回退为 "provider::model"。
     * provider 取值：openai / ollama（对齐 ChatClient#provider()）。
     */
    public static class ModelCandidate {
        private String id;
        private String provider = "openai";
        private String model;
        /** 候选项自有 URL，缺省时用 ProviderConfig.url + endpoints */
        private String url;
        private String apiKey;
        private int priority = 100;
        private boolean enabled = true;
        private boolean supportsThinking = false;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isSupportsThinking() { return supportsThinking; }
        public void setSupportsThinking(boolean supportsThinking) { this.supportsThinking = supportsThinking; }

        /** 回退 id：未显式配置时用 provider::model */
        public String resolveId() {
            return (id != null && !id.isBlank()) ? id : (provider + "::" + model);
        }
    }


    public Selection getSelection() { return selection; }
    public void setSelection(Selection selection) { this.selection = selection; }
    public Stream getStream() { return stream; }
    public void setStream(Stream stream) { this.stream = stream; }
    public Memory getMemory() { return memory; }
    public void setMemory(Memory memory) { this.memory = memory; }
    public Ollama getOllama() { return ollama; }
    public void setOllama(Ollama ollama) { this.ollama = ollama; }
}
