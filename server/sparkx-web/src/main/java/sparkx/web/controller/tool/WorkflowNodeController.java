// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和 RAG 的知识库问答系统
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.web.controller.tool;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sparkx.common.core.AjaxResult;
import sparkx.common.core.PageResult;
import sparkx.service.service.interfaces.tool.IWorkflowNodeService;
import sparkx.service.vo.tool.ToolQueryVo;
import sparkx.service.vo.tool.WorkflowNodeListVo;

@RequestMapping("api/flowNode")
@RestController
public class WorkflowNodeController {

    @Autowired
    IWorkflowNodeService iWorkflowNodeService;

    /**
     * 编排资源列表
     */
    @GetMapping("/list")
    public AjaxResult<PageResult<WorkflowNodeListVo>> index(ToolQueryVo queryVo) {

        return AjaxResult.success(iWorkflowNodeService.getWorkflowNodeList(queryVo));
    }
}