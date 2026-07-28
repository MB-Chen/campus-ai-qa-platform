// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.retrieval;

import sparkx.sparkshop.knowledge.intent.NodeScore;
import sparkx.sparkshop.knowledge.pipeline.AgentOverrides;

import java.util.Collections;
import java.util.List;

/**
 * 检索上下文（文档 5.4）—— 贯穿条件化通道与后处理器。
 *
 * 携带意图打分结果（驱动条件化通道 isEnabled 判定）、知识库等。
 * 各通道按 {@link #isEnabled} 决定是否参与本轮检索（意图驱动路由，而非简单全并行）。
 *
 * ★ agentOverrides：智能体运行时参数覆盖（topK/阈值等）。通道 retrieve 时读取，
 *   让「智能体配置的向量召回 topK / 相似度阈值」在通道路径同样生效，
 *   而非统一回退全局默认值。可为 null（IM 客服链路，无智能体）。
 */
public class RetrievalContext {

    /** 意图打分结果（KB/MCP 候选），驱动意图定向通道；可空（全局兜底场景） */
    private final List<NodeScore> intentScores;
    /** 子问题原文 */
    private final String subQuestion;
    /** 知识库 id 列表 */
    private final List<String> knowledgeBaseIds;
    /** 是否启用意图定向（配置开关） */
    private final boolean intentDirectedEnabled;
    /** 智能体运行时参数覆盖（topK/阈值等），可空 */
    private final AgentOverrides agentOverrides;

    public RetrievalContext(List<NodeScore> intentScores, String subQuestion,
                            List<String> knowledgeBaseIds,
                            boolean intentDirectedEnabled) {
        this(intentScores, subQuestion, knowledgeBaseIds, intentDirectedEnabled, null);
    }

    public RetrievalContext(List<NodeScore> intentScores, String subQuestion,
                            List<String> knowledgeBaseIds,
                            boolean intentDirectedEnabled,
                            AgentOverrides agentOverrides) {
        this.intentScores = intentScores != null ? intentScores : Collections.emptyList();
        this.subQuestion = subQuestion;
        this.knowledgeBaseIds = knowledgeBaseIds != null ? knowledgeBaseIds : Collections.emptyList();
        this.intentDirectedEnabled = intentDirectedEnabled;
        this.agentOverrides = agentOverrides;
    }

    public List<NodeScore> getIntentScores() { return intentScores; }
    public String getSubQuestion() { return subQuestion; }
    public List<String> getKnowledgeBaseIds() { return knowledgeBaseIds; }
    public boolean isIntentDirectedEnabled() { return intentDirectedEnabled; }
    public AgentOverrides getAgentOverrides() { return agentOverrides; }

    /** 是否存在足够置信的 KB 意图（供通道判定） */
    public boolean hasKbIntentAbove(double minScore) {
        return intentScores.stream().anyMatch(s -> s.node().isKB() && s.score() >= minScore);
    }
}
