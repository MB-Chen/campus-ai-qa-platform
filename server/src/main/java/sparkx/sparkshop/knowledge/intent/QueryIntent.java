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

/**
 * 查询意图 。
 */
public enum QueryIntent {

    GREETING("greeting", false, "问候"),
    SUMMARIZE("summarize", false, "总结对话"),
    WEB_SEARCH("web_search", false, "联网搜索"),
    KB_SEARCH("kb_search", true, "知识库检索"),       // 默认
    CLARIFICATION("clarification", true, "需澄清"),
    FOLLOW_UP("follow_up", false, "上下文追问"),
    IMAGE_ONLY("image_only", false, "纯图片理解"),
    DOC_ONLY("doc_only", false, "纯文档理解"),
    CHITCHAT("chitchat", false, "闲聊");

    private final String code;
    private final boolean needsRetrieval;
    private final String desc;

    QueryIntent(String code, boolean needsRetrieval, String desc) {
        this.code = code;
        this.needsRetrieval = needsRetrieval;
        this.desc = desc;
    }

    public boolean needsRetrieval() { return needsRetrieval; }
    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static QueryIntent fromCode(String code) {
        if (code == null) return KB_SEARCH;
        for (var i : values()) {
            if (i.code.equals(code)) return i;
        }
        return KB_SEARCH;
    }
}
