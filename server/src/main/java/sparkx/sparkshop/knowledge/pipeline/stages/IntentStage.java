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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 意图识别阶段 —— 管线的第一道关卡（规则前置闸门）。
 *
 * 用规则零成本识别闲聊/问候/追问/联网等场景：
 *  - 闲聊/问候（GREETING/CHITCHAT）：规则命中即短路为 CHITCHAT（needsRetrieval=false），
 *    跳过后续 Retrieve/Rerank/Merge/Fallback，直接由 GenerateStage 用 LLM 做纯对话应答。
 *    「你好」「你是谁」「谢谢」这类不查知识的输入不应走检索，否则查不到会触发兜底，
 *    出现「先白调兜底 LLM 又被覆盖」的错乱。
 *  - 追问/联网（FOLLOW_UP/WEB_SEARCH）：规则命中即短路，不走检索。
 *  - 规则未命中：默认走 KB 检索，由 TreeIntentStage 做 LLM 精分类与路由。
 *
 * 结果写入 ctx.intent（仅用于 needsRetrieval 判定），决定后续 stage 是否执行。
 */
@Component
@Order(10)
public class IntentStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(IntentStage.class);

    private final RuleBasedIntentRouter ruleRouter;

    public IntentStage(RuleBasedIntentRouter ruleRouter) {
        this.ruleRouter = ruleRouter;
    }

    @Override
    public String name() { return "intent"; }

    @Override
    public StageResult execute(PipelineContext ctx) throws Exception {
        // ★ 智能体 none 模式（纯对话，不检索）：上游已预设 intent=CHITCHAT，此处保留不覆盖，
        //   让 needsRetrieval() 保持 false，后续 Retrieve/Rerank/Fallback 全部跳过，直接走 GenerateStage。
        if (ctx.getIntent() == QueryIntent.CHITCHAT) {
            ctx.setAttr("intent", QueryIntent.CHITCHAT.getCode());
            return StageResult.CONTINUE;
        }

        String q = ctx.getOriginalQuery();

        // 1. 规则快速路径：零成本识别闲聊/问候/追问/联网
        QueryIntent intent = ruleRouter.tryClassify(q);

        // 2. 闲聊/问候 → 短路为 CHITCHAT（needsRetrieval=false），跳过检索链路，直接走纯 LLM 对话。
        //    GREETING 归一为 CHITCHAT（两者都不检索，语义一致：不查知识的对话应答）。
        //    规则命中追问/联网（FOLLOW_UP/WEB_SEARCH）保留原意图，由各自短路逻辑处理。
        //    规则未命中（null）→ 默认 KB_SEARCH，走检索 + 意图树精分类。
        if (intent == QueryIntent.GREETING || intent == QueryIntent.CHITCHAT) {
            intent = QueryIntent.CHITCHAT;
        } else if (intent == null) {
            intent = QueryIntent.KB_SEARCH;
        }

        ctx.setIntent(intent);
        ctx.setAttr("intent", intent.getCode());

        log.info("[Intent:diag] 规则判定={} 需检索={} query=\"{}\"",
                intent.getCode(), intent.needsRetrieval(), q);
        return StageResult.CONTINUE;
    }
}
