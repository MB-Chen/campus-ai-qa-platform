// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.common.trace;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * RAG 链路追踪切面（移植自 sparkxV2，文档 8.3）。
 *
 * 拦截 {@link RagTraceNode} 标注的方法，采集：
 *  - {@code rag.trace.node{type,name}} Timer：节点耗时（含 TTFT 首字延迟）
 *  - {@code rag.trace.errors{type,name,error}} Counter：节点异常计数
 *
 * 关键指标由容错层标注产生：
 *  - {@code LLM_TTFT}：首字延迟（LlmFirstPacketProbe 采集）
 *  - {@code LLM_ROUTING}：模型路由耗时
 *
 * 厂商无关：对接 Micrometer，可桥接 OTel/Prometheus。
 *
 * 说明：WF-3/4/6 移植时为避免依赖未创建的本注解，多处 @RagTraceNode 被临时移除（带 TODO）。
 * 本切面已就绪后，可按需在 RoutingLLMService.chat/streamChat、LlmFirstPacketProbe.awaitFirstPacket、
 * RagPipeline 等方法上重新加回 @RagTraceNode 注解以恢复细粒度指标采集。
 */
@Aspect
@Component
public class RagTraceAspect {

    private static final Logger log = LoggerFactory.getLogger(RagTraceAspect.class);

    private final MeterRegistry meterRegistry;

    public RagTraceAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(traceNode)")
    public Object trace(ProceedingJoinPoint pjp, RagTraceNode traceNode) throws Throwable {
        String name = traceNode.name();
        String type = traceNode.type();
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            return pjp.proceed();
        } catch (Throwable e) {
            // 异常计数
            Counter.builder("rag.trace.errors")
                    .tag("type", type)
                    .tag("name", name)
                    .tag("error", e.getClass().getSimpleName())
                    .register(meterRegistry)
                    .increment();
            log.debug("[Trace] node={}/{} 失败: {}", type, name, e.getMessage());
            throw e;
        } finally {
            // 节点耗时（含 TTFT）
            sample.stop(Timer.builder("rag.trace.node")
                    .tag("type", type)
                    .tag("name", name)
                    .register(meterRegistry));
        }
    }
}
