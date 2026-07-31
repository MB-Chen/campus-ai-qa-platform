// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import sparkx.sparkshop.knowledge.entity.ChatMessage;

/**
 * 聊天消息 Mapper。会话内全量消息（按 id 升序）用 LambdaQueryWrapper 查询。
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
