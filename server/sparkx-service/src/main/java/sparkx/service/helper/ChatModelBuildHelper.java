// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.service.helper;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sparkx.service.entity.application.ApplicationEntity;
import sparkx.service.entity.system.ModelsEntity;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class ChatModelBuildHelper {

    private ModelsEntity modelInfo;

    private ApplicationEntity applicationInfo;

    @Autowired
    ApplicationHelper applicationHelper;

    /**
     * 构建model
     * @param modelInfo ModelsEntity
     * @param applicationInfo ApplicationEntity
     * @return ChatModel
     */
    public ChatModel build(ModelsEntity modelInfo, ApplicationEntity applicationInfo) {

        this.modelInfo = modelInfo;
        this.applicationInfo = applicationInfo;

        // 清华智普
        if (modelInfo.getModelFlag().equals("zhipu")) {
            return buildZhiPu();
        }

        // Ollama
        if (modelInfo.getModelFlag().equals("ollama")) {
            return buildOllama();
        }

        // 通义千问
        if (modelInfo.getModelFlag().equals("qwen")) {
            return buildQwen();
        }

        // 千帆、豆包、GPT
        return buildOpenAI();
    }

    /**
     * 构建智普
     * @return ChatModel
     */
    private ChatModel buildZhiPu() {

        JSONArray jsonConfig = JSONUtil.parseArray(modelInfo.getCredential());
        String key = jsonConfig.getJSONObject(0).getStr("value");

        try {
            return ZhipuAiChatModel.builder()
                    .apiKey(key)
                    .temperature(applicationInfo.getTemperature()) // 温度
                    .model(applicationInfo.getModelName())
                    .connectTimeout(Duration.ofSeconds(60))
                    .readTimeout(Duration.ofSeconds(60))
                    .listeners(List.of(applicationHelper.chatModelObservability()))
                    .build();
        } catch (Exception e) {
            log.error("构建智谱AI聊天模型失败: {}", e.getMessage(), e);
            throw new RuntimeException("智谱AI模型构建失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建ollama
     * @return ChatModel
     */
    private ChatModel buildOllama() {

        JSONArray jsonOptions = JSONUtil.parseArray(modelInfo.getOptions());
        String url = jsonOptions.getJSONObject(1).getStr("value");

        return OllamaChatModel.builder()
                .baseUrl(url)
                .modelName(applicationInfo.getModelName())
                .build();
    }

    /**
     * 构建通义千问模型
     * @return ChatModel
     */
    private ChatModel buildQwen() {

        JSONArray jsonConfig = JSONUtil.parseArray(modelInfo.getCredential());
        String key = jsonConfig.getJSONObject(0).getStr("value");

        return QwenChatModel.builder()
                .apiKey(key)
                .modelName(applicationInfo.getModelName())
                .temperature((float) applicationInfo.getTemperature())
                .listeners(List.of(applicationHelper.chatModelObservability()))
                .build();
    }

    /**
     * 通过标准openai结构构建对象
     * @return ChatModel
     */
    private ChatModel buildOpenAI() {

        JSONArray jsonConfig = JSONUtil.parseArray(modelInfo.getCredential());
        String key = jsonConfig.getJSONObject(0).getStr("value");

        JSONArray jsonOptions = JSONUtil.parseArray(modelInfo.getOptions());
        String url = jsonOptions.getJSONObject(1).getStr("value");

        return OpenAiChatModel.builder()
                .baseUrl(url)
                .apiKey(key)
                .modelName(applicationInfo.getModelName())
                .listeners(List.of(applicationHelper.chatModelObservability()))
                .build();
    }
}
