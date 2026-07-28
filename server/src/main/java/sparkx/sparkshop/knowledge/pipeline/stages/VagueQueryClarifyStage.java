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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.intent.NodeScore;
import sparkx.sparkshop.knowledge.intent.VagueQueryClarifier;
import sparkx.sparkshop.knowledge.pipeline.PipelineContext;
import sparkx.sparkshop.knowledge.pipeline.PipelineStage;

import java.util.List;

/**
 * 模糊查询澄清阶段 —— @Order(50)，紧跟 {@link GuidanceStage}（@Order 40）之后。
 *
 * <p>补齐 clarification 的盲区。{@link GuidanceStage} 只在「意图候选≥2」时反问澄清，
 * 无法处理用户问题过于笼统导致候选不足（0 或 1 个低分）的场景。本阶段专门兜底这类情况：
 * <ul>
 *   <li>候选不足 2 个（即 GuidanceStage 没触发）</li>
 *   <li>且问题较短（长句通常信息量足够，无需澄清判定，省一次 LLM）</li>
 * </ul>
 * 调 {@link VagueQueryClarifier} 判定：闲聊/明确 → 放行；模糊 → 反问澄清并短路。
 *
 * <p>执行顺序与 {@link IntentStage}（@Order 20 规则闸门）、{@link TreeIntentStage}（@Order 30 意图分类）、
 * {@link GuidanceStage}（@Order 40 歧义）协同：
 * <pre>
 *   IntentStage(规则) → TreeIntentStage(意图分类) → GuidanceStage(候选≥2 歧义)
 *      → ★VagueQueryClarifyStage(候选不足 模糊澄清) → RetrieveStage(检索) → ...
 * </pre>
 */
@Component
@Order(50)
public class VagueQueryClarifyStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(VagueQueryClarifyStage.class);

    /** 触发澄清判定的最大问题长度（字符数）；超长视为信息充分，跳过判定省 LLM 调用 */
    private static final int MAX_QUERY_LEN_FOR_CLARIFY = 40;
    /**
     * ★ 意图分类高置信命中阈值：TreeIntentStage 候选≥1 且最高分≥此值时，视为意图明确，
     *   跳过模糊澄清判定（省一次 LLM 调用）。
     *   例：问"试用期一般是多长时间?"已命中"OA规章制度咨询"(0.67)，无需再花 5s 判模糊性。
     */
    private static final double HIGH_CONFIDENCE_SKIP_SCORE = 0.6;

    private final VagueQueryClarifier clarifier;

    public VagueQueryClarifyStage(VagueQueryClarifier clarifier) {
        this.clarifier = clarifier;
    }

    @Override
    public String name() { return "vague-clarify"; }

    @Override
    public boolean shouldRun(PipelineContext ctx) {
        // 仅在需要检索的场景（纯闲聊 IntentStage 已拦截，不进本阶段）
        if (!ctx.needsRetrieval()) return false;
        // 已触发歧义引导短路的话术不再重复澄清
        if (ctx.isGuidancePrompt()) return false;

        List<NodeScore> subIntents = ctx.getSubIntents();
        int count = subIntents == null ? 0 : subIntents.size();
        // ★ 只在候选不足 2 个时触发（候选≥2 已由 GuidanceStage 处理）
        if (count >= 2) return false;

        // ★ 候选=1 且最高分高置信命中时跳过：意图已明确，无需再花一次 LLM 判模糊性。
        //   例：命中"OA规章制度咨询"分数 0.67 的问题，再判 CLEAR 纯属浪费数秒。
        if (count == 1 && subIntents.get(0).score() >= HIGH_CONFIDENCE_SKIP_SCORE) {
            log.info("[VagueClarify:diag] 跳过澄清判定（意图高置信命中 score={}）query=\"{}\"",
                    String.format("%.2f", subIntents.get(0).score()), ctx.getOriginalQuery());
            return false;
        }

        String query = ctx.getOriginalQuery();
        // 问题过短或过长都不判：空/极短通常已被规则拦，过长信息充分
        if (query == null || query.trim().isEmpty()) return false;
        return query.length() <= MAX_QUERY_LEN_FOR_CLARIFY;
    }

    @Override
    public StageResult execute(PipelineContext ctx) {
        long t0 = System.currentTimeMillis();
        String query = ctx.getOriginalQuery();
        VagueQueryClarifier.ClarifyDecision decision = clarifier.clarify(query, ctx.getSubIntents());

        if (decision.isVague()) {
            // ★ 短路：反问澄清，推送话术并结束链路
            String prompt = decision.prompt();
            if (ctx.getTokenConsumer() != null) {
                ctx.getTokenConsumer().accept(prompt);
            }
            ctx.setAnswer(prompt);
            log.info("[VagueClarify:diag] LLM判定 耗时={}ms 结果=模糊→澄清 query=\"{}\"",
                    System.currentTimeMillis() - t0, query);
            return StageResult.COMPLETE;
        }

        // 闲聊/明确 → 放行（闲聊由后续 GenerateStage 用 EMPTY 场景自由作答；明确走正常检索）
        log.info("[VagueClarify:diag] LLM判定 耗时={}ms 结果={}→放行 query=\"{}\"",
                System.currentTimeMillis() - t0, decision.action(), query);
        return StageResult.CONTINUE;
    }
}
