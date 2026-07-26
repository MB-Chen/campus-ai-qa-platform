// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.query;

import java.util.List;

/**
 * 改写+拆分结果（文档 5.3）—— 一条合并问题（主检索）+ 子问题列表（各自独立检索）。
 *
 * @param rewrittenQuestion 改写后的主问题（用于主检索）
 * @param subQuestions      拆分后的子问题列表（每个独立检索，提升多意图问题召回）
 */
public record RewriteResult(String rewrittenQuestion, List<String> subQuestions) {

    /** 单问题便捷构造（无拆分） */
    public static RewriteResult single(String question) {
        return new RewriteResult(question, List.of(question));
    }
}
