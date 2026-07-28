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

import sparkx.sparkshop.workflow.vo.EdgeVo;
import sparkx.sparkshop.workflow.vo.NodeRuntimeVo;

import java.util.List;

/**
 * 编排节点处理器接口。每种 shape（llm-node / answer-node ...）一个实现。
 * <p>
 * 由 {@link NodeProvider} 按 shape 名反射解析出 bean（@Component 单例），
 * {@link FlowNodeParser} 在执行时把 per-call 上下文塞进 {@link NodeRuntimeVo} 传入。
 * <p>
 * ★ 节点是单例，<b>禁止持有任何随请求变化的状态</b>（emitter/latch 等）。
 *   这些状态统一从 {@code runtimeVo} 取（{@link NodeRuntimeVo#getEmitter()} / {@link NodeRuntimeVo#getLatch()}）。
 */
public interface IWorkflowNode {

    /**
     * 处理当前节点。
     *
     * @param runtimeVo 节点运行时上下文（含 SSE 出口、并行计数器、入边度数等 per-call 状态）
     * @return 下游边列表（分支节点只返回命中的那条；无下游返回 null）
     */
    List<EdgeVo> handle(NodeRuntimeVo runtimeVo);
}
