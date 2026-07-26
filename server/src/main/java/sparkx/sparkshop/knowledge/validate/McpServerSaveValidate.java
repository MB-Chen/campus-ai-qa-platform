// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.validate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 新增/编辑 MCP 服务参数。
 *
 * <p>{@code authConfig} 为 JSON 文本：
 * <ul>
 *   <li>{@code none}：忽略</li>
 *   <li>{@code api_key}：{@code {"apiKey":"...","apiKeyHeader":"X-API-Key"}}</li>
 *   <li>{@code bearer}：{@code {"token":"..."}}</li>
 * </ul>
 * {@code headers} 为自定义请求头 JSON {@code {"k":"v"}}。
 * testConnect 复用本入参，无需先落库。
 */
@Data
@Schema(description = "新增/编辑 MCP 服务参数")
public class McpServerSaveValidate implements Serializable {

    @Schema(description = "服务 id（编辑/测试时必填）")
    private Integer id;

    @Schema(description = "服务名称（如「天气查询服务」）")
    @NotBlank(message = "服务名称不能为空")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "传输类型：sse / http_streamable")
    @NotBlank(message = "传输类型不能为空")
    private String transportType;

    @Schema(description = "SSE / HTTP Streamable 端点")
    @NotBlank(message = "服务地址不能为空")
    private String url;

    @Schema(description = "认证类型：none / api_key / bearer")
    @NotBlank(message = "认证类型不能为空")
    private String authType;

    @Schema(description = "认证 JSON：apiKey/apiKeyHeader 或 token")
    private String authConfig;

    @Schema(description = "自定义请求头 JSON {\"k\":\"v\"}")
    private String headers;

    @Schema(description = "连接/调用超时（秒），默认 30")
    private Integer timeoutSec;

    @Schema(description = "失败重试次数，默认 1")
    private Integer retryCount;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "排序（数值小者靠前）")
    private Integer sort;
}
