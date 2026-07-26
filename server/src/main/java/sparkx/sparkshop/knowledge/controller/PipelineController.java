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
import sparkx.sparkshop.knowledge.entity.IngestionPipelineNode;
import sparkx.sparkshop.knowledge.entity.IngestionTaskNode;
import sparkx.sparkshop.knowledge.service.IKnowledgeDocumentService;
import sparkx.sparkshop.knowledge.service.IPipelineService;
import sparkx.sparkshop.knowledge.validate.PipelineNodeValidate;

import java.util.List;

/**
 * 入库流水线管理（节点定义 / 任务日志 / 手动触发入库）
 *
 * <p>仅做参数接收与编排，节点定义/日志查询逻辑下沉至 {@link IPipelineService}；
 * 手动入库仍委托 {@link IKnowledgeDocumentService}。
 */
@Tag(name = "知识库-流水线管理")
@RestController
@RequestMapping("/knowledge/pipeline")
public class PipelineController {

    @Resource
    private IPipelineService pipelineService;

    @Resource
    private IKnowledgeDocumentService knowledgeDocumentService;

    @Operation(summary = "流水线节点列表（按 pipelineId）")
    @GetMapping("/list")
    public AjaxResult<List<IngestionPipelineNode>> list(@RequestParam String pipelineId) {
        return AjaxResult.success(pipelineService.list(pipelineId));
    }

    @Operation(summary = "保存流水线节点定义（先删后插）")
    @PostMapping("/save")
    public AjaxResult<Object> save(@RequestBody @Valid PipelineNodeValidate validate) {
        pipelineService.save(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "手动触发文档入库（按 documentId 重新向量化）")
    @GetMapping("/run")
    public AjaxResult<Object> run(@RequestParam String documentId) {
        knowledgeDocumentService.embedding(List.of(documentId));
        return AjaxResult.success();
    }

    @Operation(summary = "任务节点日志（按 taskId）")
    @GetMapping("/logs")
    public AjaxResult<List<IngestionTaskNode>> logs(@RequestParam String taskId) {
        return AjaxResult.success(pipelineService.logs(taskId));
    }
}
