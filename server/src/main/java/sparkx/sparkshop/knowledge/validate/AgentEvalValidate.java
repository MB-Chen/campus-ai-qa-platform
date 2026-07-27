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
import java.util.List;

/**
 * 智能体评估入参。
 * <p>cases 为测试问题集；evalSeed 模式下可不传 cases，按 sampleCount 自动生成。
 */
@Data
@Schema(description = "智能体评估入参")
public class AgentEvalValidate implements Serializable {

    @Schema(description = "智能体 id")
    @NotBlank(message = "智能体 id 不能为空")
    private String agentId;

    @Schema(description = "测试用例（query 必填；expectedAnswer/note 可选）")
    private List<AgentEvalCase> cases;

    @Schema(description = "AI 生成测试集时每知识库生成的问题数（evalSeed 用，默认 3）")
    private Integer sampleCount;

    /**
     * 单条测试用例。
     */
    @Data
    @Schema(description = "评估测试用例")
    public static class AgentEvalCase implements Serializable {
        @Schema(description = "问题")
        private String query;
        @Schema(description = "期望答案（参考用，可空）")
        private String expectedAnswer;
        @Schema(description = "备注")
        private String note;
    }
}
