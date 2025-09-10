// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.service.service.impl.tool;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sparkx.common.core.PageResult;
import sparkx.common.exception.BusinessException;
import sparkx.common.utils.Tool;
import sparkx.service.entity.tool.WorkflowNodeEntity;
import sparkx.service.mapper.tool.WorkflowNodeMapper;
import sparkx.service.service.interfaces.tool.IWorkflowNodeService;
import sparkx.service.validate.tool.WorkflowNodeDataValidate;
import sparkx.service.vo.tool.ToolQueryVo;
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

    /**
     * 添加资源节点
     * @param validate WorkflowNodeDataValidate
     */
    @Override
    @Transactional
    public void addWorkflowNode(WorkflowNodeDataValidate validate) {

        // 校验数据库字段
        for (WorkflowNodeDataValidate.TableField field : validate.getNodeData()) {

            if (field.getName().isBlank() || !validate.getName().matches("^[a-zA-Z_]+$")
                    || field.getName().length() > 50 || field.getName().length() < 2) {
                throw new BusinessException("字段名称为英文字母和下划线长度为2-50之间");
            }

            if (field.getDesc().isBlank() || field.getName().length() > 100 || field.getName().length() < 2) {
                throw new BusinessException("字段描述长度为2-50之间");
            }

            if (field.getDesc().isBlank()) {
                throw new BusinessException("字段类型不能为空");
            }
        }

        String tableName = validate.getName();
        // 检测这个表是否存在
        int tableNum = workflowNodeMapper.existsTable(tableName);
        if (tableNum > 0) {
            throw new BusinessException("数据表" + tableName + "已经存在");
        }

        // 记录节点信息
        WorkflowNodeEntity workflowNodeEntity = new WorkflowNodeEntity();
        BeanUtils.copyProperties(validate, workflowNodeEntity);
        workflowNodeEntity.setNodeData(JSONUtil.toJsonStr(validate.getNodeData()));
        workflowNodeEntity.setCreateTime(Tool.nowDateTime());
        workflowNodeMapper.insert(workflowNodeEntity);

        // 创建实体表
        String tableDDL = "CREATE TABLE \"public\".\"" + tableName + "\" (";
        tableDDL += "\"id\" INT4 NOT NULL GENERATED ALWAYS AS IDENTITY (INCREMENT 1 MINVALUE 1 MAXVALUE 2147483647 START 1 CACHE 1),";

        String columnStr = "";
        for (WorkflowNodeDataValidate.TableField field : validate.getNodeData()) {
            String tableFieldStr = "";
            if (field.getType().equals("String")) {
                tableFieldStr = " VARCHAR (155) COLLATE \"pg_catalog\".\"default\" DEFAULT '':: CHARACTER VARYING,";
            } else if (field.getType().equals("Timestamp")) {
                tableFieldStr = " TIMESTAMP (6),";
            } else if (field.getType().equals("Integer")) {
                tableFieldStr = " int4 DEFAULT 0,";
            } else if (field.getType().equals("Double")) {
                tableFieldStr = " numeric(10,2) DEFAULT 0.00,";
            }

            if (field.getName().equals("id") || field.getName().equals("create_time")
                    || field.getName().equals("update_time")) {
                continue;
            }

            columnStr += "COMMENT ON COLUMN \"public\".\"" + tableName + "\".\"" + field.getName() + "\" IS '" + field.getDesc() + "';";
            tableDDL += "\"" + field.getName() + "\"" + tableFieldStr;
        }

        tableDDL += "\"create_time\" timestamp(6),";
        tableDDL += "\"update_time\" timestamp(6),";
        tableDDL += "CONSTRAINT \"" + tableName + "_pkey\" PRIMARY KEY (\"id\")";
        tableDDL += ");";

        tableDDL += "COMMENT ON COLUMN \"public\".\"" + tableName + "\".\"id\" IS 'id';";
        tableDDL += columnStr;
        tableDDL += "COMMENT ON COLUMN \"public\".\"" + tableName + "\".\"create_time\" IS '创建时间';";
        tableDDL += "COMMENT ON COLUMN \"public\".\"" + tableName + "\".\"update_time\" IS '更新时间';";
        tableDDL += "COMMENT ON TABLE \"public\".\"" + tableName + "\" IS '" + validate.getDescription() + "表';";

        workflowNodeMapper.createTable(tableDDL);
    }
}