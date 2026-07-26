// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.intent;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 规则快速路径。
 * 对明显的问候/闲聊，零延迟零成本判定，不调 LLM。
 */
@Component
public class RuleBasedIntentRouter {

    // ★ 问候词前允许可选语气词前缀（哈/嘿/啊/哎/哟/噢/哦 0-N个 + 可选标点/空格）
    //   覆盖"哈哈，你好啊""嘿你好""啊，您好"等常见开场白，避免白跑检索。
    private static final Pattern GREETING = Pattern.compile(
            "^[哈嘿哎啊哟噢哦]*[，,\\s]*" +
            "(你好|您好|hi|hello|hey|谢谢|感谢|再见|bye|早上?好|晚上?好|下午好).*",
            Pattern.CASE_INSENSITIVE);

    // ★ 闲聊同样加语气词前缀：覆盖"哈哈你是谁""嗯讲个笑话"等
    private static final Pattern CHITCHAT = Pattern.compile(
            "^[哈嘿哎啊哟噢哦嗯呐]*[，,\\s]*" +
            "(你是谁|你叫什么|你是什么|讲个笑话|能做什么|帮我什么|capabilities).*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern FOLLOW_UP = Pattern.compile(
            "^(上面|刚才|刚刚|你说的|第[一二三四五六七八九十]点|继续|展开|详细).*(说|讲|提|列|展开).*$",
            Pattern.MULTILINE);

    /**
     * 实时信息意图：需明确指向"外部实时数据"才命中。
     * ★ 收窄：早期用 (今天|...).*$ 会把"今天吃什么""今天心情不好"误判成 web_search，
     *   现在要求"今天/最新"后紧跟实时信息词（天气/新闻/日期/股价/汇率等），避免误伤。
     *   "今天吃什么"这类应放行给意图路由精确判断。
     */
    private static final Pattern WEB_HINT = Pattern.compile(
            "(今天.*(天气|新闻|日期|几号|星期)|最新.*(新闻|消息|资讯|动态)|实时|现在.*(几点|时间)|news|weather|股价|汇率|黄金价格)",
            Pattern.CASE_INSENSITIVE);

    /**
     * 短应答 / 敷衍 / 情绪填充词判定（不依赖穷举词表）。
     *
     * 设计原则：用"语言学特征"代替"具体词列表"，从而覆盖无限长尾——
     * 词是列举不完的（哦/好吧/昂/咳/唔/qwq/6/👌…），但"特征"可以描述。
     * 命中即由 {@code IntentClassifier} 短路归内置兜底闲聊节点 sys_chitchat，
     * 规避 LLM 把所有候选打成 <0.6 而返回空数组、导致短闲聊漏匹配。
     *
     * 判定特征（满足任一即视为 filler）：
     *  1) 纯语气词串：仅由语气词字符任意重复 + 结尾语气符号构成（嗯嗯/啊啊/哦哦/哎哎/唔唔…）。
     *     用字符类覆盖长尾，不列具体词。
     *  2) 极短应答：去掉标点空白后长度 ≤ 2 且不含疑问词/实义实体
     *     （好/行/对/昂/咳/唔/qwq/OK/嗯…），无需穷举。
     *  3) 纯标点 / emoji 串（👌 / 。。 / ～～）无实义词。
     */
    private static final Pattern PURE_TONE = Pattern.compile(
            "^(?:[嗯啊哦噢喔呃哎唉唔嘿哈哟咦嘛呗哒嘞滴额昂欸切呸诶]+[~！!。.…~\\s]*)+$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern QUESTION_HINT = Pattern.compile(
            "[?？]|吗$|呢$|怎么|怎样|如何|为什么|为啥|啥|什么|哪些|哪[里个件种]|谁|几[个号岁天]|多少|是不是|能不能|可不可以|行不行|对不对|有没有",
            Pattern.CASE_INSENSITIVE);

    /**
     * 尝试用规则快速判定。返回 null 表示无法判定，需走 LLM。
     */
    public QueryIntent tryClassify(String query) {
        if (query == null) return null;
        String q = query.trim();
        if (q.isEmpty()) return null;

        if (GREETING.matcher(q).matches()) return QueryIntent.GREETING;
        if (CHITCHAT.matcher(q).matches()) return QueryIntent.CHITCHAT;
        if (FOLLOW_UP.matcher(q).matches()) return QueryIntent.FOLLOW_UP;
        if (WEB_HINT.matcher(q).find()) return QueryIntent.WEB_SEARCH;

        return null;   // 交给 LLM
    }

    /**
     * 短应答 / 敷衍 / 情绪填充词判定（基于语言学特征，不依赖穷举词表）。
     * 命中即由 {@code IntentClassifier} 短路归内置兜底闲聊节点 sys_chitchat，
     * 规避 LLM 把所有候选打成 <0.6 而返回空数组、导致短闲聊漏匹配。
     * 快路径未覆盖的边角敷衍句（如"行吧那行""随便啦""yyds"）交由 LLM 兜底归 sys_chitchat。
     */
    public boolean isFiller(String query) {
        if (query == null) return false;
        String q = query.trim();
        if (q.isEmpty()) return false;

        // 去除标点 / 符号 / 空白，保留中文、英文、数字、emoji
        String s = q.replaceAll("[\\p{P}\\p{S}\\s]", "");
        if (s.isEmpty()) return true;                       // 纯标点 / emoji 串（👌 / 。。 / ～～）

        if (PURE_TONE.matcher(s).matches()) return true;    // 1) 纯语气词串

        // 2) 极短应答：去符号后 ≤2 字且无疑问词 / 实义实体
        if (s.length() <= 2 && !QUESTION_HINT.matcher(s).find()) return true;

        return false;
    }
}
