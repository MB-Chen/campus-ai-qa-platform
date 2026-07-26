// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.ingest;

import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 * 分块结果校验器。
 *
 * <p>策略链（heading → recursive）在每一层切完后调用 {@link #validate}，不通过则自动降级到下一层。
 * 校验故意宽松——只拦截「明显坏掉」的输出，合理的形态差异放行，避免在 tier 之间来回抖动。
 *
 * <ul>
 *   <li>空结果 → 拒绝（对齐 "no chunks produced"）。</li>
 *   <li>文档远大于 chunkSize 却只切出 1 块 → 拒绝（对齐 "single chunk for large document"）。</li>
 *   <li>非末尾的 tiny chunk（&lt; 50 字符）占比 &gt; 25% 且 &gt; 2 个 → 拒绝（对齐 "too many tiny chunks"）。</li>
 *   <li>最大块 &lt; chunkSize/4 且文档 &gt; chunkSize → 拒绝（对齐 "all chunks far below target size"）。</li>
 *   <li>最大块 &gt; 2×chunkSize → 拒绝（对齐 "chunk exceeds 2x target size"）。</li>
 * </ul>
 */
final class ChunkValidator {

    private ChunkValidator() {}

    static boolean validate(List<TextSegment> chunks, int totalChars, int chunkSize) {
        if (chunks == null || chunks.isEmpty()) return false;

        // 单 chunk 且文档远大于 chunkSize → 没真正切分
        if (chunks.size() == 1 && totalChars > 2L * chunkSize) return false;

        int maxLen = 0;
        int tinyCount = 0;
        for (int i = 0; i < chunks.size(); i++) {
            int len = chunks.get(i).text().length();
            if (len > maxLen) maxLen = len;
            // 末尾 chunk 允许很小（对齐 validator.go:50-52）
            if (i != chunks.size() - 1 && len < 50) tinyCount++;
        }

        // tiny chunk 占比过高（对齐 validator.go:57-59）
        if (tinyCount > chunks.size() / 4 && tinyCount > 2) return false;

        // 全部远低于目标（对齐 validator.go:63-65）
        if (chunkSize > 0 && maxLen < chunkSize / 4 && totalChars > chunkSize) return false;

        // 超过 2 倍目标（对齐 validator.go:69-71）
        if (chunkSize > 0 && maxLen > 2L * chunkSize) return false;

        return true;
    }
}
