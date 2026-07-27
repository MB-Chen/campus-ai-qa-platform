// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.infra.chat;

/**
 * 流式回调契约（文档 5.10）。夹在 ChatClient 与业务 SSE 回调之间。
 * {@link ProbeStreamBridge} 实现本接口做首包探测缓冲。
 */
public interface StreamCallback {

    /** 正常内容 token 到达 */
    void onContent(String token);

    /** 思考过程 token（reasoning_content，部分模型支持） */
    default void onThinking(String token) { }

    /** 流正常结束 */
    default void onComplete() { }

    /** 流异常 */
    default void onError(Throwable error) { }
}
