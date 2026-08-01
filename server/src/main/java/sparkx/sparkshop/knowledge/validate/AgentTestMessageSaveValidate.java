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
 * 落库单条测试对话消息入参。一次问答由前端批量提交 user + assistant 两条。
 * user 消息仅填 role/content；assistant 消息可带 references/stageData/stageTimings/cost。
 */
@Data
@Schema(description = "测试对话消息入参")
public class AgentTestMessageSaveValidate implements Serializable {

    @NotBlank(message = "角色不能为空")
    @Schema(description = "角色 user / assistant")
    private String role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "引用来源 JSON 字符串（assistant）")
    private String references;

    @Schema(description = "RAG 各阶段上下文 JSON 字符串（assistant）")
    private String stageData;

    @Schema(description = "RAG 各阶段耗时 JSON 字符串（assistant）")
    private String stageTimings;

    @Schema(description = "总耗时（毫秒）")
    private Long totalCost;
}
