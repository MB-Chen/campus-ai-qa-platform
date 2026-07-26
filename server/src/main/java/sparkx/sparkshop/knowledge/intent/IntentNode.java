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

import java.util.ArrayList;
import java.util.List;

/**
 * 意图节点 —— 扁平意图列表（单层），统一承载 KB/SYSTEM/MCP 三类意图。
 *
 * ★ kind 把 KB/SYSTEM/MCP 三类意图统一到一个框架：
 *  - KB：挂 collectionName（= kbId）+ topK + promptTemplate（检索特定知识库）
 *  - SYSTEM：闲聊/打招呼，直接 LLM 直答不走检索
 *  - MCP：挂 mcpToolId + paramPromptTemplate（调用业务工具）
 * 每个节点都是叶子，参与打分和挂载。
 *
 * 说明：早期为 DOMAIN/CATEGORY/TOPIC 三级树，但运行时只取叶子打分，
 * 中间层语义是死代码，已简化为单层扁平列表。level 字段保留仅为兼容旧数据，不再有业务语义。
 * 类名/方法名（如 {@code IntentTreeCacheManager.loadTree/flattenLeaves}）为历史遗留，保留不变。
 */
public class IntentNode {

    // 层级枚举（保留兼容旧数据，单层模型下统一为 LEAF，不再有 DOMAIN/CATEGORY/TOPIC 三级语义）
    public enum IntentLevel { LEAF(0);
        public final int depth;
        IntentLevel(int d) { this.depth = d; }
    }

    public enum IntentKind { KB, SYSTEM, MCP }

    private String id;
    private String parentId;
    private IntentLevel level;
    private IntentKind kind = IntentKind.KB;
    private String name;
    private String description;          // 语义说明，喂给向量/LLM
    private List<String> examples;       // 典型问法（叶子节点）
    private String fullPath;             // 预计算，如「集团信息化 > 人事」
    private String collectionName;       // KB：对应知识库 collection
    private List<String> docIds;         // KB：限定检索的文档 id 列表，为空检索整库
    private String mcpToolId;            // MCP：对应工具 ID
    private String paramPromptTemplate;  // MCP：参数提取自定义提示词
    private String promptTemplate;       // KB：意图级回答模板覆盖
    private Integer topK;                // KB：节点级 topK 覆盖
    private boolean enabled = true;

    private final List<IntentNode> children = new ArrayList<>();

    public IntentNode() { }

    public boolean isLeaf() { return children.isEmpty(); }
    public boolean isKB() { return kind == IntentKind.KB; }
    public boolean isMCP() { return kind == IntentKind.MCP && mcpToolId != null; }
    public boolean isSystem() { return kind == IntentKind.SYSTEM; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public IntentLevel getLevel() { return level; }
    public void setLevel(IntentLevel level) { this.level = level; }
    public IntentKind getKind() { return kind; }
    public void setKind(IntentKind kind) { this.kind = kind; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getExamples() { return examples; }
    public void setExamples(List<String> examples) { this.examples = examples; }
    public String getFullPath() { return fullPath; }
    public void setFullPath(String fullPath) { this.fullPath = fullPath; }
    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }
    public List<String> getDocIds() { return docIds; }
    public void setDocIds(List<String> docIds) { this.docIds = docIds; }
    public String getMcpToolId() { return mcpToolId; }
    public void setMcpToolId(String mcpToolId) { this.mcpToolId = mcpToolId; }
    public String getParamPromptTemplate() { return paramPromptTemplate; }
    public void setParamPromptTemplate(String paramPromptTemplate) { this.paramPromptTemplate = paramPromptTemplate; }
    public String getPromptTemplate() { return promptTemplate; }
    public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<IntentNode> getChildren() { return children; }
    public void addChild(IntentNode child) { this.children.add(child); }
}
