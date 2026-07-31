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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.knowledge.service.IChatSessionService;
import sparkx.sparkshop.knowledge.validate.ChatMessageSaveValidate;
import sparkx.sparkshop.knowledge.validate.ChatSessionCreateValidate;
import sparkx.sparkshop.knowledge.validate.ChatSessionUpdateValidate;
import sparkx.sparkshop.knowledge.vo.ChatMessageVo;
import sparkx.sparkshop.knowledge.vo.ChatSessionVo;
import sparkx.sparkshop.system.vo.PageQuery;
import sparkx.sparkshop.system.vo.PageResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天会话管理（建会话 / 列表 / 详情 / 改标题 / 删除 / 消息读写）。
 * <p>
 * 路径前缀 {@code /api/v1/sessions} 对齐前端 chat.ts 契约。
 * 走全局 LoginInterceptor，登录态由它校验，adminId 由它在 preHandle 写入 request 属性。
 */
@Tag(name = "聊天会话")
@RestController
@RequestMapping("/api/v1/sessions")
public class ChatSessionController {

    @Resource
    private IChatSessionService chatSessionService;

    /**
     * 从请求属性取当前登录管理员 id（由 LoginInterceptor.preHandle 写入）。
     */
    private Long currentAdminId(HttpServletRequest request) {
        Object id = request.getAttribute("adminId");
        if (id == null) {
            return null;
        }
        return id instanceof Long ? (Long) id : Long.valueOf(id.toString());
    }

    @Operation(summary = "会话列表（分页）")
    @GetMapping
    public AjaxResult<PageResult<ChatSessionVo>> list(HttpServletRequest request, PageQuery query) {
        return AjaxResult.success(chatSessionService.page(currentAdminId(request), query));
    }

    @Operation(summary = "会话详情")
    @GetMapping("/{session_id}")
    public AjaxResult<ChatSessionVo> info(HttpServletRequest request,
                                          @PathVariable("session_id") String sessionId) {
        return AjaxResult.success(chatSessionService.info(currentAdminId(request), sessionId));
    }

    @Operation(summary = "新建会话")
    @PostMapping
    public AjaxResult<ChatSessionVo> create(HttpServletRequest request,
                                            @RequestBody @Valid ChatSessionCreateValidate validate) {
        return AjaxResult.success(chatSessionService.create(currentAdminId(request), validate));
    }

    @Operation(summary = "更新会话标题/描述")
    @PutMapping("/{session_id}")
    public AjaxResult<Object> update(HttpServletRequest request,
                                     @PathVariable("session_id") String sessionId,
                                     @RequestBody @Valid ChatSessionUpdateValidate validate) {
        chatSessionService.update(currentAdminId(request), sessionId, validate);
        return AjaxResult.success();
    }

    @Operation(summary = "删除会话（级联删消息）")
    @DeleteMapping("/{session_id}")
    public AjaxResult<Object> delete(HttpServletRequest request,
                                     @PathVariable("session_id") String sessionId) {
        chatSessionService.delete(currentAdminId(request), sessionId);
        return AjaxResult.success();
    }

    @Operation(summary = "会话消息列表")
    @GetMapping("/{session_id}/messages")
    public AjaxResult<List<ChatMessageVo>> messages(HttpServletRequest request,
                                                    @PathVariable("session_id") String sessionId) {
        return AjaxResult.success(chatSessionService.messages(currentAdminId(request), sessionId));
    }

    @Operation(summary = "落库单条消息")
    @PostMapping("/{session_id}/messages")
    public AjaxResult<Map<String, Object>> saveMessage(HttpServletRequest request,
                                                       @PathVariable("session_id") String sessionId,
                                                       @RequestBody @Valid ChatMessageSaveValidate validate) {
        Long id = chatSessionService.saveMessage(currentAdminId(request), sessionId, validate);
        Map<String, Object> data = new HashMap<>(2);
        data.put("id", id);
        return AjaxResult.success(data);
    }

    @Operation(summary = "清空会话消息")
    @DeleteMapping("/{session_id}/messages")
    public AjaxResult<Object> clearMessages(HttpServletRequest request,
                                            @PathVariable("session_id") String sessionId) {
        chatSessionService.clearMessages(currentAdminId(request), sessionId);
        return AjaxResult.success();
    }
}
