// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.retrieval;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;

import java.util.List;

/**
 * 检索后处理器（文档 5.4.4）—— 责任链，Spring 集合注入，order 排序，开闭原则。
 *
 * 预留 order 间隙：去重=1，重排=10，便于插版本过滤/分数归一化（2~9）。
 * 上一个处理器输出 = 下一个处理器输入。
 */
public interface SearchResultPostProcessor {

    /** 数字越小越先执行 */
    int getOrder();

    /** 是否启用 */
    boolean isEnabled(RetrievalContext ctx);

    /** 处理（输入上一级结果，输出去重/重排/MMR 后的结果） */
    List<Content> process(List<Content> chunks, Query query, RetrievalContext ctx);
}
