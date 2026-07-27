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

import java.util.List;

/**
 * 表格块（移植自 sparkxV2）—— 表头 + 行数据。
 *
 * ★ TableChunker 独到设计：每个 chunk 带"列名: 值" key-value 作为 embedding 文本，
 * 解决 markdown 表格列名↔值靠位置对齐、向量检索读不懂的问题。
 */
public record TableBlock(String id, Provenance provenance, String outlinePath,
                         List<String> headers, List<List<String>> rows) implements Block {
    @Override public Type type() { return Type.TABLE; }
}
