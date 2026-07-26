// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.knowledge.intent.IntentEvalService;
import sparkx.sparkshop.knowledge.intent.IntentSeedService;
import sparkx.sparkshop.knowledge.service.IntentNodeService;
import sparkx.sparkshop.knowledge.validate.IntentEvalSingleValidate;
import sparkx.sparkshop.knowledge.validate.IntentEvalValidate;
import sparkx.sparkshop.knowledge.validate.IntentNodeDeleteValidate;
import sparkx.sparkshop.knowledge.validate.IntentSeedGenValidate;
import sparkx.sparkshop.knowledge.vo.IntentEvalCaseVo;
import sparkx.sparkshop.knowledge.vo.IntentEvalReportVo;
import sparkx.sparkshop.knowledge.vo.IntentEvalResultVo;
import sparkx.sparkshop.knowledge.vo.IntentNodeSaveVo;
import sparkx.sparkshop.knowledge.vo.IntentNodeTreeVo;

import java.util.List;

/**
 * 意图树管理
 */
@Tag(name = "意图树管理")
@RestController
@RequestMapping("/knowledge/intent")
public class IntentNodeController {

    @Resource
    private IntentNodeService intentNodeService;

    @Resource
    private IntentEvalService intentEvalService;

    @Resource
    private IntentSeedService intentSeedService;

    @Operation(summary = "意图树（含禁用节点）")
    @GetMapping("/tree")
    public AjaxResult<List<IntentNodeTreeVo>> tree() {
        return AjaxResult.success(intentNodeService.tree());
    }

    @Operation(summary = "新增节点")
    @PostMapping("/add")
    public AjaxResult<Void> add(@RequestBody IntentNodeSaveVo vo) {
        intentNodeService.add(vo);
        return AjaxResult.success();
    }

    @Operation(summary = "编辑节点")
    @PostMapping("/edit")
    public AjaxResult<Void> edit(@RequestBody IntentNodeSaveVo vo) {
        intentNodeService.edit(vo);
        return AjaxResult.success();
    }

    @Operation(summary = "删除节点（含子节点）")
    @PostMapping("/del")
    public AjaxResult<Void> delete(@RequestBody IntentNodeDeleteValidate req) {
        intentNodeService.delete(req.getId());
        return AjaxResult.success();
    }

    @Operation(summary = "批量启用")
    @PostMapping("/batchEnable")
    public AjaxResult<Void> batchEnable(@RequestBody List<String> ids) {
        intentNodeService.batchEnable(ids);
        return AjaxResult.success();
    }

    @Operation(summary = "批量禁用")
    @PostMapping("/batchDisable")
    public AjaxResult<Void> batchDisable(@RequestBody List<String> ids) {
        intentNodeService.batchDisable(ids);
        return AjaxResult.success();
    }

    @Operation(summary = "批量评估意图分类（产出准确率/PRF1/校准/误判报告）")
    @PostMapping("/eval")
    public AjaxResult<IntentEvalReportVo> eval(@RequestBody IntentEvalValidate req) {
        return AjaxResult.success(intentEvalService.runEval(req));
    }

    @Operation(summary = "单条实时分类（输入 query，返回 TopK 候选）")
    @PostMapping("/evalSingle")
    public AjaxResult<List<IntentEvalResultVo.HitCandidate>> evalSingle(@RequestBody IntentEvalSingleValidate req) {
        return AjaxResult.success(intentEvalService.evalSingle(req));
    }

    @Operation(summary = "AI 生成测试集（基于意图配置自动生成多样化测试用例）")
    @PostMapping("/seedGen")
    public AjaxResult<List<IntentEvalCaseVo>> seedGen(@RequestBody IntentSeedGenValidate req) {
        return AjaxResult.success(intentSeedService.generate(req));
    }
}
