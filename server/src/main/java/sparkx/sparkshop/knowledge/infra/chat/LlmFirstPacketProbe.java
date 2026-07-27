// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.infra.chat;

import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 首包探测器（文档 5.10.5）。
 *
 * ★ 刻意拆成独立 Bean：Spring AOP 不拦截类内 self-call（同类方法互调），
 * 拆出来才能让 {@code @RagTraceNode(type="LLM_TTFT")} 采集 TTFT（首字延迟）指标生效。
 *
 * {@code RoutingLLMService} 流式降级时调用本 Bean 而非直接调 bridge，
 * 从而让 AOP 切面织入计时。
 */
@Component
public class LlmFirstPacketProbe {

    /**
     * 阻塞等待首包探测结果。
     *
     * @param bridge  探测桥（包装了真实业务 callback）
     * @param timeout 超时时长
     * @param unit    时间单位
     * @return 探测结果（SUCCESS/ERROR/TIMEOUT/NO_CONTENT）
     */
    // WF-7: RagTraceNode AOP 指标待补
    public ProbeStreamBridge.ProbeResult awaitFirstPacket(ProbeStreamBridge bridge, long timeout, TimeUnit unit) {
        return bridge.awaitFirstPacket(timeout, unit);
    }
}
