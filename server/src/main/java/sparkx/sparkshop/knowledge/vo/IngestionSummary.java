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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文档入库耗时统计。
 *
 * <p>记录一个文档从解析到向量化的各阶段耗时，覆盖所有解析引擎
 * （tika/pdfbox/poi/mineru/mineru_cloud），不只是 MinerU。
 *
 * <p>JSON 结构示例（存 document.ingestion_summary jsonb）：
 * <pre>
 * {
 *   "engine": "mineru",
 *   "totalMs": 18342,
 *   "stages": [
 *     {"key":"parse",   "label":"解析",   "durationMs":12300, "status":"success"},
 *     {"key":"chunk",   "label":"分块",   "durationMs":  210, "status":"success"},
 *     {"key":"persist", "label":"入库",   "durationMs":  940, "status":"success"},
 *     {"key":"embed",   "label":"向量化", "durationMs":4892, "status":"success"}
 *   ]
 * }
 * </pre>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "文档入库耗时统计")
public class IngestionSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "解析引擎：tika/pdfbox/poi/mineru/mineru_cloud")
    private String engine;

    @Schema(description = "总耗时（毫秒）")
    private long totalMs;

    @Schema(description = "各阶段耗时明细（按 parse/chunk/persist/embed 顺序）")
    private List<StageStat> stages;

    /** 单个阶段耗时。 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "阶段耗时明细")
    public static class StageStat implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "阶段标识：parse/chunk/persist/embed")
        private String key;

        @Schema(description = "阶段中文标签：解析/分块/入库/向量化")
        private String label;

        @Schema(description = "耗时（毫秒）")
        private long durationMs;

        @Schema(description = "状态：success/failed/skipped/running")
        private String status;

        @Schema(description = "附加说明（失败原因等，可空）")
        private String detail;

        public StageStat() {}

        public StageStat(String key, String label, long durationMs, String status, String detail) {
            this.key = key;
            this.label = label;
            this.durationMs = durationMs;
            this.status = status;
            this.detail = detail;
        }
    }
}
