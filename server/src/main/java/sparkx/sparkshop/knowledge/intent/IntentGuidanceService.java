// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 歧义引导服务（文档 5.2.4）——  设计：混合策略。
 *
 * 仅在"候选≥2"时触发，规则快速跳过明显意图，边界区间调 LLM 确认。
 *
 * ★ 知识库边界聚合（对齐 ragent systemBest）：候选先按「知识库」维度聚合——
 *   KB 节点按 collectionName 分组，同库取最高分；SYSTEM/MCP 各自独立成组。
 *   聚合后的「知识库组」参与 ratio 判定。这样：
 *   - 同库的多个意图不会互相触发澄清（检索同一个库，澄清无意义）
 *   - 只有跨库分数接近才澄清（这才是用户真正需要选择的场景）
 *
 * 策略（聚合后的组，按 score 降序）：
 *  1. 规则快速跳过：第一远超第二（ratio < skipThreshold）→ 意图明确不打扰
 *  2. 用户问题含明确品类名 → 已点明，跳过
 *  3. 分数比 ≥ threshold → 直接判歧义，反问澄清
 *  4. 边界区间 [skipThreshold, threshold) → 调 LLM 二次确认，降级倾向判歧义
 */
@Component
public class IntentGuidanceService {

    private static final Logger log = LoggerFactory.getLogger(IntentGuidanceService.class);

    /** 分数比阈值：≥ 此值判歧义 */
    private static final double RATIO_THRESHOLD = 0.8;
    /** 跳过阈值 = 阈值 - margin：ratio 低于此值意图明确 */
    private static final double RATIO_MARGIN = 0.15;
    private static final double SKIP_THRESHOLD = RATIO_THRESHOLD - RATIO_MARGIN;
    private static final int MAX_OPTIONS = 6;

    private final AmbiguityChecker ambiguityChecker;

    public IntentGuidanceService(AmbiguityChecker ambiguityChecker) {
        this.ambiguityChecker = ambiguityChecker;
    }

    /**
     * 检测歧义。
     *
     * @param question   用户问题
     * @param candidates 候选意图（≥2 才触发）
     * @return 引导决策（NONE/PROMPT）
     */
    public GuidanceDecision detectAmbiguity(String question, List<NodeScore> candidates) {
        if (candidates == null || candidates.size() < 2) {
            return GuidanceDecision.none();
        }

        // ★ 知识库边界聚合：同库候选合并取最高分，跨库才可能触发澄清
        List<NodeScore> aggregated = groupByKb(candidates);
        if (aggregated.size() < 2) {
            // 聚合后只剩1组（如多个候选都来自同一个知识库）→ 不澄清，交给 VagueQueryClarifyStage 兜底
            return GuidanceDecision.none();
        }

        // 取前 N 个组，按 score 降序
        List<NodeScore> ranked = aggregated.stream()
                .sorted(NodeScore.descending())
                .limit(MAX_OPTIONS)
                .toList();

        double top = ranked.get(0).score();
        double second = ranked.get(1).score();
        if (top <= 0) return GuidanceDecision.none();
        double ratio = second / top;

        // 1. 规则快速跳过：第一远超第二 → 意图明确
        if (ratio < SKIP_THRESHOLD) {
            return GuidanceDecision.none();
        }
        // 2. 用户问题含明确品类名 → 已点明，跳过
        if (containsExplicitDomain(question, ranked)) {
            return GuidanceDecision.none();
        }
        // 3. 分数比 ≥ 阈值 → 直接判歧义
        if (ratio >= RATIO_THRESHOLD) {
            return GuidanceDecision.prompt(buildPrompt(question, ranked));
        }
        // 4. 边界区间 → 调 LLM 二次确认
        boolean ambiguous = ambiguityChecker.checkAmbiguity(question, ranked);
        return ambiguous ? GuidanceDecision.prompt(buildPrompt(question, ranked))
                         : GuidanceDecision.none();
    }

    /**
     * ★ 按知识库边界聚合候选（对齐 ragent systemBest）。
     *
     * KB 节点按 collectionName 分组，同库取最高分代表；SYSTEM/MCP 各自独立成组。
     * 聚合后每个"组"是一条 NodeScore（该库/该系统/该工具的最高分候选）。
     *
     * 效果：同库多个意图不互相触发澄清（检索同一个库，澄清无意义），
     * 只有跨库/跨类型的候选才参与歧义判定。
     *
     * @param candidates 原始候选（含 KB/SYSTEM/MCP）
     * @return 聚合后的候选（每个知识库/系统/工具最多1条）
     */
    private List<NodeScore> groupByKb(List<NodeScore> candidates) {
        // key = 聚合维度：KB 用 collectionName，SYSTEM/MCP 用节点 id（各自独立，不与 KB 混）
        Map<String, NodeScore> best = new LinkedHashMap<>();
        for (NodeScore s : candidates) {
            if (s.node() == null) continue;
            String groupKey = resolveGroupKey(s.node());
            NodeScore exist = best.get(groupKey);
            if (exist == null || s.score() > exist.score()) {
                best.put(groupKey, s);
            }
        }
        return new ArrayList<>(best.values());
    }

    /** 解析节点的聚合维度：KB→collectionName（知识库），其余→节点 id（独立成组） */
    private String resolveGroupKey(IntentNode node) {
        if (node.isKB()) {
            String coll = node.getCollectionName();
            // collectionName 为空的 KB 节点（未关联库）单独成组，避免和正常 KB 节点混
            return (coll != null && !coll.isBlank()) ? coll : ("kb_no_collection:" + node.getId());
        }
        return "node:" + node.getId();
    }

    /** 用户问题是否含明确品类名（任一候选名/路径片段出现在问题中） */
    private boolean containsExplicitDomain(String question, List<NodeScore> ranked) {
        if (question == null || question.isBlank()) return false;
        String q = question.toLowerCase();
        for (NodeScore s : ranked) {
            String name = s.node().getName();
            if (name != null && q.contains(name.toLowerCase())) return true;
        }
        return false;
    }

    private String buildPrompt(String question, List<NodeScore> ranked) {
        StringBuilder opts = new StringBuilder();
        for (int i = 0; i < ranked.size(); i++) {
            opts.append(i + 1).append(". ").append(ranked.get(i).node().getFullPath()).append("\n");
        }
        return "关于你的问题，在知识库中检索到了以下相关内容：\n" + opts +
               "\n请问你具体想了解哪个？请回复数字选择（可多选，如 1,2），或回复\"都/全部\"";
    }
}
