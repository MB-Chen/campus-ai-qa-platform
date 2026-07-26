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

import sparkx.sparkshop.knowledge.infra.chat.ChatClient;
import sparkx.sparkshop.knowledge.infra.chat.ModelClientException;
import sparkx.sparkshop.knowledge.infra.chat.ModelClientErrorType;
import sparkx.sparkshop.knowledge.infra.chat.ModelTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * 模型降级执行器（文档 5.10）—— 同步路径通用降级骨架。
 *
 * 按 priority 顺序遍历候选：allowCall 准入 → 调用 → 成功 markSuccess / 失败 markFailure 换下一个。
 * 全部失败抛 ModelClientException。
 *
 * 流式路径不能用本执行器（流式需首包探测），由 {@code RoutingLLMService} 内联实现降级循环。
 */
@Component
public class ModelRoutingExecutor {

    private static final Logger log = LoggerFactory.getLogger(ModelRoutingExecutor.class);

    private final ModelHealthStore healthStore;

    public ModelRoutingExecutor(ModelHealthStore healthStore) {
        this.healthStore = healthStore;
    }

    /**
     * 带降级的执行。
     *
     * @param capability      能力名（日志/异常用，如 "chat"/"embedding"）
     * @param targets         有序候选列表
     * @param clientResolver  按 provider 取 ChatClient
     * @param caller          实际调用逻辑
     * @param <T>             返回类型
     * @return 调用结果
     */
    public <T> T executeWithFallback(String capability, List<ModelTarget> targets,
                                     Function<String, ChatClient> clientResolver,
                                     ModelCaller<ChatClient, T> caller) {
        if (targets == null || targets.isEmpty()) {
            throw new ModelClientException(
                    ModelClientErrorType.PROVIDER_ERROR,
                    "无可用 " + capability + " 候选模型");
        }
        Throwable lastError = null;

        for (ModelTarget target : targets) {
            ChatClient client = clientResolver.apply(target.provider());
            if (client == null) {
                log.warn("[Routing] {} provider={} 无可用 client，跳过", capability, target.provider());
                continue;
            }
            // ★ 二次熔断准入（应对选择后状态变化）
            if (!healthStore.allowCall(target.id())) {
                log.debug("[Routing] {} model={} 熔断中，跳过", capability, target.id());
                continue;
            }
            try {
                T result = caller.call(client, target);
                healthStore.markSuccess(target.id());
                return result;
            } catch (Exception e) {
                healthStore.markFailure(target.id());
                lastError = e;
                log.warn("[Routing] {} model={} 失败，尝试下一个候选: {}", capability, target.id(), e.getMessage());
            }
        }
        throw new ModelClientException(
                ModelClientErrorType.PROVIDER_ERROR,
                "所有 " + capability + " 候选模型调用失败", lastError);
    }

    /** 调用器函数接口 */
    @FunctionalInterface
    public interface ModelCaller<C, T> {
        T call(C client, ModelTarget target) throws Exception;
    }
}
