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
 * {@link IngestPostHook} 执行上下文。
 *
 * <p>携带入库完成的必要信息：文档 id、知识库 id、引擎名、文件名、子块数等。
 * hook 实现按需读取，不需要的字段忽略。
 *
 * @param docId       文档 id（{@code doc_xxx}）
 * @param kbId        知识库 id
 * @param engine      解析引擎名（mineru / default 等）
 * @param fileName    原始文件名
 * @param chunkCount  子块入库成功数
 */
public record IngestPostContext(String docId, String kbId, String engine,
                                 String fileName, int chunkCount) {

    public static IngestPostContext of(String docId, String kbId, String engine,
                                        String fileName, int chunkCount) {
        return new IngestPostContext(docId, kbId, engine, fileName, chunkCount);
    }
}
