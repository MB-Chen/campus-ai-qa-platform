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

import sparkx.sparkshop.knowledge.entity.IngestionPipelineNode;
import sparkx.sparkshop.knowledge.entity.IngestionTaskNode;
import sparkx.sparkshop.knowledge.validate.PipelineNodeValidate;

import java.util.List;

/**
 * 入库流水线业务接口（节点定义 / 任务日志）。
 */
public interface IPipelineService {

    /**
     * 流水线节点列表（按 pipelineId）。
     */
    List<IngestionPipelineNode> list(String pipelineId);

    /**
     * 保存流水线节点定义（先删后插）。
     */
    void save(PipelineNodeValidate validate);

    /**
     * 任务节点日志（按 taskId）。
     */
    List<IngestionTaskNode> logs(String taskId);
}
