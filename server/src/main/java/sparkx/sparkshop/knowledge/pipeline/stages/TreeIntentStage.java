// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.pipeline.stages;

import sparkx.sparkshop.knowledge.intent.IntentClassifier;
import sparkx.sparkshop.knowledge.intent.NodeScore;
import sparkx.sparkshop.knowledge.pipeline.PipelineContext;
import sparkx.sparkshop.knowledge.pipeline.PipelineStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 意图路由分类阶段（文档 5.2.3）—— @Order(15)，按子问题并行分类 + 配额控制。
 *
 * <p>命名说明：类名 {@code TreeIntentStage} 与 stage name {@code "tree-intent"} 为历史遗留
 * （早期 IntentNode 为三级树结构），现 IntentNode 已简化为单层扁平列表，但类名/stage name
 * 作为前后端数据契约（stageTimings 的 key）保留不变，避免破坏前端时间线展示。
 *
 * 与 {@link IntentStage}（@Order(10)，规则闸门）协同：
 *  - IntentStage 先判规则路径（闲聊/问候零成本），设 ctx.intent
 *  - 本阶段做 LLM 精分类（KB/SYSTEM/MCP 三态），设 ctx.subIntents
 * 若 IntentStage 已判定纯闲聊（非检索），本阶段 shouldRun=false 跳过。
 *
 * 子问题来源：ctx.rewriteResult.subQuestions（RewriteSplitStage 产出），空则用单条主问题。
 */
@Component
@Order(15)
public class TreeIntentStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(TreeIntentStage.class);
    private static final double INTENT_MIN_SCORE = 0.35;
    private static final int MAX_INTENT_COUNT = 3;

    private final IntentClassifier classifier;
    private final ExecutorService intentExecutor;

    public TreeIntentStage(IntentClassifier classifier,
                           @Qualifier("intentClassifyExecutor") ExecutorService intentExecutor) {
        this.classifier = classifier;
        this.intentExecutor = intentExecutor;
    }

    @Override
    public String name() { return "tree-intent"; }

    @Override
    public boolean shouldRun(PipelineContext ctx) {
        // 纯闲聊（IntentStage 判定非检索）无需意图分类
        return ctx.needsRetrieval();
    }

    @Override
    public StageResult execute(PipelineContext ctx) throws Exception {
        long t0 = System.currentTimeMillis();
        // 子问题列表：优先用改写拆分结果，空则单条主问题
        List<String> subQuestions = (ctx.getRewriteResult() != null
                && ctx.getRewriteResult().subQuestions() != null
                && !ctx.getRewriteResult().subQuestions().isEmpty())
                ? ctx.getRewriteResult().subQuestions()
                : List.of(ctx.getMainQuery());

        // ★ 智能体配了专用分类模型（与改写共用一个小快模型）则用它，降本提速；null = 默认对话模型
        Integer intentModelId = ctx.getAgentOverrides() == null
                ? null : ctx.getAgentOverrides().getRewriteModelId();

        // 1. 按子问题并行分类（每个独立调 LLM，异常降级为空意图）
        List<CompletableFuture<List<NodeScore>>> futures = subQuestions.stream()
                .map(q -> CompletableFuture.supplyAsync(
                        () -> classifier.topKAboveThreshold(q, 3, INTENT_MIN_SCORE, intentModelId),
                        intentExecutor).exceptionally(ex -> List.of()))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        // 2. 合并 + 全局降序
        List<NodeScore> merged = new ArrayList<>();
        for (CompletableFuture<List<NodeScore>> f : futures) {
            merged.addAll(f.join());
        }
        merged.sort(NodeScore.descending());

        // 3. ★ 配额控制：总意图 ≤ MAX_INTENT_COUNT，每子问题至少保留 1 个最高分
        List<NodeScore> capped = capTotalIntents(merged, subQuestions.size());
        ctx.setSubIntents(capped);
        log.info("[TreeIntent:diag] LLM分类 耗时={}ms subQ={} 候选={} modelId={} (每子问1次LLM，并行)",
                System.currentTimeMillis() - t0, subQuestions.size(), capped.size(), intentModelId);
        return StageResult.CONTINUE;
    }

    /** 防意图过多拖垮检索：保底每子问题 1 个 + 按全局分数补 */
    private List<NodeScore> capTotalIntents(List<NodeScore> sorted, int subQCount) {
        if (sorted.size() <= MAX_INTENT_COUNT) return sorted;
        int keep = Math.min(subQCount, MAX_INTENT_COUNT);
        List<NodeScore> result = new ArrayList<>(sorted.subList(0, keep));
        for (NodeScore s : sorted) {
            if (result.size() >= MAX_INTENT_COUNT) break;
            if (!result.contains(s)) result.add(s);
        }
        return result;
    }
}
