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
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.retrieval.HybridContentRetriever;
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
import java.util.stream.IntStream;

/**
 * 知识库检索节点。对配置的知识库调 {@link HybridContentRetriever} 检索，
 * 可选 rerank（配置 rerankModelId 时调 {@link LLMService#rerank}）。
 * 召回内容写入 sys.result，供下游 LLM 节点作为上下文。
 */
@Slf4j
@Component
public class DatasetNode implements IWorkflowNode {

    @Autowired
    private HybridContentRetriever retriever;

    @Autowired
    private LLMService llmService;

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

        // 取检索问题（按分区读取，兼容旧扁平字段）
        String question = runtimeHelper.readVar(context.getOutputData(), inputSourceId, inputField);
        if (question == null || question.isEmpty()) {
            question = runtimeHelper.readVar(context.getOutputData(), inputSourceId, "sys.question");
        }

        // 知识库列表：每个库可单独限定文档（docIds 为空则检索整库）
        JSONArray datasetsArr = nodeObject.getJSONArray("datasets");
        List<KbScope> kbScopes = new ArrayList<>();
        if (datasetsArr != null) {
            for (int i = 0; i < datasetsArr.size(); i++) {
                JSONObject ds = datasetsArr.getJSONObject(i);
                String kbId = ds.getStr("datasetId");
                if (kbId == null || kbId.isBlank()) {
                    continue;
                }
                List<String> docIds = null;
                JSONArray docIdsArr = ds.getJSONArray("docIds");
                if (docIdsArr != null) {
                    docIds = docIdsArr.toList(String.class);
                }
                // 过滤空串
                if (docIds != null) {
                    docIds = docIds.stream()
                            .filter(d -> d != null && !d.isBlank())
                            .collect(Collectors.toList());
                }
                kbScopes.add(new KbScope(kbId, docIds));
            }
        }

        // 节点参数
        Integer topRank = nodeObject.getInt("topRank");
        if (topRank == null) {
            topRank = 3;
        }
        Double similarity = nodeObject.getDouble("similarity");
        String rerankModelId = nodeObject.getStr("rerankModelId");
        Integer rerankModelIdInt = parseModelId(rerankModelId);

        // 记录上下文（step 自增）——继承上游 outputData，本节点产出稍后写入分区
        WorkflowRuntimeContext contextEntity = new WorkflowRuntimeContext();
        contextEntity.setStep(context.getStep() + 1);
        contextEntity.setNodeType(NodeTypeEnum.DATASET.getCode());
        contextEntity.setRuntimeId(runtimeVo.getRuntimeId());
        contextEntity.setOutputData(context.getOutputData());
        contextEntity.setModelData(nodeObject.toString());
        contextEntity.setCell(runtimeVo.getNodeInfo().getId());
        contextEntity.setCreatedAt(LocalDateTime.now());
        runtimeHelper.upsertContext(contextEntity);

        // 检索：按库各自用自己的向量模型 + 文档范围（避免跨库向量模型不一致）
        List<Content> hits = new ArrayList<>();
        if (!kbScopes.isEmpty()) {
            Query query = Query.from(question);
            for (KbScope scope : kbScopes) {
                try {
                    List<Content> part = retriever.retrieve(query, scope.kbId, scope.docIds,
                            topRank, similarity, null);
                    if (part != null) {
                        hits.addAll(part);
                    }
                } catch (Exception e) {
                    log.warn("[DatasetNode] 知识库 {} 检索失败: {}", scope.kbId, e.getMessage());
                }
            }
        }

        // 可选 rerank
        List<String> passageList = hits.stream().map(c -> c.textSegment().text()).collect(Collectors.toList());
        List<String> finalPassages = passageList;
        boolean reranked = false;
        if (rerankModelIdInt != null && passageList.size() > 1) {
            try {
                List<Float> scores = llmService.rerank(question, passageList);
                if (scores != null && scores.size() == passageList.size()) {
                    // 按分数降序取 topRank
                    finalPassages = IntStream.range(0, passageList.size())
                            .mapToObj(i -> new java.util.AbstractMap.SimpleEntry<>(passageList.get(i), scores.get(i)))
                            .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                            .limit(topRank)
                            .map(java.util.AbstractMap.SimpleEntry::getKey)
                            .collect(Collectors.toList());
                    reranked = true;
                }
            } catch (Exception e) {
                log.warn("[DatasetNode] rerank 失败，回退原序: {}", e.getMessage());
            }
        } else if (passageList.size() > topRank) {
            finalPassages = new ArrayList<>(passageList.subList(0, topRank));
        }

        String result = String.join("\n", finalPassages);

        // 更新上下文：召回信息写入本节点分区 node.<cell>（避免并行检索节点互相覆盖）
        String cell = runtimeVo.getNodeInfo().getId();
        String updated = runtimeHelper.writeVar(contextEntity.getOutputData(), cell,
                "sys.result", result);
        // ★ 修复：结构化召回片段 + count + rerank 标记，替代原冗余/类型不一致的 search/originalResult
        JSONArray fragmentsArr = JSONUtil.createArray();
        for (String p : finalPassages) {
            JSONObject frag = JSONUtil.createObj();
            frag.set("text", p);
            fragmentsArr.add(frag);
        }
        updated = runtimeHelper.writeVar(updated, cell, "datasets.fragments", fragmentsArr);
        updated = runtimeHelper.writeVar(updated, cell, "datasets.count", finalPassages.size());
        updated = runtimeHelper.writeVar(updated, cell, "datasets.reranked", reranked);
        updated = runtimeHelper.writeVar(updated, cell, "datasets.rerankModelId",
                rerankModelId == null ? "" : rerankModelId);
        updated = runtimeHelper.writeVar(updated, cell, "datasets.question", question);
        contextEntity.setOutputData(updated);

        // 调试：节点耗时 + 检索问题写进 modelData
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

    /** 单个知识库的检索范围：kbId + 限定文档列表（null/空=整库） */
    private static class KbScope {
        final String kbId;
        final List<String> docIds;

        KbScope(String kbId, List<String> docIds) {
            this.kbId = kbId;
            this.docIds = docIds;
        }
    }
}
