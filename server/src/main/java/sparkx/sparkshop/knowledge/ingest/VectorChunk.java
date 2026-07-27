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

import sparkx.sparkshop.knowledge.ingest.block.AssetRef;

import java.util.List;
import java.util.Map;

/**
 * 向量分块输出（移植自 sparkxV2）—— Block 分块器 / 入库节点的统一产物。
 *
 * ★ content vs embeddingText 分离：
 *  - content：展示文本（如表格 markdown、图片 ![](url)），喂给答题模型
 *  - embeddingText：检索文本（如表格 key-value、图片描述），用于向量化
 * 二者分离让"展示友好"与"检索精准"兼得。
 *
 * @param chunkId       分块 id
 * @param index         分块序号
 * @param content       展示文本
 * @param embeddingText 检索文本（为 null 时回退到 content）
 * @param metadata      元数据（blockType/outlinePath 等）
 * @param assets        关联的多模态资产（图片）
 * @param blockType     Block 类型（HEADING/PARAGRAPH/TABLE/IMAGE/CODE/LIST）
 * @param outlinePath   章节路径
 */
public record VectorChunk(String chunkId, int index, String content, String embeddingText,
                          Map<String, Object> metadata, List<AssetRef> assets,
                          String blockType, String outlinePath) {

    /** embeddingText 缺省回退到 content */
    public String effectiveEmbeddingText() {
        return (embeddingText != null && !embeddingText.isBlank()) ? embeddingText : content;
    }
}
