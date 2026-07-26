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
import java.util.ArrayList;
import java.util.List;

/**
 * 试切预览进度（缓存于 Redis，前端轮询读取）。
 *
 * <p>与 {@link DocumentSaveProgressVo} 同构，但多一个 {@link #result} 字段：
 * mineru 等慢解析引擎异步处理时，每完成一个文件就把其 {@link DocumentPreviewVo}
 * 追加进 result，前端轮询到 {@code status=done} 时直接取 result 渲染切片，
 * 无需二次请求。
 */
@Data
public class PreviewProgressVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** processing / done / failed */
    private String status;

    /** 总文件数 */
    private int total;

    /** 已处理文件数（success + failed） */
    private int done;

    /** 成功文件数 */
    private int success;

    /** 失败文件数 */
    private int failed;

    /** 兜底错误信息（任务级失败时填写） */
    private String message;

    /**
     * 最近完成文件的阶段耗时快照（parse/chunk），让前端在轮询时能看到实时阶段进度。
     * 字段非累计，仅反映当前/最近一个文件的解析与分块耗时。
     */
    private List<IngestionSummary.StageStat> stages;

    /** 已完成文件的切片预览（processing 时逐步填充，done 时为完整结果） */
    private List<DocumentPreviewVo> result = new ArrayList<>();

    public static PreviewProgressVo processing(int total) {
        PreviewProgressVo p = new PreviewProgressVo();
        p.status = "processing";
        p.total = total;
        return p;
    }
}
