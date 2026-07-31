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

import sparkx.sparkshop.knowledge.validate.ChatMessageSaveValidate;
import sparkx.sparkshop.knowledge.validate.ChatSessionCreateValidate;
import sparkx.sparkshop.knowledge.validate.ChatSessionUpdateValidate;
import sparkx.sparkshop.knowledge.vo.ChatMessageVo;
import sparkx.sparkshop.knowledge.vo.ChatSessionVo;
import sparkx.sparkshop.system.vo.PageQuery;
import sparkx.sparkshop.system.vo.PageResult;

import java.util.List;

/**
 * 聊天会话业务接口。所有读写均按当前登录 adminId 过滤。
 */
public interface IChatSessionService {

    /** 当前用户会话分页列表（按 updated_at 倒序） */
    PageResult<ChatSessionVo> page(Long adminId, PageQuery query);

    /** 会话详情（校验归属） */
    ChatSessionVo info(Long adminId, String sessionId);

    /** 新建会话，返回带 id 的 VO。标题取 query 前 20 字截断 */
    ChatSessionVo create(Long adminId, ChatSessionCreateValidate validate);

    /** 更新标题 / 描述 */
    void update(Long adminId, String sessionId, ChatSessionUpdateValidate validate);

    /** 删除会话（级联删消息） */
    void delete(Long adminId, String sessionId);

    /** 清空会话消息（会话本身保留） */
    void clearMessages(Long adminId, String sessionId);

    /** 会话内全部消息（按 id 升序） */
    List<ChatMessageVo> messages(Long adminId, String sessionId);

    /** 落库单条消息，返回消息 id */
    Long saveMessage(Long adminId, String sessionId, ChatMessageSaveValidate validate);
}
