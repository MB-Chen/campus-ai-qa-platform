// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.validate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import sparkx.sparkshop.knowledge.vo.IngestionSummary;

import java.io.Serializable;
import java.util.List;

/**
 * 保存试切结果入参：把前端编辑后的切片列表落库（建文档记录 + 逐块向量化入库）。
 *
 * <p>结构示例：
 * <pre>
 * {
 *   "kbId": "kb_xxx",
 *   "engine": "tika",
 *   "documentList": [
 *     {
 *       "fileName": "a.pdf",
 *       "fileSize": 12345,
 *       "chunks": [ {"title": "", "content": "片段正文..."} ]
 *     }
 *   ]
 * }
 * </pre>
 */
@Data
@Schema(description = "保存切片结果入参")
public class DocumentSaveValidate implements Serializable {

    @Schema(description = "知识库 id")
    @NotBlank(message = "知识库 id 不能为空")
    private String kbId;

    @Schema(description = "解析引擎（仅记录用，保存阶段不再解析，默认 tika）")
    private String engine;

    @Schema(description = "是否启用父子分块（仅记录用，保存时切片已由前端预览确定）")
    private Boolean enableParentChild;

    @Schema(description = "是否启用问题生成（保存后为每个切片生成问题并落表）")
    private Boolean enableQuestionGen;

    @Schema(description = "每个切片生成的问题数量（1-10，默认 3）")
    private Integer questionCount;

    @Schema(description = "文档+切片列表")
    @NotEmpty(message = "文档列表不能为空")
    @Valid
    private List<DocItem> documentList;

    /** 单个文档及其切片 */
    @Data
    @Schema(description = "保存项-文档")
    public static class DocItem implements Serializable {

        @Schema(description = "文件名")
        @NotBlank(message = "文件名不能为空")
        private String fileName;

        @Schema(description = "文件大小（字节，可空）")
        private Long fileSize;

        @Schema(description = "原文件 MinIO 对象 key（预览阶段已存，透传过来直接挂到文档记录）")
        private String storageUrl;

        @Schema(description = "预览阶段已计的 parse/chunk 阶段耗时（透传，合并到最终 ingestion_summary）")
        private List<IngestionSummary.StageStat> stages;

        @Schema(description = "实际解析引擎（从预览结果透传，落 ingestion_summary.engine）")
        private String engine;

        @Schema(description = "切片列表")
        @NotEmpty(message = "切片列表不能为空")
        @Valid
        private List<ChunkItem> chunks;
    }

    /** 单个切片 */
    @Data
    @Schema(description = "保存项-切片")
    public static class ChunkItem implements Serializable {

        @Schema(description = "切片标题（可空）")
        private String title;

        @Schema(description = "切片正文")
        @NotBlank(message = "切片内容不能为空")
        private String content;

        @Schema(description = "父块上下文（开启父子分块时携带，落 parent_chunks 表；可空）")
        private String parentContext;

        @Schema(description = "该切片已生成的问题列表（预览阶段生成，直接落表避免重复调 LLM；可空）")
        private List<String> questions;
    }
}
