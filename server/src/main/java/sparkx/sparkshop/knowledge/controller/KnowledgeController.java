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
import sparkx.sparkshop.knowledge.service.IKnowledgeDocumentService;
import sparkx.sparkshop.knowledge.service.IKnowledgeService;
import sparkx.sparkshop.knowledge.validate.HitTestValidate;
import sparkx.sparkshop.knowledge.validate.KnowledgeBaseEditValidate;
import sparkx.sparkshop.knowledge.validate.KnowledgeBaseValidate;
import sparkx.sparkshop.knowledge.vo.HitTestVo;
import sparkx.sparkshop.knowledge.vo.KnowledgeBaseVo;
import sparkx.sparkshop.system.vo.PageQuery;
import sparkx.sparkshop.system.vo.PageResult;

import java.util.List;

/**
 * 知识库管理
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    @Resource
    private IKnowledgeService knowledgeService;

    @Resource
    private IKnowledgeDocumentService documentService;

    @Operation(summary = "知识库列表（分页）")
    @GetMapping("/index")
    public AjaxResult<PageResult<KnowledgeBaseVo>> index(PageQuery query) {
        return AjaxResult.success(knowledgeService.page(query));
    }

    @Operation(summary = "新增知识库")
    @PostMapping("/add")
    public AjaxResult<KnowledgeBaseVo> add(@RequestBody @Valid KnowledgeBaseValidate validate) {
        return AjaxResult.success(knowledgeService.add(validate));
    }

    @Operation(summary = "编辑知识库")
    @PostMapping("/edit")
    public AjaxResult<Object> edit(@RequestBody @Valid KnowledgeBaseEditValidate validate) {
        knowledgeService.edit(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "删除知识库（级联删除文档/子块/问题）")
    @GetMapping("/del")
    public AjaxResult<Object> del(@RequestParam String id) {
        knowledgeService.delete(id);
        return AjaxResult.success();
    }

    @Operation(summary = "切换知识库启停状态（1启用 / 2禁用）")
    @GetMapping("/status")
    public AjaxResult<Object> status(@RequestParam String id, @RequestParam Integer status) {
        knowledgeService.switchStatus(id, status);
        return AjaxResult.success();
    }

    @Operation(summary = "重新向量化整个知识库（异步）")
    @GetMapping("/embedding")
    public AjaxResult<Object> embedding(@RequestParam String id) {
        documentService.embeddingByKb(id);
        return AjaxResult.success();
    }

    @Operation(summary = "命中测试")
    @PostMapping("/hitTest")
    public AjaxResult<List<HitTestVo>> hitTest(@RequestBody @Valid HitTestValidate validate) {
        return AjaxResult.success(knowledgeService.hitTest(validate));
    }
}
