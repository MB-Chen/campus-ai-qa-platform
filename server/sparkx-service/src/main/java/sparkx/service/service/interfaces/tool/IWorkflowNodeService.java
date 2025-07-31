// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和 RAG 的知识库问答系统
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.service.service.interfaces.tool;

import sparkx.common.core.PageResult;
import sparkx.service.vo.tool.ToolQueryVo;
import sparkx.service.vo.tool.WorkflowNodeListVo;

public interface IWorkflowNodeService {

    /**
     * 获取资源列表
     * @param queryVo ToolQueryVo
     * @return PageResult<WorkflowNodeListVo>
     */
    PageResult<WorkflowNodeListVo> getWorkflowNodeList(ToolQueryVo queryVo);
}