// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文档入库进度（缓存于 Redis，前端轮询读取）。
 *
 * <p>与 {@link QuestionImportProgressVo} 同构，用于 {@code /knowledge/document/save}
 * 异步入库任务的进度回执。
 */
@Data
public class DocumentSaveProgressVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** processing / done / failed */
    private String status;

    /** 总文档数（documentList 大小） */
    private int total;

    /** 已处理文档数（success + failed） */
    private int done;

    /** 成功文档数 */
    private int success;

    /** 失败文档数 */
    private int failed;

    /** 兜底错误信息（任务级失败时填写） */
    private String message;

    /**
     * 最近完成文档的阶段耗时快照（parse/chunk/persist），让前端轮询时实时看到入库耗时。
     * 非累计，仅反映当前/最近一个文档的入库阶段耗时。
     */
    private List<IngestionSummary.StageStat> stages;

    public static DocumentSaveProgressVo processing(int total) {
        DocumentSaveProgressVo p = new DocumentSaveProgressVo();
        p.status = "processing";
        p.total = total;
        return p;
    }
}
