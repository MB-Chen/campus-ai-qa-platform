// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.system.vo.PageQuery;
import sparkx.sparkshop.system.vo.PageResult;
import sparkx.sparkshop.workflow.entity.Workflow;
import sparkx.sparkshop.workflow.entity.WorkflowRuntime;
import sparkx.sparkshop.workflow.entity.WorkflowRuntimeContext;
import sparkx.sparkshop.workflow.mapper.WorkflowMapper;
import sparkx.sparkshop.workflow.mapper.WorkflowRuntimeContextMapper;
import sparkx.sparkshop.workflow.mapper.WorkflowRuntimeMapper;
import sparkx.sparkshop.workflow.service.IWorkflowService;
import sparkx.sparkshop.workflow.validate.SaveWorkflowValidate;
import sparkx.sparkshop.workflow.validate.WorkflowMetaValidate;
import sparkx.sparkshop.workflow.vo.RuntimeContextVo;
import sparkx.sparkshop.workflow.vo.SaveWorkflowVo;
import sparkx.sparkshop.workflow.vo.WorkflowVo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 编排流程业务实现。
 */
@Slf4j
@Service
public class WorkflowServiceImpl implements IWorkflowService {

    @Resource
    private WorkflowMapper workflowMapper;

    @Resource
    private WorkflowRuntimeMapper workflowRuntimeMapper;

    @Resource
    private WorkflowRuntimeContextMapper workflowRuntimeContextMapper;

    @Override
    public PageResult<WorkflowVo> page(PageQuery query) {
        LambdaQueryWrapper<Workflow> wrapper = new LambdaQueryWrapper<Workflow>()
                .orderByDesc(Workflow::getCreatedAt);
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(Workflow::getName, query.getKeyword());
        }
        IPage<Workflow> mpPage = new Page<>(query.safePage(), query.safeSize());
        IPage<Workflow> result = workflowMapper.selectPage(mpPage, wrapper);

        List<WorkflowVo> vos = result.getRecords().stream()
                .map(this::toVo).collect(Collectors.toList());
        return new PageResult<>(vos, result.getTotal());
    }

    @Override
    public SaveWorkflowVo info(String id) {
        Workflow wf = workflowMapper.selectById(id);
        if (wf == null) {
            throw new BusinessException("编排不存在");
        }
        SaveWorkflowVo vo = new SaveWorkflowVo();
        vo.setId(wf.getId());
        vo.setFlowData(wf.getFlowData());
        return vo;
    }

    @Override
    public WorkflowVo add(WorkflowMetaValidate validate) {
        Workflow wf = new Workflow();
        wf.setId(UUID.randomUUID().toString().replace("-", ""));
        wf.setName(validate.getName() == null || validate.getName().isBlank()
                ? "未命名编排" : validate.getName());
        wf.setDescription(validate.getDescription());
        wf.setStatus(1);
        wf.setCreatedAt(LocalDateTime.now());
        wf.setUpdatedAt(LocalDateTime.now());
        workflowMapper.insert(wf);
        return toVo(wf);
    }

    @Override
    public void editMeta(WorkflowMetaValidate validate) {
        Workflow wf = workflowMapper.selectById(validate.getId());
        if (wf == null) {
            throw new BusinessException("编排不存在");
        }
        if (validate.getName() != null) {
            wf.setName(validate.getName());
        }
        if (validate.getDescription() != null) {
            wf.setDescription(validate.getDescription());
        }
        wf.setUpdatedAt(LocalDateTime.now());
        workflowMapper.updateById(wf);
    }

    @Override
    public void save(SaveWorkflowValidate validate) {
        Workflow wf = workflowMapper.selectById(validate.getId());
        if (wf == null) {
            throw new BusinessException("编排不存在");
        }
        wf.setFlowData(validate.getFlowData());
        wf.setUpdatedAt(LocalDateTime.now());
        workflowMapper.updateById(wf);
    }

    @Override
    public void delete(String id) {
        // 级联清理 runtime / context
        List<WorkflowRuntime> runtimes = workflowRuntimeMapper.selectList(
                new LambdaQueryWrapper<WorkflowRuntime>().eq(WorkflowRuntime::getWorkflowId, id));
        for (WorkflowRuntime rt : runtimes) {
            workflowRuntimeContextMapper.delete(
                    new LambdaQueryWrapper<WorkflowRuntimeContext>().eq(WorkflowRuntimeContext::getRuntimeId, rt.getId()));
        }
        if (!runtimes.isEmpty()) {
            workflowRuntimeMapper.delete(
                    new LambdaQueryWrapper<WorkflowRuntime>().eq(WorkflowRuntime::getWorkflowId, id));
        }
        workflowMapper.deleteById(id);
    }

    @Override
    public WorkflowVo copy(String id) {
        Workflow src = workflowMapper.selectById(id);
        if (src == null) {
            throw new BusinessException("编排不存在");
        }
        Workflow wf = new Workflow();
        wf.setId(UUID.randomUUID().toString().replace("-", ""));
        wf.setName((src.getName() == null ? "编排" : src.getName()) + " 副本");
        wf.setDescription(src.getDescription());
        wf.setFlowData(src.getFlowData());
        wf.setStatus(1);
        wf.setCreatedAt(LocalDateTime.now());
        wf.setUpdatedAt(LocalDateTime.now());
        workflowMapper.insert(wf);
        return toVo(wf);
    }

    @Override
    public List<RuntimeContextVo> runtimeDetail(long runtimeId) {
        List<WorkflowRuntimeContext> list = workflowRuntimeContextMapper.selectList(
                new LambdaQueryWrapper<WorkflowRuntimeContext>()
                        .eq(WorkflowRuntimeContext::getRuntimeId, runtimeId)
                        .orderByAsc(WorkflowRuntimeContext::getStep));
        return list.stream().map(ctx -> {
            RuntimeContextVo vo = new RuntimeContextVo();
            BeanUtils.copyProperties(ctx, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    private WorkflowVo toVo(Workflow wf) {
        WorkflowVo vo = new WorkflowVo();
        BeanUtils.copyProperties(wf, vo);
        return vo;
    }
}
