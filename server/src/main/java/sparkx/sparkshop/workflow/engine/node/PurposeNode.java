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

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.workflow.engine.IWorkflowNode;
import sparkx.sparkshop.workflow.engine.WorkflowRuntimeHelper;
import sparkx.sparkshop.workflow.entity.WorkflowRuntimeContext;
import sparkx.sparkshop.workflow.enums.NodeTypeEnum;
import sparkx.sparkshop.workflow.mapper.WorkflowRuntimeContextMapper;
import sparkx.sparkshop.workflow.vo.EdgeVo;
import sparkx.sparkshop.workflow.vo.NodeRuntimeVo;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图分类节点。用配置的 chat 模型（非流式）对问题分类，
 * 返回编号索引，按 purpose-node 右侧桩 Y 轴顺序匹配下游分支。
 */
@Slf4j
@Component
public class PurposeNode implements IWorkflowNode {

    private static final Pattern INDEX_PATTERN = Pattern.compile("(\\d+)");

    @Autowired
    private LLMService llmService;

    @Autowired
    private WorkflowRuntimeContextMapper runtimeContextMapper;

    @Autowired
    private WorkflowRuntimeHelper runtimeHelper;

    @Override
    public List<EdgeVo> handle(NodeRuntimeVo runtimeVo) {
        JSONObject nodeObject = runtimeVo.getNodeInfo().getData();
        JSONObject modelInfo = nodeObject.getJSONObject("modelInfo");
        Integer modelId = parseModelId(modelInfo == null ? null : modelInfo.getStr("modelId"));
        String modelName = modelInfo == null ? null : modelInfo.getStr("modelName");
        double temperature = modelInfo != null && modelInfo.getDouble("temperature") != null
                ? modelInfo.getDouble("temperature") : 0.0;
        long startTime = System.currentTimeMillis();

        // 拼分类清单
        JSONArray cateList = nodeObject.getJSONArray("cateList");
        StringBuilder cateListStr = new StringBuilder();
        for (int i = 0; i < cateList.size(); i++) {
            cateListStr.append(i + 1).append(":")
                    .append(cateList.getJSONObject(i).getStr("name")).append("\n");
        }

        // 取首个输入变量 {nodeId, field}
        java.util.List<java.util.Map<String, String>> inputs = runtimeHelper.readInputList(nodeObject);
        String inputSourceId = inputs.isEmpty() ? "" : inputs.get(0).get("nodeId");
        String inputField = inputs.isEmpty() ? "sys.question" : inputs.get(0).get("field");
        WorkflowRuntimeContext context = runtimeHelper.getRuntimeContext(
                runtimeVo.getRuntimeId(), runtimeVo.getSourceId(), inputSourceId);
        if (context == null) {
            return null;
        }

        // 取问题值（按分区读取，兼容旧扁平字段）
        String question = runtimeHelper.readVar(context.getOutputData(), inputSourceId, inputField);
        if (question == null || question.isEmpty()) {
            question = runtimeHelper.readVar(context.getOutputData(), inputSourceId, "sys.question");
        }

        String prompt = "已知问题分类：\n" + cateListStr
                + "\n请根据问题：" + question
                + "。\n判断出所属的分类并仅给出问题前的编号,例如：1";

        // 非流式分类
        String answer;
        try {
            answer = llmService.chat(LlmChatRequest.ofUser(prompt, temperature), modelId, modelName);
        } catch (Exception e) {
            log.error("[PurposeNode] 分类调用失败: {}", e.getMessage(), e);
            throw new BusinessException("意图分类节点调用模型失败");
        }
        int index = parseIndex(answer, cateList.size());

        // 调试：把分类过程（prompt / 原始回复 / 命中索引+名称 / 耗时）写进 modelData，
        // 让执行详情能展示「为什么分到这个类」
        long costMs = System.currentTimeMillis() - startTime;
        java.util.Map<String, Object> debugExtra = new java.util.HashMap<>();
        debugExtra.put("prompt", prompt);
        debugExtra.put("rawAnswer", answer);
        debugExtra.put("hitIndex", index);
        debugExtra.put("hitName", cateList.getJSONObject(index).getStr("name"));
        debugExtra.put("costMs", costMs);
        String modelDataWithDebug = runtimeHelper.mergeModelData(nodeObject.toString(), debugExtra);

        // 落库：sys.purposeName 写入本节点分区
        WorkflowRuntimeContext contextEntity = new WorkflowRuntimeContext();
        contextEntity.setStep(context.getStep() + 1);
        contextEntity.setNodeType(NodeTypeEnum.PURPOSE.getCode());
        contextEntity.setRuntimeId(runtimeVo.getRuntimeId());
        contextEntity.setOutputData(runtimeHelper.writeVar(context.getOutputData(),
                runtimeVo.getNodeInfo().getId(), "sys.purposeName",
                cateList.getJSONObject(index).getStr("name")));
        contextEntity.setModelData(modelDataWithDebug);
        contextEntity.setCell(runtimeVo.getNodeInfo().getId());
        contextEntity.setCreatedAt(LocalDateTime.now());
        runtimeHelper.upsertContext(contextEntity);

        // 按右侧桩顺序匹配分支
        List<EdgeVo> nextEdgeList = runtimeVo.getEdges().get(runtimeVo.getNodeInfo().getId());
        JSONArray targetList = nodeObject.getJSONArray("targetList");
        String targetId = String.valueOf(targetList.get(index));
        EdgeVo next = nextEdgeList.stream()
                .filter(e -> e.getSourcePort().equals(targetId))
                .findFirst()
                .orElse(new EdgeVo());

        List<EdgeVo> ret = new LinkedList<>();
        ret.add(next);
        runtimeVo.getLatch().countDown();
        return ret;
    }

    /** 解析模型 id（前端存的可能是字符串） */
    private Integer parseModelId(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从模型回复里取编号，越界兜底取第一个 */
    private int parseIndex(String answer, int size) {
        if (answer == null) {
            return 0;
        }
        Matcher m = INDEX_PATTERN.matcher(answer);
        if (m.find()) {
            try {
                int idx = Integer.parseInt(m.group(1)) - 1;
                if (idx >= 0 && idx < size) {
                    return idx;
                }
            } catch (NumberFormatException ignore) {
            }
        }
        return 0;
    }
}
