// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.evaluation.service;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.evaluation.vo.EvalProbeVo;
import sparkx.sparkshop.knowledge.agent.AgentChatService;
import sparkx.sparkshop.knowledge.entity.KnowledgeAgent;
import sparkx.sparkshop.knowledge.intent.NodeScore;
import sparkx.sparkshop.knowledge.pipeline.PipelineContext;
import sparkx.sparkshop.knowledge.service.IKnowledgeAgentService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评测旁路取证服务。
 * <p>
 * 复用 {@link AgentChatService#chatSyncWithCtx} 跑完整 RAG 管线，再把
 * {@link PipelineContext} 的检索证据（mergeResult/重排结果/意图/各阶段耗时）
 * 抽取成 {@link EvalProbeVo}，供 Python 评测工具链消费。
 * <p>
 * 取值优先级与 {@code AgentChatService.extractReferences} 一致：
 * mergeResult &gt; rerankResult &gt; searchResult，且 rerank 跑过（即使过滤成空）
 * 不再回退 searchResult。但取证给评测用时，rerank 跑了但为空需回退到 searchResult，
 * 因为检索指标（Hit@K/Recall）需要看「原始召回」是否命中期望文档——
 * 否则 rerank 阈值过高导致全过滤时，召回率会被误判为 0。
 * <p>
 * 跨模块依赖说明：本服务位于 evaluation 模块，调用 knowledge 模块的 RAG 管线
 * （被测系统入口），依赖方向为「评测 → 被测系统」，正确。
 */
@Slf4j
@Service
public class EvalProbeService {

    @Resource
    private IKnowledgeAgentService agentService;

    @Resource
    private AgentChatService agentChatService;

    /**
     * 跑一次智能体问答，返回完整链路取证。
     *
     * @param agentId 智能体 id
     * @param query   问题
     * @return 评测旁路取证结果
     */
    public EvalProbeVo evalProbe(String agentId, String query) {
        KnowledgeAgent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new BusinessException("智能体不存在");
        }
        if (agent.getStatus() != null && agent.getStatus() == 2) {
            throw new BusinessException("智能体已禁用");
        }

        AgentChatService.EvalProbeResult probe = agentChatService.chatSyncWithCtx(agent, query);
        AgentChatService.ChatResult result = probe.result();
        PipelineContext ctx = probe.ctx();

        EvalProbeVo vo = new EvalProbeVo();
        vo.setResponse(result.answer);
        vo.setFinalStatus(result.error ? "error" : "success");
        vo.setError(result.error ? result.errorMsg : null);
        vo.setConversationId(null);

        // ctx 为 null（管线异常）：只填 response/status，证据留空
        if (ctx == null) {
            vo.setRetrievedDocIds(Collections.emptyList());
            vo.setRetrievedChunkIds(Collections.emptyList());
            vo.setRetrievedContexts(Collections.emptyList());
            vo.setRetrievedContextDocIds(Collections.emptyList());
            vo.setIntentPredAll(Collections.emptyList());
            vo.setStageTimings(Collections.emptyMap());
            return vo;
        }

        // 抽取检索证据：评测视角需要原始召回，rerank 跑了但为空时回退 searchResult
        List<Content> evidence = pickRetrievalEvidence(ctx);
        fillRetrievalEvidence(vo, evidence);

        // 意图候选（意图树叶子节点 id）
        List<NodeScore> subIntents = ctx.getSubIntents();
        if (subIntents != null && !subIntents.isEmpty()) {
            List<String> intentIds = new ArrayList<>();
            for (NodeScore ns : subIntents) {
                if (ns.node() != null && ns.node().getId() != null) {
                    intentIds.add(ns.node().getId());
                }
            }
            vo.setIntentPredAll(intentIds);
            vo.setIntentPred(intentIds.isEmpty() ? null : intentIds.get(0));
        } else {
            vo.setIntentPredAll(Collections.emptyList());
        }

        // 各阶段耗时 + 总耗时 + LLM 调用次数
        vo.setStageTimings(ctx.getStageTimings());
        vo.setTotalCost(ctx.getTotalCost());
        vo.setFirstTokenMs(probe.firstTokenMs());
        vo.setLlmCallCount(ctx.getLlmCallCount());

        // 分支诊断
        boolean hasKb = ctx.getKbContext() != null && !ctx.getKbContext().isBlank();
        boolean hasMcp = ctx.getMcpContext() != null && !ctx.getMcpContext().isBlank();
        vo.setHasKb(hasKb);
        vo.setHasMcp(hasMcp);

        log.info("[EvalProbe] agent={} queryLen={} docs={} ctx={} status={} cost={}ms",
                agentId, query == null ? 0 : query.length(),
                vo.getRetrievedDocIds() == null ? 0 : vo.getRetrievedDocIds().size(),
                vo.getRetrievedContexts() == null ? 0 : vo.getRetrievedContexts().size(),
                vo.getFinalStatus(), vo.getTotalCost());
        return vo;
    }

    /**
     * 取证用的检索证据来源：mergeResult 优先，空时回退 searchResult。
     * <p>区别于 {@code AgentChatService.extractReferences}（rerank 跑过为空不回退）：
     * 评测看召回率需要原始召回，rerank 阈值过高导致全过滤时回退到 searchResult，
     * 否则 Hit@K/Recall 会被误判为 0。
     */
    private List<Content> pickRetrievalEvidence(PipelineContext ctx) {
        List<Content> merged = ctx.getMergeResult();
        if (merged != null && !merged.isEmpty()) {
            return merged;
        }
        List<Content> rerank = ctx.getRerankResult();
        if (rerank != null && !rerank.isEmpty()) {
            return rerank;
        }
        List<Content> search = ctx.getSearchResult();
        return search != null ? search : Collections.emptyList();
    }

    /** 从 Content 列表抽 docIds（去重保序）/ chunkIds / contexts / contextDocIds */
    private void fillRetrievalEvidence(EvalProbeVo vo, List<Content> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            vo.setRetrievedDocIds(Collections.emptyList());
            vo.setRetrievedChunkIds(Collections.emptyList());
            vo.setRetrievedContexts(Collections.emptyList());
            vo.setRetrievedContextDocIds(Collections.emptyList());
            return;
        }
        Set<String> docIdSet = new LinkedHashSet<>();
        List<String> chunkIds = new ArrayList<>();
        List<String> contexts = new ArrayList<>();
        List<String> contextDocIds = new ArrayList<>();

        for (Content c : evidence) {
            TextSegment seg = c.textSegment();
            String text = seg.text();
            Map<String, Object> meta = seg.metadata().toMap();
            Object docId = meta.get("document_id");

            contexts.add(text);
            if (docId != null) {
                String did = docId.toString();
                contextDocIds.add(did);
                docIdSet.add(did);
            } else {
                contextDocIds.add(null);
            }
            // chunk id：langchain4j Content 无稳定 id，用 identityHashCode 兜底（仅诊断用）
            chunkIds.add(String.valueOf(System.identityHashCode(c)));
        }

        vo.setRetrievedDocIds(new ArrayList<>(docIdSet));
        vo.setRetrievedChunkIds(chunkIds);
        vo.setRetrievedContexts(contexts);
        vo.setRetrievedContextDocIds(contextDocIds);
    }
}
