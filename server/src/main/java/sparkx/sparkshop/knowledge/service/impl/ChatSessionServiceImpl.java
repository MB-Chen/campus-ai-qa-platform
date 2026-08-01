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
import org.springframework.transaction.annotation.Transactional;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.entity.ChatMessage;
import sparkx.sparkshop.knowledge.entity.ChatSession;
import sparkx.sparkshop.knowledge.mapper.ChatMessageMapper;
import sparkx.sparkshop.knowledge.mapper.ChatSessionMapper;
import sparkx.sparkshop.knowledge.service.IChatSessionService;
import sparkx.sparkshop.knowledge.validate.ChatMessageSaveValidate;
import sparkx.sparkshop.knowledge.validate.ChatSessionCreateValidate;
import sparkx.sparkshop.knowledge.validate.ChatSessionUpdateValidate;
import sparkx.sparkshop.knowledge.vo.ChatMessageVo;
import sparkx.sparkshop.knowledge.vo.ChatSessionVo;
import sparkx.sparkshop.system.vo.PageQuery;
import sparkx.sparkshop.system.vo.PageResult;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 聊天会话业务实现。所有读写均按 adminId 过滤，保证不同账号互相隔离。
 */
@Slf4j
@Service
public class ChatSessionServiceImpl implements IChatSessionService {

    @Resource
    private ChatSessionMapper sessionMapper;

    @Resource
    private ChatMessageMapper messageMapper;

    @Override
    public PageResult<ChatSessionVo> page(Long adminId, PageQuery query) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getAdminId, adminId)
                .orderByDesc(ChatSession::getUpdatedAt);
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(ChatSession::getTitle, query.getKeyword());
        }
        IPage<ChatSession> mpPage = new Page<>(query.safePage(), query.safeSize());
        IPage<ChatSession> result = sessionMapper.selectPage(mpPage, wrapper);
        List<ChatSessionVo> vos = result.getRecords().stream()
                .map(this::toSessionVo).collect(Collectors.toList());
        return new PageResult<>(vos, result.getTotal());
    }

    @Override
    public ChatSessionVo info(Long adminId, String sessionId) {
        ChatSession session = loadOwned(adminId, sessionId);
        return toSessionVo(session);
    }

    @Override
    public ChatSessionVo create(Long adminId, ChatSessionCreateValidate v) {
        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID().toString().replace("-", ""));
        session.setAdminId(adminId);
        session.setAgentId(v.getAgentId());
        session.setKind(v.getKind() != null ? v.getKind() : "agent");
        session.setSource(v.getSource() != null ? v.getSource() : "chat");
        session.setDescription(v.getDescription());
        // 标题：优先显式传入，否则用 query 截断生成
        if (v.getTitle() != null && !v.getTitle().isBlank()) {
            session.setTitle(v.getTitle());
        } else if (v.getQuery() != null && !v.getQuery().isBlank()) {
            session.setTitle(truncateTitle(v.getQuery()));
        } else {
            session.setTitle("新会话");
        }
        // agent_config 透传字符串（jsonb 列，存 null 即 NULL）
        session.setAgentConfig(normalizeJson(v.getAgentConfig()));
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(session.getCreatedAt());
        sessionMapper.insert(session);
        return toSessionVo(session);
    }

    @Override
    public void update(Long adminId, String sessionId, ChatSessionUpdateValidate v) {
        ChatSession session = loadOwned(adminId, sessionId);
        if (v.getTitle() != null) {
            session.setTitle(v.getTitle());
        }
        if (v.getDescription() != null) {
            session.setDescription(v.getDescription());
        }
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long adminId, String sessionId) {
        loadOwned(adminId, sessionId);
        // 级联删消息
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearMessages(Long adminId, String sessionId) {
        loadOwned(adminId, sessionId);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
        // 刷新会话更新时间，让列表排序靠后
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    @Override
    public List<ChatMessageVo> messages(Long adminId, String sessionId) {
        loadOwned(adminId, sessionId);
        // ★ 用原生 SQL 查询（refs 是 PG 保留字别名，selectList 拼的 SQL 会语法错）
        List<ChatMessage> list = messageMapper.selectBySession(sessionId);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toMessageVo).collect(Collectors.toList());
    }

    @Override
    public Long saveMessage(Long adminId, String sessionId, ChatMessageSaveValidate v) {
        loadOwned(adminId, sessionId);
        // ★ jsonb 列走原生 SQL（CAST AS jsonb），BaseMapper.insert 会报类型不匹配
        LocalDateTime now = LocalDateTime.now();
        messageMapper.insertJsonb(
                sessionId,
                v.getRole(),
                v.getContent(),
                normalizeJson(v.getReferences()),
                normalizeJson(v.getStageData()),
                normalizeJson(v.getWorkflowSteps()),
                v.getTotalCost(),
                v.getTotalTokens(),
                now);
        // 落库后刷新会话更新时间
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setUpdatedAt(now);
        sessionMapper.updateById(session);
        // 返回最新消息 id（取该会话最大 id 兜底）
        List<ChatMessage> latest = messageMapper.selectBySession(sessionId);
        return (latest != null && !latest.isEmpty()) ? latest.get(latest.size() - 1).getId() : null;
    }

    // ==================== 内部辅助 ====================

    /** 取会话并校验归属当前用户，不属于则抛业务异常 */
    private ChatSession loadOwned(Long adminId, String sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || (adminId != null && !adminId.equals(session.getAdminId()))) {
            throw new BusinessException("会话不存在或无权限");
        }
        return session;
    }

    /** query 前 20 字截断生成标题，超长补省略号 */
    private String truncateTitle(String query) {
        return query.length() > 20 ? query.substring(0, 20) + "..." : query;
    }

    /** JSON 列归一：空白字符串当 null，避免把空串写进 jsonb */
    private String normalizeJson(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private ChatSessionVo toSessionVo(ChatSession s) {
        ChatSessionVo vo = new ChatSessionVo();
        vo.setId(s.getId());
        vo.setTitle(s.getTitle());
        vo.setDescription(s.getDescription());
        vo.setSource(s.getSource());
        vo.setKind(s.getKind());
        vo.setAgentId(s.getAgentId());
        // agentConfig 走 @JsonRawValue 原样输出；为 null 时 @JsonRawValue 返回 null
        vo.setAgentConfig(s.getAgentConfig());
        vo.setCreatedAt(s.getCreatedAt());
        vo.setUpdatedAt(s.getUpdatedAt());
        return vo;
    }

    private ChatMessageVo toMessageVo(ChatMessage m) {
        ChatMessageVo vo = new ChatMessageVo();
        vo.setId(m.getId());
        vo.setRole(m.getRole());
        vo.setContent(m.getContent());
        vo.setReferences(m.getReferences());
        vo.setStageData(m.getStageData());
        vo.setWorkflowSteps(m.getWorkflowSteps());
        vo.setTotalCost(m.getTotalCost());
        vo.setTotalTokens(m.getTotalTokens());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }
}
