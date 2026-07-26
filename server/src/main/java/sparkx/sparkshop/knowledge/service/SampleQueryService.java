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

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import sparkx.sparkshop.knowledge.entity.SampleQuery;
import sparkx.sparkshop.knowledge.entity.SampleQueryConfig;
import sparkx.sparkshop.knowledge.validate.SampleQueryConfigValidate;
import sparkx.sparkshop.knowledge.validate.SampleQueryListValidate;
import sparkx.sparkshop.knowledge.validate.SampleQueryValidate;
import sparkx.sparkshop.knowledge.validate.SampleQueryVectorizeBatchValidate;
import sparkx.sparkshop.knowledge.vo.SampleQueryImportResultVo;
import sparkx.sparkshop.knowledge.vo.SampleQueryVectorizeProgressVo;
import sparkx.sparkshop.knowledge.vo.TaskIdVo;
import sparkx.sparkshop.system.vo.PageResult;

/**
 * 样例查询业务接口。
 *
 * <p>覆盖：CRUD / Excel 导入 / Excel 导出 / 单条向量化 / 批量向量化（异步 + 进度轮询）/ 全局配置。
 */
public interface SampleQueryService {

    /** 分页列表（支持 keyword / status / vectorized 过滤）。 */
    PageResult<SampleQuery> page(SampleQueryListValidate query);

    /** 新增（不向量化，向量化由调用方后续触发）。 */
    void add(SampleQueryValidate validate);

    /** 编辑（问题变化时同步重新向量化已存在的 chunk）。 */
    void edit(SampleQueryValidate validate);

    /** 删除（同步删除关联的 chunk 切片）。 */
    void remove(Long id);

    /** Excel 批量导入（同步，不触发向量化，导入后由用户主动点「批量向量化」）。 */
    SampleQueryImportResultVo importExcel(MultipartFile file);

    /** Excel 导出（按当前过滤条件导出全部，不分页）。 */
    void exportExcel(SampleQueryListValidate query, HttpServletResponse response);

    /** 单条向量化（按全局配置的 embedding 模型）。 */
    void vectorize(Long id);

    /**
     * 批量向量化（按 id 列表；id 为空表示全部启用项）。
     * 同步初始化进度桶后异步执行，返回 TaskIdVo 供前端轮询。
     */
    TaskIdVo vectorizeBatch(SampleQueryVectorizeBatchValidate req);

    /** 异步执行批量向量化（@Async，由代理调用保证生效）。 */
    void doVectorizeBatchAsync(java.util.List<Long> ids, String taskId);

    /** 查询批量向量化进度。 */
    SampleQueryVectorizeProgressVo getVectorizeProgress(String taskId);

    /** 读取全局配置。 */
    SampleQueryConfig getConfig();

    /** 保存全局配置（upsert 到 id=1）。 */
    void saveConfig(SampleQueryConfigValidate validate);
}
