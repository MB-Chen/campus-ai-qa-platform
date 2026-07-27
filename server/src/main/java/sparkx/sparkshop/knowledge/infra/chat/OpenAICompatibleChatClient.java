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

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

/**
 * OpenAI 兼容供应商薄壳（文档 5.10.1）。
 *
 * 覆盖所有 OpenAI 协议兼容端点（OpenAI 官方 / 通义百炼 DashScope 兼容 / SiliconFlow / AIHubMix /
 * DeepSeek 等）。provider 标识 "openai"，与 application.yml 的 ai.chat.candidates[].provider 对齐。
 *
 * 通用逻辑全部继承自 {@link AbstractOpenAIChatClient}，本类只声明 provider + 注入共享的 HTTP 客户端。
 */
@Component
public class OpenAICompatibleChatClient extends AbstractOpenAIChatClient {

    private final OkHttpClient syncClient;
    private final OkHttpClient streamingClient;
    private final ExecutorService streamExec;

    public OpenAICompatibleChatClient(@Qualifier("modelSyncHttpClient") OkHttpClient syncClient,
                                      @Qualifier("modelStreamingHttpClient") OkHttpClient streamingClient,
                                      @Qualifier("streamingExecutor") ExecutorService streamExec) {
        this.syncClient = syncClient;
        this.streamingClient = streamingClient;
        this.streamExec = streamExec;
    }

    @Override
    public String provider() { return "openai"; }

    @Override
    protected OkHttpClient syncHttpClient() { return syncClient; }

    @Override
    protected OkHttpClient streamingHttpClient() { return streamingClient; }

    @Override
    protected ExecutorService streamExecutor() { return streamExec; }
}
