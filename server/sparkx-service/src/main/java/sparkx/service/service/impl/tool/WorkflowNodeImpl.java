// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和 RAG 的知识库问答系统
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.service.service.impl.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sparkx.common.core.PageResult;
import sparkx.service.entity.tool.ToolsEntity;
import sparkx.service.entity.tool.WorkflowNodeEntity;
import sparkx.service.mapper.tool.WorkflowNodeMapper;
import sparkx.service.service.interfaces.tool.IWorkflowNodeService;
import sparkx.service.vo.tool.ToolQueryVo;
import sparkx.service.vo.tool.ToolsListVo;
import sparkx.service.vo.tool.WorkflowNodeListVo;

import java.util.LinkedList;
import java.util.List;

@Service
public class WorkflowNodeImpl implements IWorkflowNodeService {

    @Autowired
    WorkflowNodeMapper workflowNodeMapper;

    /**
     * 获取资源列表
     * @param queryVo ToolQueryVo
     * @return PageResult<WorkflowNodeListVo>
     */
    @Override
    public PageResult<WorkflowNodeListVo> getWorkflowNodeList(ToolQueryVo queryVo) {

        long pageNo   = queryVo.getPage();
        long pageSize = queryVo.getLimit();

        QueryWrapper<WorkflowNodeEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", queryVo.getType());
        queryWrapper.orderByDesc("id");

        IPage<WorkflowNodeEntity> flowListRes = workflowNodeMapper.selectPage(new Page<>(pageNo, pageSize), queryWrapper);
        List<WorkflowNodeListVo> flowVoList = new LinkedList<>();

        for (WorkflowNodeEntity entity : flowListRes.getRecords()) {

            WorkflowNodeListVo vo = new WorkflowNodeListVo();
            BeanUtils.copyProperties(entity, vo);

            flowVoList.add(vo);
        }

        return PageResult.iPageHandle(flowListRes.getTotal(), pageNo, pageSize, flowVoList);
    }
}