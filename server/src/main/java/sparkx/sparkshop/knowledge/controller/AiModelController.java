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
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.knowledge.service.IAiModelService;
import sparkx.sparkshop.knowledge.validate.AiModelListValidate;
import sparkx.sparkshop.knowledge.validate.AiModelValidate;
import sparkx.sparkshop.knowledge.vo.ModelTestVo;

import java.util.List;
import java.util.Map;

/**
 * AI 模型管理（页面可操作多模型）
 *
 * <p>type: 1对话 2向量 3重排 4视觉(VLM)
 * 仅做参数接收与编排，CRUD / 连通性测试逻辑全部下沉至 {@link IAiModelService}。
 */
@Tag(name = "AI 模型管理")
@RestController
@RequestMapping("/ai/model")
public class AiModelController {

    @Resource
    private IAiModelService aiModelService;

    @Operation(summary = "模型列表（按类型）")
    @GetMapping("/list")
    public AjaxResult<List<Map<String, Object>>> list(@Valid AiModelListValidate query) {
        return AjaxResult.success(aiModelService.list(query.getType(), query.getStatus()));
    }

    @Operation(summary = "模型详情")
    @GetMapping("/info")
    public AjaxResult<Map<String, Object>> info(@RequestParam Integer id) {
        return AjaxResult.success(aiModelService.info(id));
    }

    @Operation(summary = "新增模型")
    @PostMapping("/add")
    public AjaxResult<Object> add(@RequestBody @Valid AiModelValidate validate) {
        aiModelService.add(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "编辑模型")
    @PostMapping("/edit")
    public AjaxResult<Object> edit(@RequestBody @Valid AiModelValidate validate) {
        aiModelService.edit(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "删除模型")
    @GetMapping("/del")
    public AjaxResult<Object> del(@RequestParam Integer id) {
        aiModelService.remove(id);
        return AjaxResult.success();
    }

    @Operation(summary = "切换模型启停")
    @GetMapping("/status")
    public AjaxResult<Object> status(@RequestParam Integer id, @RequestParam Integer status) {
        aiModelService.switchStatus(id, status);
        return AjaxResult.success();
    }

    @Operation(summary = "启用的重排模型列表（type=3,status=1）")
    @GetMapping("/rerankList")
    public AjaxResult<List<Map<String, Object>>> rerankList() {
        return AjaxResult.success(aiModelService.rerankList());
    }

    @Operation(summary = "测试模型连通性（发一句 hi）")
    @GetMapping("/test")
    public AjaxResult<ModelTestVo> test(@RequestParam Integer id) {
        return AjaxResult.success(aiModelService.test(id));
    }

    @Operation(summary = "测试模型连通性（按表单参数，无需保存）")
    @PostMapping("/testConnect")
    public AjaxResult<ModelTestVo> testConnect(@RequestBody AiModelValidate validate) {
        // 新建态下用页面当前填写的凭证/选项直接测试，不落库
        return AjaxResult.success(aiModelService.testConnect(validate));
    }
}
