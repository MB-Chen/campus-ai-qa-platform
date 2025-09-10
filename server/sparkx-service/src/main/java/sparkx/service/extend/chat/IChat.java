// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.service.extend.chat;

import dev.langchain4j.service.TokenStream;
import sparkx.service.entity.application.ApplicationEntity;
import sparkx.service.validate.application.ApplicationChatValidate;

import java.net.UnknownHostException;

public interface IChat {

    TokenStream streamChat(ApplicationEntity applicationInfo, ApplicationChatValidate validate) throws UnknownHostException;
}
