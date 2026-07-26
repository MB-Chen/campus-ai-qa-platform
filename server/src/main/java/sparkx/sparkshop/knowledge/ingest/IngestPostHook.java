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

/**
 * 文档入库后置钩子（可插拔增强点）—— 仿 ragent GraphSyncingVectorStoreService 装饰器模式。
 *
 * <p>★ 设计动机：把「入库完成后触发的增强逻辑」（知识图谱抽取、未来可能的关键词索引同步、
 * 问答对预生成等）从 {@code DocumentIngestService.ingest()} 主流程里解耦，
 * 让每个增强成为独立的、可条件装配的 Bean，新增增强不改 ingest 主流程代码。
 *
 * <p>★ 与装饰器的区别：ragent 用装饰器包 {@code VectorStoreService}（向量写→触发建图），
 * SparkX 的入库是「解析→分块→向量化→图谱」一体的编排方法，没有独立 VectorStoreService 可包。
 * 这里改用「事件钩子」模式：{@code DocumentIngestService} 在文档状态置 done 后遍历所有
 * {@link IngestPostHook}，每个 hook 自己判断是否生效（{@link #shouldRun}）。
 *
 * <p>★ 装配约定：实现类应配合 {@code @ConditionalOnProperty} / {@code @ConditionalOnMissingBean}
 * 做条件装配——KG 关闭时对应 Bean 不存在，DocumentIngestService 注入空列表，零开销跳过。
 *
 * <p>★ 容错约定：单个 hook 抛异常由 DocumentIngestService try/catch 兜底，不影响其它 hook 和主流程。
 */
public interface IngestPostHook {

    /**
     * 本轮是否生效（hook 自判，例如检查全局开关、KB 级开关、文档类型等）。
     * 返回 false 时直接跳过，不调 {@link #afterIngest}。
     */
    boolean shouldRun(IngestPostContext ctx);

    /**
     * 入库完成后执行（文档状态已置 done）。允许抛异常，由调用方兜底。
     * 建议实现内部用 @Async 异步化，避免阻塞主入库链路。
     */
    void afterIngest(IngestPostContext ctx);

    /** 执行顺序（数字小先执行）。同 order 的 hook 之间无依赖保证。 */
    default int getOrder() { return 100; }
}
