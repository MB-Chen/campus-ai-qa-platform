// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.infra.model;

import sparkx.sparkshop.knowledge.config.AiModelProperties;
import sparkx.sparkshop.knowledge.infra.chat.ModelTarget;
import sparkx.sparkshop.knowledge.service.IAiModelService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型选择器（文档 5.10.3）—— 多候选优先级调度。
 *
 * 三级排序：是否首选模型 → priority 升序 → id 字典序。
 * ★ 关键：过滤熔断中的模型（{@link ModelHealthStore#isUnavailable}）。
 *
 * ★★ 适配（与 sparkxV2 差异）：
 *  sparkxV2 的 ModelSelector 注入 AiModelProperties + RagProperties，从 yml 读候选清单，
 *  apiKey 复用 RagProperties 的 chat.apiKey。本项目候选清单已迁移到 ai_model 表
 *  （页面可编辑），由 {@link IAiModelService} 提供候选 + 默认模型；apiKey 由 IAiModelService
 *  从 ai_model 表的 credential 字段解析后写入每个 ModelCandidate，故 toTarget 直接用
 *  candidate 自带的 apiKey，无需再按 provider 兜底。
 */
@Component
public class ModelSelector {

    private final IAiModelService aiModelService;
    private final ModelHealthStore healthStore;

    public ModelSelector(IAiModelService aiModelService, ModelHealthStore healthStore) {
        this.aiModelService = aiModelService;
        this.healthStore = healthStore;
    }

    /** 对话候选（支持深度思考首选） */
    public List<ModelTarget> selectChatCandidates(boolean deepThinking) {
        String firstChoice = deepThinking ? resolveDeepThinkingModel()
                                           : aiModelService.getDefaultChatModelId();
        return filterAndSort(aiModelService.getChatCandidates(), firstChoice, deepThinking);
    }

    /** 嵌入候选 */
    public List<ModelTarget> selectEmbeddingCandidates() {
        return filterAndSort(aiModelService.getEmbeddingCandidates(),
                aiModelService.getDefaultEmbeddingModelId(), false);
    }

    /** 重排候选 */
    public List<ModelTarget> selectRerankCandidates() {
        return filterAndSort(aiModelService.getRerankCandidates(),
                aiModelService.getDefaultRerankModelId(), false);
    }

    /** 视觉候选 */
    public List<ModelTarget> selectVlmCandidates() {
        return filterAndSort(aiModelService.getVlmCandidates(),
                aiModelService.getDefaultVlmModelId(), false);
    }

    /**
     * 过滤 + 排序 + 转 ModelTarget。
     *
     * @param candidates      候选列表
     * @param firstChoice     首选模型 id（浮动到最前）
     * @param requireThinking 是否要求支持思考
     */
    private List<ModelTarget> filterAndSort(List<AiModelProperties.ModelCandidate> candidates,
                                            String firstChoice, boolean requireThinking) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        return candidates.stream()
                .filter(AiModelProperties.ModelCandidate::isEnabled)
                .filter(c -> !requireThinking || c.isSupportsThinking())
                .filter(c -> !healthStore.isUnavailable(c.resolveId()))   // ★ 过滤熔断模型
                .sorted(Comparator
                        .comparing((AiModelProperties.ModelCandidate c) ->
                                !c.resolveId().equals(firstChoice))        // 首选排前
                        .thenComparingInt(AiModelProperties.ModelCandidate::getPriority)  // priority 升序
                        .thenComparing(AiModelProperties.ModelCandidate::resolveId))      // id 兜底
                .map(this::toTarget)
                .collect(Collectors.toList());
    }

    /**
     * candidate → ModelTarget。
     * 适配：apiKey 直接取自 candidate（IAiModelService 已从 ai_model 表 credential 解析填入）。
     */
    private ModelTarget toTarget(AiModelProperties.ModelCandidate c) {
        return new ModelTarget(c.resolveId(), c.getModel(), c.getProvider(), c.getUrl(), c.getApiKey(),
                c.isSupportsThinking());
    }

    /**
     * 解析深度思考首选模型 id。
     * IAiModelService 当前未提供 getDeepThinkingChatModelId，故从对话候选中
     * 过滤 supportsThinking=true 的第一条兜底（与 sparkxV2 语义一致）。
     * TODO 后续 IAiModelService 增设 getDeepThinkingChatModelId() 后可改为优先调用它。
     */
    private String resolveDeepThinkingModel() {
        List<AiModelProperties.ModelCandidate> chatCandidates = aiModelService.getChatCandidates();
        if (chatCandidates == null || chatCandidates.isEmpty()) return null;
        return chatCandidates.stream()
                .filter(AiModelProperties.ModelCandidate::isSupportsThinking)
                .map(AiModelProperties.ModelCandidate::resolveId)
                .findFirst()
                .orElse(null);
    }
}
