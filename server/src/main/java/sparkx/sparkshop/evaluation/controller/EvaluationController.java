// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.evaluation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.evaluation.service.EvalProbeService;
import sparkx.sparkshop.evaluation.service.EvaluationService;
import sparkx.sparkshop.evaluation.validate.RagEvalValidate;
import sparkx.sparkshop.evaluation.vo.EvalProbeVo;
import sparkx.sparkshop.evaluation.vo.RagEvalReportVo;
import sparkx.sparkshop.knowledge.validate.AgentChatValidate;

/**
 * RAG 评测模块。
 * <p>
 * 独立于 knowledge 模块，专注于「评测算指标」：
 * <ul>
 *   <li>{@code /evaluation/evalProbe}：单接口同源取证（供 Python 评测工具链 sparkx-ragas 调用）</li>
 *   <li>{@code /evaluation/evalRetrieval}：检索评估（前端可视化，纯标准库秒级）</li>
 * </ul>
 * <p>
 * 入参校验复用 knowledge 的 {@link AgentChatValidate}（agentId + query），
 * 评测逻辑（指标计算、报告产出）在本模块内闭环。
 */
@Tag(name = "RAG 评测")
@RestController
@RequestMapping("/evaluation")
public class EvaluationController {

    @Resource
    private EvalProbeService evalProbeService;

    @Resource
    private EvaluationService evaluationService;

    @Operation(summary = "评测旁路取证（单接口同源拿答案+检索证据+意图+耗时，供 Python 评测工具链用）")
    @PostMapping("/evalProbe")
    public AjaxResult<EvalProbeVo> evalProbe(@RequestBody @Valid AgentChatValidate validate) {
        return AjaxResult.success(evalProbeService.evalProbe(validate.getAgentId(), validate.getQuery()));
    }

    @Operation(summary = "检索评估（Hit@K/Recall/MRR/误拒/过召回，纯 Java 标准库秒级，前端可视化）")
    @PostMapping("/evalRetrieval")
    public AjaxResult<RagEvalReportVo> evalRetrieval(@RequestBody @Valid RagEvalValidate validate) {
        return AjaxResult.success(evaluationService.evalRetrieval(validate));
    }
}
