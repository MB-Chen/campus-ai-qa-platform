// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.workflow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.system.vo.PageQuery;
import sparkx.sparkshop.system.vo.PageResult;
import sparkx.sparkshop.workflow.engine.WorkflowChatService;
import sparkx.sparkshop.workflow.service.IWorkflowService;
import sparkx.sparkshop.workflow.validate.SaveWorkflowValidate;
import sparkx.sparkshop.workflow.validate.WorkflowAddValidate;
import sparkx.sparkshop.workflow.validate.WorkflowChatValidate;
import sparkx.sparkshop.workflow.validate.WorkflowMetaValidate;
import sparkx.sparkshop.workflow.vo.RuntimeContextVo;
import sparkx.sparkshop.workflow.vo.SaveWorkflowVo;
import sparkx.sparkshop.workflow.vo.WorkflowVo;

import java.util.List;

/**
 * 编排流程管理（CRUD + SSE 调试对话 + 执行详情）。
 */
@Tag(name = "编排流程")
@RestController
@RequestMapping("/workflow")
public class WorkflowController {

    @Resource
    private IWorkflowService workflowService;

    @Resource
    private WorkflowChatService workflowChatService;

    @Operation(summary = "编排列表（分页）")
    @GetMapping("/index")
    public AjaxResult<PageResult<WorkflowVo>> index(PageQuery query) {
        return AjaxResult.success(workflowService.page(query));
    }

    @Operation(summary = "编排详情（含流程 JSON）")
    @GetMapping("/info")
    public AjaxResult<SaveWorkflowVo> info(@RequestParam String id) {
        return AjaxResult.success(workflowService.info(id));
    }

    @Operation(summary = "新建编排")
    @PostMapping("/add")
    public AjaxResult<WorkflowVo> add(@RequestBody @Valid WorkflowAddValidate validate) {
        return AjaxResult.success(workflowService.add(validate));
    }

    @Operation(summary = "修改编排名称/描述")
    @PostMapping("/editMeta")
    public AjaxResult<Object> editMeta(@RequestBody @Valid WorkflowMetaValidate validate) {
        workflowService.editMeta(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "保存流程设计")
    @PostMapping("/save")
    public AjaxResult<Object> save(@RequestBody @Valid SaveWorkflowValidate validate) {
        workflowService.save(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "删除编排（级联清理运行时数据）")
    @GetMapping("/del")
    public AjaxResult<Object> del(@RequestParam String id) {
        workflowService.delete(id);
        return AjaxResult.success();
    }

    @Operation(summary = "复制编排")
    @PostMapping("/copy")
    public AjaxResult<WorkflowVo> copy(@RequestParam String id) {
        return AjaxResult.success(workflowService.copy(id));
    }

    @Operation(summary = "执行详情（按步骤排序的节点上下文）")
    @GetMapping("/runDetail")
    public AjaxResult<List<RuntimeContextVo>> runDetail(@RequestParam long runtimeId) {
        return AjaxResult.success(workflowService.runtimeDetail(runtimeId));
    }

    @Operation(summary = "编排调试对话（SSE 流式）")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody @Valid WorkflowChatValidate validate) {
        return workflowChatService.chat(validate);
    }
}
