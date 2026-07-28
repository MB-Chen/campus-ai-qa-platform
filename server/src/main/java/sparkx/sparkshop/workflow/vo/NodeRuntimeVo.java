// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.workflow.vo;

import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sparkx.sparkshop.workflow.engine.AnswerMergeState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * 节点运行时上下文，在 DAG 执行时传递给每个节点。
 * <p>
 * ★ 并发安全：本对象是 <b>per-call</b> 的（每次 execute 新建），承载所有「随本次编排执行而变」的状态，
 *   包括 SSE 出口、并行计数器、入边度数、Answer 汇合点合并态。
 *   节点处理器（@Component 单例）不再用 setter 注入这些可变字段，全部从这里取，
 *   避免多个并发编排请求在单例 bean 上互相覆盖状态。
 */
@Data
public class NodeRuntimeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 运行时 id */
    private long runtimeId;

    /** 来源节点 id（本节点的上游 cell；汇合点多次调用时为当前这条入边的上游） */
    private String sourceId;

    /** 用户 id */
    private String userId;

    /** 边信息：source cell → 出边列表 */
    private Map<String, List<EdgeVo>> edges;

    /** 全部节点：cell id → NodeVo */
    private Map<String, NodeVo> nodes;

    /** 当前节点信息 */
    private NodeVo nodeInfo;

    /** 会话 id（记忆隔离，跨轮稳定） */
    private String sessionId;

    /** SSE 出口（per-call，替代旧的单例 setter 注入） */
    private SseEmitter emitter;

    /** 并行计数器（per-call，本层节点处理完调用 countDown） */
    private CountDownLatch latch;

    /** 当前节点的入边总数（Answer 汇合点延迟执行用；单入边时为 1） */
    private int inDegree;

    /**
     * Answer 汇合点的合并态（仅 Answer 节点用，per-runtime + per-cell，
     * 由 FlowNodeParser 在 execute 时从 per-run 的 Map 中取出注入）。
     */
    private AnswerMergeState mergeState;
}
