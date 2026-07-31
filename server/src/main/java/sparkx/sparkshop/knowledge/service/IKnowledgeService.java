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

import sparkx.sparkshop.knowledge.validate.HitTestValidate;
import sparkx.sparkshop.knowledge.validate.KnowledgeBaseEditValidate;
import sparkx.sparkshop.knowledge.validate.KnowledgeBaseValidate;
import sparkx.sparkshop.knowledge.vo.HitTestVo;
import sparkx.sparkshop.knowledge.vo.KnowledgeBaseVo;
import sparkx.sparkshop.system.vo.PageQuery;
import sparkx.sparkshop.system.vo.PageResult;

import java.util.List;

/**
 * 知识库业务接口（KB CRUD + 命中测试）
 */
public interface IKnowledgeService {

    /**
     * 分页查询知识库列表
     */
    PageResult<KnowledgeBaseVo> page(PageQuery query);

    /**
     * 新增知识库（按 embeddingModelId 解析模型名与维度）
     */
    KnowledgeBaseVo add(KnowledgeBaseValidate validate);

    /**
     * 编辑知识库（不修改 embeddingModel）
     */
    void edit(KnowledgeBaseEditValidate validate);

    /**
     * 删除知识库（级联删除 chunks/documents/parent_chunks/questions + MinIO 对象）
     */
    void delete(String id);

    /**
     * 切换知识库启停状态（1启用 / 2禁用）
     */
    void switchStatus(String id, Integer status);

    /**
     * 命中测试
     */
    List<HitTestVo> hitTest(HitTestValidate validate);
}
