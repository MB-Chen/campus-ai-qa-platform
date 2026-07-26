// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.graph;

import java.util.List;

/**
 * LLM 抽取单父块得到的实体/关系结果（JSON 解析后的结构化形式）。
 *
 * <p>对应抽取 prompt 的 JSON 输出：
 * <pre>{@code
 * {"entities":[{"name":"","type":"","description":"","canonical_name":"","aliases":[]}],
 *  "relations":[{"head":"","type":"","tail":""}]}
 * }</pre>
 *
 * <p>★ {@link Entity#canonicalName} 是消歧键：同义实体（"北京" / "北京市"）应输出相同 canonical_name，
 * 写 Neo4j 时以 (kb_id, canonical_name) 为唯一约束 MERGE，实现节点合并。
 *
 * <p>★ {@link Relation#head}/{@link Relation#tail} 引用的是 entity 的 canonical_name（不是原始 name），
 * 保证关系两端的节点能被 MERGE 命中。
 *
 * @param entities  实体列表
 * @param relations 关系列表
 */
public record GraphExtractionResult(List<Entity> entities, List<Relation> relations) {

    public static GraphExtractionResult empty() {
        return new GraphExtractionResult(List.of(), List.of());
    }

    /** 实体 */
    public record Entity(
            String name,             // 原始抽取名
            String type,             // 实体类型（人/组织/产品/概念/...）
            String description,      // 描述
            String canonicalName,    // 消歧后规范名（合并键）
            List<String> aliases     // 别名列表
    ) {}

    /** 关系（head/tail 均为 canonical_name） */
    public record Relation(
            String head,   // 头实体 canonical_name
            String type,   // 关系类型（如 WORKS_FOR / LOCATED_IN）
            String tail    // 尾实体 canonical_name
    ) {}
}
