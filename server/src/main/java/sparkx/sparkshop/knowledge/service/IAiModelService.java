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

import sparkx.sparkshop.knowledge.config.AiModelProperties;
import sparkx.sparkshop.knowledge.entity.AiModel;
import sparkx.sparkshop.knowledge.infra.chat.ModelTarget;
import sparkx.sparkshop.knowledge.validate.AiModelValidate;
import sparkx.sparkshop.knowledge.vo.ModelTestVo;

import java.util.List;
import java.util.Map;

/**
 * AI 模型业务接口（对话/向量/重排/视觉 LLM 模型管理）。
 *
 * <p>从 ai_model 表读取多候选模型，替代 sparkxV2 的 yml 配置；
 * 装配成 ModelCandidate 列表交给容错层（ModelSelector / RoutingLLMService）。
 *
 * <p>type：1对话 / 2向量 / 3重排 / 4视觉(VLM)；status：1启用 / 2禁用；
 * priority：数值小者优先（决定降级顺序与默认模型）。
 */
public interface IAiModelService {

    /** 对话模型候选（type=1，status=1，按 priority ASC） */
    List<AiModelProperties.ModelCandidate> getChatCandidates();

    /** 嵌入/向量化模型候选（type=2） */
    List<AiModelProperties.ModelCandidate> getEmbeddingCandidates();

    /** 重排模型候选（type=3，model 用整个 models 字段，重排只允许单个） */
    List<AiModelProperties.ModelCandidate> getRerankCandidates();

    /** 视觉模型候选（type=4） */
    List<AiModelProperties.ModelCandidate> getVlmCandidates();

    /** 默认对话模型 id（最低 priority 的启用对话模型），无则 null */
    String getDefaultChatModelId();

    /** 默认向量模型 id，无则 null */
    String getDefaultEmbeddingModelId();

    /** 默认重排模型 id，无则 null */
    String getDefaultRerankModelId();

    /** 默认视觉模型 id，无则 null */
    String getDefaultVlmModelId();

    /** 按主键取模型（运营后台编辑用） */
    AiModel getById(Integer id);

    /**
     * 按 id 取对话模型的路由目标（ModelTarget）。
     * 不经过候选过滤（启用/熔断），调用方明确指定要用这个模型；
     * id 为空 / 模型不存在 / 非对话类型 / model 名为空 时返回 null。
     */
    ModelTarget getChatTarget(Integer id);

    /** 刷新缓存占位（页面编辑模型后调用） */
    void refresh();

    /** 模型列表（按类型，可选状态过滤，按 priority ASC） */
    List<Map<String, Object>> list(Integer type, Integer status);

    /** 模型详情 */
    Map<String, Object> info(Integer id);

    /** 新增模型 */
    void add(AiModelValidate v);

    /** 编辑模型 */
    void edit(AiModelValidate v);

    /** 删除模型 */
    void remove(Integer id);

    /** 切换模型启停 */
    void switchStatus(Integer id, Integer status);

    /** 启用的重排模型列表（type=3,status=1） */
    List<Map<String, Object>> rerankList();

    /** 测试模型连通性（按已保存的模型 id） */
    ModelTestVo test(Integer id);

    /** 测试模型连通性（按表单参数，无需先保存） */
    ModelTestVo testConnect(AiModelValidate v);
}
