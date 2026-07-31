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

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天会话展示 VO。对齐前端 chat.ts 的 {@code ChatSession}（snake_case 由 Jackson 全局配置输出）。
 */
@Data
@Schema(description = "聊天会话")
public class ChatSessionVo implements Serializable {

    @Schema(description = "会话 id")
    private String id;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "会话描述")
    private String description;

    @Schema(description = "来源场景")
    private String source;

    @Schema(description = "目标类型 agent / workflow")
    private String kind;

    @Schema(description = "绑定的智能体/编排 id")
    private String agentId;

    /**
     * 智能体配置 JSON。库内存的是字符串，用 @JsonRawValue 让 Jackson 原样输出为 JSON 对象，
     * 避免前端拿到被转义的字符串。空值保持 null。
     */
    @JsonRawValue
    @Schema(description = "智能体配置 JSON")
    private String agentConfig;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
