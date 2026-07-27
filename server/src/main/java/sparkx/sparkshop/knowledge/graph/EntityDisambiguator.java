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

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.config.RagProperties;
import sparkx.sparkshop.knowledge.entity.KgConfig;
import sparkx.sparkshop.knowledge.infra.EmbeddingModelProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体消歧器（第三期）—— 在 {@code GraphExtractionService.disambiguate()} 字符串合并之后，
 * 增一道基于 embedding 余弦相似度的合并，解决 LLM 跨父块对同一实体输出不同 canonical_name 的问题。
 *
 * <p>★ 动机：现有 {@code disambiguate()} 仅按 canonical_name 字符串精确匹配合并，
 * 当 LLM 在父块 A 输出 canonical_name="北京"、在父块 B 输出 canonical_name="北京市" 时，
 * 两者无法合并，会生成两个独立 Neo4j 节点，破坏图谱连通性。
 *
 * <p>★ 算法：对每个实体构造「name + description」的 embedding，两两计算余弦相似度，
 * 相似度 ≥ {@code similarityThreshold}（默认 0.88）且 type 兼容的实体合并为一个，
 * 合并规则与字符串消歧一致（aliases 并集、description 取最长、type 取非空）。
 *
 * <p>★ 合并后 canonical_name 的选择：保留首个出现的 canonical_name（保持关系引用稳定），
 * 被合并者的 canonical_name 全部加入 aliases。
 *
 * <p>★ 关系修正：合并实体后，relations 里 head/tail 引用的旧 canonical_name
 * 需要映射到合并后的新 canonical_name（否则关系会断），由 {@link #remapRelations} 处理。
 *
 * <p>★ 容错：embedding 生成失败或模型不可达时，降级为「跳过 embedding 合并，返回原始列表」，
 * 不阻断抽取主流程（字符串消歧已经做过一遍，embedding 只是补充）。
 *
 * <p>★ 阈值来源：{@link KgConfig#getEntityMergeThreshold()}（页面可调，默认 0.88），
 * 不在 yml 配置——保持与 KG 模块其它运行期参数（similarity_threshold / hop_depth 等）一致的表化风格。
 */
@Component
public class EntityDisambiguator {

    private static final Logger log = LoggerFactory.getLogger(EntityDisambiguator.class);

    /** 阈值兜底（kg_config 字段为 null 时用） */
    private static final double DEFAULT_THRESHOLD = 0.88;

    private final EmbeddingModelProvider embeddingModelProvider;

    public EntityDisambiguator(EmbeddingModelProvider embeddingModelProvider,
                                RagProperties props) {
        this.embeddingModelProvider = embeddingModelProvider;
        // props 注入保留，未来若要把阈值挪到 yml 可直接读
    }

    /**
     * 合并结果：合并后的实体列表 + 旧→新 canonical_name 映射表（供 {@link #remapRelations} 使用）。
     */
    public record MergeResult(
            List<GraphExtractionResult.Entity> entities,
            Map<String, String> nameRemap) {}

    /**
     * 基于 embedding 相似度合并实体。
     *
     * @param kbId         知识库 id（仅日志用，模型按 kg_config 配置）
     * @param entities     字符串消歧后的实体列表
     * @param config       kg_config 全局配置（取 entity_merge_threshold + embedding 模型）
     * @return 合并结果（实体列表 + nameRemap）；模型不可达时返回原列表 + 空 map（降级）
     */
    public MergeResult mergeByEmbedding(
            String kbId,
            List<GraphExtractionResult.Entity> entities,
            KgConfig config) {
        if (entities == null || entities.size() < 2) return new MergeResult(entities, Map.of());

        double threshold = resolveThreshold(config);
        EmbeddingModel embModel = resolveModel(config);
        if (embModel == null) {
            log.debug("[EntityDisambiguator] embedding 模型不可用，跳过 embedding 消歧 kbId={}", kbId);
            return new MergeResult(entities, Map.of());
        }

        // 1. 计算每个实体的 embedding（失败则该实体跳过合并，保留原样）
        int n = entities.size();
        Embedding[] embs = new Embedding[n];
        boolean[] valid = new boolean[n];
        for (int i = 0; i < n; i++) {
            try {
                GraphExtractionResult.Entity e = entities.get(i);
                String text = buildEmbeddingText(e);
                embs[i] = embModel.embed(TextSegment.from(text)).content();
                valid[i] = true;
            } catch (Exception ex) {
                log.debug("[EntityDisambiguator] 实体 embedding 失败 idx={} name={}: {}",
                        i, entities.get(i).canonicalName(), ex.getMessage());
                valid[i] = false;
            }
        }

        // 2. 贪心合并：按顺序遍历，每个实体与「已合并组」的代表计算相似度，≥ 阈值则归并
        // canonical_name → 合并后的代表实体
        Map<String, GraphExtractionResult.Entity> merged = new LinkedHashMap<>();
        // 旧 canonical_name → 新 canonical_name 映射（关系修正用）
        Map<String, String> nameRemap = new LinkedHashMap<>();
        // 已合并组的代表 canonical_name 与其 embedding
        List<String> repNames = new ArrayList<>();
        List<Embedding> repEmbeddings = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            GraphExtractionResult.Entity e = entities.get(i);
            String curName = e.canonicalName();

            // 找最相似的已有代表
            String bestRep = null;
            double bestSim = -1;
            if (valid[i]) {
                for (int j = 0; j < repNames.size(); j++) {
                    Embedding repEmb = repEmbeddings.get(j);
                    double sim = cosine(embs[i], repEmb);
                    if (sim >= threshold && sim > bestSim) {
                        // type 兼容性：双方都有 type 时必须一致；一方无 type 则放行（让 description 主导）
                        GraphExtractionResult.Entity repEntity = merged.get(repNames.get(j));
                        if (typeCompatible(repEntity, e)) {
                            bestSim = sim;
                            bestRep = repNames.get(j);
                        }
                    }
                }
            }

            if (bestRep != null) {
                // 合并到 bestRep
                merged.compute(bestRep, (k, rep) -> mergeTwo(rep, e));
                nameRemap.put(curName, bestRep);
            } else {
                // 新建组：当前实体作为代表
                merged.put(curName, e);
                nameRemap.put(curName, curName);
                repNames.add(curName);
                if (valid[i]) repEmbeddings.add(embs[i]);
            }
        }

        List<GraphExtractionResult.Entity> result = new ArrayList<>(merged.values());
        int mergedCount = n - result.size();
        if (mergedCount > 0) {
            log.info("[EntityDisambiguator] embedding 消歧 kbId={} before={} after={} merged={}",
                    kbId, n, result.size(), mergedCount);
        }
        return new MergeResult(result, nameRemap);
    }

    /**
     * 修正关系：把 relations 里 head/tail 引用的旧 canonical_name 映射到合并后的新名。
     * 同时丢弃两端指向同一实体（自环）的关系。
     */
    public List<GraphExtractionResult.Relation> remapRelations(
            List<GraphExtractionResult.Relation> relations, Map<String, String> nameRemap) {
        if (relations == null || relations.isEmpty()) return relations;
        if (nameRemap == null || nameRemap.isEmpty()) return relations;

        List<GraphExtractionResult.Relation> result = new ArrayList<>(relations.size());
        Set<String> seen = new HashSet<>();
        for (GraphExtractionResult.Relation r : relations) {
            String head = nameRemap.getOrDefault(r.head(), r.head());
            String tail = nameRemap.getOrDefault(r.tail(), r.tail());
            if (head == null || tail == null || head.equals(tail)) continue;  // 自环丢弃
            String key = head + "|" + r.type() + "|" + tail;
            if (seen.add(key)) {
                result.add(new GraphExtractionResult.Relation(head, r.type(), tail));
            }
        }
        return result;
    }


    /** 合并两个实体：aliases 并集（含被合并者的 canonical_name）、description 取最长、type 取非空 */
    private GraphExtractionResult.Entity mergeTwo(GraphExtractionResult.Entity rep,
                                                   GraphExtractionResult.Entity other) {
        Set<String> allAliases = new HashSet<>();
        if (rep.aliases() != null) allAliases.addAll(rep.aliases());
        if (other.aliases() != null) allAliases.addAll(other.aliases());
        // 被合并者的 canonical_name 与原始 name 都进 aliases
        if (other.canonicalName() != null && !other.canonicalName().equals(rep.canonicalName())) {
            allAliases.add(other.canonicalName());
        }
        if (other.name() != null && !other.name().equals(rep.canonicalName())) {
            allAliases.add(other.name());
        }

        String desc = longest(rep.description(), other.description());
        String type = rep.type() != null && !rep.type().isBlank() ? rep.type() : other.type();

        return new GraphExtractionResult.Entity(
                rep.name(), type, desc, rep.canonicalName(), new ArrayList<>(allAliases));
    }

    /** type 兼容性：双方都有 type 时必须相等（忽略大小写）；一方无 type 则放行 */
    private boolean typeCompatible(GraphExtractionResult.Entity a, GraphExtractionResult.Entity b) {
        boolean aHas = a.type() != null && !a.type().isBlank();
        boolean bHas = b.type() != null && !b.type().isBlank();
        if (!aHas || !bHas) return true;
        return a.type().equalsIgnoreCase(b.type());
    }

    /** 构造 embedding 文本：canonical_name + " " + description（description 为空时仅 name） */
    private static String buildEmbeddingText(GraphExtractionResult.Entity e) {
        String name = e.canonicalName() != null ? e.canonicalName() : e.name();
        String desc = e.description();
        return (desc == null || desc.isBlank()) ? name : name + " " + desc;
    }

    private static String longest(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.length() >= b.length() ? a : b;
    }

    /** 余弦相似度（embedding 已归一化时等价于点积，这里做通用归一化兜底） */
    private static double cosine(Embedding a, Embedding b) {
        float[] va = a.vector();
        float[] vb = b.vector();
        int len = Math.min(va.length, vb.length);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < len; i++) {
            dot += va[i] * vb[i];
            na += va[i] * va[i];
            nb += vb[i] * vb[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private double resolveThreshold(KgConfig config) {
        if (config != null && config.getEntityMergeThreshold() != null) {
            double t = config.getEntityMergeThreshold().doubleValue();
            if (t > 0 && t <= 1) return t;
        }
        return DEFAULT_THRESHOLD;
    }

    private EmbeddingModel resolveModel(KgConfig config) {
        if (config == null) return null;
        try {
            return embeddingModelProvider.resolveByModelId(
                    config.getEmbeddingModelId(), config.getEmbeddingModelName());
        } catch (Exception e) {
            log.debug("[EntityDisambiguator] 解析 embedding 模型失败: {}", e.getMessage());
            return null;
        }
    }
}
