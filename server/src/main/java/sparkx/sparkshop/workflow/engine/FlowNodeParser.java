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

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.workflow.entity.WorkflowRuntimeContext;
import sparkx.sparkshop.workflow.mapper.WorkflowRuntimeContextMapper;
import sparkx.sparkshop.workflow.vo.EdgeVo;
import sparkx.sparkshop.workflow.vo.NodeRuntimeVo;
import sparkx.sparkshop.workflow.vo.NodeVo;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * 编排流程解释器：解析 X6 graph JSON，递归执行节点 DAG，并行分支用 CountDownLatch 同步。
 * <p>
 * 执行入口 {@link #run(SseEmitter, long, String, String, String)}（@Async），由 {@code WorkflowChatService} 触发。
 * 输出通过 {@link SseEmitter} + {@link WorkflowSseHelper} 推前端。
 * <p>
 * ★ 并发安全（Bug D 修复）：本 bean 虽是 @Component 单例，但所有「随请求变化」的状态
 *   （graph 解析结果、emitter、runtimeId、Answer 汇合点合并态）都收敛进 {@link ExecuteScope}，
 *   每次 {@link #run} 新建一个 scope，贯穿整次执行，彻底避免并发编排请求互相覆盖。
 */
@Component
@Slf4j
public class FlowNodeParser {

    @Autowired
    private NodeProvider nodeProvider;

    @Autowired
    private WorkflowRuntimeContextMapper runtimeContextMapper;

    @Autowired
    private WorkflowSseHelper sseHelper;

    /**
     * 执行编排流程。
     *
     * @param emitter    SSE 出口（per-call）
     * @param runtimeId  运行时 id（per-call）
     * @param flowData   流程 JSON
     * @param userId     用户 id
     * @param sessionId  会话 id（记忆隔离，跨轮稳定）
     */
    @Async("ragTaskExecutor")
    public void run(SseEmitter emitter, long runtimeId, String flowData, String userId, String sessionId) {
        ExecuteScope scope = new ExecuteScope();
        scope.emitter = emitter;
        scope.runtimeId = runtimeId;
        scope.timer = new TimeInterval();

        try {
            buildData(flowData, scope);

            List<EdgeVo> startEdges = scope.edges.get(scope.startId);
            if (CollectionUtils.isEmpty(startEdges)) {
                sseHelper.sendError(emitter, "流程异常：开始节点无下游");
                emitter.complete();
                return;
            }
            execute(startEdges.get(0), scope.startId, userId, sessionId, scope);
        } catch (Exception e) {
            log.error("[FlowNodeParser] 流程执行异常 runtimeId={}: {}", runtimeId, e.getMessage(), e);
            sseHelper.sendError(emitter, "编排执行异常：" + e.getMessage());
            emitter.completeWithError(e);
        }
    }

    /**
     * 递归执行一层节点。
     *
     * @param edgeVo    当前边
     * @param sourceId  上游节点 cell
     * @param userId    用户 id
     * @param sessionId 会话 id
     * @param scope     本次执行的 per-call 作用域
     */
    private void execute(EdgeVo edgeVo, String sourceId, String userId, String sessionId, ExecuteScope scope) {
        Map<String, EdgeVo> nextNeedVoMap = new HashMap<>();
        List<String> targetIds = edgeVo.getTarget();

        CountDownLatch latch = new CountDownLatch(targetIds.size());

        // 并联流程
        for (String targetId : targetIds) {
            NodeVo nodeInfo = scope.nodes.get(targetId);
            if (nodeInfo == null) {
                continue;
            }
            IWorkflowNode flowNode = nodeProvider.handle(nodeInfo.getShape());

            // 推节点开始事件（前端执行详情用）
            sseHelper.sendNodeStart(scope.emitter, scope.runtimeId, nodeInfo.getId(), nodeInfo.getShape());

            NodeRuntimeVo runtimeVo = new NodeRuntimeVo();
            runtimeVo.setNodeInfo(nodeInfo);
            runtimeVo.setEdges(scope.edges);
            runtimeVo.setNodes(scope.nodes);
            runtimeVo.setRuntimeId(scope.runtimeId);
            runtimeVo.setSourceId(sourceId);
            runtimeVo.setUserId(userId);
            runtimeVo.setSessionId(sessionId);
            // ★ per-call 状态注入（替代旧的单例 setter）
            runtimeVo.setEmitter(scope.emitter);
            runtimeVo.setLatch(latch);
            runtimeVo.setInDegree(scope.inDegreeOf(nodeInfo.getId()));
            // Answer 汇合点合并态：per-runtime + per-cell，首次取时懒创建
            if ("answer-node".equals(nodeInfo.getShape())) {
                runtimeVo.setMergeState(scope.mergeStateOf(nodeInfo.getId()));
            }

            List<EdgeVo> nextEdgeList = flowNode.handle(runtimeVo);
            sseHelper.sendNodeEnd(scope.emitter, scope.runtimeId, nodeInfo.getId());

            if (!CollectionUtils.isEmpty(nextEdgeList)) {
                nextNeedVoMap.put(nodeInfo.getId(), nextEdgeList.get(0));
            }
        }

        try {
            // 阻塞等本层节点异步处理完
            latch.await();

            // 进入下一层
            if (!MapUtil.isEmpty(nextNeedVoMap)) {
                nextNeedVoMap.forEach((nodeId, edge) -> execute(edge, nodeId, userId, sessionId, scope));
            } else {
                // 流程结束：聚合 token 用量
                long seconds = scope.timer.intervalSecond();
                List<WorkflowRuntimeContext> contextList = runtimeContextMapper.selectList(
                        new LambdaQueryWrapper<WorkflowRuntimeContext>()
                                .eq(WorkflowRuntimeContext::getRuntimeId, scope.runtimeId));

                int totalTokens = 0;
                int inputTokens = 0;
                int outputTokens = 0;
                for (WorkflowRuntimeContext ctx : contextList) {
                    if (ctx.getModelData() != null) {
                        JSONObject json = JSONUtil.parseObj(ctx.getModelData());
                        if (json.getInt("totalTokenCount") != null) {
                            inputTokens += json.getInt("inputTokenCount");
                            outputTokens += json.getInt("outputTokenCount");
                            totalTokens += json.getInt("totalTokenCount");
                        }
                    }
                }
                sseHelper.sendComplete(scope.emitter, inputTokens, outputTokens, totalTokens, seconds);
                scope.emitter.complete();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[FlowNodeParser] 线程阻塞中断: {}", e.getMessage());
            sseHelper.sendError(scope.emitter, "流程执行被中断");
            scope.emitter.completeWithError(e);
        }
    }

    /**
     * 解析 graph JSON：构建 edges/nodes 映射、入边度数表、识别开始节点。
     */
    protected void buildData(String flowData, ExecuteScope scope) {
        JSONObject flowObject = JSONUtil.parseObj(flowData);
        JSONArray cells = flowObject.getJSONArray("cells");
        if (cells == null) {
            throw new BusinessException("流程数据为空");
        }

        for (int i = 0; i < cells.size(); i++) {
            JSONObject item = cells.getJSONObject(i);
            Object shape = item.get("shape");

            // 识别开始节点，回写 cell
            if ("start-node".equals(shape)) {
                scope.startId = item.getStr("id");
                WorkflowRuntimeContext update = new WorkflowRuntimeContext();
                update.setCell(scope.startId);
                runtimeContextMapper.update(update,
                        new LambdaQueryWrapper<WorkflowRuntimeContext>()
                                .eq(WorkflowRuntimeContext::getNodeType, "start-node")
                                .eq(WorkflowRuntimeContext::getRuntimeId, scope.runtimeId));
            }

            if ("edge".equals(shape)) {
                buildEdge(item, scope);
                // 统计入边度数（Bug B/C：汇合点延迟执行 + 多行防护依赖入边数）
                String targetCell = item.getJSONObject("target").getStr("cell");
                scope.inDegree.merge(targetCell, 1, Integer::sum);
            } else {
                buildNode(item, shape.toString(), scope);
            }
        }
    }

    /** 解析一条边，合并同源并联 */
    @SuppressWarnings("unchecked")
    private void buildEdge(JSONObject item, ExecuteScope scope) {
        String key = item.getJSONObject("source").getStr("cell");
        List<EdgeVo> hasEdges = scope.edges.get(key);

        EdgeVo edgeVo = new EdgeVo();
        edgeVo.setId(item.getStr("id"));
        edgeVo.setSource(key);
        edgeVo.setSourcePort(item.getJSONObject("source").getStr("port"));

        if (CollectionUtils.isEmpty(hasEdges)) {
            List<String> targetCells = new LinkedList<>();
            targetCells.add(item.getJSONObject("target").getStr("cell"));
            edgeVo.setTarget(targetCells);

            List<EdgeVo> list = new LinkedList<>();
            list.add(edgeVo);
            scope.edges.put(key, list);
        } else {
            // 并联节点：同源同桩合并
            boolean merged = false;
            for (EdgeVo hasEdge : hasEdges) {
                if (hasEdge.getSourcePort().equals(edgeVo.getSourcePort())) {
                    hasEdge.getTarget().add(item.getJSONObject("target").getStr("cell"));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                List<String> targetCells = new LinkedList<>();
                targetCells.add(item.getJSONObject("target").getStr("cell"));
                edgeVo.setTarget(targetCells);
                hasEdges.add(edgeVo);
            }
            scope.edges.put(key, hasEdges);
        }
    }

    /** 解析一个节点；purpose-node 按右侧桩 Y 轴排序保证分支顺序 */
    private void buildNode(JSONObject item, String shape, ExecuteScope scope) {
        JSONObject nodeData = item.getJSONObject("data");
        if ("purpose-node".equals(shape)) {
            JSONObject ports = item.getJSONObject("ports");
            if (ports != null) {
                JSONArray items = ports.getJSONArray("items");
                if (items != null) {
                    List<String> sortIds = items.stream()
                            .map(obj -> (JSONObject) obj)
                            .filter(obj -> "rightPorts".equals(obj.getStr("group")))
                            .sorted(Comparator.comparingInt(obj -> obj.getJSONObject("args").getInt("y")))
                            .map(obj -> obj.getStr("id"))
                            .toList();
                    nodeData.set("targetList", sortIds);
                }
            }
        }

        NodeVo nodeVo = new NodeVo();
        nodeVo.setId(item.getStr("id"));
        nodeVo.setShape(shape);
        nodeVo.setData(nodeData);
        scope.nodes.put(item.getStr("id"), nodeVo);
    }

    // ====================================================================
    // ExecuteScope：本次编排执行的 per-call 作用域（Bug D 修复核心）。
    // 所有「随请求变化」的状态都在这里，FlowNodeParser 单例本身不再持有可变状态。
    // ====================================================================
    private static class ExecuteScope {
        /** SSE 出口 */
        SseEmitter emitter;
        /** 运行时 id */
        long runtimeId;
        /** 计时器 */
        TimeInterval timer;
        /** 所有连线：source cell → 出边列表 */
        final Map<String, List<EdgeVo>> edges = new HashMap<>();
        /** 所有节点：cell id → NodeVo */
        final Map<String, NodeVo> nodes = new HashMap<>();
        /** 入边度数表：target cell → 入边数（Bug B 汇合点延迟执行用） */
        final Map<String, Integer> inDegree = new HashMap<>();
        /** 开始节点 cell id */
        String startId = "";
        /** Answer 汇合点合并态：runtimeId+cell → state（懒创建；同一汇合点多次调用共享） */
        final Map<String, AnswerMergeState> mergeStates = new ConcurrentHashMap<>();

        /** 取某节点的入边度数（无入边返回 0；正常节点至少 1） */
        int inDegreeOf(String cell) {
            return inDegree.getOrDefault(cell, 0);
        }

        /** 取/创建某 Answer 节点的合并态（同一汇合点的多次 handle 调用共享同一个） */
        AnswerMergeState mergeStateOf(String cell) {
            return mergeStates.computeIfAbsent(runtimeId + ":" + cell, k -> new AnswerMergeState());
        }
    }
}
