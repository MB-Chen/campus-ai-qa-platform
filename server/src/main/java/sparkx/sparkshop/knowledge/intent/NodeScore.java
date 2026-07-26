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
 * 意图打分结果（文档 5.2.2）—— 叶子节点与其 LLM 打分的配对。
 *
 * 按 score 降序排列供 {@code IntentStage} 配额控制与 {@code IntentGuidanceService} 歧义判定。
 */
public record NodeScore(IntentNode node, double score) {

    /**
     * 统一的降序比较器：先按分数降序，分数相同时【用户配置节点优先于内置兜底节点】。
     *
     * ★ tie-break 动机：内置兜底节点（id 以 sys_ 开头，如 sys_chitchat）的 examples 通常很宽泛，
     *   会和用户配置的细分节点（如「问候打招呼」「通用闲聊」）对同一句话给出相同分数。
     *   此时若让兜底节点排前面，等于架空了用户配置。用户节点优先才符合「兜底」的语义——
     *   只有用户没配相关节点时，兜底节点才该接管。
     */
    public static java.util.Comparator<NodeScore> descending() {
        return java.util.Comparator
                .comparingDouble(NodeScore::score).reversed()
                .thenComparingInt(NodeScore::presetRank);
    }

    /** 内置兜底节点排得靠后（返回较大值），用户节点优先（返回 0） */
    private int presetRank() {
        String id = node == null ? null : node.getId();
        return id != null && id.startsWith("sys_") ? 1 : 0;
    }
}
