// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.service;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import sparkx.sparkshop.knowledge.validate.DocumentListValidate;
import sparkx.sparkshop.knowledge.validate.DocumentSaveValidate;
import sparkx.sparkshop.knowledge.validate.KbQuestionGenValidate;
import sparkx.sparkshop.knowledge.validate.PreviewValidate;
import sparkx.sparkshop.knowledge.vo.DocumentDetailVo;
import sparkx.sparkshop.knowledge.vo.DocumentPreviewVo;
import sparkx.sparkshop.knowledge.vo.DocumentSaveProgressVo;
import sparkx.sparkshop.knowledge.vo.DocumentVo;
import sparkx.sparkshop.knowledge.vo.IngestionSummary;
import sparkx.sparkshop.knowledge.vo.PreviewProgressVo;
import sparkx.sparkshop.knowledge.vo.TaskIdVo;
import sparkx.sparkshop.system.vo.PageResult;

import java.util.List;

/**
 * 知识库文档业务接口（上传入库 / 重新向量化 / 问题生成 / 删除 / 详情）
 */
public interface IKnowledgeDocumentService {

    /**
     * 分页查询文档
     */
    PageResult<DocumentVo> page(DocumentListValidate query);

    /**
     * 上传文档：落 MinIO → 建文档记录 → 同步入库
     *
     * @return 文档记录
     */
    DocumentVo upload(MultipartFile file, String kbId, String engine);

    /**
     * 试切预览：对每个文件解析 + 按运行时分块参数切分，返回切片预览。
     * 不落库、不向量化、不建文档记录。
     *
     * <p>支持父子分块：开启时每个子块携带 parentContext（父块全文）。
     * 支持问题生成：开启时每个子块携带 questions（LLM 生成的问题列表）。
     *
     * <p>★ 异步分支：当 parserEngineRules 含 mineru 系列引擎（解析耗时长）时，
     * 走异步路径——同步读字节 + 初始化 Redis 进度桶 + 通过 {@code self} 代理派发
     * {@link #doPreviewAsync}（@Async），返回 taskId（String）；
     * 其他引擎（tika/pdfbox/poi）解析很快，走原同步路径，返回切片列表。
     *
     * @param validate 试切参数（files/engine/分块参数/父子开关/问题生成开关）
     * @return mineru 场景返回 {@code TaskIdVo}（taskId）；其他场景返回 {@code List<DocumentPreviewVo>}。
     *         返回值即 Controller 直接回写的最终数据形态，C 层无需再做 instanceof/包装。
     */
    Object preview(PreviewValidate validate);

    /**
     * 异步预览执行体（由 {@code ragTaskExecutor} 执行，仅 mineru 场景触发）。
     *
     * <p>★ 不要直接调用本方法，应通过 {@code self.doPreviewAsync(...)} 走 Spring AOP 代理，
     * 否则 {@code @Async} 不生效（同类 self-call 会绕过代理）。
     *
     * @param payload 文件字节 + 分块参数载体（MultipartFile 请求结束后失效，故预读为 byte[]）
     * @param taskId  {@link #preview} 生成的任务 id（定位 Redis 进度桶）
     */
    void doPreviewAsync(Object payload, String taskId);

    /**
     * 查询预览进度（从 Redis 进度桶读取，含已完成的切片结果）。
     *
     * @param taskId {@link #preview} 返回的任务 id
     * @return 进度 VO；桶过期 / 不存在时返回 null
     */
    PreviewProgressVo getPreviewProgress(String taskId);

    /**
     * 保存试切结果（异步）：同步校验 + 初始化 Redis 进度桶 + 通过 {@code self} 代理派发
     * {@link #doSaveAsync}（@Async）后台入库，立即返回 taskId 供前端轮询。
     *
     * <p>异步入库过程：建文档记录（status=pending）→ 逐块写文本+tsv（不向量化，
     * 向量由用户主动点「向量化」触发）→ 可选问题生成。每个文档处理完后刷新进度桶，
     * 全部完成置 done；任务级异常置 failed。
     *
     * @param validate 入参（kbId + documentList）
     * @return 任务回执（taskId，前端用此轮询 {@link #getSaveProgress}）
     */
    TaskIdVo save(DocumentSaveValidate validate);

    /**
     * 异步入库执行体（由 {@code ragTaskExecutor} 执行）。
     *
     * <p>★ 不要直接调用本方法，应通过 {@code self.doSaveAsync(...)} 走 Spring AOP 代理，
     * 否则 {@code @Async} 不生效（同类 self-call 会绕过代理）。
     *
     * @param validate 入参（与 {@link #save} 同）
     * @param taskId   {@link #save} 生成的任务 id（用于定位 Redis 进度桶）
     */
    void doSaveAsync(DocumentSaveValidate validate, String taskId);

    /**
     * 查询入库存度（从 Redis 进度桶读取）。
     *
     * @param taskId {@link #save} 返回的任务 id
     * @return 进度 VO；桶过期 / 不存在时返回 null
     */
    DocumentSaveProgressVo getSaveProgress(String taskId);

    /**
     * 重新向量化指定文档（异步，由 ragTaskExecutor 执行）。
     *
     * <p>调用方应先调 {@link #markProcessing} 同步置 processing 状态（让前端立即看到），
     * 再触发本方法；本方法跑完置 done / failed。
     */
    void embedding(List<String> documentIds);

    /**
     * 重新向量化整个知识库（异步）。
     *
     * <p>查出该知识库下所有文档，复用文档级向量化逻辑（先清旧向量再按 KB 绑定模型重新 embedding）。
     * 内部已同步置 processing，调用方（Controller）无需再调 {@link #markProcessing}。
     *
     * @param kbId 知识库 id
     */
    void embeddingByKb(String kbId);

    /**
     * 同步将指定文档置为 processing 状态（供异步 embedding 触发前调用，
     * 让前端轮询立即看到「向量化中」）。
     */
    void markProcessing(List<String> documentIds);

    /**
     * 按文档列表生成问题（异步，由 ragTaskExecutor 执行）。
     *
     * <p>遍历所选文档的每个原文分块，用 LLM 生成若干问题：
     * <ul>
     *   <li>每个问题作为独立 chunk 入库（content=问题文本，metadata.type=question，
     *       metadata.source_chunk_id=来源原文 chunk id），并用 KB 绑定的 embedding 模型生成向量，
     *       以此增加向量/关键词检索的召回率。</li>
     *   <li>同步写一条 knowledge_question 记录，chunk_id 指向新生成的问题 chunk，
     *       供问题管理界面展示。</li>
     * </ul>
     *
     * <p>调用方应先调 {@link #markQuestionGenerating} 同步置 questionStatus=2（让前端轮询立即看到），
     * 再触发本方法；本方法跑完置 3/1。整体失败时外层兜底会把仍处于 2 的文档回退为 1。
     *
     * @param validate 入参（documentIds + 可选 modelId + 可选 questionCount）
     */
    void generateKbQuestions(KbQuestionGenValidate validate);

    /**
     * 生成问题编排入口（供 Controller 直接调用）：
     * 同步置 questionStatus=2（前端轮询立即看到「生成中」）→ 通过代理触发异步 {@link #generateKbQuestions}。
     *
     * <p>★ 必须走代理调用异步方法（self 注入），否则 Spring AOP 的 {@code @Async} 在同类 self-call 下不生效。
     */
    void triggerGenerateKbQuestions(KbQuestionGenValidate validate);

    /**
     * 同步将指定文档置为 questionStatus=2（生成中）。
     * 供异步 generateKbQuestions 触发前调用，让前端轮询立即看到状态变化。
     */
    void markQuestionGenerating(List<String> documentIds);

    /**
     * 删除文档：级联删除 chunks + MinIO 对象 + 图谱贡献（Neo4j + kg_entity + 抽取记录）
     */
    void delete(List<String> documentIds);

    /**
     * 切换文档级知识图谱开关（kg_enabled: 1=启用 2=禁用）。
     *
     * <p>关闭时若该文档已有抽取数据，自然级联清理其图谱（与删除文档一致的清理逻辑），
     * 避免关闭后图谱中残留该文档的实体/关系污染检索召回。
     *
     * @param documentId 文档 id
     * @param kgEnabled  1=启用 2=禁用
     */
    void toggleKg(String documentId, Integer kgEnabled);

    /**
     * 文档详情（含子块列表）
     */
    DocumentDetailVo detail(String documentId);

    /**
     * 查询单文档的入库耗时统计（前端「统计」图标弹窗用）。
     * 若该文档无统计（老数据 / 未走新流程），返回 null，前端兜底显示「暂无耗时数据」。
     */
    IngestionSummary getIngestionSummary(String documentId);

    /**
     * 下载文档原文件（浏览器直下载，由 MinIO 流式回写 HttpServletResponse）。
     *
     * @param documentId 文档 id
     * @param response   响应（写入 attachment 头 + 字节流）
     */
    void downloadDocument(String documentId, HttpServletResponse response);

    /**
     * 把逗号分隔的 id 字符串解析为 List（去空白、去空串）。
     * 供 Controller 层把 {@code @RequestParam("documentIds") String} 直接转成 List，
     * C 层不持有解析逻辑。
     *
     * @param documentIds 逗号分隔的 id 字符串（可空）
     * @return 解析后的 id 列表（永不为 null，空输入返回空 List）
     */
    List<String> parseIds(String documentIds);

    /**
     * 重新向量化编排入口（供 Controller 直接调用）：
     * 解析逗号分隔的 documentIds → 空列表直接返回 → 同步置 processing（前端立即看到「向量化中」）
     * → 异步触发 {@link #embedding}。
     *
     * @param documentIds 逗号分隔的文档 id（可空）
     */
    void triggerEmbedding(String documentIds);

    /**
     * 删除文档编排入口（供 Controller 直接调用）：
     * 解析逗号分隔的 documentIds 后级联删除（子块 + MinIO 对象 + 图谱数据）。
     *
     * @param documentIds 逗号分隔的文档 id（可空）
     */
    void deleteDocuments(String documentIds);
}
