// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 模型容错层基础设施装配（移植自 sparkxV2）。
 *
 * 提供 ChatClient 依赖的共享 OkHttp 客户端：
 *  - {@code modelSyncHttpClient}：同步调用（较短超时）
 *  - {@code modelStreamingHttpClient}：流式调用（长读超时，SSE 逐 token）
 *
 * ChatClient 实现类（OpenAICompatibleChatClient / OllamaChatClient）注入这两个 Bean。
 */
@Configuration
public class InfraAiConfig {

    /** 同步 HTTP 客户端：连接 30s，读 300s（本地小模型推理慢，需较长超时） */
    @Bean("modelSyncHttpClient")
    public OkHttpClient modelSyncHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(300))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }

    /** 流式 HTTP 客户端：连接 30s，读 300s（SSE 长连接逐 token） */
    @Bean("modelStreamingHttpClient")
    public OkHttpClient modelStreamingHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(300))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }
}
