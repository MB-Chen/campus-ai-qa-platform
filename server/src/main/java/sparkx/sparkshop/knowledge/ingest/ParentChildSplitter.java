// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.ingest;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 父子分块器
 *
 * 流程：
 *  1. 用 parentSplitter 切成大父块（不向量化，仅存储全文）
 *  2. 用 childSplitter 把每个父块切成小子块（向量化、用于检索）
 *  3. 子块 metadata 携带 parentId，检索命中子块后展开为父块内容
 *
 * 改进点 #5：用 metadata 承载父子关系，不依赖固定 DB 字段。
 */
public class ParentChildSplitter implements DocumentSplitter {

    private final DocumentSplitter parentSplitter;   // 大窗口
    private final DocumentSplitter childSplitter;     // 小窗口

    /** 父块内容缓存：parentId -> text，供入库时落 parent_chunks 表 */
    private final Map<String, String> parentContents = new HashMap<>();

    public ParentChildSplitter(DocumentSplitter parentSplitter, DocumentSplitter childSplitter) {
        this.parentSplitter = parentSplitter;
        this.childSplitter = childSplitter;
    }

    /**
     * 返回所有子块（父块内容由 {@link #getParentContents()} 单独获取并落库）。
     * 每个子块 metadata 含 parentId、childIndex、chunkRole=child。
     */
    @Override
    public List<TextSegment> split(Document document) {
        parentContents.clear();
        List<TextSegment> parents = parentSplitter.split(document);
        List<TextSegment> children = new ArrayList<>();

        int childSeq = 0;
        for (int pi = 0; pi < parents.size(); pi++) {
            TextSegment parent = parents.get(pi);
            String parentId = "p_" + UUID.randomUUID().toString().replace("-", "");

            // 父块内容缓存
            parentContents.put(parentId, parent.text());

            Document parentDoc = Document.from(parent.text(), parent.metadata());
            List<TextSegment> subs = childSplitter.split(parentDoc);

            for (TextSegment sub : subs) {
                Metadata meta = Metadata.from(sub.metadata().toMap());
                meta.put("parentId", parentId);
                meta.put("parentIndex", String.valueOf(pi));
                meta.put("childIndex", String.valueOf(childSeq++));
                meta.put("chunkRole", "child");
                children.add(TextSegment.from(sub.text(), meta));
            }
        }
        return children;
    }

    /** 父块内容（parentId -> 全文），入库时由调用方写入 parent_chunks 表 */
    public Map<String, String> getParentContents() {
        return parentContents;
    }
}
