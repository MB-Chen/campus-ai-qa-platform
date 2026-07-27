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
import lombok.Setter;
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
import java.util.concurrent.CountDownLatch;

/**
 * 编排流程解释器：解析 X6 graph JSON，递归执行节点 DAG，并行分支用 CountDownLatch 同步。
 * <p>
 * 执行入口 {@link #run(String, String, String)}（@Async），由 {@code WorkflowChatService} 触发。
 * 输出通过注入的 {@link SseEmitter} + {@link WorkflowSseHelper} 推前端。
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

    /** 所有连线：source cell → 出边列表 */
    private Map<String, List<EdgeVo>> edges = new HashMap<>();

    /** 所有节点：cell id → NodeVo */
    private Map<String, NodeVo> nodes = new HashMap<>();

    /** 开始节点 cell id */
    private String startId = "";

    @Setter
    public SseEmitter emitter;

    /** 运行时 id */
    @Setter
    public Long runtimeId;

    private TimeInterval timer;

    /**
     * 执行编排流程。
     *
     * @param flowData  流程 JSON
     * @param userId    用户 id
     * @param sessionId 会话 id（记忆隔离）
     */
    @Async("ragTaskExecutor")
    public void run(String flowData, String userId, String sessionId) {
        this.edges = new HashMap<>();
        this.nodes = new HashMap<>();
        this.timer = new TimeInterval();

        try {
            buildData(flowData);

            List<EdgeVo> startEdges = this.edges.get(this.startId);
            if (CollectionUtils.isEmpty(startEdges)) {
                sseHelper.sendError(emitter, "流程异常：开始节点无下游");
                emitter.complete();
                return;
            }
            execute(startEdges.get(0), this.startId, userId, sessionId);
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
     */
    private void execute(EdgeVo edgeVo, String sourceId, String userId, String sessionId) {
        Map<String, EdgeVo> nextNeedVoMap = new HashMap<>();
        List<String> targetIds = edgeVo.getTarget();

        CountDownLatch latch = new CountDownLatch(targetIds.size());

        // 并联流程
        for (String targetId : targetIds) {
            NodeVo nodeInfo = this.nodes.get(targetId);
            if (nodeInfo == null) {
                continue;
            }
            IWorkflowNode flowNode = nodeProvider.handle(nodeInfo.getShape());
            flowNode.setEmitter(this.emitter);
            flowNode.setLatch(latch);

            // 推节点开始事件（前端执行详情用）
            sseHelper.sendNodeStart(emitter, this.runtimeId, nodeInfo.getId(), nodeInfo.getShape());

            NodeRuntimeVo runtimeVo = new NodeRuntimeVo();
            runtimeVo.setNodeInfo(nodeInfo);
            runtimeVo.setEdges(this.edges);
            runtimeVo.setNodes(this.nodes);
            runtimeVo.setRuntimeId(this.runtimeId);
            runtimeVo.setSourceId(sourceId);
            runtimeVo.setUserId(userId);
            runtimeVo.setSessionId(sessionId);

            List<EdgeVo> nextEdgeList = flowNode.handle(runtimeVo);
            sseHelper.sendNodeEnd(emitter, this.runtimeId, nodeInfo.getId());

            if (!CollectionUtils.isEmpty(nextEdgeList)) {
                nextNeedVoMap.put(nodeInfo.getId(), nextEdgeList.get(0));
            }
        }

        try {
            // 阻塞等本层节点异步处理完
            latch.await();

            // 进入下一层
            if (!MapUtil.isEmpty(nextNeedVoMap)) {
                nextNeedVoMap.forEach((nodeId, edge) -> execute(edge, nodeId, userId, sessionId));
            } else {
                // 流程结束：聚合 token 用量
                long seconds = this.timer.intervalSecond();
                List<WorkflowRuntimeContext> contextList = runtimeContextMapper.selectList(
                        new LambdaQueryWrapper<WorkflowRuntimeContext>()
                                .eq(WorkflowRuntimeContext::getRuntimeId, this.runtimeId));

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
                sseHelper.sendComplete(emitter, inputTokens, outputTokens, totalTokens, seconds);
                emitter.complete();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[FlowNodeParser] 线程阻塞中断: {}", e.getMessage());
            sseHelper.sendError(emitter, "流程执行被中断");
            emitter.completeWithError(e);
        }
    }

    /**
     * 解析 graph JSON：构建 edges/nodes 映射，识别开始节点。
     */
    protected void buildData(String flowData) {
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
                this.startId = item.getStr("id");
                WorkflowRuntimeContext update = new WorkflowRuntimeContext();
                update.setCell(this.startId);
                runtimeContextMapper.update(update,
                        new LambdaQueryWrapper<WorkflowRuntimeContext>()
                                .eq(WorkflowRuntimeContext::getNodeType, "start-node")
                                .eq(WorkflowRuntimeContext::getRuntimeId, this.runtimeId));
            }

            if ("edge".equals(shape)) {
                buildEdge(item);
            } else {
                buildNode(item, shape.toString());
            }
        }
    }

    /** 解析一条边，合并同源并联 */
    @SuppressWarnings("unchecked")
    private void buildEdge(JSONObject item) {
        String key = item.getJSONObject("source").getStr("cell");
        List<EdgeVo> hasEdges = this.edges.get(key);

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
            this.edges.put(key, list);
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
            this.edges.put(key, hasEdges);
        }
    }

    /** 解析一个节点；purpose-node 按右侧桩 Y 轴排序保证分支顺序 */
    private void buildNode(JSONObject item, String shape) {
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
        this.nodes.put(item.getStr("id"), nodeVo);
    }
}
