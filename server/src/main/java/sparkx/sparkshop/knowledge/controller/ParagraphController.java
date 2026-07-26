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
import sparkx.sparkshop.knowledge.service.IParagraphService;
import sparkx.sparkshop.knowledge.validate.ParagraphListValidate;
import sparkx.sparkshop.knowledge.validate.ParagraphValidate;
import sparkx.sparkshop.knowledge.vo.ChunkVo;
import sparkx.sparkshop.system.vo.PageResult;

/**
 * 段落/子块管理（手动增删改；手动录入仅走关键词 tsv，不带向量）
 *
 * <p>仅做参数接收与编排，业务逻辑下沉至 {@link IParagraphService}。
 */
@Tag(name = "知识库-段落管理")
@RestController
@RequestMapping("/knowledge/paragraph")
public class ParagraphController {

    @Resource
    private IParagraphService paragraphService;

    @Operation(summary = "段落列表（按知识库或文档）")
    @GetMapping("/list")
    public AjaxResult<PageResult<ChunkVo>> list(ParagraphListValidate query) {
        return AjaxResult.success(paragraphService.page(query));
    }

    @Operation(summary = "手动新增段落（无向量，仅关键词）")
    @PostMapping("/add")
    public AjaxResult<Object> add(@RequestBody @Valid ParagraphValidate validate) {
        paragraphService.add(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "编辑段落内容")
    @PostMapping("/edit")
    public AjaxResult<Object> edit(@RequestBody @Valid ParagraphValidate validate) {
        paragraphService.edit(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "切换段落启停（active 字段记录在 metadata）")
    @GetMapping("/active")
    public AjaxResult<Object> active(@RequestParam String id, @RequestParam Integer active) {
        paragraphService.switchActive(id, active);
        return AjaxResult.success();
    }

    @Operation(summary = "删除段落")
    @GetMapping("/del")
    public AjaxResult<Object> del(@RequestParam String id) {
        paragraphService.remove(id);
        return AjaxResult.success();
    }
}
