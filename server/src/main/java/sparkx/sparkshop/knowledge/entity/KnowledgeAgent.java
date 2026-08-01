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
 * 知识库智能体。
 * <p>
 * 智能体 = 一套可保存的 RAG 参数（关联知识库 + 温度 + 记忆轮数 + system prompt + 检索/rerank/兜底配置）。
 * 运行时由 AgentChatService 读出，构造 {@code AgentOverrides} 覆盖 RAG 管线默认参数，
 * 再跑 {@code RagPipeline} 做 SSE 流式问答。
 */
@Data
@TableName("knowledge_agent")
public class KnowledgeAgent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（UUID hex，业务生成） */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 智能体名称 */
    @TableField(value = "name")
    private String name;

    /** 描述 */
    @TableField(value = "description")
    private String description;

    /** 头像 emoji */
    @TableField(value = "avatar")
    private String avatar;

    /** 知识库模式 all全部/selected指定/none不使用 */
    @TableField(value = "kb_mode")
    private String kbMode;

    /** 关联知识库 id，逗号分隔（kb_mode=selected 时生效） */
    @TableField(value = "knowledge_base_ids")
    private String knowledgeBaseIds;

    /** 限定文档 id，逗号分隔（kb_mode=selected 时可选，空=整库） */
    @TableField(value = "document_ids")
    private String documentIds;

    /** 对话模型 ai_model.id，空走默认模型 */
    @TableField(value = "chat_model_id")
    private Integer chatModelId;

    /** 冗余：对话模型显示名 */
    @TableField(value = "chat_model_name")
    private String chatModelName;

    /** 自定义系统提示词（空走场景模板） */
    @TableField(value = "system_prompt")
    private String systemPrompt;

    /** 温度（0~2） */
    @TableField(value = "temperature")
    private Double temperature;

    /** 最大生成 token */
    @TableField(value = "max_tokens")
    private Integer maxTokens;

    /** 上下文记忆轮数 */
    @TableField(value = "history_turns")
    private Integer historyTurns;

    /** 向量召回 topK */
    @TableField(value = "embedding_top_k")
    private Integer embeddingTopK;

    /** 向量相似度阈值 */
    @TableField(value = "vector_threshold")
    private Double vectorThreshold;

    /** 关键词阈值 */
    @TableField(value = "keyword_threshold")
    private Double keywordThreshold;

    /** 重排模型 ai_model.id（type=3），空用全局默认 */
    @TableField(value = "rerank_model_id")
    private Integer rerankModelId;

    /** 冗余：重排模型显示名 */
    @TableField(value = "rerank_model_name")
    private String rerankModelName;

    /** 是否启用重排 1启用 2禁用 */
    @TableField(value = "rerank_enabled")
    private Integer rerankEnabled;

    /** 重排 topK */
    @TableField(value = "rerank_top_k")
    private Integer rerankTopK;

    /** 重排阈值 */
    @TableField(value = "rerank_threshold")
    private Double rerankThreshold;

    /** 意图/改写专用模型 ai_model.id（type=1，对话模型），空用全局默认大模型 */
    @TableField(value = "rewrite_model_id")
    private Integer rewriteModelId;

    /** 冗余：意图/改写专用模型显示名 */
    @TableField(value = "rewrite_model_name")
    private String rewriteModelName;

    /** 兜底策略 model/fixed */
    @TableField(value = "fallback_strategy")
    private String fallbackStrategy;

    /** 兜底固定话术（strategy=fixed 时生效） */
    @TableField(value = "fallback_response")
    private String fallbackResponse;

    /** 是否启用样例查询优先匹配 1启用 2禁用（启用后用户消息先到样例库向量匹配，命中阈值直接返回样例答案） */
    @TableField(value = "sample_query_enabled")
    private Integer sampleQueryEnabled;

    /** 样例匹配相似度阈值（0~1，null 时回退全局 sample_query_config.similarity_threshold） */
    @TableField(value = "sample_query_threshold")
    private Double sampleQueryThreshold;

    /** 是否启用跨会话持久记忆 1启用 2禁用（启用后按 agentId+adminId 沉淀/共享长期记忆） */
    @TableField(value = "persistent_memory_enabled")
    private Integer persistentMemoryEnabled;

    /** 开场白 */
    @TableField(value = "welcome")
    private String welcome;

    /** 推荐问题 JSON 数组（["问题1","问题2"]） */
    @TableField(value = "suggested_questions")
    private String suggestedQuestions;

    /** 1正常 2禁用 */
    @TableField(value = "status")
    private Integer status;

    @TableField(value = "created_at", update = "now()")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
