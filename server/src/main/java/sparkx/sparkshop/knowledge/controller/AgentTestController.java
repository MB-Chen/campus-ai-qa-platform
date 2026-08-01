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
import sparkx.sparkshop.common.utils.AdminContextUtils;
import sparkx.sparkshop.knowledge.service.IAgentTestSessionService;
import sparkx.sparkshop.knowledge.validate.AgentTestMessageSaveValidate;
import sparkx.sparkshop.knowledge.validate.AgentTestSessionSaveValidate;
import sparkx.sparkshop.knowledge.vo.AgentTestMessageVo;
import sparkx.sparkshop.knowledge.vo.AgentTestSessionVo;

import java.util.List;

/**
 * 智能体测试对话管理（CRUD，独立于业务对话，仅调试用）。
 *
 * <p>★ 与 {@code KnowledgeAgentController} 的 SSE /chat 解耦：/chat 只负责流式回答，
 * 消息落库由前端在 SSE complete 后调用本 Controller 的端点完成。
 */
@Tag(name = "智能体测试对话")
@RestController
@RequestMapping("/knowledge/agent/test")
public class AgentTestController {

    @Resource
    private IAgentTestSessionService testSessionService;

    @Operation(summary = "测试会话列表")
    @GetMapping("/sessions")
    public AjaxResult<List<AgentTestSessionVo>> sessions(@RequestParam String agentId) {
        Long adminId = AdminContextUtils.getAdminIdAsLong();
        return AjaxResult.success(testSessionService.listSessions(adminId, agentId));
    }

    @Operation(summary = "新建测试会话")
    @PostMapping("/session")
    public AjaxResult<AgentTestSessionVo> createSession(@RequestBody @Valid AgentTestSessionSaveValidate validate) {
        Long adminId = AdminContextUtils.getAdminIdAsLong();
        return AjaxResult.success(testSessionService.createSession(adminId, validate));
    }

    @Operation(summary = "更新测试会话标题")
    @PostMapping("/session/update")
    public AjaxResult<Object> updateSession(@RequestParam String id,
                                             @RequestBody @Valid AgentTestSessionSaveValidate validate) {
        Long adminId = AdminContextUtils.getAdminIdAsLong();
        testSessionService.updateSession(adminId, id, validate);
        return AjaxResult.success();
    }

    @Operation(summary = "删除测试会话（级联删消息）")
    @GetMapping("/session/del")
    public AjaxResult<Object> deleteSession(@RequestParam String id) {
        Long adminId = AdminContextUtils.getAdminIdAsLong();
        testSessionService.deleteSession(adminId, id);
        return AjaxResult.success();
    }

    @Operation(summary = "测试会话消息列表")
    @GetMapping("/messages")
    public AjaxResult<List<AgentTestMessageVo>> messages(@RequestParam String sessionId) {
        Long adminId = AdminContextUtils.getAdminIdAsLong();
        return AjaxResult.success(testSessionService.getMessages(adminId, sessionId));
    }

    @Operation(summary = "批量落库测试会话消息（一次问答 user+assistant）")
    @PostMapping("/messages")
    public AjaxResult<Object> saveMessages(@RequestParam String sessionId,
                                            @RequestBody @Valid List<AgentTestMessageSaveValidate> messages) {
        Long adminId = AdminContextUtils.getAdminIdAsLong();
        testSessionService.saveMessages(adminId, sessionId, messages);
        return AjaxResult.success();
    }
}
