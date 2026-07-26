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
import sparkx.sparkshop.knowledge.service.IExtServiceConfigService;
import sparkx.sparkshop.knowledge.validate.ExtServiceConfigListValidate;
import sparkx.sparkshop.knowledge.validate.ExtServiceConfigValidate;
import sparkx.sparkshop.knowledge.vo.ModelTestVo;

import java.util.List;
import java.util.Map;

/**
 * 外部服务配置（MinerU 等非 LLM 外部服务，页面可操作）。
 *
 * <p>与 {@link AiModelController} 区分：本接口管 {@code ext_service_config} 表
 * （category 分类 + config JSON），AiModel 管 {@code ai_model} 表（LLM 模型 + 容错降级链）。
 * 仅做参数接收与编排，CRUD / 连通性测试逻辑全部下沉至 {@link IExtServiceConfigService}。
 */
@Tag(name = "外部服务配置")
@RestController
@RequestMapping("/ai/service")
public class ExtServiceConfigController {

    @Resource
    private IExtServiceConfigService extServiceConfigService;

    @Operation(summary = "配置列表（按类别，可选状态过滤）")
    @GetMapping("/list")
    public AjaxResult<List<Map<String, Object>>> list(@Valid ExtServiceConfigListValidate query) {
        return AjaxResult.success(extServiceConfigService.list(query.getCategory(), query.getStatus()));
    }

    @Operation(summary = "配置详情")
    @GetMapping("/info")
    public AjaxResult<Map<String, Object>> info(@RequestParam Integer id) {
        return AjaxResult.success(extServiceConfigService.info(id));
    }

    @Operation(summary = "新增配置")
    @PostMapping("/add")
    public AjaxResult<Object> add(@RequestBody @Valid ExtServiceConfigValidate validate) {
        extServiceConfigService.add(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "编辑配置")
    @PostMapping("/edit")
    public AjaxResult<Object> edit(@RequestBody @Valid ExtServiceConfigValidate validate) {
        extServiceConfigService.edit(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "删除配置")
    @GetMapping("/del")
    public AjaxResult<Object> del(@RequestParam Integer id) {
        extServiceConfigService.remove(id);
        return AjaxResult.success();
    }

    @Operation(summary = "切换配置启停")
    @GetMapping("/status")
    public AjaxResult<Object> status(@RequestParam Integer id, @RequestParam Integer status) {
        extServiceConfigService.switchStatus(id, status);
        return AjaxResult.success();
    }

    @Operation(summary = "测试已保存配置连通性")
    @GetMapping("/test")
    public AjaxResult<ModelTestVo> test(@RequestParam Integer id) {
        return AjaxResult.success(extServiceConfigService.test(id));
    }

    @Operation(summary = "测试连通性（按表单参数，无需保存）")
    @PostMapping("/testConnect")
    public AjaxResult<ModelTestVo> testConnect(@RequestBody ExtServiceConfigValidate validate) {
        // 新建态下用页面当前填写的 config 直接测试，不落库
        return AjaxResult.success(extServiceConfigService.testConnect(validate));
    }
}
