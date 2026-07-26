// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.service;

import sparkx.sparkshop.knowledge.validate.McpServerSaveValidate;
import sparkx.sparkshop.knowledge.vo.McpServerVo;
import sparkx.sparkshop.knowledge.vo.McpTestResultVo;
import sparkx.sparkshop.knowledge.vo.McpToolVo;

import java.util.List;

/**
 * MCP 服务管理业务接口。
 *
 * <p>管 {@code mcp_server}（服务配置）+ {@code mcp_tool}（工具快照）两张表，
 * 并通过 {@code McpClientManager} 连接外部 MCP Server 做测试连接/工具发现/工具调用。
 */
public interface IMcpServerService {

    /** 服务列表（按 sort ASC，密钥脱敏） */
    List<McpServerVo> list(Boolean enabled);

    /** 服务详情（密钥脱敏） */
    McpServerVo info(Integer id);

    /** 新增服务（保存后立即测试连接并同步工具快照） */
    void add(McpServerSaveValidate v);

    /** 编辑服务（配置变更则关闭缓存连接强制重连） */
    void edit(McpServerSaveValidate v);

    /** 删除服务（级联删工具快照 + 关闭连接） */
    void remove(Integer id);

    /** 切换启停 */
    void switchStatus(Integer id, Boolean enabled);

    /** 测试已保存配置（按 id） */
    McpTestResultVo test(Integer id);

    /** 测试连通性（按表单参数，无需先保存；新建态用） */
    McpTestResultVo testConnect(McpServerSaveValidate v);

    /** 获取某服务的工具列表（从 mcp_tool 子表读，供意图树下拉） */
    List<McpToolVo> tools(Integer serverId);

    /** 获取全部已启用服务的工具列表（供意图树下拉，含服务名上下文） */
    List<McpToolVo> allEnabledTools();

    /** 重新拉取某服务的工具并刷新快照（手动刷新） */
    McpTestResultVo refresh(Integer id);
}
