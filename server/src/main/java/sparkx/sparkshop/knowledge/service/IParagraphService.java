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

import sparkx.sparkshop.knowledge.validate.ParagraphListValidate;
import sparkx.sparkshop.knowledge.validate.ParagraphValidate;
import sparkx.sparkshop.knowledge.vo.ChunkVo;
import sparkx.sparkshop.system.vo.PageResult;

/**
 * 段落/子块业务接口（手动增删改；手动录入仅走关键词 tsv，不带向量）。
 */
public interface IParagraphService {

    /**
     * 段落列表（按知识库或文档），分页。
     */
    PageResult<ChunkVo> page(ParagraphListValidate query);

    /**
     * 手动新增段落（无向量，仅关键词）。
     */
    void add(ParagraphValidate validate);

    /**
     * 编辑段落内容。
     */
    void edit(ParagraphValidate validate);

    /**
     * 切换段落启停（active 字段记录在 metadata）。
     */
    void switchActive(String id, Integer active);

    /**
     * 删除段落。
     */
    void remove(String id);
}
