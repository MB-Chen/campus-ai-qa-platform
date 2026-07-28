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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.agent.AgentChatService;
import sparkx.sparkshop.knowledge.entity.KnowledgeAgent;
import sparkx.sparkshop.knowledge.service.IKnowledgeAgentService;
import sparkx.sparkshop.workflow.engine.IWorkflowNode;
import sparkx.sparkshop.workflow.engine.WorkflowRuntimeHelper;
import sparkx.sparkshop.workflow.engine.WorkflowSseHelper;
import sparkx.sparkshop.workflow.entity.WorkflowRuntimeContext;
import sparkx.sparkshop.workflow.enums.NodeTypeEnum;
import sparkx.sparkshop.workflow.mapper.WorkflowRuntimeContextMapper;
import sparkx.sparkshop.workflow.vo.EdgeVo;
import sparkx.sparkshop.workflow.vo.NextAnswerNodeVo;
import sparkx.sparkshop.workflow.vo.NodeRuntimeVo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体节点。委托给 spark-x 的 {@link AgentChatService#chatSync} 跑完整 RAG 链路，
 * 同步取结果后推前端（当下游是 Answer 且引用本节点输出时）。
 */
@Slf4j
@Component
public class AgentNode implements IWorkflowNode {

    @Autowired
    private IKnowledgeAgentService agentService;

    @Autowired
    private AgentChatService agentChatService;

    @Autowired
    private WorkflowRuntimeContextMapper runtimeContextMapper;

    @Autowired
    private WorkflowRuntimeHelper runtimeHelper;

    @Autowired
    private WorkflowSseHelper sseHelper;

    @Override
    public List<EdgeVo> handle(NodeRuntimeVo runtimeVo) {
        JSONObject nodeObject = runtimeVo.getNodeInfo().getData();
        long startTime = System.currentTimeMillis();
        // 取首个输入变量 {nodeId, field}
        java.util.List<java.util.Map<String, String>> inputs = runtimeHelper.readInputList(nodeObject);
        String inputSourceId = inputs.isEmpty() ? "" : inputs.get(0).get("nodeId");
        String inputField = inputs.isEmpty() ? "sys.question" : inputs.get(0).get("field");

        WorkflowRuntimeContext context = runtimeHelper.getRuntimeContext(
                runtimeVo.getRuntimeId(), runtimeVo.getSourceId(), inputSourceId);
        if (context == null) {
            return null;
        }

        String question = runtimeHelper.readVar(context.getOutputData(), inputSourceId, inputField);
        if (question == null || question.isEmpty()) {
            question = runtimeHelper.readVar(context.getOutputData(), inputSourceId, "sys.question");
        }
        String agentId = nodeObject.getStr("agentId");

        KnowledgeAgent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new BusinessException("智能体不存在: " + agentId);
        }

        // 落库本节点（先继承上游 outputData，产出稍后写入分区）
        WorkflowRuntimeContext contextEntity = new WorkflowRuntimeContext();
        contextEntity.setStep(context.getStep() + 1);
        contextEntity.setNodeType(NodeTypeEnum.AGENT.getCode());
        contextEntity.setRuntimeId(runtimeVo.getRuntimeId());
        contextEntity.setOutputData(context.getOutputData());
        contextEntity.setCell(runtimeVo.getNodeInfo().getId());
        contextEntity.setCreatedAt(LocalDateTime.now());
        runtimeHelper.upsertContext(contextEntity);

        // 同步跑智能体（复用 RAG 全链路：检索/记忆/兜底）
        AgentChatService.ChatResult result;
        try {
            result = agentChatService.chatSync(agent, question);
        } catch (Exception e) {
            log.error("[AgentNode] 智能体调用失败: {}", e.getMessage(), e);
            throw new BusinessException("智能体调用失败：" + e.getMessage());
        }

        String answer = result != null && result.answer != null ? result.answer : "";

        // 更新输出：sys.agentContent 写入本节点分区
        String cell = runtimeVo.getNodeInfo().getId();
        String updated = runtimeHelper.writeVar(contextEntity.getOutputData(), cell,
                "agent.input", question);
        updated = runtimeHelper.writeVar(updated, cell, "sys.agentContent", answer);
        contextEntity.setOutputData(updated);
        // 调试：节点耗时写进 modelData
        long costMs = System.currentTimeMillis() - startTime;
        contextEntity.setModelData(runtimeHelper.withCostMs(contextEntity.getModelData(), costMs));
        runtimeContextMapper.updateById(contextEntity);

        // 下游是 Answer 且引用本节点输出 → 推前端
        NextAnswerNodeVo next = runtimeHelper.checkNextIsAnswerNode(runtimeVo);
        if (next.isNodeIsAnswer() && next.getAnswerType() == 1) {
            sseHelper.sendAnswerChunk(runtimeVo.getEmitter(), runtimeVo.getRuntimeId(),
                    runtimeVo.getNodeInfo().getId(), answer);
        }

        runtimeVo.getLatch().countDown();
        return runtimeVo.getEdges().get(runtimeVo.getNodeInfo().getId());
    }
}
