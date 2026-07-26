// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MCP 服务列表/详情出参。
 *
 * <p>密钥脱敏：{@code authConfig} 在 {@code api_key}/{@code bearer} 下不回显 apiKey/token 明文，
 * 仅返回 {@code hasApiKey} / {@code hasToken} 布尔标记前端展示是否已配置。
 */
@Data
@Schema(description = "MCP 服务出参")
public class McpServerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "服务 id")
    private Integer id;

    @Schema(description = "服务名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "传输类型：sse / http_streamable")
    private String transportType;

    @Schema(description = "服务地址")
    private String url;

    @Schema(description = "认证类型：none / api_key / bearer")
    private String authType;

    /** 自定义请求头 JSON（原样返回，无敏感信息） */
    @Schema(description = "自定义请求头 JSON")
    private String headers;

    @Schema(description = "是否已配置 API Key（authConfig.apiKey 非空）")
    private Boolean hasApiKey;

    @Schema(description = "是否已配置 Token（authConfig.token 非空）")
    private Boolean hasToken;

    @Schema(description = "API Key 请求头名称（api_key 类型下展示用）")
    private String apiKeyHeader;

    @Schema(description = "连接/调用超时（秒）")
    private Integer timeoutSec;

    @Schema(description = "失败重试次数")
    private Integer retryCount;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "工具数量（冗余，列表展示用）")
    private Integer toolCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
