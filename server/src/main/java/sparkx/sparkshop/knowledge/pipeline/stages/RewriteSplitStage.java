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

import sparkx.sparkshop.knowledge.intent.QueryIntent;
import sparkx.sparkshop.knowledge.intent.RuleBasedIntentRouter;
import sparkx.sparkshop.knowledge.pipeline.PipelineContext;
import sparkx.sparkshop.knowledge.pipeline.PipelineStage;
import sparkx.sparkshop.knowledge.query.MultiQuestionRewriteService;
import sparkx.sparkshop.knowledge.query.RewriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 改写+拆分阶段（文档 5.3.2）—— @Order(10)，在意图分类之前。
 *
 * 改写+拆分让复杂问题（"OA 介绍和数据安全要求分别是什么"）拆成两个独立子问题，
 * 配合 TreeIntentStage 按子问题并行意图分类，召回更精准。
 *
 * 本阶段是管线中唯一的改写入口，产出：
 *  - ctx.rewriteResult（主问题+子问题），供 TreeIntentStage 按子问题并行意图分类
 *  - ctx.rewriteQuery（主问题），供下游 RetrieveStage/RerankStage/GenerateStage 使用
 *
 * 改写能力（规则归一化 + LLM 改写+拆分 + 历史指代消解）全部内聚在
 * {@link MultiQuestionRewriteService} 中，开关 app.rag.retrieval.enable-rewrite
 * 关闭时降级为归一化 + 规则拆分。
 *
 * shouldRun：需要检索时才改写（纯闲聊无需）。
 */
@Component
@Order(10)
public class RewriteSplitStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(RewriteSplitStage.class);

    private final MultiQuestionRewriteService rewriteService;
    private final RuleBasedIntentRouter ruleRouter;

    public RewriteSplitStage(MultiQuestionRewriteService rewriteService,
                             RuleBasedIntentRouter ruleRouter) {
        this.rewriteService = rewriteService;
        this.ruleRouter = ruleRouter;
    }

    @Override
    public String name() { return "rewrite-split"; }

    @Override
    public boolean shouldRun(PipelineContext ctx) {
        // 需要检索时才改写。
        if (!ctx.needsRetrieval()) {
            return false;
        }
        // ★ 闲聊预判：本阶段（@Order 10）在 IntentStage（@Order 20）之前执行，此时 ctx.intent 仍为 null，
        //   needsRetrieval() 默认 true，会导致"你好"这类闲聊被白白做一次 LLM 改写（数秒纯浪费）。
        //   这里用规则零成本预判：命中 GREETING/CHITCHAT 则直接跳过，闲聊本就无需改写。
        //   与 IntentStage 的闲聊短路保持一致（同一套 RuleBasedIntentRouter）。
        QueryIntent ruleIntent = ruleRouter.tryClassify(ctx.getOriginalQuery());
        if (ruleIntent == QueryIntent.GREETING || ruleIntent == QueryIntent.CHITCHAT) {
            log.info("[RewriteSplit:diag] 跳过改写（规则判定闲聊={}）query=\"{}\"",
                    ruleIntent, ctx.getOriginalQuery());
            return false;
        }
        return true;
    }

    @Override
    public StageResult execute(PipelineContext ctx) {
        long t0 = System.currentTimeMillis();
        // ★ 智能体配了专用改写模型（小快模型）则用它，降本提速；null = 默认对话模型
        Integer rewriteModelId = ctx.getAgentOverrides() == null
                ? null : ctx.getAgentOverrides().getRewriteModelId();
        String rewriteModelName = ctx.getAgentOverrides() == null
                ? null : ctx.getAgentOverrides().getRewriteModelName();
        RewriteResult result = rewriteService.rewriteWithSplit(
                ctx.getOriginalQuery(), ctx.getSessionId(), ctx.getUserId(), rewriteModelId, rewriteModelName);
        ctx.setRewriteResult(result);
        // 同步设置 rewriteQuery（向后兼容现有 GenerateStage/RerankStage 取 rewriteQuery）
        ctx.setRewriteQuery(result.rewrittenQuestion());
        log.info("[RewriteSplit:diag] LLM改写 耗时={}ms subs={} modelId={} (含1次LLM调用)",
                System.currentTimeMillis() - t0, result.subQuestions().size(), rewriteModelId);
        return StageResult.CONTINUE;
    }
}
