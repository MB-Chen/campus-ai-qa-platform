// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库主表（移植自 sparkxV2 knowledge_base，扩展 embedding_model_id/dimension）。
 */
@Data
@TableName("knowledge_base")
public class KnowledgeBase implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（UUID，由业务生成） */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField(value = "name")
    private String name;

    @TableField(value = "description")
    private String description;

    /** 绑定的嵌入模型 ai_model.id（确定服务地址/凭证） */
    @TableField(value = "embedding_model_id")
    private Integer embeddingModelId;

    /**
     * 绑定的具体模型名（ai_model.models 里的某一项）。
     * <p>★ 一条 ai_model 记录可配多个模型（逗号分隔），不同模型维度可能不同；
     * 知识库必须绑定到具体模型名，避免运行时 firstModel 取首项被偷换。
     * 老数据为空时回退取 models 首项。
     */
    @TableField(value = "embedding_model_name")
    private String embeddingModelName;

    /** 冗余：嵌入模型显示名（如 "Ollama"，展示用） */
    @TableField(value = "embedding_model")
    private String embeddingModel;

    /**
     * 创建时快照的服务地址（来自 ai_model.options.url）。
     * <p>★ 快照设计：知识库一旦向量化，配置就不应再变。运行时 EmbeddingModelProvider
     * 优先用本字段构造模型，不再回查 ai_model，避免后续在 /ai/model 页面改 url 导致
     * 已落库向量与查询向量维度/服务不一致。
     */
    @TableField(value = "embedding_model_url")
    private String embeddingModelUrl;

    /**
     * 创建时快照的凭证（来自 ai_model.credential.apiKey）。
     * <p>与 url 同属快照；ollama 等无 key 服务为空。明文落库与 ai_model.credential 现状一致。
     */
    @TableField(value = "embedding_model_api_key")
    private String embeddingModelApiKey;

    /** 该知识库的向量维度（创建时按绑定 embedding 模型记录） */
    @TableField(value = "dimension")
    private Integer dimension;

    /** 1正常 2禁用 */
    @TableField(value = "status")
    private Integer status;

    /** 文档数（冗余，列表展示） */
    @TableField(value = "doc_count")
    private Integer docCount;

    /**
     * 知识图谱开关 1=启用 2=禁用。
     * <p>用户录入文档/配置 KB 时主动勾选（对齐 WeKnora ExtractConfig.Enabled）。
     * 启用后，新文档摄入完成会异步触发实体/关系抽取，写入 Neo4j + kg_entity 表。
     * 需配合全局开关 {@code app.rag.knowledge-graph.enabled} 与 {@code kg_config.enabled} 双闸生效。
     */
    @TableField(value = "kg_enabled")
    private Integer kgEnabled;

    @TableField(value = "created_at", update = "now()")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
