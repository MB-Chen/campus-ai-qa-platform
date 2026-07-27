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

import sparkx.sparkshop.knowledge.entity.KnowledgeAgent;
import sparkx.sparkshop.knowledge.validate.KnowledgeAgentValidate;
import sparkx.sparkshop.knowledge.vo.KnowledgeAgentVo;
import sparkx.sparkshop.system.vo.PageQuery;
import sparkx.sparkshop.system.vo.PageResult;

import java.util.List;

/**
 * 知识库智能体业务接口（CRUD）。
 */
public interface IKnowledgeAgentService {

    /** 分页查询智能体列表 */
    PageResult<KnowledgeAgentVo> page(PageQuery query);

    /** 新增智能体 */
    KnowledgeAgentVo add(KnowledgeAgentValidate validate);

    /** 编辑智能体 */
    void edit(KnowledgeAgentValidate validate);

    /** 删除智能体（级联清理测试会话记忆） */
    void delete(String id);

    /** 详情 */
    KnowledgeAgentVo info(String id);

    /** 启用状态列表（测试页下拉用） */
    List<KnowledgeAgentVo> listEnabled();

    /** 按 id 取实体（聊天/评估内部用） */
    KnowledgeAgent getById(String id);
}
