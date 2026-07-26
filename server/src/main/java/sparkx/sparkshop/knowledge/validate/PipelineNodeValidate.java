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
 * 保存流水线节点定义参数
 */
@Data
@Schema(description = "保存流水线节点定义参数")
public class PipelineNodeValidate implements Serializable {

    @Schema(description = "流水线 id")
    @NotBlank(message = "流水线 id 不能为空")
    private String pipelineId;

    @Schema(description = "节点定义列表")
    private List<NodeItem> nodes;

    @Data
    @Schema(description = "节点定义项")
    public static class NodeItem implements Serializable {

        @Schema(description = "节点 id")
        private String nodeId;

        @Schema(description = "节点类型 fetcher/parser/enhancer/chunker/enricher/indexer")
        private String nodeType;

        @Schema(description = "下一节点 id")
        private String nextNodeId;

        @Schema(description = "节点配置 JSON")
        private String settingsJson;

        @Schema(description = "条件执行 JSON")
        private String conditionJson;

        @Schema(description = "是否启用")
        private Boolean enabled;
    }
}
