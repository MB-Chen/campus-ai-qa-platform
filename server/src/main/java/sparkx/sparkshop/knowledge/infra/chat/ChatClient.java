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
 * 供应商适配接口（文档 5.10.1）。
 * 假设所有供应商走 OpenAI 兼容协议，{@link ModelTarget} 携带 provider+model+url+apiKey，
 * 上层不关心 URL/API Key/协议字段差异。
 *
 * 各具体 client 通过 {@link AbstractOpenAIChatClient} 模板方法复用，仅 ~45 行。
 */
public interface ChatClient {

    /** 供应商标识：openai / ollama / bailian / siliconflow / aihubmix */
    String provider();

    /**
     * 同步对话。
     *
     * @param request 调用请求
     * @param target  目标模型（含 url/apiKey）
     * @return 模型回复文本
     */
    String chat(LlmChatRequest request, ModelTarget target);

    /**
     * 流式对话。读取 SSE 流并通过 callback 回调，返回取消句柄。
     *
     * @param request  调用请求
     * @param callback 流式回调（可能被 ProbeStreamBridge 包装）
     * @param target   目标模型
     * @return 取消句柄
     */
    StreamCancellationHandle streamChat(LlmChatRequest request, StreamCallback callback, ModelTarget target);
}
