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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * RAG 管线引擎
 *
 * 按顺序执行阶段链，支持：
 *  - 条件裁剪（shouldRun）
 *  - 兜底分支（FALLBACK → 后续 FallbackStage 自然接管）
 *  - 提前完成（COMPLETE，如缓存命中）
 *  - 每阶段计时监控
 *
 * 阶段通过 Spring 自动注入，按 @Order 排序。
 * 新增阶段只需加 @Component 并实现接口，开闭原则。
 */
@Component
public class RagPipeline {

    private static final Logger log = LoggerFactory.getLogger(RagPipeline.class);

    private final List<PipelineStage> stages;
    private final MeterRegistry meterRegistry;

    public RagPipeline(List<PipelineStage> stages, MeterRegistry meterRegistry) {
        this.stages = new ArrayList<>(stages);
        // 按 @Order 注解排序
        AnnotationAwareOrderComparator.sort(this.stages);
        this.meterRegistry = meterRegistry;
        log.info("[Pipeline] 已装配 {} 个阶段: {}", this.stages.size(),
                this.stages.stream().map(PipelineStage::name).toList());
    }

    /** 会调 LLM 的阶段名（用于汇总日志统计 LLM 调用次数） */
    private static final Set<String> LLM_STAGES = Set.of(
            "rewrite-split", "tree-intent", "vague-clarify", "generate", "fallback");

    /**
     * 执行完整管线。
     */
    public void run(PipelineContext ctx) throws Exception {
        long pipelineStart = System.currentTimeMillis();
        log.info("[Pipeline] start session={} query=\"{}\"",
                ctx.getSessionId(), ctx.getOriginalQuery());

        // 各阶段耗时累计（name → ms），用于结尾汇总
        Map<String, Long> stageCosts = new LinkedHashMap<>();
        int llmCallCount = 0;

        for (PipelineStage stage : stages) {
            if (!stage.shouldRun(ctx)) {
                log.debug("[Pipeline] skip stage={} (shouldRun=false)", stage.name());
                continue;
            }

            long stageStart = System.currentTimeMillis();
            Timer.Sample sample = Timer.start(meterRegistry);
            PipelineStage.StageResult result;
            try {
                result = stage.execute(ctx);
            } catch (Exception e) {
                meterRegistry.counter("rag.pipeline.errors",
                        "stage", stage.name(), "error", e.getClass().getSimpleName()).increment();
                log.error("[Pipeline] stage={} failed", stage.name(), e);
                throw e;
            } finally {
                sample.stop(Timer.builder("rag.pipeline.stage")
                        .tag("stage", stage.name())
                        .register(meterRegistry));
            }

            long cost = System.currentTimeMillis() - stageStart;
            stageCosts.put(stage.name(), cost);
            ctx.getStageTimings().put(stage.name(), cost);   // ★ 同步写入 ctx，供前端时间线展示
            if (LLM_STAGES.contains(stage.name())) {
                llmCallCount++;
            }
            log.info("[Pipeline:diag] stage={} 耗时={}ms{}", stage.name(), cost,
                    LLM_STAGES.contains(stage.name()) ? " [含LLM]" : "");
            log.debug("[Pipeline] stage={} result={}", stage.name(), result);

            if (result == PipelineStage.StageResult.FALLBACK) {
                log.info("[Pipeline] fallback triggered at stage={}", stage.name());
                ctx.setAttr("needFallback", true);
            }
            if (result == PipelineStage.StageResult.COMPLETE) {
                log.info("[Pipeline] early complete at stage={}", stage.name());
                break;
            }
        }

        long totalCost = System.currentTimeMillis() - pipelineStart;
        // ★ 写入 ctx，供 SSE complete 事件回传前端时间线展示
        ctx.setTotalCost(totalCost);
        ctx.setLlmCallCount(llmCallCount);
        // ★ 汇总：一条日志看清总耗时 + 各阶段分布 + LLM 调用次数（卡顿排查用）
        log.info("[Pipeline:diag] ===== 汇总 总耗时={}ms LLM调用={}次 阶段分布={} session={} =====",
                totalCost, llmCallCount, stageCosts, ctx.getSessionId());
        log.info("[Pipeline] done session={} answerLen={}",
                ctx.getSessionId(), ctx.getAnswer() == null ? 0 : ctx.getAnswer().length());
    }
}
