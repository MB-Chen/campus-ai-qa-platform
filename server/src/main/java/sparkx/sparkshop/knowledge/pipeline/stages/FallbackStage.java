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

import cn.hutool.core.util.StrUtil;
import sparkx.sparkshop.knowledge.fallback.FallbackProvider;
import sparkx.sparkshop.knowledge.pipeline.AgentOverrides;
import sparkx.sparkshop.knowledge.pipeline.PipelineContext;
import sparkx.sparkshop.knowledge.pipeline.PipelineStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 兜底阶段 —— 仅在 needFallback=true 或召回为空时执行。
 *
 * 兜底策略选择（优先级从高到低）：
 *  1. 智能体 fixed 策略：agentOverrides.fallbackStrategy=fixed 且配了 fallbackResponse
 *     → 直接用固定话术，不调 LLM（整段经 tokenConsumer 推一次）。
 *  2. 否则走全局 {@link FallbackProvider}（model=流式调 LLM 逐 token 推 / fixed=固定话术）。
 *
 * 这样智能体的 fixed 兜底在管线内就生效，AgentChatService 无需再事后覆盖，
 * 避免「先白调全局 model 兜底生成答案，又被固定话术覆盖」的错乱。
 */
@Component
@Order(90)
public class FallbackStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(FallbackStage.class);

    private final FallbackProvider fallbackProvider;

    public FallbackStage(FallbackProvider fallbackProvider) {
        this.fallbackProvider = fallbackProvider;
    }

    @Override
    public String name() { return "fallback"; }

    @Override
    public boolean shouldRun(PipelineContext ctx) {
        boolean needFallback = Boolean.TRUE.equals(ctx.getAttr("needFallback", Boolean.class));
        // emptyMerge：KB 文档为空 且 MCP 工具结果也为空 → 真正无证据，才走兜底；
        // 若 mcpContext 非空（MCP 工具已返回结果），应让 GenerateStage 接管，不兜底。
        boolean emptyMerge = ctx.needsRetrieval()
                && (ctx.getMergeResult() == null || ctx.getMergeResult().isEmpty())
                && StrUtil.isBlank(ctx.getMcpContext());
        return needFallback || emptyMerge;
    }

    @Override
    public StageResult execute(PipelineContext ctx) throws Exception {
        String language = ctx.getLanguage() == null ? "中文" : ctx.getLanguage();

        // ★ 智能体 fixed 策略优先：配了固定话术则直接用，不调 LLM
        String answer = resolveAgentFixedFallback(ctx);
        if (StrUtil.isBlank(answer)) {
            // 无 agent fixed 覆盖 → 走全局 provider。
            // 用流式版 fallbackStream：model 策略时逐 token 推（告别「干等全文才一次性蹦出字」），
            // fixed 策略时退化为整段回调一次。token 经 ctx.tokenConsumer 推给 SSE。
            answer = fallbackProvider.fallbackStream(
                    ctx.getOriginalQuery(), ctx.getRewriteQuery(), language, ctx.getTokenConsumer());
            ctx.setAnswer(answer);
            // fallbackStream 已自行经 tokenConsumer 推送，无需再推一次
            log.info("[Fallback] 触发兜底 query=\"{}\" answerLen={}",
                    ctx.getOriginalQuery(), answer == null ? 0 : answer.length());
            return StageResult.COMPLETE;
        }

        // agent fixed 分支：固定话术整段推送一次
        ctx.setAnswer(answer);
        if (ctx.getTokenConsumer() != null) {
            ctx.getTokenConsumer().accept(answer);
        }
        log.info("[Fallback] 触发兜底(fixed) query=\"{}\" answerLen={}",
                ctx.getOriginalQuery(), answer.length());
        return StageResult.COMPLETE;   // 兜底后直接完成，跳过生成阶段
    }

    /**
     * 智能体 fixed 兜底：agentOverrides.fallbackStrategy=fixed 且配了 fallbackResponse 时返回固定话术。
     * 其他情况（model 策略 / 未配话术 / 无 overrides）返回 null，交由全局 provider 处理。
     */
    private String resolveAgentFixedFallback(PipelineContext ctx) {
        AgentOverrides ov = ctx.getAgentOverrides();
        if (ov == null) return null;
        if (!"fixed".equalsIgnoreCase(ov.getFallbackStrategy())) return null;
        return ov.getFallbackResponse();
    }
}
