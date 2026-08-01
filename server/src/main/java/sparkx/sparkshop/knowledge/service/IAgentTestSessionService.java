// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.service;

import sparkx.sparkshop.knowledge.validate.AgentTestMessageSaveValidate;
import sparkx.sparkshop.knowledge.validate.AgentTestSessionSaveValidate;
import sparkx.sparkshop.knowledge.vo.AgentTestMessageVo;
import sparkx.sparkshop.knowledge.vo.AgentTestSessionVo;

import java.util.List;

/**
 * 智能体测试对话会话业务接口。所有读写均按当前登录 adminId 过滤，与业务对话表隔离。
 */
public interface IAgentTestSessionService {

    /** 某智能体下当前用户的测试会话列表（按 updated_at 倒序） */
    List<AgentTestSessionVo> listSessions(Long adminId, String agentId);

    /** 新建测试会话，返回带 id 的 VO */
    AgentTestSessionVo createSession(Long adminId, AgentTestSessionSaveValidate validate);

    /** 更新会话标题 */
    void updateSession(Long adminId, String sessionId, AgentTestSessionSaveValidate validate);

    /** 删除会话（级联删消息） */
    void deleteSession(Long adminId, String sessionId);

    /** 会话内全部消息（按 id 升序） */
    List<AgentTestMessageVo> getMessages(Long adminId, String sessionId);

    /** 批量落库消息（一次问答 user + assistant 两条），并刷新会话 updated_at */
    void saveMessages(Long adminId, String sessionId, List<AgentTestMessageSaveValidate> messages);
}
