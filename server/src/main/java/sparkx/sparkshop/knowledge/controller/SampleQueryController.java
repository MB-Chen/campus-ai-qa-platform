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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.knowledge.entity.SampleQuery;
import sparkx.sparkshop.knowledge.entity.SampleQueryConfig;
import sparkx.sparkshop.knowledge.service.SampleQueryService;
import sparkx.sparkshop.knowledge.validate.SampleQueryConfigValidate;
import sparkx.sparkshop.knowledge.validate.SampleQueryListValidate;
import sparkx.sparkshop.knowledge.validate.SampleQueryValidate;
import sparkx.sparkshop.knowledge.validate.SampleQueryVectorizeBatchValidate;
import sparkx.sparkshop.knowledge.vo.SampleQueryImportResultVo;
import sparkx.sparkshop.knowledge.vo.SampleQueryVectorizeProgressVo;
import sparkx.sparkshop.knowledge.vo.TaskIdVo;
import sparkx.sparkshop.system.vo.PageResult;

/**
 * 样例查询管理。
 *
 * <p>路由前缀 {@code /knowledge/sampleQuery}，与菜单 component 路径对齐。
 * 仅做参数接收与编排，业务逻辑下沉至 {@link SampleQueryService}。
 */
@Tag(name = "知识库-样例查询")
@RestController
@RequestMapping("/knowledge/sampleQuery")
public class SampleQueryController {

    @Resource
    private SampleQueryService sampleQueryService;

    @Operation(summary = "样例分页列表")
    @GetMapping("/list")
    public AjaxResult<PageResult<SampleQuery>> list(SampleQueryListValidate query) {
        return AjaxResult.success(sampleQueryService.page(query));
    }

    @Operation(summary = "新增样例")
    @PostMapping("/add")
    public AjaxResult<Object> add(@RequestBody @Valid SampleQueryValidate validate) {
        sampleQueryService.add(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "编辑样例")
    @PostMapping("/edit")
    public AjaxResult<Object> edit(@RequestBody @Valid SampleQueryValidate validate) {
        sampleQueryService.edit(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "删除样例")
    @GetMapping("/del")
    public AjaxResult<Object> del(@RequestParam Long id) {
        sampleQueryService.remove(id);
        return AjaxResult.success();
    }

    @Operation(summary = "批量导入样例（Excel，同步处理）")
    @PostMapping("/import")
    public AjaxResult<SampleQueryImportResultVo> importExcel(@RequestParam("file") MultipartFile file) {
        return AjaxResult.success(sampleQueryService.importExcel(file));
    }

    @Operation(summary = "导出样例（Excel，按当前过滤条件）")
    @GetMapping("/export")
    public void export(SampleQueryListValidate query, HttpServletResponse response) {
        sampleQueryService.exportExcel(query, response);
    }

    @Operation(summary = "单条向量化")
    @GetMapping("/vectorize")
    public AjaxResult<Object> vectorize(@RequestParam Long id) {
        sampleQueryService.vectorize(id);
        return AjaxResult.success();
    }

    @Operation(summary = "批量向量化（异步，返回 taskId）")
    @PostMapping("/vectorize/batch")
    public AjaxResult<TaskIdVo> vectorizeBatch(@RequestBody(required = false) SampleQueryVectorizeBatchValidate req) {
        return AjaxResult.success(sampleQueryService.vectorizeBatch(req));
    }

    @Operation(summary = "查询批量向量化进度")
    @GetMapping("/vectorize/progress")
    public AjaxResult<SampleQueryVectorizeProgressVo> vectorizeProgress(@RequestParam String taskId) {
        return AjaxResult.success(sampleQueryService.getVectorizeProgress(taskId));
    }

    @Operation(summary = "读取全局配置")
    @GetMapping("/config")
    public AjaxResult<SampleQueryConfig> getConfig() {
        return AjaxResult.success(sampleQueryService.getConfig());
    }

    @Operation(summary = "保存全局配置")
    @PostMapping("/config/save")
    public AjaxResult<Object> saveConfig(@RequestBody @Valid SampleQueryConfigValidate validate) {
        sampleQueryService.saveConfig(validate);
        return AjaxResult.success();
    }
}
