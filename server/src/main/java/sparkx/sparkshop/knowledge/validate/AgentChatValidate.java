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
 * 智能体测试对话入参。
 */
@Data
@Schema(description = "智能体测试对话入参")
public class AgentChatValidate implements Serializable {

    @Schema(description = "智能体 id")
    @NotBlank(message = "智能体 id 不能为空")
    private String agentId;

    @Schema(description = "会话 id（前端生成 UUID；同 id 维持多轮记忆，留空则单轮）")
    private String conversationId;

    @Schema(description = "用户问题")
    @NotBlank(message = "问题不能为空")
    private String query;
}
