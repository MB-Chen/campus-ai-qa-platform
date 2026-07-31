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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.entity.KnowledgeAgent;
import sparkx.sparkshop.knowledge.entity.AiModel;
import sparkx.sparkshop.knowledge.entity.KnowledgeBase;
import sparkx.sparkshop.knowledge.mapper.AiModelMapper;
import sparkx.sparkshop.knowledge.mapper.ConversationMessageMapper;
import sparkx.sparkshop.knowledge.entity.ConversationMessageEntity;
import sparkx.sparkshop.knowledge.mapper.KnowledgeAgentMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeBaseMapper;
import sparkx.sparkshop.knowledge.service.IKnowledgeAgentService;
import sparkx.sparkshop.knowledge.validate.KnowledgeAgentValidate;
import sparkx.sparkshop.knowledge.vo.KnowledgeAgentVo;
import sparkx.sparkshop.system.vo.PageQuery;
import sparkx.sparkshop.system.vo.PageResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库智能体业务实现（CRUD）。
 * <p>
 * knowledgeBaseIds 逗号串 ↔ List 互转；suggestedQuestions JSON 数组 ↔ List 互转。
 * 删除时级联清理该智能体的测试会话记忆（conversation_id 以 "agent:{id}:" 为前缀）。
 */
@Slf4j
@Service
public class KnowledgeAgentServiceImpl implements IKnowledgeAgentService {

    @Resource
    private KnowledgeAgentMapper agentMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private AiModelMapper aiModelMapper;

    @Resource
    private ConversationMessageMapper conversationMessageMapper;

    @Override
    public PageResult<KnowledgeAgentVo> page(PageQuery query) {
        LambdaQueryWrapper<KnowledgeAgent> wrapper = new LambdaQueryWrapper<KnowledgeAgent>()
                .orderByDesc(KnowledgeAgent::getCreatedAt);
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(KnowledgeAgent::getName, query.getKeyword());
        }
        IPage<KnowledgeAgent> mpPage = new Page<>(query.safePage(), query.safeSize());
        IPage<KnowledgeAgent> result = agentMapper.selectPage(mpPage, wrapper);

        List<KnowledgeAgentVo> vos = result.getRecords().stream()
                .map(this::toVo).collect(Collectors.toList());
        return new PageResult<>(vos, result.getTotal());
    }

    @Override
    public KnowledgeAgentVo add(KnowledgeAgentValidate validate) {
        KnowledgeAgent agent = new KnowledgeAgent();
        agent.setId(UUID.randomUUID().toString().replace("-", ""));
        agent.setName(validate.getName());
        agent.setDescription(validate.getDescription());
        agent.setAvatar(validate.getAvatar());
        agent.setKbMode(validate.getKbMode() != null ? validate.getKbMode() : "selected");
        agent.setKnowledgeBaseIds(joinIds(validate.getKnowledgeBaseIds()));
        agent.setDocumentIds(joinIds(validate.getDocumentIds()));
        agent.setChatModelId(validate.getChatModelId());
        // 冗余：对话模型显示名（取 models 首项，即实际调用的具体模型名）
        if (validate.getChatModelId() != null) {
            var m = aiModelMapper.selectById(validate.getChatModelId());
            if (m != null) {
                agent.setChatModelName(resolveFirstModel(m));
            }
        }
        // 重排模型 id + 具体模型名（优先用前端传入且合法的 rerankModelName，否则回退首项）
        agent.setRerankModelId(validate.getRerankModelId());
        if (validate.getRerankModelId() != null) {
            var rm = aiModelMapper.selectById(validate.getRerankModelId());
            if (rm != null) {
                agent.setRerankModelName(resolveRerankModelName(rm, validate.getRerankModelName()));
            }
        }
        // 意图/改写专用模型 id + 显示名（取 models 首项，意图/改写无需像 rerank 那样细分到具体模型名）
        agent.setRewriteModelId(validate.getRewriteModelId());
        if (validate.getRewriteModelId() != null) {
            var wm = aiModelMapper.selectById(validate.getRewriteModelId());
            if (wm != null) {
                agent.setRewriteModelName(resolveFirstModel(wm));
            }
        }
        agent.setSystemPrompt(validate.getSystemPrompt());
        agent.setTemperature(validate.getTemperature() != null ? validate.getTemperature() : 0.3);
        agent.setMaxTokens(validate.getMaxTokens() != null ? validate.getMaxTokens() : 2048);
        agent.setHistoryTurns(validate.getHistoryTurns() != null ? validate.getHistoryTurns() : 4);
        agent.setEmbeddingTopK(validate.getEmbeddingTopK() != null ? validate.getEmbeddingTopK() : 10);
        agent.setVectorThreshold(validate.getVectorThreshold() != null ? validate.getVectorThreshold() : 0.2);
        agent.setKeywordThreshold(validate.getKeywordThreshold() != null ? validate.getKeywordThreshold() : 0.3);
        agent.setRerankEnabled(validate.getRerankEnabled() != null ? validate.getRerankEnabled() : 1);
        agent.setRerankTopK(validate.getRerankTopK() != null ? validate.getRerankTopK() : 5);
        agent.setRerankThreshold(validate.getRerankThreshold() != null ? validate.getRerankThreshold() : 0.3);
        // 样例查询优先匹配：默认关闭（2），避免没配样例库的智能体意外启用
        agent.setSampleQueryEnabled(validate.getSampleQueryEnabled() != null ? validate.getSampleQueryEnabled() : 2);
        agent.setSampleQueryThreshold(validate.getSampleQueryThreshold() != null ? validate.getSampleQueryThreshold() : 0.85);
        agent.setFallbackStrategy(validate.getFallbackStrategy() != null ? validate.getFallbackStrategy() : "model");
        agent.setFallbackResponse(validate.getFallbackResponse());
        agent.setWelcome(validate.getWelcome());
        agent.setSuggestedQuestions(joinQuestions(validate.getSuggestedQuestions()));
        agent.setStatus(validate.getStatus() != null ? validate.getStatus() : 1);
        LocalDateTime now = LocalDateTime.now();
        agent.setCreatedAt(now);
        agent.setUpdatedAt(now);
        agentMapper.insert(agent);
        return toVo(agent);
    }

    @Override
    public void edit(KnowledgeAgentValidate validate) {
        if (validate.getId() == null || validate.getId().isBlank()) {
            throw new BusinessException("智能体 id 不能为空");
        }
        KnowledgeAgent agent = agentMapper.selectById(validate.getId());
        if (agent == null) {
            throw new BusinessException("智能体不存在");
        }
        agent.setName(validate.getName());
        agent.setDescription(validate.getDescription());
        agent.setAvatar(validate.getAvatar());
        if (validate.getKbMode() != null) agent.setKbMode(validate.getKbMode());
        agent.setKnowledgeBaseIds(joinIds(validate.getKnowledgeBaseIds()));
        agent.setDocumentIds(joinIds(validate.getDocumentIds()));
        agent.setChatModelId(validate.getChatModelId());
        if (validate.getChatModelId() != null) {
            var m = aiModelMapper.selectById(validate.getChatModelId());
            agent.setChatModelName(m != null ? resolveFirstModel(m) : null);
        } else {
            agent.setChatModelName(null);
        }
        // 重排模型 id + 具体模型名（优先用前端传入且合法的 rerankModelName，否则回退首项；允许置空）
        agent.setRerankModelId(validate.getRerankModelId());
        if (validate.getRerankModelId() != null) {
            var rm = aiModelMapper.selectById(validate.getRerankModelId());
            agent.setRerankModelName(rm != null ? resolveRerankModelName(rm, validate.getRerankModelName()) : null);
        } else {
            agent.setRerankModelName(null);
        }
        // 意图/改写专用模型 id + 显示名（允许置空）
        agent.setRewriteModelId(validate.getRewriteModelId());
        if (validate.getRewriteModelId() != null) {
            var wm = aiModelMapper.selectById(validate.getRewriteModelId());
            agent.setRewriteModelName(wm != null ? resolveFirstModel(wm) : null);
        } else {
            agent.setRewriteModelName(null);
        }
        agent.setSystemPrompt(validate.getSystemPrompt());
        if (validate.getTemperature() != null) agent.setTemperature(validate.getTemperature());
        if (validate.getMaxTokens() != null) agent.setMaxTokens(validate.getMaxTokens());
        if (validate.getHistoryTurns() != null) agent.setHistoryTurns(validate.getHistoryTurns());
        if (validate.getEmbeddingTopK() != null) agent.setEmbeddingTopK(validate.getEmbeddingTopK());
        if (validate.getVectorThreshold() != null) agent.setVectorThreshold(validate.getVectorThreshold());
        if (validate.getKeywordThreshold() != null) agent.setKeywordThreshold(validate.getKeywordThreshold());
        if (validate.getRerankEnabled() != null) agent.setRerankEnabled(validate.getRerankEnabled());
        if (validate.getRerankTopK() != null) agent.setRerankTopK(validate.getRerankTopK());
        if (validate.getRerankThreshold() != null) agent.setRerankThreshold(validate.getRerankThreshold());
        if (validate.getSampleQueryEnabled() != null) agent.setSampleQueryEnabled(validate.getSampleQueryEnabled());
        if (validate.getSampleQueryThreshold() != null) agent.setSampleQueryThreshold(validate.getSampleQueryThreshold());
        if (validate.getFallbackStrategy() != null) agent.setFallbackStrategy(validate.getFallbackStrategy());
        agent.setFallbackResponse(validate.getFallbackResponse());
        agent.setWelcome(validate.getWelcome());
        agent.setSuggestedQuestions(joinQuestions(validate.getSuggestedQuestions()));
        if (validate.getStatus() != null) agent.setStatus(validate.getStatus());
        agent.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(agent);
    }

    @Override
    public void delete(String id) {
        KnowledgeAgent agent = agentMapper.selectById(id);
        if (agent == null) {
            return;
        }
        agentMapper.deleteById(id);
        // 级联清理该智能体的测试会话记忆（conversation_id 以 "agent:{id}:" 为前缀）
        try {
            conversationMessageMapper.delete(new LambdaQueryWrapper<ConversationMessageEntity>()
                    .likeRight(ConversationMessageEntity::getConversationId, "agent:" + id + ":"));
        } catch (Exception e) {
            log.warn("[Agent] 删除智能体 {} 测试会话记忆失败: {}", id, e.getMessage());
        }
    }

    @Override
    public KnowledgeAgentVo info(String id) {
        KnowledgeAgent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new BusinessException("智能体不存在");
        }
        return toVo(agent);
    }

    @Override
    public List<KnowledgeAgentVo> listEnabled() {
        LambdaQueryWrapper<KnowledgeAgent> wrapper = new LambdaQueryWrapper<KnowledgeAgent>()
                .eq(KnowledgeAgent::getStatus, 1)
                .orderByDesc(KnowledgeAgent::getCreatedAt);
        return agentMapper.selectList(wrapper).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public KnowledgeAgent getById(String id) {
        return agentMapper.selectById(id);
    }


    private KnowledgeAgentVo toVo(KnowledgeAgent agent) {
        KnowledgeAgentVo vo = new KnowledgeAgentVo();
        vo.setId(agent.getId());
        vo.setName(agent.getName());
        vo.setDescription(agent.getDescription());
        vo.setAvatar(agent.getAvatar());
        vo.setKbMode(agent.getKbMode());
        vo.setKnowledgeBaseIds(splitIds(agent.getKnowledgeBaseIds()));
        vo.setKnowledgeBaseNames(resolveKbNames(vo.getKnowledgeBaseIds()));
        vo.setDocumentIds(splitIds(agent.getDocumentIds()));
        vo.setChatModelId(agent.getChatModelId());
        vo.setChatModelName(agent.getChatModelName());
        vo.setSystemPrompt(agent.getSystemPrompt());
        vo.setTemperature(agent.getTemperature());
        vo.setMaxTokens(agent.getMaxTokens());
        vo.setHistoryTurns(agent.getHistoryTurns());
        vo.setEmbeddingTopK(agent.getEmbeddingTopK());
        vo.setVectorThreshold(agent.getVectorThreshold());
        vo.setKeywordThreshold(agent.getKeywordThreshold());
        vo.setRerankModelId(agent.getRerankModelId());
        vo.setRerankModelName(agent.getRerankModelName());
        vo.setRerankEnabled(agent.getRerankEnabled());
        vo.setRerankTopK(agent.getRerankTopK());
        vo.setRerankThreshold(agent.getRerankThreshold());
        vo.setSampleQueryEnabled(agent.getSampleQueryEnabled());
        vo.setSampleQueryThreshold(agent.getSampleQueryThreshold());
        vo.setRewriteModelId(agent.getRewriteModelId());
        vo.setRewriteModelName(agent.getRewriteModelName());
        vo.setFallbackStrategy(agent.getFallbackStrategy());
        vo.setFallbackResponse(agent.getFallbackResponse());
        vo.setWelcome(agent.getWelcome());
        vo.setSuggestedQuestions(splitQuestions(agent.getSuggestedQuestions()));
        vo.setStatus(agent.getStatus());
        vo.setCreatedAt(agent.getCreatedAt());
        return vo;
    }

    /** 批量解析知识库名称（展示用） */
    private List<String> resolveKbNames(List<String> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<KnowledgeBase> kbs = knowledgeBaseMapper.selectBatchIds(kbIds);
        // 保持入参顺序
        Map<String, String> idToName = new LinkedHashMap<>();
        for (KnowledgeBase kb : kbs) {
            idToName.put(kb.getId(), kb.getName());
        }
        List<String> names = new ArrayList<>(kbIds.size());
        for (String id : kbIds) {
            names.add(idToName.getOrDefault(id, ""));
        }
        return names;
    }

    /**
     * 从 ai_model 的 models 字段（逗号分隔）取第一个具体模型名；
     * models 为空时回退到模型配置名 name。
     * <p>与知识图谱页 toModelOption 逻辑一致：label = name / models 首项。
     */
    private String resolveFirstModel(AiModel m) {
        String models = m.getModels();
        if (models != null && !models.isBlank()) {
            for (String part : models.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return m.getName();
    }

    /**
     * ★ 解析重排模型的具体模型名：优先用前端传入的 rerankModelName（需属于该 ai_model 的 models 列表），
     *   否则回退到 models 首项（兼容旧数据 / 未指定子模型的场景）。
     *   <p>前端拉平 option 时 value 编码为 {@code modelId::modelName}，提交时拆出 modelName 传入此处。
     */
    private String resolveRerankModelName(AiModel m, String preferred) {
        if (preferred != null && !preferred.isBlank()) {
            String trimmed = preferred.trim();
            // 校验传入的模型名确实属于该 ai_model 的 models 列表，防止脏数据
            String models = m.getModels();
            if (models != null && !models.isBlank()) {
                for (String part : models.split(",")) {
                    if (trimmed.equals(part.trim())) {
                        return trimmed;
                    }
                }
            }
        }
        return resolveFirstModel(m);
    }


    /** List<String> → 逗号分隔串（落 knowledge_base_ids） */
    private String joinIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return ids.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(","));
    }

    /** 逗号分隔串 → List<String> */
    private List<String> splitIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * List<String> → JSON 数组串（落 suggested_questions，如 ["问题1","问题2"]）。
     * 手工拼接避免引入 ObjectMapper Bean（遵守项目 Jackson 约定）。
     */
    private String joinQuestions(List<String> questions) {
        if (questions == null || questions.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < questions.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            // 转义双引号与反斜杠
            String esc = questions.get(i)
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n");
            sb.append("\"").append(esc).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * JSON 数组串 → List<String>（容错解析）。
     */
    private List<String> splitQuestions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        String s = json.trim();
        if (s.length() < 2 || !s.startsWith("[") || !s.endsWith("]")) {
            return Collections.emptyList();
        }
        s = s.substring(1, s.length() - 1).trim();
        if (s.isEmpty()) {
            return Collections.emptyList();
        }
        // 简易按逗号切分（引号外），满足推荐问题文本不含复杂转义的常规场景
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                result.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            result.add(cur.toString().trim());
        }
        // 去掉首尾引号
        return result.stream()
                .map(t -> {
                    String tt = t.trim();
                    if (tt.length() >= 2 && tt.startsWith("\"") && tt.endsWith("\"")) {
                        tt = tt.substring(1, tt.length() - 1)
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\")
                                .replace("\\n", "\n");
                    }
                    return tt;
                })
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }
}
