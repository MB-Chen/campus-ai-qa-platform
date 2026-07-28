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
import cn.hutool.json.JSONUtil;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.entity.KgConfig;
import sparkx.sparkshop.knowledge.entity.KnowledgeDocument;
import sparkx.sparkshop.knowledge.graph.KnowledgeGraphChannel;
import sparkx.sparkshop.knowledge.mapper.KgConfigMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeDocumentMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识图谱检索节点。绑定某个知识库下的若干文档，对该文档的知识图谱做检索，
 * 召回关联文本片段写入 sys.result，供下游 LLM 节点作为上下文。
 *
 * <p>检索能力复用 {@link KnowledgeGraphChannel#retrieveForDocument}（向量召回实体 → 子图扩展 → 取关联 chunk 原文），
 * 天然按文档维度隔离。与知识检索（DatasetNode）节点并列，二者都连到同一 LLM 节点时，
 * 由 LLM 节点做 RRF 多源融合（见 {@code LlmNode}）。
 *
 * <p>双闸校验：全局 kg_config.enabled 必须为 1，且被绑定的文档 kg_enabled 必须为 1，否则跳过该文档检索。
 */
@Slf4j
@Component
public class GraphNode implements IWorkflowNode {

    @Autowired
    private KnowledgeGraphChannel knowledgeGraphChannel;

    @Autowired
    private KgConfigMapper kgConfigMapper;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

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
        List<Map<String, String>> inputs = runtimeHelper.readInputList(nodeObject);
        String inputSourceId = inputs.isEmpty() ? "" : inputs.get(0).get("nodeId");
        String inputField = inputs.isEmpty() ? "sys.question" : inputs.get(0).get("field");

        WorkflowRuntimeContext context = runtimeHelper.getRuntimeContext(
                runtimeVo.getRuntimeId(), runtimeVo.getSourceId(), inputSourceId);
        if (context == null) {
            return null;
        }

        // 取检索问题
        String question = runtimeHelper.readVar(context.getOutputData(), inputSourceId, inputField);
        if (question == null || question.isEmpty()) {
            question = runtimeHelper.readVar(context.getOutputData(), inputSourceId, "sys.question");
        }

        // 节点参数：绑定知识库 + 文档列表
        String kbId = nodeObject.getStr("kbId");
        JSONArray docIdsArr = nodeObject.getJSONArray("docIds");
        List<String> docIds = new ArrayList<>();
        if (docIdsArr != null) {
            docIds = docIdsArr.toList(String.class).stream()
                    .filter(d -> d != null && !d.isBlank())
                    .collect(Collectors.toList());
        }
        Integer topRank = nodeObject.getInt("topRank");
        if (topRank == null || topRank < 1) {
            topRank = 5;
        }

        // 记录上下文（继承上游 outputData，本节点产出稍后写入分区）
        WorkflowRuntimeContext contextEntity = new WorkflowRuntimeContext();
        contextEntity.setStep(context.getStep() + 1);
        contextEntity.setNodeType(NodeTypeEnum.GRAPH.getCode());
        contextEntity.setRuntimeId(runtimeVo.getRuntimeId());
        contextEntity.setOutputData(context.getOutputData());
        contextEntity.setModelData(nodeObject.toString());
        contextEntity.setCell(runtimeVo.getNodeInfo().getId());
        contextEntity.setCreatedAt(LocalDateTime.now());
        runtimeHelper.upsertContext(contextEntity);

        // 双闸校验：全局开关
        KgConfig globalConfig = kgConfigMapper.selectById(1);
        boolean globalEnabled = globalConfig != null && globalConfig.getEnabled() != null
                && globalConfig.getEnabled() == 1;

        // 检索：逐文档调用图谱通道（文档级隔离），同时收集被开关跳过的文档
        List<Content> hits = new ArrayList<>();
        List<String> skippedDocs = new ArrayList<>();
        if (globalEnabled && kbId != null && !kbId.isBlank() && !docIds.isEmpty()) {
            Query query = Query.from(question);
            for (String docId : docIds) {
                // 文档级开关校验
                KnowledgeDocument doc = knowledgeDocumentMapper.selectById(docId);
                if (doc == null || doc.getKgEnabled() == null || doc.getKgEnabled() != 1) {
                    log.debug("[GraphNode] 文档 {} 未开启知识图谱，跳过", docId);
                    skippedDocs.add(docId);
                    continue;
                }
                try {
                    List<Content> part = knowledgeGraphChannel.retrieveForDocument(kbId, docId, question);
                    if (part != null) {
                        hits.addAll(part);
                    }
                } catch (Exception e) {
                    log.warn("[GraphNode] 文档 {} 图谱检索失败: {}", docId, e.getMessage());
                }
            }
        } else if (!globalEnabled) {
            log.warn("[GraphNode] 知识图谱全局开关未开启(kg_config.enabled!=1)，跳过检索 runtimeId={}", runtimeVo.getRuntimeId());
        }

        // 按图通道返回顺序（已在通道内按 score 排序）截断 topRank
        List<String> passageList = hits.stream().map(c -> c.textSegment().text()).collect(Collectors.toList());
        List<String> finalPassages = passageList;
        if (passageList.size() > topRank) {
            finalPassages = new ArrayList<>(passageList.subList(0, topRank));
        }

        String result = String.join("\n", finalPassages);

        // 记录双闸校验结果，避免「图谱静默返回空」让调试误判检索失效
        String skipReason = "";
        if (!globalEnabled) {
            skipReason = "知识图谱全局开关未开启(kg_config.enabled!=1)，本节点未做检索，不参与 LLM 融合";
        } else if (!skippedDocs.isEmpty()) {
            skipReason = "以下文档未开启知识图谱(kg_enabled!=1)，已跳过检索：" + String.join(", ", skippedDocs);
            if (hits.isEmpty()) {
                skipReason += "；当前无有效召回，不参与 LLM 融合";
            }
        }

        // 更新上下文：召回信息写入本节点分区 node.<cell>
        String cell = runtimeVo.getNodeInfo().getId();
        String updated = runtimeHelper.writeVar(contextEntity.getOutputData(), cell,
                "sys.result", result);
        JSONArray fragmentsArr = JSONUtil.createArray();
        for (String p : finalPassages) {
            JSONObject frag = JSONUtil.createObj();
            frag.set("text", p);
            fragmentsArr.add(frag);
        }
        updated = runtimeHelper.writeVar(updated, cell, "graph.fragments", fragmentsArr);
        updated = runtimeHelper.writeVar(updated, cell, "graph.count", finalPassages.size());
        updated = runtimeHelper.writeVar(updated, cell, "graph.kbId", kbId == null ? "" : kbId);
        updated = runtimeHelper.writeVar(updated, cell, "graph.question", question);
        // 双闸校验诊断字段：执行详情可直接看到图谱为何返回空
        updated = runtimeHelper.writeVar(updated, cell, "graph.gateGlobalEnabled", globalEnabled);
        JSONArray skippedArr = JSONUtil.createArray();
        for (String d : skippedDocs) {
            skippedArr.add(d);
        }
        updated = runtimeHelper.writeVar(updated, cell, "graph.gateSkippedDocs", skippedArr);
        updated = runtimeHelper.writeVar(updated, cell, "graph.skipReason", skipReason);
        contextEntity.setOutputData(updated);

        long costMs = System.currentTimeMillis() - startTime;
        contextEntity.setModelData(runtimeHelper.withCostMs(contextEntity.getModelData(), costMs));
        runtimeContextMapper.updateById(contextEntity);

        // 若下一节点是 Answer 且引用本节点输出，则把检索结果推给前端
        NextAnswerNodeVo next = runtimeHelper.checkNextIsAnswerNode(runtimeVo);
        if (next.isNodeIsAnswer() && next.getAnswerType() == 1) {
            sseHelper.sendAnswerChunk(runtimeVo.getEmitter(), runtimeVo.getRuntimeId(),
                    runtimeVo.getNodeInfo().getId(), result);
        }

        runtimeVo.getLatch().countDown();
        return runtimeVo.getEdges().get(runtimeVo.getNodeInfo().getId());
    }
}
