// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.config.AiModelProperties;
import sparkx.sparkshop.knowledge.config.RagProperties;
import sparkx.sparkshop.knowledge.entity.AiModel;
import sparkx.sparkshop.knowledge.entity.KnowledgeBase;
import sparkx.sparkshop.knowledge.infra.EmbeddingModelProvider;
import sparkx.sparkshop.knowledge.infra.chat.ModelTarget;
import sparkx.sparkshop.knowledge.mapper.AiModelMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeBaseMapper;
import sparkx.sparkshop.knowledge.service.IAiModelService;
import sparkx.sparkshop.knowledge.validate.AiModelValidate;
import sparkx.sparkshop.knowledge.vo.ModelTestVo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI 模型候选数据源适配器。
 *
 * <p>★ 本类是 SparkX 适配层（与 sparkxV2 关键差异点）：
 * sparkxV2 从 {@code application.yml} 的 {@code app.ai.*.candidates} 读多候选模型清单；
 * SparkX 改为从 {@code ai_model} 表（页面可编辑）读取，由本类装配成
 * {@link AiModelProperties.ModelCandidate} 列表交给容错层（ModelSelector / RoutingLLMService）。
 *
 * <p>这样既复用 sparkxV2 已成熟的多模型熔断 + 首包探测 + 降级链逻辑（候选结构 1:1 对齐），
 * 又满足"多模型接入 + 页面可操作"：运营在后台增删模型无需改配置发版。
 *
 * <p>字段映射：
 * <ul>
 *   <li>{@code type} 1对话 / 2向量 / 3重排 / 4视觉(VLM)</li>
 *   <li>{@code status} 1启用 / 2禁用 —— 仅返回启用项</li>
 *   <li>{@code priority} 数值小者优先（按 ASC 排序，决定降级顺序与默认模型）</li>
 *   <li>{@code models} 逗号分隔，对话/向量/视觉取首项；重排允许单模型</li>
 *   <li>{@code credential} JSON 数组 [{"field":"apiKey","value":"sk-xx"}]，提取 apiKey</li>
 *   <li>{@code options} JSON 数组 [{"field":"url","value":"https://..."}]，提取 url</li>
 *   <li>{@code supportsThinking} 0否 1是</li>
 * </ul>
 *
 * <p>apiKey 缺省时回退到 {@link RagProperties#getChat()#getApiKey()}（yml 默认兜底）。
 */
@Service
public class AiModelServiceImpl implements IAiModelService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /**
     * 视觉模型连通性测试图：64x64 红底白字 "A" 的 PNG，base64 编码。
     * <p>用内置 data URI，避免依赖外部图床可达性；图片极小（~600B）不增加显著请求体积。
     * 仅用于「验证模型是否真能处理图像输入」，识别结果不在连通性判定范围内。
     */
    private static final String VLM_TEST_IMAGE_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAG0SURBVHhe7ZgxboNAEEVzEi7FeTgMV6FK69odckvrduMkoETJW2eN5w+KZkb6hT8CeR/z2YGX164rkZUAyIykBEBmJCUAMiMpAZAZSQmAzEhKAGRGUgIgM5ISAJmRlADIVOs0zqVec7n0fJ5CBwDoy+Xe+m91HXs4TyN/AMO0LvNOzWM50bkCuQM4N6zfMwbOAIayrEv8qqksBGUa4Hx7+QKg9n9fKMZiKme6hrFcAVD7L8PtWD+W6/r7e30cg+tYyhEAt//nXa7sDA4x8ANQa//1OM8G+hg4AaA7/ONJf1AMfADQ4n7t9cfEwAUAtTdNe0fEwAFAQ/tvOiAGegBN7b+pEgPhaCwH0Nr+mzgGutFYDOCB9t9UiYHqDVELgPb+P9vZNwZSAG1vfq2liYEQAI2+z5UiBjoALR8+Hi1BDGQAbNt/K/sYiABw+z800DjtBhoAJh84KruB8WgsAMB/fM+d46HIdjS2B4CtuzO7lRhYviGaA8C7tvvprY+BMQC79t+kjoHmIfiPlADIjKQEQGYkJQAyIykBkBlJCYDMSEoAZEZSAiAzkhIAmZGUAMiMo668AbbAtuz3Zv56AAAAAElFTkSuQmCC";

    /** type 常量（对齐 AiModel 注释） */
    private static final int TYPE_CHAT = 1;
    private static final int TYPE_EMBEDDING = 2;
    private static final int TYPE_RERANK = 3;
    private static final int TYPE_VLM = 4;
    /** status：启用 */
    private static final int STATUS_ENABLED = 1;
    /** supportsThinking：是 */
    private static final int THINKING_YES = 1;

    private final AiModelMapper aiModelMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final RagProperties ragProperties;
    private final EmbeddingModelProvider embeddingModelProvider;

    public AiModelServiceImpl(AiModelMapper aiModelMapper,
                              KnowledgeBaseMapper knowledgeBaseMapper,
                              RagProperties ragProperties,
                              EmbeddingModelProvider embeddingModelProvider) {
        this.aiModelMapper = aiModelMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.ragProperties = ragProperties;
        this.embeddingModelProvider = embeddingModelProvider;
    }


    /**
     * 对话模型候选（type=1，status=1，按 priority ASC）。
     * model 取 models 逗号分隔首项；apiKey 缺省回退 rag.chat.apiKey。
     */
    @Override
    public List<AiModelProperties.ModelCandidate> getChatCandidates() {
        return convert(queryEnabledByType(TYPE_CHAT), true, true);
    }

    /** 嵌入/向量化模型候选（type=2）。 */
    @Override
    public List<AiModelProperties.ModelCandidate> getEmbeddingCandidates() {
        return convert(queryEnabledByType(TYPE_EMBEDDING), true, false);
    }

    /** 重排模型候选（type=3，model 用整个 models 字段，重排只允许单个）。 */
    @Override
    public List<AiModelProperties.ModelCandidate> getRerankCandidates() {
        return convert(queryEnabledByType(TYPE_RERANK), false, false);
    }

    /** 视觉模型候选（type=4）。 */
    @Override
    public List<AiModelProperties.ModelCandidate> getVlmCandidates() {
        return convert(queryEnabledByType(TYPE_VLM), true, false);
    }


    /** 默认对话模型 id（最低 priority 的启用对话模型），无则 null。 */
    @Override
    public String getDefaultChatModelId() {
        return firstId(getChatCandidates());
    }

    /** 默认向量模型 id，无则 null。 */
    @Override
    public String getDefaultEmbeddingModelId() {
        return firstId(getEmbeddingCandidates());
    }

    /** 默认重排模型 id，无则 null。 */
    @Override
    public String getDefaultRerankModelId() {
        return firstId(getRerankCandidates());
    }

    /** 默认视觉模型 id，无则 null。 */
    @Override
    public String getDefaultVlmModelId() {
        return firstId(getVlmCandidates());
    }


    /** 按主键取模型（运营后台编辑用）。 */
    @Override
    public AiModel getById(Integer id) {
        if (id == null) return null;
        return aiModelMapper.selectById(id);
    }

    /**
     * 按 id 取对话模型的路由目标（ModelTarget）。
     *
     * <p>用于「按指定 modelId 调 LLM」场景（如生成问题时由用户选定 chat 模型）：
     * 复用 {@link #convert} 的字段映射与 credential/options 解析逻辑，
     * 产出含 provider/model/url/apiKey 的 ModelTarget，供 RoutingLLMService 按 provider 取 ChatClient。
     *
     * <p>不经过候选过滤（启用/熔断），与 selectChatCandidates 的「候选链」语义不同——
     * 调用方明确指定要用这个模型；模型不存在或非对话类型时返回 null，由调用方降级到默认链。
     *
     * @param id ai_model.id
     * @return 路由目标；id 为空 / 模型不存在 / 非对话类型 / model 名为空 时返回 null
     */
    @Override
    public ModelTarget getChatTarget(Integer id) {
        return getChatTarget(id, null);
    }

    /**
     * 按 id 取对话模型路由目标，并以 modelNameOverride 指定具体子模型。
     * <p>ai_model.models 逗号分隔，默认只取首项；override 命中列表内某项才采用，否则回退首项。
     * ModelTarget 是 record，这里按覆盖后的 model 名重建一个。
     *
     * @param id ai_model.id
     * @param modelNameOverride 前端选择的子模型名；null/空串/不在列表内 → 取首项
     * @return 路由目标；id 为空 / 模型不存在 / 非对话类型 / model 名为空 时返回 null
     */
    @Override
    public ModelTarget getChatTarget(Integer id, String modelNameOverride) {
        if (id == null) return null;
        AiModel row = aiModelMapper.selectById(id);
        if (row == null) return null;
        if (row.getType() == null || row.getType() != TYPE_CHAT) return null;
        List<AiModelProperties.ModelCandidate> cs = convert(List.of(row), true, true);
        if (cs.isEmpty()) return null;
        AiModelProperties.ModelCandidate c = cs.get(0);
        // override 命中逗号列表才采用，否则回退首项（与 AgentRerankClient.resolveModelName 一致）
        String effectiveModel = resolveModelName(row.getModels(), modelNameOverride);
        if (effectiveModel == null || effectiveModel.isBlank()) {
            effectiveModel = c.getModel();
        }
        return new ModelTarget(c.resolveId(), effectiveModel, c.getProvider(), c.getUrl(), c.getApiKey(),
                c.isSupportsThinking());
    }

    /**
     * 刷新缓存占位。
     * <p>// TODO 后续可在本类加进程内缓存（如缓存候选列表 + TTL），
     * 页面编辑模型后调用本方法失效缓存。当前为直读 DB 的最简形态。
     */
    @Override
    public void refresh() {
        // no-op：当前直接查 DB，无缓存可失效
    }


    /**
     * 模型列表（按类型，可选状态过滤，按 priority ASC）。
     * 时间字段手动格式化为字符串，避免 Jackson 直接序列化 LocalDateTime 报错。
     */
    @Override
    public List<Map<String, Object>> list(Integer type, Integer status) {
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getType, type);
        if (status != null && status > 0) {
            wrapper.eq(AiModel::getStatus, status);
        }
        wrapper.orderByAsc(AiModel::getPriority);
        List<Map<String, Object>> data = new ArrayList<>();
        for (AiModel m : aiModelMapper.selectList(wrapper)) {
            data.add(toMap(m));
        }
        return data;
    }

    /** 模型详情。 */
    @Override
    public Map<String, Object> info(Integer id) {
        return toMap(getById(id));
    }

    /** 新增模型。 */
    @Override
    public void add(AiModelValidate v) {
        AiModel model = new AiModel();
        applyToEntity(v, model);
        LocalDateTime now = LocalDateTime.now();
        model.setCreateTime(now);
        model.setUpdateTime(now);
        aiModelMapper.insert(model);
        refresh();
    }

    /** 编辑模型。 */
    @Override
    public void edit(AiModelValidate v) {
        if (v.getId() == null) {
            throw new BusinessException("模型 id 不能为空");
        }
        AiModel model = aiModelMapper.selectById(v.getId());
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        // ★ 向量模型(type=2)被知识库引用时，锁定 models(模型名) 与 options.dimension：
        // 改这二者会让存量向量与模型实际维度失配（pgvector 报错被吞成检索哑火）。
        // url/apiKey(换服务器地址)/显示名/priority/status 允许改。
        guardEmbeddingModelMutation(model, v);
        applyToEntity(v, model);
        model.setUpdateTime(LocalDateTime.now());
        aiModelMapper.updateById(model);
        refresh();
        // 失效 EmbeddingModelProvider 缓存，让 url/apiKey 等变更立即生效
        embeddingModelProvider.invalidate(model.getId());
    }

    /**
     * 向量模型被引用时的变更校验：不允许从 {@code models} 中删除任何被知识库绑定的具体模型名。
     *
     * <p>知识库绑定的是 {@code ai_model.id + 具体模型名（embedding_model_name）}。
     * 如果编辑时把某个已被绑定的模型名从 models 里删掉，那个知识库的 embedding 就会失效。
     * 因此：被引用的模型名必须保留在新的 models 里。新增模型名、改 url/apiKey/显示名 允许。
     *
     * <p>只对 type=2（向量）且被至少一个知识库引用的模型生效；其余模型无约束。
     */
    private void guardEmbeddingModelMutation(AiModel oldModel, AiModelValidate v) {
        if (oldModel.getType() == null || oldModel.getType() != TYPE_EMBEDDING) {
            return;
        }
        // 查所有引用此模型的知识库，收集它们绑定的具体模型名
        List<KnowledgeBase> refs = knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getEmbeddingModelId, oldModel.getId()));
        if (refs.isEmpty()) {
            // 未被引用，自由修改
            return;
        }
        java.util.Set<String> boundNames = new java.util.HashSet<>();
        for (KnowledgeBase kb : refs) {
            if (kb.getEmbeddingModelName() != null && !kb.getEmbeddingModelName().isBlank()) {
                boundNames.add(kb.getEmbeddingModelName().trim());
            }
        }
        if (boundNames.isEmpty()) {
            // 老数据未存具体模型名，无法精确校验，退化为"models 首项不能变"的保护
            String oldFirst = firstModel(oldModel.getModels());
            String newFirst = firstModel(v.getModels());
            if (!strEq(oldFirst, newFirst)) {
                throw new BusinessException("该向量模型已被 " + refs.size()
                        + " 个知识库引用（老数据未绑定具体模型名），修改模型名可能导致已有向量失效。"
                        + "如需更换模型，请新建模型配置。");
            }
            return;
        }
        // 新 models 包含的所有模型名
        java.util.Set<String> newModels = new java.util.HashSet<>();
        if (v.getModels() != null && !v.getModels().isBlank()) {
            for (String p : v.getModels().split(",")) {
                if (p != null && !p.isBlank()) newModels.add(p.trim());
            }
        }
        // 任何被绑定的模型名不在新 models 里 → 拦截
        java.util.Set<String> missing = new java.util.LinkedHashSet<>();
        for (String n : boundNames) {
            if (!newModels.contains(n)) missing.add(n);
        }
        if (!missing.isEmpty()) {
            throw new BusinessException("该向量模型被 " + refs.size()
                    + " 个知识库引用，以下模型名正在被使用，不能从模型列表中删除："
                    + String.join("、", missing)
                    + "。删除会导致对应知识库的向量失效。如需更换，请新建模型配置并重建知识库。"
                    + "（可新增模型名或修改服务地址 url / apiKey）");
        }
    }

    /** 删除模型。 */
    @Override
    public void remove(Integer id) {
        // 向量模型被引用时禁止删除（同 edit 的维度一致性保护）
        AiModel model = aiModelMapper.selectById(id);
        if (model != null && model.getType() != null && model.getType() == TYPE_EMBEDDING) {
            long refCount = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBase>()
                    .eq(KnowledgeBase::getEmbeddingModelId, id));
            if (refCount > 0) {
                throw new BusinessException("该向量模型已被 " + refCount + " 个知识库引用，无法删除。请先解除引用或删除相关知识库。");
            }
        }
        aiModelMapper.deleteById(id);
        refresh();
        embeddingModelProvider.invalidate(id);
    }

    /** 切换模型启停。 */
    @Override
    public void switchStatus(Integer id, Integer status) {
        AiModel model = aiModelMapper.selectById(id);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        model.setStatus(status);
        model.setUpdateTime(LocalDateTime.now());
        aiModelMapper.updateById(model);
        refresh();
        embeddingModelProvider.invalidate(id);
    }

    /** 启用的重排模型列表（type=3,status=1）。 */
    @Override
    public List<Map<String, Object>> rerankList() {
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getType, TYPE_RERANK)
                .eq(AiModel::getStatus, STATUS_ENABLED)
                .orderByAsc(AiModel::getPriority);
        List<Map<String, Object>> data = new ArrayList<>();
        for (AiModel m : aiModelMapper.selectList(wrapper)) {
            data.add(toMap(m));
        }
        return data;
    }

    /** 测试已保存模型连通性（按 id 取库内配置）。 */
    @Override
    public ModelTestVo test(Integer id) {
        AiModel model = aiModelMapper.selectById(id);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        return doTest(model.getType(), model.getOptions(), model.getCredential(), model.getModels());
    }

    /** 测试模型连通性（按表单参数，无需保存；新建态用）。 */
    @Override
    public ModelTestVo testConnect(AiModelValidate v) {
        return doTest(v.getType(), v.getOptions(), v.getCredential(), v.getModels());
    }

    /**
     * 实际连通性测试：按模型 type 分别构造 OpenAI 兼容请求并探测。
     *
     * <ul>
     *   <li>chat(1)：{@code /chat/completions}，body={@code {model, messages:[{user,hi}], max_tokens:16}}</li>
     *   <li>embedding(2)：{@code /embeddings}，body={@code {model, input:"hi"}}</li>
     *   <li>rerank(3)：{@code /rerank}，body={@code {model, query, documents:[...]}}</li>
     *   <li>vlm(4)：{@code /chat/completions}，多模态 body（content 含 text + image_url data URI），
     *       真正验证模型对图像输入的支持，而非退化为纯文本 chat</li>
     * </ul>
     */
    private ModelTestVo doTest(int type, String optionsJson, String credentialJson, String models) {
        ModelTestVo vo = new ModelTestVo();
        long start = System.currentTimeMillis();

        try {
            String url = resolveTestUrl(type, extractField(optionsJson, "url"));
            String apiKey = extractField(credentialJson, "apiKey");
            String modelName = firstModel(models);

            ObjectNode body = buildTestBody(type, modelName);

            Request.Builder rb = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), JSON_MEDIA));
            if (apiKey != null && !apiKey.isBlank()) {
                rb.header("Authorization", "Bearer " + apiKey);
            }

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(15).toMillis(), TimeUnit.MILLISECONDS)
                    .readTimeout(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS)
                    .build();

            try (Response resp = client.newCall(rb.build()).execute()) {
                vo.setLatencyMs(System.currentTimeMillis() - start);
                if (!resp.isSuccessful()) {
                    vo.setSuccess(false);
                    vo.setMessage("HTTP " + resp.code() + " " + resp.message());
                    return vo;
                }
                try (ResponseBody respBody = resp.body()) {
                    String respText = respBody == null ? "" : respBody.string();
                    JsonNode root = MAPPER.readTree(respText);
                    JsonNode err = root.path("error");
                    if (!err.isMissingNode()) {
                        vo.setSuccess(false);
                        vo.setMessage("供应商返回错误: " + err.path("message").asText());
                        return vo;
                    }
                    vo.setSuccess(true);
                    vo.setMessage("连接成功");
                    return vo;
                }
            }
        } catch (Exception e) {
            vo.setSuccess(false);
            vo.setMessage("测试失败: " + e.getMessage());
            vo.setLatencyMs(System.currentTimeMillis() - start);
            return vo;
        }
    }

    /**
     * 按模型 type 构造连通性测试请求体（OpenAI 兼容协议族）。
     *
     * @param type      1对话 2向量 3重排 4视觉
     * @param modelName 取自 models 逗号分隔首项
     */
    private ObjectNode buildTestBody(int type, String modelName) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", modelName);
        if (type == TYPE_EMBEDDING) {
            // 向量模型：POST /embeddings，body={model, input}
            body.put("input", "hi");
            return body;
        }
        if (type == TYPE_RERANK) {
            // 重排模型：POST /rerank，body={model, query, documents}（SiliconFlow/Cohere/Jina 通用格式）
            body.put("query", "你好");
            ArrayNode docs = body.putArray("documents");
            docs.add("测试文档内容一");
            docs.add("测试文档内容二");
            return body;
        }
        if (type == TYPE_VLM) {
            // 视觉模型：POST /chat/completions，body 含多模态 content（OpenAI 视觉协议）
            // content 是数组：先文本提示、再 image_url(data URI)。
            // 必须走多模态格式才能真正验证「模型是否支持图像输入」，否则退化成纯文本 chat
            // 会把用户填错的非视觉模型也判为连通成功，造成误导。
            ArrayNode messages = body.putArray("messages");
            ObjectNode m = messages.addObject();
            m.put("role", "user");
            ArrayNode content = m.putArray("content");
            ObjectNode textPart = content.addObject();
            textPart.put("type", "text");
            textPart.put("text", "请描述这张图片的内容");
            ObjectNode imgPart = content.addObject();
            imgPart.put("type", "image_url");
            ObjectNode imageUrl = imgPart.putObject("image_url");
            imageUrl.put("url", "data:image/png;base64," + VLM_TEST_IMAGE_BASE64);
            body.put("max_tokens", 32);
            return body;
        }
        // 对话(1)：POST /chat/completions，body={model, messages:[{user,hi}], max_tokens}
        ArrayNode messages = body.putArray("messages");
        ObjectNode m = messages.addObject();
        m.put("role", "user");
        m.put("content", "hi");
        body.put("max_tokens", 16);
        return body;
    }

    /**
     * 实体转 Map：时间字段手动格式化为字符串，避免 Jackson 直接序列化 LocalDateTime 报错。
     */
    private Map<String, Object> toMap(AiModel model) {
        if (model == null) {
            return null;
        }
        Map<String, Object> map = BeanUtil.beanToMap(model, false, false);
        map.put("createTime", model.getCreateTime() == null ? null : model.getCreateTime().format(FMT));
        map.put("updateTime", model.getUpdateTime() == null ? null : model.getUpdateTime().format(FMT));
        return map;
    }

    /** validate → 实体字段赋值（状态/优先级/思考能力缺省）。 */
    private void applyToEntity(AiModelValidate v, AiModel m) {
        m.setName(v.getName());
        m.setType(v.getType());
        m.setProvider(v.getProvider());
        m.setCredential(v.getCredential());
        m.setModels(v.getModels());
        m.setFunctionCalling(v.getFunctionCalling());
        m.setOptions(v.getOptions());
        m.setStatus(v.getStatus() == null ? STATUS_ENABLED : v.getStatus());
        m.setPriority(v.getPriority() == null ? 100 : v.getPriority());
        m.setSupportsThinking(v.getSupportsThinking() == null ? 0 : v.getSupportsThinking());
    }

    /** 取 models 逗号分隔首项。 */
    private String firstModel(String models) {
        if (models == null || models.isBlank()) return "";
        for (String p : models.split(",")) {
            if (!p.isBlank()) return p.trim();
        }
        return "";
    }

    /**
     * 校验子模型名是否在 models 逗号列表内。
     * <p>override 非空且命中列表某项 → 返回该项；否则返回首项（null/空串 models 返回 null）。
     * 与 AgentRerankClient.resolveModelName 行为一致，用于 getChatTarget 的子模型覆盖。
     */
    private String resolveModelName(String models, String override) {
        if (models == null || models.isBlank()) return null;
        if (override != null && !override.isBlank()) {
            String trimmed = override.trim();
            for (String part : models.split(",")) {
                if (trimmed.equals(part.trim())) {
                    return trimmed;
                }
            }
        }
        return firstModel(models);
    }

    /** null-safe 字符串相等比较（null 与空串视为相等，用于"是否变更"判定）。 */
    private static boolean strEq(String a, String b) {
        String sa = a == null ? "" : a.trim();
        String sb = b == null ? "" : b.trim();
        return sa.equals(sb);
    }

    /**
     * 按模型 type 解析连通性测试 URL。
     *
     * <p>★ 设计变更：{@code options.url} 由用户填写<b>完整接口地址</b>（含协议路径，
     * 如对话/视觉 {@code /chat/completions}、向量 {@code /embeddings}、重排 {@code /rerank}），
     * 后端<b>不再自动补全路径</b>。此处直接以用户输入为准（仅做 trim）；
     * 仅在 url 完全缺失时回退 OpenAI 默认端点（全地址形态），便于发现漏配。
     */
    private String resolveTestUrl(int type, String base) {
        if (base == null || base.isBlank()) {
            // 未配置 url 时回退 OpenAI 默认端点（全地址形态）
            return "https://api.openai.com/v1" + pathByType(type);
        }
        // 用户应填写完整接口地址，后端不再补全
        return base.trim();
    }

    /** 按 type 取 OpenAI 兼容协议路径后缀。 */
    private String pathByType(int type) {
        if (type == TYPE_EMBEDDING) return "/embeddings";
        if (type == TYPE_RERANK) return "/rerank";
        // chat(1) / vlm(4) 都走 chat completions
        return "/chat/completions";
    }


    /**
     * 查询某 type 下启用模型（按 priority ASC）。
     */
    private List<AiModel> queryEnabledByType(int type) {
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getType, type)
                .eq(AiModel::getStatus, STATUS_ENABLED)
                .orderByAsc(AiModel::getPriority);
        return aiModelMapper.selectList(wrapper);
    }

    /**
     * 将 AiModel 列表转换为 ModelCandidate 列表。
     *
     * @param takeFirstModel true：取 models 逗号分隔首项；false：用整个 models（重排）
     * @param fallbackChatKey true：apiKey 缺省时回退 rag.chat.apiKey
     */
    private List<AiModelProperties.ModelCandidate> convert(List<AiModel> rows,
                                                           boolean takeFirstModel,
                                                           boolean fallbackChatKey) {
        List<AiModelProperties.ModelCandidate> result = new ArrayList<>();
        if (rows == null) return result;
        for (AiModel row : rows) {
            AiModelProperties.ModelCandidate c = new AiModelProperties.ModelCandidate();
            c.setId(String.valueOf(row.getId()));
            c.setProvider(row.getProvider());

            String model = resolveModel(row.getModels(), takeFirstModel);
            c.setModel(model);

            c.setUrl(extractOptionField(row.getOptions(), "url"));

            String apiKey = extractCredentialField(row.getCredential(), "apiKey");
            if (apiKey == null && fallbackChatKey) {
                apiKey = ragProperties.getChat().getApiKey();
            }
            c.setApiKey(apiKey);

            c.setPriority(row.getPriority() != null ? row.getPriority() : 100);
            c.setEnabled(true);
            c.setSupportsThinking(row.getSupportsThinking() != null && row.getSupportsThinking() == THINKING_YES);

            // model 为空的跳过（无法调用）
            if (model == null || model.isBlank()) continue;
            result.add(c);
        }
        return result;
    }

    /**
     * 解析 models 字段。
     *
     * @param takeFirst true=取首项（对话/向量/视觉），false=整体（重排单模型）
     */
    private String resolveModel(String models, boolean takeFirst) {
        if (models == null || models.isBlank()) return null;
        if (!takeFirst) return models.trim();
        String[] parts = models.split(",");
        for (String p : parts) {
            if (p != null && !p.isBlank()) return p.trim();
        }
        return null;
    }

    /**
     * 从 credential JSON 数组提取字段值。
     * 格式：[{"field":"apiKey","value":"sk-xx"}, ...]
     *
     * @return 匹配项的 value；JSON 异常/缺字段返回 null
     */
    private String extractCredentialField(String credentialJson, String field) {
        return extractField(credentialJson, field);
    }

    /**
     * 从 options JSON 数组提取字段值。
     * 格式：[{"field":"url","value":"https://..."}, ...]
     *
     * @return 匹配项的 value；JSON 异常/缺字段返回 null
     */
    private String extractOptionField(String optionsJson, String field) {
        return extractField(optionsJson, field);
    }

    /**
     * 通用：从 [{"field":"xxx","value":"yyy"}] 形态 JSON 数组中找 field 对应 value。
     * 任意异常（null/空/非数组/解析失败/缺字段）返回 null。
     */
    private String extractField(String json, String field) {
        if (json == null || json.isBlank() || field == null) return null;
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) return null;
            for (JsonNode node : root) {
                JsonNode f = node.path("field");
                JsonNode v = node.path("value");
                if (f != null && field.equals(f.asText())) {
                    return v != null && !v.isNull() && !v.isMissingNode() ? v.asText() : null;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 取候选列表首个 id，无则 null。 */
    private String firstId(List<AiModelProperties.ModelCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        return candidates.get(0).getId();
    }
}
