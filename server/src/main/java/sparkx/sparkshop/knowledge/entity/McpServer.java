// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MCP 服务配置实体（外部 MCP Server 连接配置，页面可编辑）。
 *
 * <p>与 {@link ExtServiceConfig} 区分：本表管 MCP 协议的外部服务，
 * 结构相对固定（传输类型/URL/认证/自定义头/高级配置），由 {@code McpClientManager} 按 id 缓存连接。
 * 工具列表快照存 {@link McpTool} 子表。
 *
 * <p>auth_config / headers 存 JSON 文本，schema 见 {@code McpServerServiceImpl} 类注释。
 */
@Data
@TableName("mcp_server")
public class McpServer implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 服务名称（如「天气查询服务」） */
    @TableField(value = "name")
    private String name;

    /** 描述 */
    @TableField(value = "description")
    private String description;

    /** 启停 */
    @TableField(value = "enabled")
    private Boolean enabled;

    /** 传输类型：sse / http_streamable */
    @TableField(value = "transport_type")
    private String transportType;

    /** SSE / HTTP Streamable 端点 */
    @TableField(value = "url")
    private String url;

    /** 认证类型：none / api_key / bearer */
    @TableField(value = "auth_type")
    private String authType;

    /** 认证 JSON：{"apiKey":"...","apiKeyHeader":"X-API-Key"} 或 {"token":"..."} */
    @TableField(value = "auth_config")
    private String authConfig;

    /** 自定义请求头 JSON {"k":"v"} */
    @TableField(value = "headers")
    private String headers;

    /** 连接/调用超时（秒） */
    @TableField(value = "timeout_sec")
    private Integer timeoutSec;

    /** 失败重试次数 */
    @TableField(value = "retry_count")
    private Integer retryCount;

    /** 备注 */
    @TableField(value = "remark")
    private String remark;

    /** 排序（数值小者靠前） */
    @TableField(value = "sort")
    private Integer sort;

    @TableField(value = "create_time")
    private LocalDateTime createTime;

    @TableField(value = "update_time")
    private LocalDateTime updateTime;
}
