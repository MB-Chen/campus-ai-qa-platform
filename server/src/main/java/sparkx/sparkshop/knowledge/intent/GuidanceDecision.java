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
 * 歧义引导决策（文档 5.2.4）—— 置信度不足时是否反问澄清。
 *
 * action：
 *  - NONE：意图明确，无需澄清
 *  - PROMPT：歧义，需反问（prompt 为澄清话术，含候选选项）
 */
public record GuidanceDecision(Action action, String prompt) {

    public enum Action { NONE, PROMPT }

    public static GuidanceDecision none() { return new GuidanceDecision(Action.NONE, null); }

    public static GuidanceDecision prompt(String p) { return new GuidanceDecision(Action.PROMPT, p); }

    public boolean isPrompt() { return action == Action.PROMPT; }
}
