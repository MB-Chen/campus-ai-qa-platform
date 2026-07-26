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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 试切预览结果：一个文档对应其切片列表
 */
@Data
@Schema(description = "文档试切预览结果")
public class DocumentPreviewVo implements Serializable {

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    /**
     * 预览阶段已把原文件存 MinIO 后的对象 key。前端透传给 /save，
     * 使持久化阶段不必重复上传原文件（multipart 字节请求结束后失效，
     * preview 阶段是唯一确定有原始字节的时机）。可空（极少数失败回退情况）。
     */
    @Schema(description = "原文件 MinIO 对象 key（透传给 save 用）")
    private String storageUrl;

    /**
     * 预览阶段已计的 parse/chunk 阶段耗时快照（前端透传给 save 合并到 ingestion_summary）。
     * 可空（兜底/异常路径）。
     */
    @Schema(description = "解析与分块阶段耗时（透传给 save）")
    private List<IngestionSummary.StageStat> stages;

    /**
     * 实际解析该文档使用的引擎（按扩展名 + parserEngineRules 推导，非前端硬编码的 tika）。
     * 透传给 save，落 ingestion_summary.engine，避免统计图显示错误的引擎名。
     * 表格类（xls/xlsx/csv）走 SpreadsheetRowSplitter，引擎记为 poi 兜底。
     */
    @Schema(description = "实际解析引擎（透传给 save）")
    private String engine;

    @Schema(description = "切片列表")
    private List<PreviewChunkVo> chunks;

    public DocumentPreviewVo() {}

    public DocumentPreviewVo(String fileName, Long fileSize, List<PreviewChunkVo> chunks) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.chunks = chunks;
    }

    public DocumentPreviewVo(String fileName, Long fileSize, String storageUrl, List<PreviewChunkVo> chunks) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.storageUrl = storageUrl;
        this.chunks = chunks;
    }
}
