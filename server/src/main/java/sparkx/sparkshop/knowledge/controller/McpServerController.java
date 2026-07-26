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
import sparkx.sparkshop.knowledge.service.IMcpServerService;
import sparkx.sparkshop.knowledge.validate.McpServerSaveValidate;
import sparkx.sparkshop.knowledge.vo.McpServerVo;
import sparkx.sparkshop.knowledge.vo.McpTestResultVo;
import sparkx.sparkshop.knowledge.vo.McpToolVo;

import java.util.List;

/**
 * MCP 服务管理（外部 MCP Server 连接配置 + 工具发现）。
 *
 * <p>仅做参数接收与编排，CRUD / 测试 / 工具同步逻辑全部下沉至 {@link IMcpServerService}。
 */
@Tag(name = "MCP服务管理")
@RestController
@RequestMapping("/ai/mcp")
public class McpServerController {

    @Resource
    private IMcpServerService mcpServerService;

    @Operation(summary = "服务列表（可选 enabled 过滤）")
    @GetMapping("/list")
    public AjaxResult<List<McpServerVo>> list(@RequestParam(required = false) Boolean enabled) {
        return AjaxResult.success(mcpServerService.list(enabled));
    }

    @Operation(summary = "服务详情")
    @GetMapping("/info")
    public AjaxResult<McpServerVo> info(@RequestParam Integer id) {
        return AjaxResult.success(mcpServerService.info(id));
    }

    @Operation(summary = "新增服务")
    @PostMapping("/add")
    public AjaxResult<Object> add(@RequestBody @Valid McpServerSaveValidate validate) {
        mcpServerService.add(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "编辑服务")
    @PostMapping("/edit")
    public AjaxResult<Object> edit(@RequestBody @Valid McpServerSaveValidate validate) {
        mcpServerService.edit(validate);
        return AjaxResult.success();
    }

    @Operation(summary = "删除服务")
    @GetMapping("/del")
    public AjaxResult<Object> del(@RequestParam Integer id) {
        mcpServerService.remove(id);
        return AjaxResult.success();
    }

    @Operation(summary = "切换启停")
    @GetMapping("/status")
    public AjaxResult<Object> status(@RequestParam Integer id, @RequestParam Boolean enabled) {
        mcpServerService.switchStatus(id, enabled);
        return AjaxResult.success();
    }

    @Operation(summary = "测试已保存配置连通性")
    @GetMapping("/test")
    public AjaxResult<McpTestResultVo> test(@RequestParam Integer id) {
        return AjaxResult.success(mcpServerService.test(id));
    }

    @Operation(summary = "测试连通性（按表单参数，无需保存）")
    @PostMapping("/testConnect")
    public AjaxResult<McpTestResultVo> testConnect(@RequestBody McpServerSaveValidate validate) {
        return AjaxResult.success(mcpServerService.testConnect(validate));
    }

    @Operation(summary = "获取某服务的工具列表（从快照读，供意图树下拉）")
    @GetMapping("/tools")
    public AjaxResult<List<McpToolVo>> tools(@RequestParam Integer serverId) {
        return AjaxResult.success(mcpServerService.tools(serverId));
    }

    @Operation(summary = "获取全部已启用服务的工具（供意图树下拉）")
    @GetMapping("/enabledTools")
    public AjaxResult<List<McpToolVo>> enabledTools() {
        return AjaxResult.success(mcpServerService.allEnabledTools());
    }

    @Operation(summary = "重新拉取工具并刷新快照")
    @GetMapping("/refresh")
    public AjaxResult<McpTestResultVo> refresh(@RequestParam Integer id) {
        return AjaxResult.success(mcpServerService.refresh(id));
    }
}
