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

import sparkx.sparkshop.knowledge.entity.IntentNodeEntity;
import sparkx.sparkshop.knowledge.mapper.IntentNodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图节点缓存管理 —— 内存缓存意图节点列表（预设底座 + DB 自定义合并）。
 *
 * <p>命名说明：类名 {@code IntentTreeCacheManager}、方法 {@code loadTree/flattenLeaves} 为历史遗留
 * （早期 IntentNode 为三级树结构），现 IntentNode 已简化为单层扁平列表，但命名作为公共 API 保留不变。
 *
 * <p>加载策略：内存缓存 miss 时，合并「预设底座节点（SYSTEM 闲聊等系统级兜底）」
 * 与「DB 自定义节点（t_intent_node，enabled 且未删除）」，按 id 去重（自定义覆盖同 id 预设），
 * 组装返回。后台编辑后调 {@link #clearIntentTreeCache()} 失效缓存。</p>
 *
 * 合并模式（而非"DB 有就用 DB、没有就用默认"的二选一）的动机：避免用户漏配某类意图
 * （如只配 KB 没配 SYSTEM 闲聊）导致该类能力彻底丢失。预设底座始终在场，自定义叠加其上。
 */
@Component
public class IntentTreeCacheManager {

    private static final Logger log = LoggerFactory.getLogger(IntentTreeCacheManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final IntentNodeMapper intentNodeMapper;

    /** 进程内缓存（后台编辑后通过 clearIntentTreeCache() 失效） */
    private volatile IntentNode cachedTree;

    public IntentTreeCacheManager(IntentNodeMapper intentNodeMapper) {
        this.intentNodeMapper = intentNodeMapper;
    }

    /** 加载意图树（内存缓存 → 预设底座 + DB 自定义合并去重） */
    public IntentNode loadTree() {
        IntentNode snapshot = this.cachedTree;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            if (this.cachedTree != null) {
                return this.cachedTree;
            }
            IntentNode tree;
            try {
                tree = loadMergedTree();
            } catch (Exception e) {
                log.warn("[IntentTree] 合并加载失败，降级到预设底座: {}", e.getMessage());
                tree = buildPresetRoot();
            }
            this.cachedTree = tree;
            return tree;
        }
    }

    /** 后台编辑后失效缓存 */
    public void clearIntentTreeCache() {
        this.cachedTree = null;
    }

    /**
     * 加载合并后的意图树：预设底座 + DB 自定义节点，按 id 去重（自定义覆盖同 id 预设）。
     *
     * 设计动机：避免"全有或全无"——用户只要漏配某类意图（如只配 KB 没配 SYSTEM），
     * 该类意图就彻底没人管。合并模式保证闲聊兜底等系统预设永远在场，
     * 用户自定义叠加上去，自定义优先。
     *
     * @return 合并后的虚拟根；DB 无数据时仅返回预设底座
     */
    private IntentNode loadMergedTree() {
        // 1. 预设底座节点（始终参与，作为 SYSTEM 闲聊等系统级兜底）
        Map<String, IntentNode> byId = new LinkedHashMap<>();
        for (IntentNode preset : buildPresetNodes()) {
            byId.put(preset.getId(), preset);
        }

        // 2. DB 自定义节点（enabled 且未删除），同 id 覆盖预设
        List<IntentNodeEntity> rows = intentNodeMapper.selectList(
                new LambdaQueryWrapper<IntentNodeEntity>()
                        .eq(IntentNodeEntity::getEnabled, true)
                        .eq(IntentNodeEntity::getDeleted, false));
        if (rows != null) {
            for (IntentNodeEntity row : rows) {
                IntentNode node = toNode(row);
                byId.put(node.getId(), node); // 同 id 覆盖预设
            }
        }

        // 3. 按 parentId 拼 children，收集根节点（预设/自定义 parentId 通常为空，均挂虚拟根）
        List<IntentNode> roots = new ArrayList<>();
        for (IntentNode node : byId.values()) {
            String pid = node.getParentId();
            if (pid == null || pid.isBlank() || !byId.containsKey(pid)) {
                roots.add(node);
            } else {
                byId.get(pid).addChild(node);
            }
        }

        // 4. 所有根节点挂到虚拟根下
        IntentNode virtualRoot = new IntentNode();
        virtualRoot.setId("__virtual_root__");
        virtualRoot.setName("");
        virtualRoot.setLevel(IntentNode.IntentLevel.LEAF);
        for (IntentNode r : roots) {
            virtualRoot.addChild(r);
        }
        fillFullPath(virtualRoot, "");
        return virtualRoot;
    }

    /** 预设底座根节点（DB 合并异常时的兜底） */
    private IntentNode buildPresetRoot() {
        IntentNode virtualRoot = new IntentNode();
        virtualRoot.setId("__virtual_root__");
        virtualRoot.setName("");
        virtualRoot.setLevel(IntentNode.IntentLevel.LEAF);
        for (IntentNode preset : buildPresetNodes()) {
            virtualRoot.addChild(preset);
        }
        fillFullPath(virtualRoot, "");
        return virtualRoot;
    }

    /** 实体 → 节点 */
    private IntentNode toNode(IntentNodeEntity row) {
        IntentNode n = new IntentNode();
        n.setId(row.getId());
        n.setParentId(row.getParentId());
        n.setLevel(toLevel(row.getLevel()));
        n.setKind(toKind(row.getKind()));
        n.setName(row.getName());
        n.setDescription(row.getDescription());
        n.setExamples(parseExamples(row.getExamples()));
        n.setCollectionName(row.getCollectionName());
        n.setDocIds(parseDocIds(row.getDocIds()));
        n.setMcpToolId(row.getMcpToolId());
        n.setParamPromptTemplate(row.getParamPromptTemplate());
        n.setPromptTemplate(row.getPromptTemplate());
        n.setTopK(row.getTopK());
        n.setEnabled(row.getEnabled() == null || row.getEnabled());
        return n;
    }

    private IntentNode.IntentLevel toLevel(Integer lvl) {
        // 单层模型：统一返回 LEAF，旧数据 level 值忽略
        return IntentNode.IntentLevel.LEAF;
    }

    private IntentNode.IntentKind toKind(String kind) {
        if (kind == null) return IntentNode.IntentKind.KB;
        try {
            return IntentNode.IntentKind.valueOf(kind.toUpperCase());
        } catch (IllegalArgumentException e) {
            return IntentNode.IntentKind.KB;
        }
    }

    /** examples 字段为 JSON 数组（如 ["你好","你是谁"]），解析失败返回 null */
    private List<String> parseExamples(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.debug("[IntentTree] examples JSON 解析失败，忽略: {}", e.getMessage());
            return null;
        }
    }

    /** doc_ids 字段为 JSON 数组（KB 限定文档列表），解析失败返回 null（检索整库） */
    private List<String> parseDocIds(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.debug("[IntentTree] doc_ids JSON 解析失败，忽略: {}", e.getMessage());
            return null;
        }
    }

    /** 递归填充 fullPath：父路径 + 当前名 */
    private void fillFullPath(IntentNode node, String parentPath) {
        if (node == null) return;
        String name = node.getName() == null ? "" : node.getName();
        node.setFullPath(parentPath == null || parentPath.isBlank() ? name : parentPath + " > " + name);
        for (IntentNode child : node.getChildren()) {
            fillFullPath(child, node.getFullPath());
        }
    }

    /**
     * 预设底座节点列表（始终与 DB 自定义合并，保证系统级兜底能力不丢）。
     *
     * 当前仅含 SYSTEM 闲聊兜底。KB 检索不设预设——用户配了 KB 节点就用用户的精准路由，
     * 没配时 IntentDirectedChannel 自身有"退回会话首个库"的兜底，无需占位节点抢分。
     */
    private List<IntentNode> buildPresetNodes() {
        List<IntentNode> presets = new ArrayList<>();

        // SYSTEM 闲聊兜底（仅兜底，不抢分）。
        // ★ 兜底节点始终在场：即使用户一个意图都不配，闲聊也能被 LLM 路由到这里直答，系统不裸奔。
        // ★ 收窄原则：兜底节点只覆盖"无法归入任何用户细分节点"的纯礼貌/情绪表达，
        //   不再收录笑话、常识问答、自我介绍、内容生成等具体问法——
        //   这些应由用户配置的细分闲聊节点（如「通用闲聊」）承接，否则会凭宽泛 examples
        //   和用户节点对同一句话打出相同分数，反而架空用户配置（参见 NodeScore tie-break）。
        IntentNode chitchat = new IntentNode();
        chitchat.setId("sys_chitchat");
        chitchat.setName("闲聊问候");
        chitchat.setDescription("兜底闲聊：无法归入任何具体意图的纯礼貌用语（你好/谢谢/再见）与模糊情绪表达。笑话、常识问答、自我介绍、内容生成等具体闲聊请归到用户配置的细分闲聊节点。");
        chitchat.setExamples(List.of(
                "你好", "您好", "hi", "hello", "早上好", "下午好", "晚上好",
                "谢谢", "感谢", "再见", "拜拜",
                "陪我聊聊天", "无聊", "今天心情不好", "我好累", "烦死了", "开心",
                // 短应答 / 敷衍 / 情绪填充词（规避 LLM 全 <0.6 返回空，导致短闲聊漏匹配）
                "哦", "噢", "嗯", "嗯嗯", "呃", "额", "哎", "啊", "唉",
                "好吧", "好的", "好嘞", "行", "可以", "收到", "了解", "明白",
                "嗨", "嘿", "唔", "这样啊", "原来如此"
        ));
        chitchat.setKind(IntentNode.IntentKind.SYSTEM);
        chitchat.setLevel(IntentNode.IntentLevel.LEAF);
        chitchat.setFullPath("闲聊问候");
        presets.add(chitchat);

        return presets;
    }

    /** 展平叶子节点（参与打分的只有叶子） */
    public static List<IntentNode> flattenLeaves(IntentNode root) {
        List<IntentNode> leaves = new ArrayList<>();
        collectLeaves(root, leaves);
        return leaves;
    }

    private static void collectLeaves(IntentNode node, List<IntentNode> leaves) {
        if (node == null) return;
        if (node.isLeaf()) {
            leaves.add(node);
        } else {
            for (IntentNode child : node.getChildren()) {
                collectLeaves(child, leaves);
            }
        }
    }
}
