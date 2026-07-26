// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.entity.KnowledgeQuestion;
import sparkx.sparkshop.knowledge.ingest.QuestionIndexer;
import sparkx.sparkshop.knowledge.mapper.KnowledgeQuestionMapper;
import sparkx.sparkshop.knowledge.service.IKnowledgeQuestionService;
import sparkx.sparkshop.knowledge.validate.QuestionListValidate;
import sparkx.sparkshop.knowledge.validate.QuestionValidate;
import sparkx.sparkshop.knowledge.validate.RelationValidate;
import sparkx.sparkshop.knowledge.vo.QuestionImportProgressVo;
import sparkx.sparkshop.knowledge.vo.TaskIdVo;
import sparkx.sparkshop.system.vo.PageResult;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * 知识库问题（Q&A）业务实现。
 *
 * <p>★ 手动新增 / 编辑 / 删除 / 批量导入 与「AI 生成问题」入库效果对齐：
 * 通过 {@link QuestionIndexer} 写 chunks 表（type=question）+ 生成向量，
 * 让手动录入的问题也能被向量 / 关键词检索召回。
 */
@Slf4j
@Service
public class KnowledgeQuestionServiceImpl implements IKnowledgeQuestionService {

    /** 单次导入最大行数（清洗后），防止一次性任务过长 */
    private static final int IMPORT_MAX_ROWS = 500;

    /** 进度桶 Redis key 前缀 */
    private static final String PROGRESS_KEY_PREFIX = "knowledge:question:import:";

    /** 进度桶 TTL */
    private static final Duration PROGRESS_TTL = Duration.ofHours(1);

    @Resource
    private KnowledgeQuestionMapper knowledgeQuestionMapper;

    @Resource
    private QuestionIndexer questionIndexer;

    @Resource
    private RedissonClient redisson;

    /**
     * 自注入代理对象：用于在 {@link #importQuestions} 中调 {@link #doImportQuestionsAsync}（{@code @Async}）。
     * 同类内直接 this 调用会绕过 Spring AOP 代理导致 {@code @Async} 失效，必须走代理。
     * 用 {@code @Lazy} 打破「自己依赖自己」的启动期循环依赖。
     */
    @Lazy
    @Resource
    private IKnowledgeQuestionService self;

    /** 问题列表（按知识库），分页。 */
    @Override
    public PageResult<KnowledgeQuestion> page(QuestionListValidate query) {
        LambdaQueryWrapper<KnowledgeQuestion> wrapper = new LambdaQueryWrapper<KnowledgeQuestion>()
                .eq(KnowledgeQuestion::getKbId, query.getKbId())
                .orderByDesc(KnowledgeQuestion::getCreatedAt);
        IPage<KnowledgeQuestion> mpPage = new Page<>(query.safePage(), query.safeSize());
        IPage<KnowledgeQuestion> result = knowledgeQuestionMapper.selectPage(mpPage, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    /** 手动新增问题（走 embedding 向量化入库，source=manual）。 */
    @Override
    public void add(QuestionValidate validate) {
        String kbId = validate.getKbId();
        String content = validate.getContent();
        // 1) 向量化 + 写 chunks 表（type=question）
        String chunkId = questionIndexer.index(kbId, content, null, null);
        // 2) 写 knowledge_question 表（chunk_id 关联）
        KnowledgeQuestion q = new KnowledgeQuestion();
        q.setKbId(kbId);
        q.setContent(content);
        q.setChunkId(chunkId);
        q.setSource("manual");
        q.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        knowledgeQuestionMapper.insert(q);
    }

    /** 编辑问题内容（同步刷新 chunk 的 content / tsv / 向量）。 */
    @Override
    public void edit(QuestionValidate validate) {
        if (validate.getId() == null) {
            throw new BusinessException("问题 id 不能为空");
        }
        KnowledgeQuestion q = knowledgeQuestionMapper.selectById(validate.getId());
        if (q == null) {
            throw new BusinessException("问题不存在");
        }
        String newContent = validate.getContent();
        // 有 chunkId → 走 reindex；无 chunkId（历史数据）→ 补建一条问题切片
        if (q.getChunkId() != null && !q.getChunkId().isBlank()) {
            questionIndexer.reindex(q.getChunkId(), q.getKbId(), newContent);
        } else {
            String chunkId = questionIndexer.index(q.getKbId(), newContent, null, null);
            q.setChunkId(chunkId);
        }
        q.setContent(newContent);
        q.setUpdatedAt(LocalDateTime.now());
        knowledgeQuestionMapper.updateById(q);
    }

    /** 删除问题（同步删除关联的 chunk 切片，避免孤儿向量）。 */
    @Override
    public void remove(Long id) {
        KnowledgeQuestion q = knowledgeQuestionMapper.selectById(id);
        if (q == null) {
            return;
        }
        if (q.getChunkId() != null && !q.getChunkId().isBlank()) {
            questionIndexer.unindex(q.getChunkId());
        }
        knowledgeQuestionMapper.deleteById(id);
    }

    /** 关联问题到指定子块（设置 chunkId）。 */
    @Override
    public void doRelation(RelationValidate validate) {
        KnowledgeQuestion q = knowledgeQuestionMapper.selectById(validate.getQuestionId());
        if (q == null) {
            throw new BusinessException("问题不存在");
        }
        q.setChunkId(validate.getChunkId());
        q.setUpdatedAt(LocalDateTime.now());
        knowledgeQuestionMapper.updateById(q);
    }


    /** 批量导入问题：同步解析 + 清洗 + 派发异步处理。 */
    @Override
    public TaskIdVo importQuestions(String kbId, MultipartFile file) {
        if (kbId == null || kbId.isBlank()) {
            throw new BusinessException("知识库 id 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 Excel 文件");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null
                || (!fileName.toLowerCase().endsWith(".xls") && !fileName.toLowerCase().endsWith(".xlsx"))) {
            throw new BusinessException("仅支持 .xls / .xlsx 格式");
        }

        // 1) 同步解析 Excel + 清洗去重
        List<String> questions;
        try (InputStream is = file.getInputStream()) {
            questions = parseQuestions(is);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("[ImportQuestion] 解析 Excel 失败: {}", e.getMessage(), e);
            throw new BusinessException("Excel 解析失败：" + e.getMessage());
        }
        if (questions.isEmpty()) {
            throw new BusinessException("文件中没有可导入的问题（首行视为表头已跳过，请确认从第二行开始填写）");
        }
        if (questions.size() > IMPORT_MAX_ROWS) {
            throw new BusinessException("单次导入不能超过 " + IMPORT_MAX_ROWS + " 行，当前 " + questions.size() + " 行");
        }

        // 2) 初始化进度桶
        String taskId = UUID.randomUUID().toString().replace("-", "");
        RBucket<QuestionImportProgressVo> bucket = progressBucket(taskId);
        bucket.set(QuestionImportProgressVo.processing(questions.size()), PROGRESS_TTL);

        // 3) 通过代理派发异步任务（保证 @Async 生效）
        self.doImportQuestionsAsync(kbId, questions, taskId);
        return new TaskIdVo(taskId);
    }

    /**
     * 异步执行批量导入。逐条向量化入库（独立 try-catch，单条失败不中断整批），
     * 每条处理后刷新 Redis 进度桶，全部完成置 status=done。
     */
    @Override
    @Async("ragTaskExecutor")
    public void doImportQuestionsAsync(String kbId, List<String> questions, String taskId) {
        RBucket<QuestionImportProgressVo> bucket = progressBucket(taskId);
        QuestionImportProgressVo progress = bucket.get();
        if (progress == null) {
            // 进度桶已过期（Redis 重启 / 超过 1 小时），不再处理
            log.warn("[ImportQuestion] 进度桶不存在 taskId={}，放弃处理", taskId);
            return;
        }
        int success = 0;
        int failed = 0;
        for (String q : questions) {
            try {
                String chunkId = questionIndexer.index(kbId, q, null, null);
                KnowledgeQuestion kq = new KnowledgeQuestion();
                kq.setKbId(kbId);
                kq.setChunkId(chunkId);
                kq.setContent(q);
                kq.setSource("import");
                kq.setStatus(1);
                LocalDateTime now = LocalDateTime.now();
                kq.setCreatedAt(now);
                kq.setUpdatedAt(now);
                knowledgeQuestionMapper.insert(kq);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("[ImportQuestion] 单条入库失败 kb={} q={} : {}", kbId, q, e.getMessage());
            }
            // 刷新进度
            progress.setSuccess(success);
            progress.setFailed(failed);
            progress.setDone(success + failed);
            bucket.set(progress, PROGRESS_TTL);
        }
        progress.setStatus("done");
        bucket.set(progress, PROGRESS_TTL);
        log.info("[ImportQuestion] 任务 {} 完成 total={} success={} failed={}",
                taskId, progress.getTotal(), success, failed);
    }

    /** 查询导入进度。 */
    @Override
    public QuestionImportProgressVo getImportProgress(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return progressBucket(taskId).get();
    }


    private RBucket<QuestionImportProgressVo> progressBucket(String taskId) {
        return redisson.getBucket(PROGRESS_KEY_PREFIX + taskId);
    }

    /**
     * 解析 Excel：跳过首行表头，取每行第一列，清洗不可见空白 + 去重 + 过滤空串。
     * 复用 {@link sparkx.sparkshop.knowledge.ingest.SpreadsheetRowSplitter} 的清洗思路
     * （NBSP / ZERO-WIDTH 等不可见字符），独立写一份避免动到那个类的 private 方法。
     */
    private List<String> parseQuestions(InputStream is) throws Exception {
        List<String> raw = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(is)) {
            for (Iterator<Sheet> it = wb.sheetIterator(); it.hasNext(); ) {
                Sheet sheet = it.next();
                boolean firstRow = true;
                for (Row row : sheet) {
                    if (firstRow) {
                        // 首行视为表头跳过（不论 sheet 名）
                        firstRow = false;
                        continue;
                    }
                    if (row == null) {
                        continue;
                    }
                    Cell cell = row.getCell(0);
                    String val = cell == null ? "" : formatCell(cell, formatter);
                    raw.add(val);
                }
            }
        }
        // 清洗 + 过滤 + 去重（保持顺序）
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : raw) {
            String cleaned = stripWs(s);
            if (!cleaned.isEmpty()) {
                set.add(cleaned);
            }
        }
        return new ArrayList<>(set);
    }

    /** 公式取计算值；其余按 DataFormatter 输出（保留原显示格式）。 */
    private static String formatCell(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                return stripWs(switch (cell.getCachedFormulaResultType()) {
                    case NUMERIC -> String.valueOf(cell.getNumericCellValue());
                    case STRING -> cell.getStringCellValue();
                    case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                    default -> formatter.formatCellValue(cell);
                });
            } catch (Exception ignore) {
                return stripWs(formatter.formatCellValue(cell));
            }
        }
        return stripWs(formatter.formatCellValue(cell));
    }

    /** 去除首尾空白，包括常见的不可见空白字符（NBSP U+00A0 等），这些常混入从 Excel 复制来的文本。 */
    private static String stripWs(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        int start = 0;
        int end = s.length();
        while (start < end && isWs(s.charAt(start))) {
            start++;
        }
        while (end > start && isWs(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    private static boolean isWs(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r'
                || c == '\u00A0' || c == '\u200B' || c == '\uFEFF';
    }
}
