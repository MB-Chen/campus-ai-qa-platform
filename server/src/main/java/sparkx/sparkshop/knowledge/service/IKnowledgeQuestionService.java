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

import org.springframework.web.multipart.MultipartFile;
import sparkx.sparkshop.knowledge.entity.KnowledgeQuestion;

import java.util.List;
import sparkx.sparkshop.knowledge.validate.QuestionListValidate;
import sparkx.sparkshop.knowledge.validate.QuestionValidate;
import sparkx.sparkshop.knowledge.validate.RelationValidate;
import sparkx.sparkshop.knowledge.vo.QuestionImportProgressVo;
import sparkx.sparkshop.knowledge.vo.TaskIdVo;
import sparkx.sparkshop.system.vo.PageResult;

/**
 * 知识库问题（Q&A）业务接口。
 */
public interface IKnowledgeQuestionService {

    /**
     * 问题列表（按知识库），分页。
     */
    PageResult<KnowledgeQuestion> page(QuestionListValidate query);

    /**
     * 手动新增问题（走 embedding 向量化入库，与 AI 生成问题对齐）。
     */
    void add(QuestionValidate validate);

    /**
     * 编辑问题（同步刷新 chunk 的 content / tsv / 向量）。
     */
    void edit(QuestionValidate validate);

    /**
     * 删除问题（同步删除关联的 chunk 切片）。
     */
    void remove(Long id);

    /**
     * 关联问题到子块。
     */
    void doRelation(RelationValidate validate);

    /**
     * 批量导入问题（Excel）：解析 + 清洗 + 派发异步处理，立即返回 TaskIdVo。
     *
     * @param kbId 目标知识库
     * @param file Excel 文件（.xls/.xlsx），单列「问题」
     * @return 任务回执（taskId，前端用它轮询进度）
     */
    TaskIdVo importQuestions(String kbId, MultipartFile file);

    /**
     * 查询导入进度（从 Redis 读）。
     */
    QuestionImportProgressVo getImportProgress(String taskId);

    /**
     * 异步执行批量导入（由 {@link #importQuestions} 通过代理调用，外部不应直接调）。
     *
     * @param kbId      知识库 id
     * @param questions 已清洗去重的问题列表
     * @param taskId    进度桶 taskId
     */
    void doImportQuestionsAsync(String kbId, List<String> questions, String taskId);
}
