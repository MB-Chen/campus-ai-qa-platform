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
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 三态熔断器（文档 5.10.2）—— 容错层核心亮点。
 *
 * 状态机：
 *   CLOSED →（连续失败 ≥ threshold）→ OPEN →（冷却 openDurationMs 到期）→ HALF_OPEN
 *         →（单探测成功）→ CLOSED /（失败）→ OPEN
 *
 * 纯内存 ConcurrentHashMap + compute 原子操作，无框架依赖。
 * 每个模型独立维护健康状态，key = modelId。
 *
 * ★ 双重准入（应对并发竞态）：
 *  - {@link #isUnavailable}：选择期预过滤（被 ModelSelector 调用，剔除熔断模型）
 *  - {@link #allowCall}：调用期精确准入（被 RoutingExecutor 调用，HALF_OPEN 时占用探测名额）
 */
@Component
public class ModelHealthStore {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    /** 单模型健康状态（CAS 通过外层 ConcurrentHashMap.compute 保证） */
    private static class Health {
        int consecutiveFailures;
        long openUntil;             // 熔断到期时间戳（ms）
        boolean halfOpenInFlight;   // HALF_OPEN 探测请求是否在途
        State state = State.CLOSED;
    }

    private final Map<String, Health> healthById = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final long openDurationMs;

    public ModelHealthStore(AiModelProperties props) {
        this.failureThreshold = Math.max(1, props.getSelection().getFailureThreshold());
        this.openDurationMs = props.getSelection().getOpenDurationMs();
    }

    /**
     * 选择期过滤：被 {@link ModelSelector} 调用，剔除熔断中的模型。
     * 注意：本方法只读不改状态（OPEN→HALF_OPEN 的转换留给 allowCall 在调用期完成）。
     */
    public boolean isUnavailable(String modelId) {
        Health h = healthById.get(modelId);
        if (h == null) return false;
        synchronized (h) {
            return switch (h.state) {
                case OPEN -> h.openUntil > System.currentTimeMillis();   // 仍在冷却期
                case HALF_OPEN -> h.halfOpenInFlight;                   // 探测请求占用名额
                case CLOSED -> false;
            };
        }
    }

    /**
     * 调用期准入闸（被 RoutingExecutor/RoutingLLMService 调用）。
     * 原子更新：冷却到期的 OPEN → 转 HALF_OPEN 占用探测名额；返回是否允许调用。
     */
    public boolean allowCall(String modelId) {
        Health result = healthById.compute(modelId, (id, h) -> {
            if (h == null) return new Health();   // 默认 CLOSED
            long now = System.currentTimeMillis();
            switch (h.state) {
                case CLOSED -> { return h; }   // 直接放行
                case OPEN -> {
                    if (h.openUntil > now) {
                        return h;   // 仍在冷却期，拒绝（isBlocked 判定 false）
                    }
                    // 冷却到期 → 转 HALF_OPEN，占用 inFlight 名额（探测请求）
                    h.state = State.HALF_OPEN;
                    h.halfOpenInFlight = true;
                    return h;
                }
                case HALF_OPEN -> {
                    if (h.halfOpenInFlight) return h;   // 名额已占，拒绝
                    h.halfOpenInFlight = true;
                    return h;
                }
            }
            return h;
        });
        return !isBlocked(result);
    }

    /** 探测/调用成功：状态重置为 CLOSED（HALF_OPEN 探测成功即"愈合"） */
    public void markSuccess(String modelId) {
        healthById.computeIfPresent(modelId, (id, h) -> {
            h.state = State.CLOSED;
            h.consecutiveFailures = 0;
            h.openUntil = 0;
            h.halfOpenInFlight = false;
            return h;
        });
    }

    /** 失败：HALF_OPEN→立即回 OPEN 重置冷却；CLOSED→计数，达阈值熔断 */
    public void markFailure(String modelId) {
        healthById.computeIfPresent(modelId, (id, h) -> {
            if (h.state == State.HALF_OPEN) {
                // 探测失败，立即回 OPEN，重置冷却期
                h.state = State.OPEN;
                h.openUntil = System.currentTimeMillis() + openDurationMs;
                h.consecutiveFailures = 0;
                h.halfOpenInFlight = false;
            } else if (h.state == State.CLOSED) {
                h.consecutiveFailures++;
                if (h.consecutiveFailures >= failureThreshold) {
                    h.state = State.OPEN;
                    h.openUntil = System.currentTimeMillis() + openDurationMs;
                    h.consecutiveFailures = 0;
                }
            }
            // OPEN 状态下失败：无需处理（仍在冷却期）
            return h;
        });
    }

    /** 调试/指标用：当前熔断中的模型数 */
    public long countUnavailable() {
        return healthById.entrySet().stream().filter(e -> isUnavailable(e.getKey())).count();
    }

    private boolean isBlocked(Health h) {
        if (h == null) return false;
        synchronized (h) {
            return switch (h.state) {
                case OPEN -> h.openUntil > System.currentTimeMillis();
                // allowCall 已占用 inFlight 名额时本调用获准，故此处不再阻断 HALF_OPEN
                case HALF_OPEN, CLOSED -> false;
            };
        }
    }
}
