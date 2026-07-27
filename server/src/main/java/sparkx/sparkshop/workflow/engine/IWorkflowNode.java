// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.workflow.engine;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sparkx.sparkshop.workflow.vo.EdgeVo;
import sparkx.sparkshop.workflow.vo.NodeRuntimeVo;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 编排节点处理器接口。每种 shape（llm-node / answer-node ...）一个实现。
 * <p>
 * 由 {@link NodeProvider} 按 shape 名反射解析出 bean，{@link FlowNodeParser} 在执行时注入 emitter/latch。
 */
public interface IWorkflowNode {

    /** 注入 SSE 出口 */
    void setEmitter(SseEmitter emitter);

    /** 注入并行计数器（节点处理完调用 countDown） */
    void setLatch(CountDownLatch latch);

    /**
     * 处理当前节点。
     *
     * @param runtimeVo 节点运行时上下文
     * @return 下游边列表（分支节点只返回命中的那条；无下游返回 null）
     */
    List<EdgeVo> handle(NodeRuntimeVo runtimeVo);
}
