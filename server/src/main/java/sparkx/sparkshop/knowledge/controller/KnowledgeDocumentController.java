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
import sparkx.sparkshop.knowledge.service.IKnowledgeDocumentService;
import sparkx.sparkshop.knowledge.validate.DocumentListValidate;
import sparkx.sparkshop.knowledge.validate.DocumentSaveValidate;
import sparkx.sparkshop.knowledge.validate.KbQuestionGenValidate;
import sparkx.sparkshop.knowledge.validate.PreviewValidate;
import sparkx.sparkshop.knowledge.vo.DocumentDetailVo;
import sparkx.sparkshop.knowledge.vo.DocumentSaveProgressVo;
import sparkx.sparkshop.knowledge.vo.DocumentVo;
import sparkx.sparkshop.knowledge.vo.IngestionSummary;
import sparkx.sparkshop.knowledge.vo.PreviewProgressVo;
import sparkx.sparkshop.knowledge.vo.TaskIdVo;
import sparkx.sparkshop.system.vo.PageResult;

/**
 * 知识库文档管理
 */
@Tag(name = "知识库-文档管理")
@RestController
@RequestMapping("/knowledge/document")
public class KnowledgeDocumentController {

    @Resource
    private IKnowledgeDocumentService documentService;

    @Operation(summary = "文档列表（按知识库）")
    @GetMapping("/list")
    public AjaxResult<PageResult<DocumentVo>> list(DocumentListValidate query) {
        return AjaxResult.success(documentService.page(query));
    }

    @Operation(summary = "上传文档（同步解析+分块+向量化）")
    @PostMapping("/upload")
    public AjaxResult<DocumentVo> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam("kbId") String kbId,
                                         @RequestParam(value = "engine", defaultValue = "tika") String engine) {
        return AjaxResult.success(documentService.upload(file, kbId, engine));
    }

    @Operation(summary = "试切预览（非 mineru 同步返回切片；mineru 异步返回 taskId）")
    @PostMapping("/preview")
    public AjaxResult<Object> preview(@Valid PreviewValidate validate) {
        // mineru 场景 service 返回 TaskIdVo（{taskId}），其他场景返回切片列表，均原样透传
        return AjaxResult.success(documentService.preview(validate));
    }

    @Operation(summary = "查询预览进度（mineru 异步预览轮询用）")
    @GetMapping("/preview/progress")
    public AjaxResult<PreviewProgressVo> previewProgress(@RequestParam String taskId) {
        return AjaxResult.success(documentService.getPreviewProgress(taskId));
    }

    @Operation(summary = "保存切片结果（异步入库，返回 taskId 供轮询）")
    @PostMapping("/save")
    public AjaxResult<TaskIdVo> save(@RequestBody @Valid DocumentSaveValidate validate) {
        return AjaxResult.success(documentService.save(validate));
    }

    @Operation(summary = "查询入库进度")
    @GetMapping("/save/progress")
    public AjaxResult<DocumentSaveProgressVo> saveProgress(@RequestParam String taskId) {
        return AjaxResult.success(documentService.getSaveProgress(taskId));
    }

    @Operation(summary = "重新向量化指定文档（异步）")
    @GetMapping("/embedding")
    public AjaxResult<Object> embedding(@RequestParam("documentIds") String documentIds) {
        documentService.triggerEmbedding(documentIds);
        return AjaxResult.success();
    }

    @Operation(summary = "生成问题")
    @PostMapping("/generateKbQuestions")
    public AjaxResult<Object> generateKbQuestions(@RequestBody @Valid KbQuestionGenValidate validate) {
        documentService.triggerGenerateKbQuestions(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "删除文档（级联删子块+MinIO 对象+图谱数据）")
    @GetMapping("/del")
    public AjaxResult<Object> del(@RequestParam("documentIds") String documentIds) {
        documentService.deleteDocuments(documentIds);
        return AjaxResult.success();
    }

    @Operation(summary = "切换文档级知识图谱开关（kg_enabled: 1启用 2禁用，关闭时清理已有图谱）")
    @PostMapping("/kgToggle")
    public AjaxResult<Object> kgToggle(@RequestParam String documentId,
                                        @RequestParam Integer kgEnabled) {
        documentService.toggleKg(documentId, kgEnabled);
        return AjaxResult.success();
    }

    @Operation(summary = "文档详情（含子块列表）")
    @GetMapping("/detail")
    public AjaxResult<DocumentDetailVo> detail(@RequestParam String documentId) {
        return AjaxResult.success(documentService.detail(documentId));
    }

    @Operation(summary = "查询文档入库耗时统计（各阶段耗时）")
    @GetMapping("/ingestionSummary")
    public AjaxResult<IngestionSummary> ingestionSummary(@RequestParam String documentId) {
        return AjaxResult.success(documentService.getIngestionSummary(documentId));
    }

    @Operation(summary = "下载文档原文件（浏览器直下载）")
    @GetMapping("/download")
    public void download(@RequestParam String documentId, HttpServletResponse response) {
        documentService.downloadDocument(documentId, response);
    }
}
