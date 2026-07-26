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
import org.springframework.web.multipart.MultipartFile;
import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.knowledge.entity.KnowledgeQuestion;
import sparkx.sparkshop.knowledge.service.IKnowledgeQuestionService;
import sparkx.sparkshop.knowledge.validate.QuestionListValidate;
import sparkx.sparkshop.knowledge.validate.QuestionValidate;
import sparkx.sparkshop.knowledge.validate.RelationValidate;
import sparkx.sparkshop.knowledge.vo.TaskIdVo;
import sparkx.sparkshop.system.vo.PageResult;

/**
 * 知识库问题（Q&A）管理
 *
 * <p>仅做参数接收与编排，业务逻辑下沉至 {@link IKnowledgeQuestionService}。
 */
@Tag(name = "知识库-问题管理")
@RestController
@RequestMapping("/knowledge/question")
public class QuestionController {

    @Resource
    private IKnowledgeQuestionService knowledgeQuestionService;

    @Operation(summary = "问题列表（按知识库）")
    @GetMapping("/list")
    public AjaxResult<PageResult<KnowledgeQuestion>> list(QuestionListValidate query) {
        return AjaxResult.success(knowledgeQuestionService.page(query));
    }

    @Operation(summary = "手动新增问题")
    @PostMapping("/add")
    public AjaxResult<Object> add(@RequestBody @Valid QuestionValidate validate) {
        knowledgeQuestionService.add(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "编辑问题")
    @PostMapping("/edit")
    public AjaxResult<Object> edit(@RequestBody @Valid QuestionValidate validate) {
        knowledgeQuestionService.edit(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "删除问题")
    @GetMapping("/del")
    public AjaxResult<Object> del(@RequestParam Long id) {
        knowledgeQuestionService.remove(id);
        return AjaxResult.success();
    }

    @Operation(summary = "关联问题到子块")
    @PostMapping("/doRelation")
    public AjaxResult<Object> doRelation(@RequestBody @Valid RelationValidate validate) {
        knowledgeQuestionService.doRelation(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "批量导入问题（Excel，异步处理，返回 taskId）")
    @PostMapping("/import")
    public AjaxResult<TaskIdVo> importQuestions(
            @RequestParam("file") MultipartFile file,
            @RequestParam("kbId") String kbId) {
        return AjaxResult.success(knowledgeQuestionService.importQuestions(kbId, file));
    }

    @Operation(summary = "查询批量导入进度")
    @GetMapping("/import/progress")
    public AjaxResult<Object> importProgress(@RequestParam String taskId) {
        return AjaxResult.success(knowledgeQuestionService.getImportProgress(taskId));
    }
}
