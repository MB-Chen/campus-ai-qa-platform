// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.workflow.engine;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Answer 汇合点的合并态（per-runtime + per-cell）。
 * <p>
 * 背景：Answer 节点作为多条入边的汇聚点时，会被 {@code FlowNodeParser.execute} 在不同递归层各调用一次 handle。
 * 每次调用代表「一条上游入边到达」。本类线程安全地累积：
 * <ul>
 *   <li>已到达的上游 sourceId 集合（去重，防同一条入边重复触发）</li>
 *   <li>各上游 outputData 的 node.&lt;cell&gt; 分区合并结果</li>
 * </ul>
 * 当「已到达上游数 == 入边总数」时，Answer 才真正生成回复；之前的调用只做合并累加。
 * <p>
 * ★ 关键修复（Bug B）：原实现用「cell 是否已有落库行」做幂等，导致只有首个到达的上游被聚合，其余上游产出被丢弃。
 *
 * <b>线程安全说明</b>：同一汇合点的多次 handle 调用可能来自不同并行分支的线程，故所有变更方法 synchronized。
 */
public class AnswerMergeState {

    /** 已到达的上游 sourceId（去重） */
    private final Set<String> arrivedSources = new HashSet<>();

    /** 合并后的 outputData（JSON 字符串，随上游到达逐步累积） */
    private String mergedOutput = JSONUtil.createObj().toString();

    /**
     * 记录一条上游到达，并合并其 outputData。
     *
     * @param sourceId      本次到达的上游 cell
     * @param sourceOutput  本次上游的 outputData（JSON 字符串）
     * @return 合并是否真正发生（true=新上游；false=重复上游，已忽略）
     */
    public synchronized boolean arrive(String sourceId, String sourceOutput) {
        if (!arrivedSources.add(sourceId)) {
            return false; // 同一上游重复到达，忽略
        }
        // 把本次上游 outputData 里的 node.<cell> 分区合并进来
        JSONObject merged = JSONUtil.parseObj(mergedOutput);
        if (sourceOutput != null && !sourceOutput.isBlank()) {
            JSONObject src = JSONUtil.parseObj(sourceOutput);
            for (String key : src.keySet()) {
                if (key.startsWith("node.")) {
                    Object part = src.get(key);
                    if (part instanceof JSONObject) {
                        merged.set(key, part);
                    }
                }
            }
            // 全局 sys.* 也以「后到者不覆盖」的方式合并（首个非空生效）
            for (String key : src.keySet()) {
                if (key.startsWith("sys.") && merged.getStr(key, "").isEmpty()) {
                    merged.set(key, src.get(key));
                }
            }
        }
        mergedOutput = merged.toString();
        return true;
    }

    /** 已到达的不同上游数（去重后） */
    public synchronized int arrivedCount() {
        return arrivedSources.size();
    }

    /** 取合并后的 outputData */
    public synchronized String getMergedOutput() {
        return mergedOutput;
    }
}
