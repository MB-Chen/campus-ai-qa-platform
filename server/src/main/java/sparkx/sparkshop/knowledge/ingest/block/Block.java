// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.ingest.block;

/**
 * Block IR（移植自 sparkxV2）—— sealed interface，解析→分块间的强类型契约。
 *
 * 编译期穷举：新增 Block 类型所有 switch 必须显式处理；
 * 强类型字段告别 Map<String,Object>。
 */
public sealed interface Block
        permits HeadingBlock, ParagraphBlock, TableBlock, ImageBlock, CodeBlock, ListBlock {

    /** Block 类型标签——供 Java 17 下按类型 switch 分发（避免预览特性 switch 类型模式） */
    enum Type { HEADING, PARAGRAPH, TABLE, IMAGE, CODE, LIST }

    /** 块 id */
    String id();

    /** 来源（文件/页码/sheet/bbox） */
    Provenance provenance();

    /** 章节路径（HeadingHandler 注入，如「第3章 > 3.2 销售分析」） */
    String outlinePath();

    /** 本块类型标签，由各 record 实现返回自身 Type */
    Type type();
}
