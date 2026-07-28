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

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.workflow.entity.WorkflowRuntimeContext;
import sparkx.sparkshop.workflow.mapper.WorkflowRuntimeContextMapper;
import sparkx.sparkshop.workflow.vo.EdgeVo;
import sparkx.sparkshop.workflow.vo.NextAnswerNodeVo;
import sparkx.sparkshop.workflow.vo.NodeRuntimeVo;
import sparkx.sparkshop.workflow.vo.NodeVo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 编排运行时上下文辅助（移植自 spark-x-master 的 ApplicationHelper 相关方法）。
 *
 * 提供节点上游上下文查询 + 下游是否为 Answer 节点检测。
 */
@Component
public class WorkflowRuntimeHelper {

    @Resource
    private WorkflowRuntimeContextMapper runtimeContextMapper;

    /**
     * 获取运行时上下文节点。
     * <p>并行分支时按 inputSourceId 精确匹配上游；非并行直接返回唯一上游。
     *
     * @param runtimeId     运行时 id
     * @param sourceId      上游节点 cell
     * @param inputSourceId 当前节点配置的输入来源 cell（并行时区分用）
     */
    public WorkflowRuntimeContext getRuntimeContext(long runtimeId, String sourceId, String inputSourceId) {
        List<WorkflowRuntimeContext> context = runtimeContextMapper.selectList(
                new LambdaQueryWrapper<WorkflowRuntimeContext>()
                        .eq(WorkflowRuntimeContext::getRuntimeId, runtimeId)
                        .eq(WorkflowRuntimeContext::getCell, sourceId));
        if (context == null || context.isEmpty()) {
            return null;
        }
        // 只有一个上游直接返回
        if (context.size() == 1) {
            return context.get(0);
        }
        // 并行：优先匹配 inputSourceId
        for (WorkflowRuntimeContext item : context) {
            if (item.getCell().equals(inputSourceId)) {
                return item;
            }
        }
        // 兜底返回第一个（上下文输入里保存了全部上游输出）
        return context.get(0);
    }

    /**
     * 检测下个节点是否为回复节点以及回复节点的回复类型。
     */
    public NextAnswerNodeVo checkNextIsAnswerNode(NodeRuntimeVo runtimeVo) {
        NextAnswerNodeVo vo = new NextAnswerNodeVo();
        List<EdgeVo> nextEdgeList = runtimeVo.getEdges().get(runtimeVo.getNodeInfo().getId());
        if (nextEdgeList == null || nextEdgeList.isEmpty()) {
            vo.setNodeIsAnswer(false);
            vo.setAnswerType(2);
            return vo;
        }

        Map<String, NodeVo> nodes = runtimeVo.getNodes();
        boolean hasAnswer = false;
        int answerType = 2;
        NodeVo nowNode = runtimeVo.getNodeInfo();

        for (EdgeVo edge : nextEdgeList) {
            List<String> targetIds = edge.getTarget();
            for (String targetId : targetIds) {
                NodeVo target = nodes.get(targetId);
                if (target == null) {
                    continue;
                }
                if ("answer-node".equals(target.getShape())) {
                    // 检测下个节点的输入是否是当前节点
                    var inputArr = target.getData().getJSONArray("inputData");
                    if (inputArr != null && !inputArr.isEmpty()) {
                        String inputNodeId = inputArr.get(0).toString();
                        if (nowNode.getId().equals(inputNodeId)) {
                            answerType = 1;
                        }
                    }
                    hasAnswer = true;
                }
            }
        }

        vo.setNodeIsAnswer(hasAnswer);
        vo.setAnswerType(answerType);
        return vo;
    }

    // ----- 多输入 + 分区输出契约（Q3/Q4） -----
    //
    // inputData 形态：Array<{nodeId, field}>（变量引用列表）
    //   - 单输入节点（LLM/Dataset/Agent/Purpose）用 inputData[0]
    //   - 多输入节点（Answer、可聚合）用整个列表
    //
    // outputData 形态：
    //   - 全局变量 sys.question/sys.time/sys.sessionId/sys.workflowId/sys.ip 保持扁平
    //   - 节点产出（sys.content/sys.result/sys.purposeName/sys.agentContent/sys.answer/datasets.*/switch.result）
    //     按「来源 cell」分区写到 node.<cellId> 子对象里，避免并行分支同名输出互相覆盖
    //
    // 读写都走下面的工具方法，保证契约一致。

    /**
     * 取节点 inputData 列表（每项 {nodeId, field}）。异常/缺失返回空表。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> readInputList(JSONObject nodeData) {
        List<Map<String, String>> list = new ArrayList<>();
        if (nodeData == null) return list;
        Object raw = nodeData.get("inputData");
        if (!(raw instanceof JSONArray)) return list;
        JSONArray arr = (JSONArray) raw;
        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            // 兼容旧契约：扁平 [nodeId, field] 数组的元素是字符串
            if (item instanceof String s) {
                // 跳过，旧契约元素无法还原 nodeId/field
                continue;
            }
            JSONObject obj = (JSONObject) item;
            String nodeId = obj.getStr("nodeId");
            String field = obj.getStr("field");
            if (nodeId != null && field != null) {
                Map<String, String> m = new HashMap<>();
                m.put("nodeId", nodeId);
                m.put("field", field);
                list.add(m);
            }
        }
        return list;
    }

    /**
     * 按变量引用 {nodeId, field} 从上游 outputData 取值。
     * outputData 里每个来源节点的产出挂在 node.<cellId> 下。
     *
     * @param outputData 上游节点落库的 outputData（JSON 字符串）
     */
    public String readVar(String outputData, String nodeId, String field) {
        if (outputData == null || outputData.isBlank() || nodeId == null || field == null) {
            return "";
        }
        JSONObject obj = JSONUtil.parseObj(outputData);
        // 优先按分区取 node.<cellId>.<field>
        JSONObject partition = obj.getJSONObject("node." + nodeId);
        if (partition != null) {
            String v = partition.getStr(field);
            if (v != null) return v;
        }
        // 兼容旧契约：扁平字段（全局 sys.* 或旧版扁平 sys.content）
        String v = obj.getStr(field);
        return v == null ? "" : v;
    }

    /**
     * 把本节点的产出写进 outputData 的分区 node.<cell> 下。
     *
     * @param outputData 当前 outputData（JSON 字符串，可为空）
     * @param cell       本节点 X6 cell id
     * @param field      产出字段名（如 sys.content / sys.result / sys.answer）
     * @param value      产出值
     * @return 写入后的 outputData JSON 字符串
     */
    public String writeVar(String outputData, String cell, String field, Object value) {
        JSONObject obj = (outputData == null || outputData.isBlank())
                ? JSONUtil.createObj() : JSONUtil.parseObj(outputData);
        String key = "node." + cell;
        JSONObject partition = obj.getJSONObject(key);
        if (partition == null) {
            partition = JSONUtil.createObj();
        }
        partition.set(field, value);
        obj.set(key, partition);
        return obj.toString();
    }

    /**
     * 把节点耗时（costMs）及任意额外调试字段合并进 modelData（保留原有内容）。
     * <p>用于执行详情按节点展示耗时，不改表结构——costMs 寄生在 modelData 的 JSON 里。
     *
     * @param modelData 原 modelData（JSON 字符串，可为空）
     * @param costMs    节点耗时（毫秒）
     * @return 合并后的 modelData JSON 字符串
     */
    public String withCostMs(String modelData, long costMs) {
        JSONObject obj = (modelData == null || modelData.isBlank())
                ? JSONUtil.createObj() : JSONUtil.parseObj(modelData);
        obj.set("costMs", costMs);
        return obj.toString();
    }

    /**
     * 按 (runtimeId, cell) 落库上下文：存在则更新，不存在则插入。
     * <p>★ Bug C 修复：原各节点直接 {@code insert}，表无 (runtime_id, cell) 唯一约束时，
     * 同一节点（尤其汇合点 / 重复入边）会落多行，导致 {@link #getRuntimeContext} 兜底取首行串数据。
     * 配合迁移脚本加的唯一约束，这里用 upsert 保证「同一 runtime + cell 仅一行」。
     * <p>注意：step 字段不参与更新匹配（同一节点不同次执行的 step 可能不同），update 时保留 entity 里的值。
     *
     * @param entity 待落库的上下文（需有 runtimeId + cell）
     */
    public void upsertContext(WorkflowRuntimeContext entity) {
        if (entity == null || entity.getRuntimeId() == null || entity.getCell() == null) {
            return;
        }
        Long count = runtimeContextMapper.selectCount(
                new LambdaQueryWrapper<WorkflowRuntimeContext>()
                        .eq(WorkflowRuntimeContext::getRuntimeId, entity.getRuntimeId())
                        .eq(WorkflowRuntimeContext::getCell, entity.getCell()));
        if (count != null && count > 0) {
            runtimeContextMapper.update(entity,
                    new LambdaQueryWrapper<WorkflowRuntimeContext>()
                            .eq(WorkflowRuntimeContext::getRuntimeId, entity.getRuntimeId())
                            .eq(WorkflowRuntimeContext::getCell, entity.getCell()));
        } else {
            runtimeContextMapper.insert(entity);
        }
    }

    /**
     * 把一组调试字段合并进 modelData（保留原有内容，已有 key 会被覆盖）。
     *
     * @param modelData 原 modelData（JSON 字符串，可为空）
     * @param extra     待合并字段（key→value）
     * @return 合并后的 modelData JSON 字符串
     */
    public String mergeModelData(String modelData, Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return modelData;
        }
        JSONObject obj = (modelData == null || modelData.isBlank())
                ? JSONUtil.createObj() : JSONUtil.parseObj(modelData);
        for (Map.Entry<String, Object> e : extra.entrySet()) {
            obj.set(e.getKey(), e.getValue());
        }
        return obj.toString();
    }

    /**
     * 取所有上游产出（用于 Answer 节点多变量聚合渲染等）。
     *
     * @return cellId → 该节点产出的 JSONObject
     */
    public Map<String, JSONObject> readAllNodeOutputs(String outputData) {
        Map<String, JSONObject> result = new HashMap<>();
        if (outputData == null || outputData.isBlank()) return result;
        JSONObject obj = JSONUtil.parseObj(outputData);
        for (String key : obj.keySet()) {
            if (key.startsWith("node.")) {
                Object v = obj.get(key);
                if (v instanceof JSONObject jo) {
                    result.put(key.substring(5), jo);
                }
            }
        }
        return result;
    }
}
