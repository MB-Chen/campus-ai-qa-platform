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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

/**
 * 回复节点。把内容推给前端（answer 事件），并落库 sys.answer。
 * <ul>
 *   <li>answerType=1：引用上游节点输出变量（支持多上游，模板 answerTemplate 用 {{var}} 聚合）</li>
 *   <li>answerType=2：静态文本</li>
 * </ul>
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

    @Setter
    public SseEmitter emitter;

    @Setter
    public CountDownLatch latch;

    @Override
    public List<EdgeVo> handle(NodeRuntimeVo runtimeVo) {
        try {
            JSONObject nodeObject = runtimeVo.getNodeInfo().getData();
            Integer answerType = nodeObject.getInt("answerType");

            // 幂等保护：Answer 处于汇合点时会被多个入边各调一次，只让首次调用真正执行。
            // 通过本节点 cell 是否已有落库行判断。
            Long existed = runtimeContextMapper.selectCount(
                    new LambdaQueryWrapper<WorkflowRuntimeContext>()
                            .eq(WorkflowRuntimeContext::getRuntimeId, runtimeVo.getRuntimeId())
                            .eq(WorkflowRuntimeContext::getCell, runtimeVo.getNodeInfo().getId()));
            if (existed != null && existed > 0) {
                latch.countDown();
                return runtimeVo.getEdges().get(runtimeVo.getNodeInfo().getId());
            }
            // 多输入变量列表（Q3/Q4：Answer 可引用多个上游输出）
            java.util.List<java.util.Map<String, String>> inputs = runtimeHelper.readInputList(nodeObject);
            String firstSourceId = inputs.isEmpty() ? "" : inputs.get(0).get("nodeId");

            // Answer 可能有多个上游（如 Switch 后 LLM1+LLM2），每个上游各调一次 handle。
            // 为了聚合所有上游输出，合并各引用变量对应上游上下文的 outputData。
            String mergedOutput = mergeUpstreamOutputs(runtimeVo, inputs);

            WorkflowRuntimeContext context = runtimeHelper.getRuntimeContext(
                    runtimeVo.getRuntimeId(), runtimeVo.getSourceId(), firstSourceId);
            if (context == null) {
                return null;
            }

            WorkflowRuntimeContext contextEntity = new WorkflowRuntimeContext();
            contextEntity.setStep(context.getStep() + 1);
            contextEntity.setNodeType(NodeTypeEnum.ANSWER.getCode());
            contextEntity.setRuntimeId(runtimeVo.getRuntimeId());

            String answer;
            if (answerType != null && answerType == 1) {
                // 引用变量
                String template = nodeObject.getStr("answerTemplate");
                if (template != null && !template.isBlank()) {
                    // 模板模式：{{n}} 取第 n 个引用变量值（按 inputData 顺序，区分同字段不同节点）；
                    //          {{field}} 跨分区解析（取首个命中）
                    answer = renderTemplate(template, mergedOutput, inputs);
                } else if (inputs.size() > 1) {
                    // 多变量无模板：按顺序拼接（每个变量值各占一段，便于追溯）
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
                if (!firstSourceId.equals(context.getCell())) {
                    sseHelper.sendAnswerChunk(emitter, runtimeVo.getRuntimeId(),
                            runtimeVo.getNodeInfo().getId(), answer);
                }
            } else {
                // 静态文本
                answer = nodeObject.getStr("answer") == null ? "" : nodeObject.getStr("answer");
                sseHelper.sendAnswerChunk(emitter, runtimeVo.getRuntimeId(),
                        runtimeVo.getNodeInfo().getId(), answer);
            }

            // 落库：sys.answer 写入本节点分区（基于合并后的上游 outputData）
            contextEntity.setOutputData(runtimeHelper.writeVar(mergedOutput,
                    runtimeVo.getNodeInfo().getId(), "sys.answer", answer));
            contextEntity.setCell(runtimeVo.getNodeInfo().getId());
            contextEntity.setCreatedAt(LocalDateTime.now());
            runtimeContextMapper.insert(contextEntity);
        } catch (Exception e) {
            log.error("[AnswerNode] 回复节点异常: {}", e.getMessage(), e);
            sseHelper.sendError(emitter, "回复节点异常：" + e.getMessage());
        }

        latch.countDown();
        return runtimeVo.getEdges().get(runtimeVo.getNodeInfo().getId());
    }

    /**
     * 合并所有引用变量对应上游上下文的 outputData。
     * <p>Answer 处于汇合点时（如 Switch 后 LLM1+LLM2），每个上游 context 只含自己分支的分区输出，
     * 必须把各上游 outputData 的 node.* 分区合并到一起，才能在模板/拼接时取到全部上游产出。
     *
     * @param runtimeVo 节点运行时
     * @param inputs    本节点引用的输入变量列表
     * @return 合并后的 outputData JSON 字符串
     */
    private String mergeUpstreamOutputs(NodeRuntimeVo runtimeVo,
                                        java.util.List<java.util.Map<String, String>> inputs) {
        JSONObject merged = JSONUtil.createObj();
        // 先取当前 sourceId 上下文（含全局 sys.* + 该上游分区）
        WorkflowRuntimeContext ctx = runtimeHelper.getRuntimeContext(
                runtimeVo.getRuntimeId(), runtimeVo.getSourceId(), "");
        if (ctx != null && ctx.getOutputData() != null) {
            merged = JSONUtil.parseObj(ctx.getOutputData());
        }
        // 再把其它引用的上游（非当前 sourceId）分区补进来
        for (java.util.Map<String, String> in : inputs) {
            String nodeId = in.get("nodeId");
            if (nodeId == null || nodeId.equals(runtimeVo.getSourceId())) {
                continue;
            }
            WorkflowRuntimeContext up = runtimeHelper.getRuntimeContext(
                    runtimeVo.getRuntimeId(), nodeId, nodeId);
            if (up == null || up.getOutputData() == null) continue;
            JSONObject upObj = JSONUtil.parseObj(up.getOutputData());
            JSONObject upPartition = upObj.getJSONObject("node." + nodeId);
            if (upPartition != null) {
                merged.set("node." + nodeId, upPartition);
            }
        }
        return merged.toString();
    }

    /**
     * 渲染回复模板：
     * <ul>
     *   <li>{{n}}（纯数字）：取第 n 个引用变量值（按 inputData 顺序，区分同字段不同节点）</li>
     *   <li>{{field}}：跨上游分区解析（取首个命中）</li>
     * </ul>
     *
     * @param outputData 上游 outputData JSON
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
