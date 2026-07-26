// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.fallback;

import java.util.function.Consumer;

/**
 * 兜底策略接口。
 * 分 model / fixed 两档。
 */
public interface FallbackProvider {

    /**
     * @param query         原始查询
     * @param rewriteQuery  改写后查询（可能为空）
     * @param language      输出语言
     * @return 兜底回答
     */
    String fallback(String query, String rewriteQuery, String language);

    /**
     * 流式兜底（默认实现退化为同步：先 {@link #fallback} 拿到全文，再整段回调一次）。
     *
     * <p>model 实现可覆写为真正的逐 token 流式（基于通用知识边生成边推），
     * 告别「同步等全文才一次性蹦出字」的体验问题。
     *
     * @param query         原始查询
     * @param rewriteQuery  改写后查询（可能为空）
     * @param language      输出语言
     * @param tokenConsumer 逐 token 回调（可能为 null，表示调用方不需要流式）
     * @return 兜底回答全文
     */
    default String fallbackStream(String query, String rewriteQuery, String language,
                                  Consumer<String> tokenConsumer) {
        String answer = fallback(query, rewriteQuery, language);
        if (tokenConsumer != null && answer != null && !answer.isEmpty()) {
            tokenConsumer.accept(answer);
        }
        return answer;
    }
}
