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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.knowledge.agent.AgentChatService;
import sparkx.sparkshop.knowledge.agent.AgentEvalService;
import sparkx.sparkshop.knowledge.service.IKnowledgeAgentService;
import sparkx.sparkshop.knowledge.validate.AgentChatValidate;
import sparkx.sparkshop.knowledge.validate.AgentEvalValidate;
import sparkx.sparkshop.knowledge.validate.KnowledgeAgentValidate;
import sparkx.sparkshop.knowledge.vo.AgentEvalReportVo;
import sparkx.sparkshop.knowledge.vo.KnowledgeAgentVo;
import sparkx.sparkshop.system.vo.PageQuery;
import sparkx.sparkshop.system.vo.PageResult;

import java.util.List;

/**
 * 知识库智能体管理（CRUD + SSE 测试对话 + LLM-as-judge 评估）。
 */
@Tag(name = "知识库智能体")
@RestController
@RequestMapping("/knowledge/agent")
public class KnowledgeAgentController {

    @Resource
    private IKnowledgeAgentService agentService;

    @Resource
    private AgentChatService agentChatService;

    @Resource
    private AgentEvalService agentEvalService;

    @Operation(summary = "智能体列表（分页）")
    @GetMapping("/index")
    public AjaxResult<PageResult<KnowledgeAgentVo>> index(PageQuery query) {
        return AjaxResult.success(agentService.page(query));
    }

    @Operation(summary = "智能体列表（启用，测试页下拉用）")
    @GetMapping("/list")
    public AjaxResult<List<KnowledgeAgentVo>> list() {
        return AjaxResult.success(agentService.listEnabled());
    }

    @Operation(summary = "智能体详情")
    @GetMapping("/info")
    public AjaxResult<KnowledgeAgentVo> info(@RequestParam String id) {
        return AjaxResult.success(agentService.info(id));
    }

    @Operation(summary = "新增智能体")
    @PostMapping("/add")
    public AjaxResult<KnowledgeAgentVo> add(@RequestBody @Valid KnowledgeAgentValidate validate) {
        return AjaxResult.success(agentService.add(validate));
    }

    @Operation(summary = "编辑智能体")
    @PostMapping("/edit")
    public AjaxResult<Object> edit(@RequestBody @Valid KnowledgeAgentValidate validate) {
        agentService.edit(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "删除智能体（级联清理测试会话记忆）")
    @GetMapping("/del")
    public AjaxResult<Object> del(@RequestParam String id) {
        agentService.delete(id);
        return AjaxResult.success();
    }


    @Operation(summary = "智能体测试对话（SSE 流式）")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody @Valid AgentChatValidate validate) {
        return agentChatService.chat(validate);
    }


    @Operation(summary = "批量评估（LLM-as-judge）")
    @PostMapping("/eval")
    public AjaxResult<AgentEvalReportVo> eval(@RequestBody @Valid AgentEvalValidate validate) {
        return AjaxResult.success(agentEvalService.runEval(validate));
    }

    @Operation(summary = "AI 生成测试集（按关联知识库文档）")
    @PostMapping("/evalSeed")
    public AjaxResult<List<String>> evalSeed(@RequestBody @Valid AgentEvalValidate validate) {
        return AjaxResult.success(agentEvalService.genSeedQuestions(validate));
    }
}
