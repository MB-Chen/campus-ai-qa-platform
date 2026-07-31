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
import lombok.Data;

import java.io.Serializable;

/**
 * 新建聊天会话入参。对齐前端 chat.ts 的 {@code CreateSessionData}。
 */
@Data
@Schema(description = "新建聊天会话入参")
public class ChatSessionCreateValidate implements Serializable {

    @Schema(description = "绑定的智能体/编排 id")
    private String agentId;

    @Schema(description = "首条问题（用于截断生成标题，可不传）")
    private String query;

    @Schema(description = "标题（不传则用 query 截断生成）")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "来源场景")
    private String source;

    @Schema(description = "目标类型 agent / workflow")
    private String kind;

    @Schema(description = "智能体配置 JSON 字符串")
    private String agentConfig;
}
