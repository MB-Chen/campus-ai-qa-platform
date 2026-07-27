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
 * 知识图谱检索测试参数。
 */
@Data
@Schema(description = "知识图谱检索测试参数")
public class KgHitTestValidate implements Serializable {

    @Schema(description = "知识库 id")
    @NotBlank(message = "知识库 id 不能为空")
    private String kbId;

    @Schema(description = "文档 id（可选，非空时限定在该文档子图内检索）")
    private String documentId;

    @Schema(description = "查询文本")
    @NotBlank(message = "查询文本不能为空")
    private String query;

    @Schema(description = "返回实体数量限制（默认 10）")
    private Integer topK;

    /**
     * ★ 检索模式：local/global/hybrid（可选）。
     * 不传时读全局配置 kg_config.retrieval_mode（默认 local）。
     * 与 {@code KnowledgeGraphChannel.retrieve} 的分发逻辑对齐：
     * <ul>
     *   <li>local：向量召回实体 → 子图扩展取 chunk（受 documentId 限定文档作用域）</li>
     *   <li>global：社区摘要召回（KB 级，documentId 不参与；前置需 community_enabled=1 + 已跑社区检测）</li>
     *   <li>hybrid：local + global 双路并行</li>
     * </ul>
     */
    @Schema(description = "检索模式：local/global/hybrid（可选，不传读全局配置）")
    private String retrievalMode;
}
