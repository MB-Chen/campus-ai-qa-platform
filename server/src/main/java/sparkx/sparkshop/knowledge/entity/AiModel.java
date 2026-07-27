// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 模型配置实体（新增，对标 spark-x 模型管理）。
 *
 * 页面可编辑，运行时从本表读取多候选模型，替代 sparkxV2 的 yml 配置。
 * type: 1=对话 2=向量/嵌入 3=重排 4=视觉(VLM)。
 *
 * credential / options 为 JSON 文本（页面动态渲染字段）：
 *  - credential: [{"field":"apiKey","value":"sk-xxx"}]
 *  - options:    [{"field":"url","value":"https://..."},{"field":"temperature","range":[0,2],"value":0.3}, ...]
 */
@Data
@TableName("ai_model")
public class AiModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 模型厂家/名称（如 OpenAI、通义千问、Ollama） */
    @TableField(value = "name")
    private String name;

    /** 1对话 2向量 3重排 4视觉(VLM) */
    @TableField(value = "type")
    private Integer type;

    /** openai / ollama */
    @TableField(value = "provider")
    private String provider;

    /** 凭证 JSON 数组 */
    @TableField(value = "credential")
    private String credential;

    /** 可用模型名（逗号分隔）；重排模型只允许一个 */
    @TableField(value = "models")
    private String models;

    /** 函数调用能力（逗号分隔，对话模型用） */
    @TableField(value = "function_calling")
    private String functionCalling;

    /** 选项 JSON 数组：url/temperature/maxOutputTokens 等 */
    @TableField(value = "options")
    private String options;

    /** 1启用 2禁用 */
    @TableField(value = "status")
    private Integer status;

    /** 候选优先级（数值小者优先，多模型容错降级用） */
    @TableField(value = "priority")
    private Integer priority;

    /** 是否支持深度思考 0否 1是 */
    @TableField(value = "supports_thinking")
    private Integer supportsThinking;

    @TableField(value = "create_time")
    private LocalDateTime createTime;

    @TableField(value = "update_time")
    private LocalDateTime updateTime;
}
