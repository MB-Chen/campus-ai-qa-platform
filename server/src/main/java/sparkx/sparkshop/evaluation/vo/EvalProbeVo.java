// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.evaluation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 评测旁路取证结果。
 * <p>
 * 单接口同源取证：一次调用同时返回「答案 + 检索证据 + 意图 + 各阶段耗时」，
 * 供 Python 评测工具链（sparkx-ragas）消费，也供 Java 侧检索评估用。
 * <p>
 * 字段命名对齐 Python 侧 EvalRecord（驼峰），由 Python runner 直接读取。
 * 设计上与生产链路（AgentChatService.chatSync → RagPipeline.run）完全一致：
 * 复用同一套 ctx，只是把 {@code PipelineContext} 的检索证据一并吐出，
 * 规避「答案与检索不同源」的妥协。
 */
@Data
@Schema(description = "评测旁路取证结果")
public class EvalProbeVo implements Serializable {

    @Schema(description = "最终回答")
    private String response;

    @Schema(description = "检索命中的文档 id 列表（去重，从 mergeResult 的 metadata.document_id 抽取）")
    private List<String> retrievedDocIds;

    @Schema(description = "检索命中的 chunk id 列表")
    private List<String> retrievedChunkIds;

    @Schema(description = "检索命中的 chunk 文本列表（完整，未截断，供 RAGAS context_recall 用）")
    private List<String> retrievedContexts;

    @Schema(description = "chunk 维度的文档 id（长度与 retrievedContexts 严格相等，保留 null）")
    private List<String> retrievedContextDocIds;

    @Schema(description = "意图分类 top-1 候选节点 id（可空）")
    private String intentPred;

    @Schema(description = "意图分类全部候选节点 id 列表")
    private List<String> intentPredAll;

    @Schema(description = "各阶段耗时(ms)：name → ms")
    private Map<String, Long> stageTimings;

    @Schema(description = "管线总耗时(ms)")
    private Long totalCost;

    @Schema(description = "首字耗时(ms)，正式回答首个 token 到达（可空：未采集到时为 null）")
    private Long firstTokenMs;

    @Schema(description = "LLM 调用次数")
    private Integer llmCallCount;

    @Schema(description = "最终状态：success / refused / error")
    private String finalStatus;

    @Schema(description = "异常信息（finalStatus 非 success 时）")
    private String error;

    @Schema(description = "会话 id")
    private String conversationId;

    @Schema(description = "是否走了知识库检索")
    private Boolean hasKb;

    @Schema(description = "是否走了 MCP 工具")
    private Boolean hasMcp;
}
