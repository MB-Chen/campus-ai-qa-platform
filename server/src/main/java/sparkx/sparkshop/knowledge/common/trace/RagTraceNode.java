// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.common.trace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RAG 链路追踪节点（移植自 sparkxV2，文档 8.3）。
 *
 * AOP 切面 {@code RagTraceAspect} 采集被标注方法的耗时，上报 Micrometer 指标。
 * 关键 type 取值：
 *  - LLM_ROUTING：模型路由（含降级链）
 *  - LLM_TTFT：首字延迟（首包探测，time-to-first-token）
 *  - LLM_PROVIDER：供应商调用
 *  - PIPELINE_STAGE：管线阶段
 *  - RETRIEVAL：检索
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RagTraceNode {
    /** 节点名（指标 tag "name"） */
    String name();
    /** 节点类型（指标 tag "type"） */
    String type() default "";
}
