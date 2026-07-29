// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.evaluation.validate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * RAG 检索评估入参。
 * <p>对齐 Python 侧评估集 schema：每条用例带 expectedDocIds（must 必须召回的核心证据）、
 * requiresRag（是否应走检索）、difficulty（难度分层）。
 */
@Data
@Schema(description = "RAG 检索评估入参")
public class RagEvalValidate implements Serializable {

    @Schema(description = "智能体 id")
    @NotBlank(message = "智能体 id 不能为空")
    private String agentId;

    @Schema(description = "评估用例列表")
    @Valid
    private List<RagEvalCase> cases;

    /**
     * 单条评估用例。
     */
    @Data
    @Schema(description = "RAG 评估用例")
    public static class RagEvalCase implements Serializable {

        @Schema(description = "问题")
        @NotBlank(message = "问题不能为空")
        private String query;

        @Schema(description = "期望召回的文档 id 列表（must：必须召回）")
        private List<String> expectedDocIds;

        @Schema(description = "是否应走 RAG 检索（false=应走兜底/闲聊）")
        private boolean requiresRag;

        @Schema(description = "难度：easy/medium/hard")
        private String difficulty;

        @Schema(description = "备注")
        private String note;
    }
}
