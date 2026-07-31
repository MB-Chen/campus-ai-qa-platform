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
import sparkx.sparkshop.knowledge.entity.KgConfig;
import sparkx.sparkshop.knowledge.entity.KgExtractionRecord;
import sparkx.sparkshop.knowledge.service.KnowledgeGraphService;
import sparkx.sparkshop.knowledge.validate.KgConfigValidate;
import sparkx.sparkshop.knowledge.validate.KgExtractValidate;
import sparkx.sparkshop.knowledge.validate.KgHitTestValidate;
import sparkx.sparkshop.knowledge.validate.KgKbSettingValidate;
import sparkx.sparkshop.knowledge.validate.KgRecordListValidate;
import sparkx.sparkshop.knowledge.vo.KgExtractionProgressVo;
import sparkx.sparkshop.knowledge.vo.KgKbSettingVo;
import sparkx.sparkshop.knowledge.vo.TaskIdVo;
import sparkx.sparkshop.system.vo.PageResult;

import java.util.Map;

/**
 * 知识图谱管理（仿 SampleQueryController 范式）。
 *
 * <p>路由前缀 {@code /knowledge/graph}，与菜单 component 路径对齐。
 * 仅做参数接收与编排，业务逻辑下沉至 {@link KnowledgeGraphService}。
 */
@Tag(name = "知识库-知识图谱")
@RestController
@RequestMapping("/knowledge/graph")
public class KnowledgeGraphController {

    @Resource
    private KnowledgeGraphService knowledgeGraphService;


    @Operation(summary = "读取全局配置")
    @GetMapping("/config")
    public AjaxResult<KgConfig> getConfig() {
        return AjaxResult.success(knowledgeGraphService.getConfig());
    }

    @Operation(summary = "保存全局配置")
    @PostMapping("/config/save")
    public AjaxResult<Object> saveConfig(@RequestBody @Valid KgConfigValidate validate) {
        knowledgeGraphService.saveConfig(validate);
        return AjaxResult.success();
    }


    @Operation(summary = "测试 Neo4j 连通性")
    @GetMapping("/testConnect")
    public AjaxResult<String> testConnect() {
        return AjaxResult.success(knowledgeGraphService.testConnect());
    }


    @Operation(summary = "查询知识库级 KG 开关")
    @GetMapping("/kbSetting")
    public AjaxResult<KgKbSettingVo> getKbSetting(@RequestParam String kbId) {
        return AjaxResult.success(knowledgeGraphService.getKbSetting(kbId));
    }

    @Operation(summary = "设置知识库级 KG 开关")
    @PostMapping("/kbSetting")
    public AjaxResult<Object> saveKbSetting(@RequestBody @Valid KgKbSettingValidate validate) {
        knowledgeGraphService.saveKbSetting(validate);
        return AjaxResult.success();
    }


    @Operation(summary = "触发抽取（异步，返回 taskId）")
    @PostMapping("/extract")
    public AjaxResult<TaskIdVo> extract(@RequestBody @Valid KgExtractValidate validate) {
        return AjaxResult.success(knowledgeGraphService.triggerExtract(validate));
    }

    @Operation(summary = "查询抽取进度")
    @GetMapping("/extract/progress")
    public AjaxResult<KgExtractionProgressVo> extractProgress(@RequestParam String taskId) {
        return AjaxResult.success(knowledgeGraphService.getExtractProgress(taskId));
    }


    @Operation(summary = "抽取记录列表")
    @GetMapping("/records")
    public AjaxResult<PageResult<KgExtractionRecord>> records(KgRecordListValidate query) {
        return AjaxResult.success(knowledgeGraphService.getRecords(query));
    }


    @Operation(summary = "检索测试（hitTest）")
    @PostMapping("/hitTest")
    public AjaxResult<Map<String, Object>> hitTest(@RequestBody @Valid KgHitTestValidate validate) {
        return AjaxResult.success(knowledgeGraphService.hitTest(validate));
    }


    @Operation(summary = "图谱可视化数据（nodes/edges，可选 documentId 过滤单文档子图）")
    @GetMapping("/visualization")
    public AjaxResult<Map<String, Object>> visualization(@RequestParam String kbId,
                                                          @RequestParam(required = false) String documentId) {
        return AjaxResult.success(knowledgeGraphService.visualization(kbId, documentId));
    }


    @Operation(summary = "删除某文档的图谱数据")
    @GetMapping("/delete")
    public AjaxResult<Object> delete(@RequestParam String kbId, @RequestParam String documentId) {
        knowledgeGraphService.deleteByDocument(kbId, documentId);
        return AjaxResult.success();
    }


    @Operation(summary = "运行社区检测 + 生成社区摘要（异步，global/hybrid 模式前置）")
    @PostMapping("/community/detect")
    public AjaxResult<Object> detectCommunity(@RequestParam String kbId) {
        knowledgeGraphService.triggerCommunityDetect(kbId);
        return AjaxResult.success();
    }
}
