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
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.entity.AgentTestMessage;
import sparkx.sparkshop.knowledge.entity.AgentTestSession;
import sparkx.sparkshop.knowledge.mapper.AgentTestMessageMapper;
import sparkx.sparkshop.knowledge.mapper.AgentTestSessionMapper;
import sparkx.sparkshop.knowledge.service.IAgentTestSessionService;
import sparkx.sparkshop.knowledge.validate.AgentTestMessageSaveValidate;
import sparkx.sparkshop.knowledge.validate.AgentTestSessionSaveValidate;
import sparkx.sparkshop.knowledge.vo.AgentTestMessageVo;
import sparkx.sparkshop.knowledge.vo.AgentTestSessionVo;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 智能体测试对话会话业务实现。所有读写均按 adminId 过滤，保证不同账号互相隔离。
 *
 * <p>★ 独立于业务对话 {@code ChatSessionServiceImpl}：不复用业务表，仅服务调试用的测试对话。
 */
@Slf4j
@Service
public class AgentTestSessionServiceImpl implements IAgentTestSessionService {

    @Resource
    private AgentTestSessionMapper sessionMapper;

    @Resource
    private AgentTestMessageMapper messageMapper;

    /** 某智能体下当前用户的测试会话列表（按 updated_at 倒序） */
    @Override
    public List<AgentTestSessionVo> listSessions(Long adminId, String agentId) {
        List<AgentTestSession> list = sessionMapper.selectList(new LambdaQueryWrapper<AgentTestSession>()
                .eq(AgentTestSession::getAdminId, adminId)
                .eq(AgentTestSession::getAgentId, agentId)
                .orderByDesc(AgentTestSession::getUpdatedAt));
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toSessionVo).collect(Collectors.toList());
    }

    /** 新建测试会话，返回带 id 的 VO */
    @Override
    public AgentTestSessionVo createSession(Long adminId, AgentTestSessionSaveValidate v) {
        AgentTestSession session = new AgentTestSession();
        session.setId(UUID.randomUUID().toString().replace("-", ""));
        session.setAdminId(adminId);
        session.setAgentId(v.getAgentId());
        session.setTitle((v.getTitle() != null && !v.getTitle().isBlank()) ? v.getTitle() : "新会话");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(session.getCreatedAt());
        sessionMapper.insert(session);
        return toSessionVo(session);
    }

    /** 更新会话标题 */
    @Override
    public void updateSession(Long adminId, String sessionId, AgentTestSessionSaveValidate v) {
        AgentTestSession session = loadOwned(adminId, sessionId);
        if (v.getTitle() != null) {
            session.setTitle(v.getTitle());
        }
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    /** 删除会话（级联删消息） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long adminId, String sessionId) {
        loadOwned(adminId, sessionId);
        // 级联删消息
        messageMapper.delete(new LambdaQueryWrapper<AgentTestMessage>()
                .eq(AgentTestMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }

    /** 会话内全部消息（按 id 升序） */
    @Override
    public List<AgentTestMessageVo> getMessages(Long adminId, String sessionId) {
        loadOwned(adminId, sessionId);
        List<AgentTestMessage> list = messageMapper.selectList(new LambdaQueryWrapper<AgentTestMessage>()
                .eq(AgentTestMessage::getSessionId, sessionId)
                .orderByAsc(AgentTestMessage::getId));
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toMessageVo).collect(Collectors.toList());
    }

    /** 批量落库消息（一次问答 user + assistant 两条），并刷新会话 updated_at */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMessages(Long adminId, String sessionId, List<AgentTestMessageSaveValidate> messages) {
        loadOwned(adminId, sessionId);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (AgentTestMessageSaveValidate v : messages) {
            // ★ jsonb 列走原生 SQL（CAST AS jsonb），不能走 BaseMapper.insert（String 参数会被
            //   PG JDBC 绑定为 varchar，报 "column is of type jsonb but expression is of type character varying"）
            messageMapper.insertJsonb(
                    sessionId,
                    v.getRole(),
                    v.getContent(),
                    normalizeJson(v.getReferences()),
                    normalizeJson(v.getStageData()),
                    normalizeJson(v.getStageTimings()),
                    v.getTotalCost(),
                    now);
        }
        // 落库后刷新会话更新时间，让列表排序靠后
        AgentTestSession session = new AgentTestSession();
        session.setId(sessionId);
        session.setUpdatedAt(now);
        sessionMapper.updateById(session);
    }

    /** 取会话并校验归属当前用户，不属于则抛业务异常 */
    private AgentTestSession loadOwned(Long adminId, String sessionId) {
        AgentTestSession session = sessionMapper.selectById(sessionId);
        if (session == null || (adminId != null && !adminId.equals(session.getAdminId()))) {
            throw new BusinessException("会话不存在或无权限");
        }
        return session;
    }

    /** JSON 列归一：空白字符串当 null，避免把空串写进 jsonb（PG 会报非法 JSON） */
    private String normalizeJson(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private AgentTestSessionVo toSessionVo(AgentTestSession s) {
        AgentTestSessionVo vo = new AgentTestSessionVo();
        vo.setId(s.getId());
        vo.setAgentId(s.getAgentId());
        vo.setTitle(s.getTitle());
        vo.setCreatedAt(s.getCreatedAt());
        vo.setUpdatedAt(s.getUpdatedAt());
        return vo;
    }

    private AgentTestMessageVo toMessageVo(AgentTestMessage m) {
        AgentTestMessageVo vo = new AgentTestMessageVo();
        vo.setId(m.getId());
        vo.setRole(m.getRole());
        vo.setContent(m.getContent());
        vo.setReferences(m.getReferences());
        vo.setStageData(m.getStageData());
        vo.setStageTimings(m.getStageTimings());
        vo.setTotalCost(m.getTotalCost());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }
}
