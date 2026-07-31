// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.infra;

import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.knowledge.infra.chat.StreamCallback;
import sparkx.sparkshop.knowledge.infra.chat.StreamCancellationHandle;

import java.util.List;

/**
 * 模型服务门面（文档 5.10.6）—— 业务/管线只注入此接口，零感知多模型存在。
 *
 * 所有熔断、首包探测、降级全部内聚在 {@code RoutingLLMService} 实现里。
 * 业务代码（GenerateStage / IntentClassifier / 摘要服务等）只与本接口对话。
 */
public interface LLMService {

    /**
     * 同步对话（便捷：单条 system+user 或仅 user）。
     *
     * @param prompt      完整 prompt（含 system 指令与问题）
     * @param temperature 采样温度
     * @param topP        nucleus sampling
     * @param thinking    是否启用深度思考
     * @return 模型回复文本
     */
    String chat(String prompt, double temperature, double topP, boolean thinking);

    /**
     * 同步对话（完整请求，多消息）。
     *
     * @param request 调用请求（含消息列表）
     * @return 模型回复文本
     */
    String chat(LlmChatRequest request);

    /**
     * 同步对话（指定模型 id）。
     *
     * <p>用于「用户明确选定某个 chat 模型」的场景（如生成问题时选模型）：
     * modelId 非空时按 ai_model.id 强制路由到该模型（仍走熔断降级骨架），
     * modelId 为空或对应模型不可用时回退到默认候选链。
     *
     * @param request 调用请求（含消息列表）
     * @param modelId ai_model.id；为 null 时等同 {@link #chat(LlmChatRequest)}
     * @return 模型回复文本
     */
    String chat(LlmChatRequest request, Integer modelId);

    /**
     * 同步对话（指定模型 id + 具体子模型名）。
     *
     * <p>ai_model.models 是逗号分隔的多模型，默认只取首项。当调用方明确选了某个子模型
     * （如前端把 gpt-4o-mini,gpt-4o 打平后选了 gpt-4o）时，用本重载把具体名透传给路由层：
     * 仅当 modelName 命中逗号列表内某项才采用，否则回退首项（语义同 getChatTarget(id, modelName)）。
     *
     * @param request   调用请求（含消息列表）
     * @param modelId   ai_model.id；为 null 时等同 {@link #chat(LlmChatRequest)}
     * @param modelName 具体子模型名；null/空串/不命中列表 → 取首项
     * @return 模型回复文本
     */
    String chat(LlmChatRequest request, Integer modelId, String modelName);

    /**
     * 流式对话（经容错层首包探测 + 降级链）。
     *
     * @param request     调用请求
     * @param callback    流式回调（SSE 推前端）
     * @param deepThinking 是否深度思考
     * @return 取消句柄
     */
    StreamCancellationHandle streamChat(LlmChatRequest request, StreamCallback callback, boolean deepThinking);

    /**
     * 流式对话（指定模型 id，强制路由到该模型，仍享熔断降级骨架）。
     *
     * <p>用于「用户/智能体明确选定某个 chat 模型」的场景：
     * modelId 非空时按 ai_model.id 强制路由到该模型，对齐 {@link #chat(LlmChatRequest, Integer)} 的语义。
     * modelId 为空、对应模型不存在/未注册 ChatClient 时回退到默认候选链（等同 {@link #streamChat(LlmChatRequest, StreamCallback, boolean)}）。
     *
     * @param request     调用请求
     * @param callback    流式回调（SSE 推前端）
     * @param deepThinking 是否深度思考
     * @param modelId     ai_model.id；为 null 时等同三参重载
     * @return 取消句柄
     */
    StreamCancellationHandle streamChat(LlmChatRequest request, StreamCallback callback, boolean deepThinking, Integer modelId);

    /**
     * 流式对话（指定模型 id + 具体子模型名）。
     *
     * <p>与 {@link #chat(LlmChatRequest, Integer, String)} 对齐的子模型覆盖语义：
     * ai_model.models 逗号分隔，modelName 命中列表内某项才采用，否则回退首项。
     * 用于智能体/工作流等「明确选了某个对话子模型」的流式场景。
     *
     * @param request     调用请求
     * @param callback    流式回调（SSE 推前端）
     * @param deepThinking 是否深度思考
     * @param modelId     ai_model.id；为 null 时等同三参重载
     * @param modelName   具体子模型名；null/空串/不命中列表 → 取首项
     * @return 取消句柄
     */
    StreamCancellationHandle streamChat(LlmChatRequest request, StreamCallback callback, boolean deepThinking, Integer modelId, String modelName);

    /**
     * 文本向量化（用默认兜底 embedding 模型）。
     *
     * @param text 文本
     * @return 向量（float 数组）
     */
    float[] embed(String text);

    /**
     * 文本向量化（按知识库绑定的 embedding 模型）。
     *
     * <p>★ 用于查询向量化，与入库向量化保持维度一致：
     * 按 {@code kb.embedding_model_id} 解析模型，解析失败回退默认模型。
     *
     * @param text 文本
     * @param kbId 知识库 id；为空走默认模型
     * @return 向量（float 数组）
     */
    float[] embed(String text, String kbId);

    /**
     * 重排打分。
     *
     * @param query    查询
     * @param passages 候选段落
     * @return 每个段落的相关性分数
     */
    List<Float> rerank(String query, List<String> passages);

    /**
     * 重排打分（指定 rerank 模型 id）。
     *
     * <p>★ 与智能体 RerankStage 对齐：优先用 {@code rerankModelId} 调真实 rerank API
     * （URL 取自 ai_model 表，而非 yml 默认 OpenAI 地址），失败再回退 embedding 余弦相似度
     * （embedding 模型同样取自 ai_model 表默认配置）。
     *
     * @param query         查询
     * @param passages      候选段落
     * @param rerankModelId ai_model.id（type=3）；为 null 时等同 {@link #rerank(String, List)}
     * @return 每个段落的相关性分数
     */
    List<Float> rerank(String query, List<String> passages, Integer rerankModelId);

    /**
     * 重排打分（指定 rerank 模型 id + 具体子模型名）。
     *
     * <p>与 {@link #rerank(String, List, Integer)} 一致，区别仅在于把 modelName 透传给真实 rerank API
     * （命中逗号列表才采用，否则取首项），用于工作流/智能体「选了具体 rerank 子模型」的场景。
     *
     * @param query           查询
     * @param passages        候选段落
     * @param rerankModelId   ai_model.id（type=3）；为 null 时等同 {@link #rerank(String, List)}
     * @param rerankModelName 具体子模型名；null/空串/不命中列表 → 取首项
     * @return 每个段落的相关性分数
     */
    List<Float> rerank(String query, List<String> passages, Integer rerankModelId, String rerankModelName);

    /**
     * 视觉模型图生文（VLM）。
     *
     * @param content   图片字节数据
     * @param mime      图片 MIME 类型
     * @param prompt    描述/OCR 指令
     * @param maxTokens 最大生成 token
     * @return 图片描述文本
     */
    String describeImage(byte[] content, String mime, String prompt, int maxTokens);
}
