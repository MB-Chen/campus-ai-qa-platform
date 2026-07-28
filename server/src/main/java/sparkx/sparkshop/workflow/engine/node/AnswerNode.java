// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.workflow.engine.node;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.workflow.engine.AnswerMergeState;
import sparkx.sparkshop.workflow.engine.IWorkflowNode;
import sparkx.sparkshop.workflow.engine.WorkflowRuntimeHelper;
import sparkx.sparkshop.workflow.engine.WorkflowSseHelper;
import sparkx.sparkshop.workflow.entity.WorkflowRuntimeContext;
import sparkx.sparkshop.workflow.enums.NodeTypeEnum;
import sparkx.sparkshop.workflow.mapper.WorkflowRuntimeContextMapper;
import sparkx.sparkshop.workflow.vo.EdgeVo;
import sparkx.sparkshop.workflow.vo.NodeRuntimeVo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 回复节点。把内容推给前端（answer 事件），并落库 sys.answer。
 * <ul>
 *   <li>answerType=1：引用上游节点输出变量（支持多上游，模板 answerTemplate 用 {{var}} 聚合）</li>
 *   <li>answerType=2：静态文本</li>
 * </ul>
 * <p>
 * <b>汇合点延迟执行（Bug B 修复）</b>：Answer 作为多条入边的汇聚点时，{@code FlowNodeParser.execute}
 * 会在不同递归层各调用一次 handle（每条入边一次）。本节点用 {@link AnswerMergeState} 累积上游到达，
 * 只有当「已到达上游数 == 入边总数」时才真正生成回复；之前的调用只做合并累加。
 * <p>
 * 若上游节点已经流推过（如 LLM 直连 Answer 且引用其输出），则不再重复推。
 */
@Slf4j
@Component
public class AnswerNode implements IWorkflowNode {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(.*?)\\}\\}");

    @Autowired
    private WorkflowSseHelper sseHelper;

    @Autowired
    private WorkflowRuntimeContextMapper runtimeContextMapper;

    @Autowired
    private WorkflowRuntimeHelper runtimeHelper;

    @Override
    public List<EdgeVo> handle(NodeRuntimeVo runtimeVo) {
        long startTime = System.currentTimeMillis();
        try {
            JSONObject nodeObject = runtimeVo.getNodeInfo().getData();
            Integer answerType = nodeObject.getInt("answerType");

            // ====== Bug B 修复：入边计数 + 延迟执行 ======
            // Answer 处于汇合点时会被多个上游入边各调一次。每次调用先把当前上游产出合并进 mergeState，
            // 然后判断是否所有上游都已到达；未到齐则只累加后返回，不生成回复/不落库/不推 SSE。
            int inDegree = runtimeVo.getInDegree();
            AnswerMergeState mergeState = runtimeVo.getMergeState();

            // 取本次上游（sourceId）的上下文 outputData，合并进 mergeState
            WorkflowRuntimeContext sourceCtx = runtimeHelper.getRuntimeContext(
                    runtimeVo.getRuntimeId(), runtimeVo.getSourceId(),
                    runtimeVo.getSourceId());
            String sourceOutput = sourceCtx != null ? sourceCtx.getOutputData() : null;
            boolean newSource = mergeState.arrive(runtimeVo.getSourceId(), sourceOutput);

            // 已到达不同上游数（去重后）。未达入边总数 → 仅累加，放行本层 latch 后返回。
            int arrived = mergeState.arrivedCount();
            if (inDegree > 0 && arrived < inDegree) {
                runtimeVo.getLatch().countDown();
                return runtimeVo.getEdges().get(runtimeVo.getNodeInfo().getId());
            }

            // ====== 所有上游已到齐（或单入边），真正生成回复 ======
            // 多输入变量列表（Answer 可引用多个上游输出）
            java.util.List<java.util.Map<String, String>> inputs = runtimeHelper.readInputList(nodeObject);
            // 合并后的 outputData（含所有上游分区 + 全局 sys.*）
            String mergedOutput = mergeState.getMergedOutput();

            // 落库行（汇合点只 insert 一次——这里已经是最后一次调用）
            WorkflowRuntimeContext contextEntity = new WorkflowRuntimeContext();
            // step 取首个上游的 step +1（上游都已落库，取任一即可）
            if (sourceCtx != null) {
                contextEntity.setStep(sourceCtx.getStep() + 1);
            } else {
                contextEntity.setStep(1);
            }
            contextEntity.setNodeType(NodeTypeEnum.ANSWER.getCode());
            contextEntity.setRuntimeId(runtimeVo.getRuntimeId());

            String answer;
            if (answerType != null && answerType == 1) {
                // 引用变量
                String template = nodeObject.getStr("answerTemplate");
                if (template != null && !template.isBlank()) {
                    // 模板模式：{{n}} 取第 n 个引用变量值；{{field}} 跨分区解析（取首个命中）
                    answer = renderTemplate(template, mergedOutput, inputs);
                } else if (inputs.size() > 1) {
                    // 多变量无模板：按顺序拼接
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < inputs.size(); i++) {
                        String v = runtimeHelper.readVar(mergedOutput,
                                inputs.get(i).get("nodeId"), inputs.get(i).get("field"));
                        if (sb.length() > 0) sb.append("\n\n");
                        sb.append(v == null ? "" : v);
                    }
                    answer = sb.toString();
                } else if (!inputs.isEmpty()) {
                    // 单变量引用：取首个引用的值
                    answer = runtimeHelper.readVar(mergedOutput,
                            inputs.get(0).get("nodeId"), inputs.get(0).get("field"));
                } else {
                    answer = "";
                }
                if (answer == null) answer = "";

                // 仅当首个引用的上游不是「已经流推过的那个 cell」时才补推
                // （LLM 节点 needStream 时已把内容流推给前端，避免重复）
                String firstSourceId = inputs.isEmpty() ? "" : inputs.get(0).get("nodeId");
                if (!firstSourceId.equals(sourceCtx != null ? sourceCtx.getCell() : null)) {
                    sseHelper.sendAnswerChunk(runtimeVo.getEmitter(), runtimeVo.getRuntimeId(),
                            runtimeVo.getNodeInfo().getId(), answer);
                }
            } else {
                // 静态文本
                answer = nodeObject.getStr("answer") == null ? "" : nodeObject.getStr("answer");
                sseHelper.sendAnswerChunk(runtimeVo.getEmitter(), runtimeVo.getRuntimeId(),
                        runtimeVo.getNodeInfo().getId(), answer);
            }

            // 落库：sys.answer 写入本节点分区（基于合并后的上游 outputData）
            contextEntity.setOutputData(runtimeHelper.writeVar(mergedOutput,
                    runtimeVo.getNodeInfo().getId(), "sys.answer", answer));
            contextEntity.setCell(runtimeVo.getNodeInfo().getId());
            // 调试：回复类型 + 耗时写进 modelData
            long costMs = System.currentTimeMillis() - startTime;
            java.util.Map<String, Object> answerDebug = new java.util.HashMap<>();
            answerDebug.put("answerType", answerType == null ? 0 : answerType);
            answerDebug.put("answerTypeLabel", answerType != null && answerType == 1 ? "引用变量" : "静态文本");
            answerDebug.put("costMs", costMs);
            answerDebug.put("inDegree", inDegree);
            contextEntity.setModelData(
                    runtimeHelper.mergeModelData(nodeObject.toString(), answerDebug));
            contextEntity.setCreatedAt(LocalDateTime.now());
            // ★ Bug C 配合：用 upsert 防止汇合点极端时序下重复落库
            runtimeHelper.upsertContext(contextEntity);
        } catch (Exception e) {
            log.error("[AnswerNode] 回复节点异常: {}", e.getMessage(), e);
            sseHelper.sendError(runtimeVo.getEmitter(), "回复节点异常：" + e.getMessage());
        }

        runtimeVo.getLatch().countDown();
        return runtimeVo.getEdges().get(runtimeVo.getNodeInfo().getId());
    }

    /**
     * 渲染回复模板：
     * <ul>
     *   <li>{{n}}（纯数字）：取第 n 个引用变量值（按 inputData 顺序，区分同字段不同节点）</li>
     *   <li>{{field}}：跨上游分区解析（取首个命中）</li>
     * </ul>
     *
     * @param outputData 合并后的上游 outputData JSON
     * @param inputs     本节点引用的输入变量列表
     */
    private String renderTemplate(String template, String outputData,
                                  java.util.List<java.util.Map<String, String>> inputs) {
        JSONObject obj = (outputData == null || outputData.isBlank())
                ? JSONUtil.createObj() : JSONUtil.parseObj(outputData);
        java.util.regex.Matcher m = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String token = m.group(1);
            String val;
            if (token.matches("\\d+")) {
                // {{n}}：第 n 个引用变量
                int idx = Integer.parseInt(token) - 1;
                if (idx >= 0 && idx < inputs.size()) {
                    val = runtimeHelper.readVar(outputData,
                            inputs.get(idx).get("nodeId"), inputs.get(idx).get("field"));
                } else {
                    val = "";
                }
            } else {
                // {{field}}：跨分区解析
                val = resolveVar(obj, token);
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(val == null ? "" : val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 跨分区解析变量 */
    private String resolveVar(JSONObject obj, String field) {
        Object v = obj.get(field);
        if (v != null) return v.toString();
        for (String key : obj.keySet()) {
            if (key.startsWith("node.")) {
                Object part = obj.get(key);
                if (part instanceof JSONObject jo) {
                    Object pv = jo.get(field);
                    if (pv != null) return pv.toString();
                }
            }
        }
        return "";
    }
}
