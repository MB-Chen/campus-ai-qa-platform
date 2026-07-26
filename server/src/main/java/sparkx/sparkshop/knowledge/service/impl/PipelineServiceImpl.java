// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.entity.IngestionPipelineNode;
import sparkx.sparkshop.knowledge.entity.IngestionTaskNode;
import sparkx.sparkshop.knowledge.mapper.IngestionPipelineNodeMapper;
import sparkx.sparkshop.knowledge.mapper.IngestionTaskNodeMapper;
import sparkx.sparkshop.knowledge.service.IPipelineService;
import sparkx.sparkshop.knowledge.validate.PipelineNodeValidate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库流水线业务实现（节点定义 / 任务日志）。
 */
@Service
public class PipelineServiceImpl implements IPipelineService {

    @Resource
    private IngestionPipelineNodeMapper ingestionPipelineNodeMapper;

    @Resource
    private IngestionTaskNodeMapper ingestionTaskNodeMapper;

    /** 查询流水线节点列表（按 pipelineId）。 */
    @Override
    public List<IngestionPipelineNode> list(String pipelineId) {
        LambdaQueryWrapper<IngestionPipelineNode> wrapper = new LambdaQueryWrapper<IngestionPipelineNode>()
                .eq(IngestionPipelineNode::getPipelineId, pipelineId);
        return ingestionPipelineNodeMapper.selectList(wrapper);
    }

    /** 保存流水线节点定义（先删后插，全量覆盖）。 */
    @Override
    public void save(PipelineNodeValidate validate) {
        // 先删除该 pipelineId 下的旧节点
        ingestionPipelineNodeMapper.delete(new LambdaQueryWrapper<IngestionPipelineNode>()
                .eq(IngestionPipelineNode::getPipelineId, validate.getPipelineId()));
        // 再批量插入新节点
        if (validate.getNodes() != null) {
            for (PipelineNodeValidate.NodeItem item : validate.getNodes()) {
                IngestionPipelineNode node = new IngestionPipelineNode();
                node.setPipelineId(validate.getPipelineId());
                node.setNodeId(item.getNodeId());
                node.setNodeType(item.getNodeType());
                node.setNextNodeId(item.getNextNodeId());
                node.setSettingsJson(item.getSettingsJson());
                node.setConditionJson(item.getConditionJson());
                node.setEnabled(item.getEnabled() == null || item.getEnabled());
                node.setCreatedAt(LocalDateTime.now());
                ingestionPipelineNodeMapper.insert(node);
            }
        }
    }

    /** 查询任务节点执行日志（按 taskId，按创建时间升序）。 */
    @Override
    public List<IngestionTaskNode> logs(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BusinessException("taskId 不能为空");
        }
        LambdaQueryWrapper<IngestionTaskNode> wrapper = new LambdaQueryWrapper<IngestionTaskNode>()
                .eq(IngestionTaskNode::getTaskId, taskId)
                .orderByAsc(IngestionTaskNode::getCreatedAt);
        return ingestionTaskNodeMapper.selectList(wrapper);
    }
}
