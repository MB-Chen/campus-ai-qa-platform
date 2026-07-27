// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.ingest.block;

import sparkx.sparkshop.knowledge.config.RagProperties;
import sparkx.sparkshop.knowledge.ingest.VectorChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Block 感知分块分发器（文档 5.8.2）—— 【RAGent】BlockAwareChunkerDispatcher。
 *
 * 按 Block 类型分发到专属 chunker：Heading 只更新路径不产 chunk。
 *
 * ★ 表格分块独到设计（TableChunker）：每个 chunk 带"列名: 值" key-value 作为 embedding 文本。
 * ★ 图片分块（ImageChunker）：atomic，渲染 ![](url)，description 作 embedding 文本。
 *
 * <p>注：本类按 1:1 移植自 sparkxV2。
 */
@Component
public class BlockAwareChunker {

    private static final Logger log = LoggerFactory.getLogger(BlockAwareChunker.class);

    private final int maxChars;
    private final int overlap;

    public BlockAwareChunker(RagProperties props) {
        this.maxChars = Math.max(128, props.getChunking().getChunkSize());
        this.overlap = Math.min(props.getChunking().getChunkOverlap(), this.maxChars / 2);
    }

    /** 对 Block 列表分块，返回 VectorChunk 列表 */
    public List<VectorChunk> chunk(List<Block> blocks) {
        List<VectorChunk> chunks = new ArrayList<>();
        // 当前章节路径（Heading 更新），用单元素数组模拟可变引用
        String[] outlinePath = {""};
        AtomicInteger seq = new AtomicInteger(0);

        for (Block block : blocks) {
            // 按 Block.type() 分发到专属 chunker（Java 17 正式语法，等价于 switch 类型模式）
            switch (block.type()) {
                case HEADING   -> updateOutline(outlinePath, (HeadingBlock) block);
                case PARAGRAPH -> chunks.addAll(chunkParagraph((ParagraphBlock) block, outlinePath[0], seq));
                case TABLE     -> chunks.addAll(chunkTable((TableBlock) block, outlinePath[0], seq));
                case IMAGE     -> chunks.add(chunkImage((ImageBlock) block, outlinePath[0], seq));
                case CODE      -> chunks.add(chunkCode((CodeBlock) block, outlinePath[0], seq));
                case LIST      -> chunks.addAll(chunkList((ListBlock) block, outlinePath[0], seq));
            }
        }
        log.debug("[BlockChunker] blocks={} → chunks={}", blocks.size(), chunks.size());
        return chunks;
    }

    /** Heading 只更新 outlinePath，不产 chunk */
    private void updateOutline(String[] outlinePath, HeadingBlock h) {
        if (h.level() <= 1) {
            outlinePath[0] = h.text();
        } else {
            outlinePath[0] = outlinePath[0].isEmpty()
                    ? h.text()
                    : outlinePath[0] + " > " + h.text();
        }
    }

    /** 段落分块：按 maxChars + overlap 切分 */
    private List<VectorChunk> chunkParagraph(ParagraphBlock p, String path, AtomicInteger seq) {
        List<VectorChunk> result = new ArrayList<>();
        String text = p.text();
        if (text == null || text.isBlank()) return result;
        int step = Math.max(1, maxChars - overlap);
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + maxChars, text.length());
            String slice = text.substring(start, end);
            result.add(buildChunk(slice, slice, path, "PARAGRAPH", null, seq));
            if (end >= text.length()) break;
        }
        return result;
    }

    /**
     * ★ 表格分块独到设计：每个 chunk 带完整表头 + "列名: 值" key-value 作为 embedding 文本。
     * 解决 markdown 表格列名↔值靠位置对齐、向量检索读不懂的问题。
     */
    private List<VectorChunk> chunkTable(TableBlock t, String path, AtomicInteger seq) {
        List<VectorChunk> result = new ArrayList<>();
        if (t.rows() == null || t.rows().isEmpty()) return result;
        List<String> headers = t.headers() != null ? t.headers() : List.of();

        StringBuilder mdBuffer = new StringBuilder();
        List<List<String>> kvRows = new ArrayList<>();
        for (List<String> row : t.rows()) {
            String mdRow = "| " + String.join(" | ", row) + " |";
            if (mdBuffer.length() + mdRow.length() > maxChars && mdBuffer.length() > 0) {
                // 达到预算，切出一个 chunk
                result.add(buildTableChunk(headers, mdBuffer, kvRows, path, seq));
                mdBuffer = new StringBuilder();
                kvRows = new ArrayList<>();
            }
            mdBuffer.append(mdRow).append("\n");
            kvRows.add(row);
        }
        if (mdBuffer.length() > 0) {
            result.add(buildTableChunk(headers, mdBuffer, kvRows, path, seq));
        }
        return result;
    }

    /** 构建表格 chunk：content=markdown 表格（带完整表头），embeddingText=key-value */
    private VectorChunk buildTableChunk(List<String> headers, StringBuilder mdRows,
                                        List<List<String>> rows, String path, AtomicInteger seq) {
        String headerLine = "| " + String.join(" | ", headers) + " |\n";
        String content = headerLine + mdRows;
        String embeddingText = toKeyValue(headers, rows);
        return buildChunk(content, embeddingText, path, "TABLE", null, seq);
    }

    /** embedding 文本用 "列名: 值" key-value 格式，而非 markdown 符号 */
    private String toKeyValue(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < headers.size() && i < row.size(); i++) {
                sb.append(headers.get(i)).append(": ").append(row.get(i)).append("; ");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /** ★ 图片分块：atomic，渲染 ![](url)，description 作 embedding 文本 */
    private VectorChunk chunkImage(ImageBlock i, String path, AtomicInteger seq) {
        String url = i.assetUrl() != null ? i.assetUrl() : "";
        String desc = i.description() != null ? i.description() : "";
        String content = "![" + desc + "](" + url + ")";
        List<AssetRef> assets = url.isEmpty() ? null
                : List.of(new AssetRef(url, "image", i.id()));
        return buildChunk(content, desc, path, "IMAGE", assets, seq);
    }

    /** 代码分块：atomic */
    private VectorChunk chunkCode(CodeBlock c, String path, AtomicInteger seq) {
        String content = c.code() != null ? c.code() : "";
        String lang = c.language() != null ? c.language() : "";
        String embeddingText = lang.isEmpty() ? content : "[" + lang + "]\n" + content;
        return buildChunk(content, embeddingText, path, "CODE", null, seq);
    }

    /** 列表分块：短列表 atomic，长列表按 maxChars 分组 */
    private List<VectorChunk> chunkList(ListBlock l, String path, AtomicInteger seq) {
        List<VectorChunk> result = new ArrayList<>();
        if (l.items() == null || l.items().isEmpty()) return result;
        StringBuilder buf = new StringBuilder();
        for (String item : l.items()) {
            String line = "- " + item + "\n";
            if (buf.length() + line.length() > maxChars && buf.length() > 0) {
                String content = buf.toString().trim();
                result.add(buildChunk(content, content, path, "LIST", null, seq));
                buf = new StringBuilder();
            }
            buf.append(line);
        }
        if (buf.length() > 0) {
            String content = buf.toString().trim();
            result.add(buildChunk(content, content, path, "LIST", null, seq));
        }
        return result;
    }

    /** 构建单个 VectorChunk（统一元数据） */
    private VectorChunk buildChunk(String content, String embeddingText, String path,
                                   String blockType, List<AssetRef> assets, AtomicInteger seq) {
        int idx = seq.getAndIncrement();
        return new VectorChunk(
                "vc_" + UUID.randomUUID(),
                idx,
                content,
                embeddingText,
                java.util.Map.of("blockType", blockType, "outlinePath", path),
                assets,
                blockType,
                path);
    }
}
