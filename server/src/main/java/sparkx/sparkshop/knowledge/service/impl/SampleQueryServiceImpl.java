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
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.entity.SampleQuery;
import sparkx.sparkshop.knowledge.entity.SampleQueryConfig;
import sparkx.sparkshop.knowledge.ingest.SampleQueryIndexer;
import sparkx.sparkshop.knowledge.mapper.SampleQueryConfigMapper;
import sparkx.sparkshop.knowledge.mapper.SampleQueryMapper;
import sparkx.sparkshop.knowledge.service.SampleQueryService;
import sparkx.sparkshop.knowledge.validate.SampleQueryConfigValidate;
import sparkx.sparkshop.knowledge.validate.SampleQueryListValidate;
import sparkx.sparkshop.knowledge.validate.SampleQueryValidate;
import sparkx.sparkshop.knowledge.validate.SampleQueryVectorizeBatchValidate;
import sparkx.sparkshop.knowledge.vo.SampleQueryImportResultVo;
import sparkx.sparkshop.knowledge.vo.SampleQueryVectorizeProgressVo;
import sparkx.sparkshop.knowledge.vo.TaskIdVo;
import sparkx.sparkshop.system.vo.PageResult;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 样例查询业务实现。
 *
 * <p>★ 向量化时机：CRUD / 导入 <b>不自动</b>向量化，由用户主动点「向量化」/「批量向量化」。
 * 这样导入大表不会被向量化阻塞，且换模型后可随时重做批量向量化。
 *
 * <p>★ 编辑时若问题文本变化且已有 chunkId：同步重新向量化该条（保证向量与最新文本一致）；
 * 若无 chunkId（之前未向量化），编辑不触发向量化（仍需用户主动点）。
 */
@Slf4j
@Service
public class SampleQueryServiceImpl implements SampleQueryService {

    /** 单次导入最大行数（清洗后） */
    private static final int IMPORT_MAX_ROWS = 500;

    /** 批量向量化进度桶 Redis key 前缀 */
    private static final String PROGRESS_KEY_PREFIX = "sample_query:vectorize:";

    /** 进度桶 TTL */
    private static final Duration PROGRESS_TTL = Duration.ofHours(1);

    /** 全局配置固定 id */
    private static final int CONFIG_ID = 1;

    /** 默认相似度阈值（与表 DDL DEFAULT 对齐） */
    private static final java.math.BigDecimal DEFAULT_THRESHOLD = new java.math.BigDecimal("0.850");

    @Resource
    private SampleQueryMapper sampleQueryMapper;

    @Resource
    private SampleQueryConfigMapper sampleQueryConfigMapper;

    @Resource
    private SampleQueryIndexer sampleQueryIndexer;

    @Resource
    private RedissonClient redisson;

    /**
     * 自注入代理对象：用于在 {@link #vectorizeBatch} 中调 {@link #doVectorizeBatchAsync}（{@code @Async}）。
     * 同类内直接 this 调用会绕过 Spring AOP 代理导致 {@code @Async} 失效，必须走代理。
     * 用 {@code @Lazy} 打破「自己依赖自己」的启动期循环依赖。
     */
    @Lazy
    @Resource
    private SampleQueryService self;


    @Override
    public PageResult<SampleQuery> page(SampleQueryListValidate query) {
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        LambdaQueryWrapper<SampleQuery> wrapper = new LambdaQueryWrapper<SampleQuery>()
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(SampleQuery::getQuestion, keyword)
                        .or().like(SampleQuery::getAnswer, keyword))
                .eq(query.getStatus() != null, SampleQuery::getStatus, query.getStatus())
                // vectorized 过滤：1=已向量化 2=未向量化（直接按 vectorized 字段过滤，避免对 pgvector 列判空）
                .eq(query.getVectorized() != null && query.getVectorized() == 1, SampleQuery::getVectorized, 1)
                .eq(query.getVectorized() != null && query.getVectorized() == 2, SampleQuery::getVectorized, 0)
                .orderByDesc(SampleQuery::getCreatedAt);
        IPage<SampleQuery> mpPage = new Page<>(query.safePage(), query.safeSize());
        IPage<SampleQuery> result = sampleQueryMapper.selectPage(mpPage, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public void add(SampleQueryValidate validate) {
        SampleQuery q = new SampleQuery();
        q.setQuestion(validate.getQuestion().trim());
        q.setAnswer(validate.getAnswer().trim());
        q.setSource("manual");
        q.setStatus(validate.getStatus() != null ? validate.getStatus() : 1);
        LocalDateTime now = LocalDateTime.now();
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        sampleQueryMapper.insert(q);
    }

    @Override
    public void edit(SampleQueryValidate validate) {
        if (validate.getId() == null) {
            throw new BusinessException("样例 id 不能为空");
        }
        SampleQuery existed = sampleQueryMapper.selectById(validate.getId());
        if (existed == null) {
            throw new BusinessException("样例不存在");
        }
        String newQuestion = validate.getQuestion().trim();
        String oldQuestion = existed.getQuestion();

        // 问题文本变化 + 已向量化 → 同步重新向量化该条（保持向量与文本一致）
        // 向量直接回填到 sample_query 表，无需关联 chunks
        if (!newQuestion.equals(oldQuestion) && existed.getVectorized() != null && existed.getVectorized() == 1) {
            SampleQueryConfig cfg = getConfig();
            sampleQueryIndexer.index(existed.getId(), newQuestion,
                    cfg.getEmbeddingModelId(), cfg.getEmbeddingModelName());
        }
        existed.setQuestion(newQuestion);
        existed.setAnswer(validate.getAnswer().trim());
        if (validate.getStatus() != null) {
            existed.setStatus(validate.getStatus());
        }
        existed.setUpdatedAt(LocalDateTime.now());
        sampleQueryMapper.updateById(existed);
    }

    @Override
    public void remove(Long id) {
        if (id == null) {
            return;
        }
        SampleQuery q = sampleQueryMapper.selectById(id);
        if (q == null) {
            return;
        }
        // 向量直接挂在本表，删除行即向量随行消失；无需额外清理 chunks 表
        sampleQueryMapper.deleteById(id);
    }


    @Override
    public SampleQueryImportResultVo importExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 Excel 文件");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null
                || (!fileName.toLowerCase().endsWith(".xls") && !fileName.toLowerCase().endsWith(".xlsx"))) {
            throw new BusinessException("仅支持 .xls / .xlsx 格式");
        }

        // 1) 解析：每行 {question, answer}，首行表头跳过
        List<String[]> rows;
        try (InputStream is = file.getInputStream()) {
            rows = parseRows(is);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("[ImportSampleQuery] 解析 Excel 失败: {}", e.getMessage(), e);
            throw new BusinessException("Excel 解析失败：" + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new BusinessException("文件中没有可导入的行（首行视为表头已跳过，请确认从第二行开始填写）");
        }
        if (rows.size() > IMPORT_MAX_ROWS) {
            throw new BusinessException("单次导入不能超过 " + IMPORT_MAX_ROWS + " 行，当前 " + rows.size() + " 行");
        }

        // 2) 逐行入库（不向量化，由用户后续主动触发）
        int success = 0, failed = 0, skipped = 0;
        LocalDateTime now = LocalDateTime.now();
        for (String[] row : rows) {
            String question = row.length > 0 ? row[0] : "";
            String answer = row.length > 1 ? row[1] : "";
            if (question.isBlank() || answer.isBlank()) {
                skipped++;
                continue;
            }
            try {
                SampleQuery q = new SampleQuery();
                q.setQuestion(question.trim());
                q.setAnswer(answer.trim());
                q.setSource("import");
                q.setStatus(1);
                q.setCreatedAt(now);
                q.setUpdatedAt(now);
                sampleQueryMapper.insert(q);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("[ImportSampleQuery] 单条入库失败 q={} a={} : {}", question, answer, e.getMessage());
            }
        }
        log.info("[ImportSampleQuery] 导入完成 total={} success={} failed={} skipped={}",
                rows.size(), success, failed, skipped);
        return SampleQueryImportResultVo.of(rows.size(), success, failed, skipped);
    }

    @Override
    public void exportExcel(SampleQueryListValidate query, HttpServletResponse response) {
        // 与列表同过滤口径，但不分页（导出全部）
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        LambdaQueryWrapper<SampleQuery> wrapper = new LambdaQueryWrapper<SampleQuery>()
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(SampleQuery::getQuestion, keyword)
                        .or().like(SampleQuery::getAnswer, keyword))
                .eq(query.getStatus() != null, SampleQuery::getStatus, query.getStatus())
                .eq(query.getVectorized() != null && query.getVectorized() == 1, SampleQuery::getVectorized, 1)
                .eq(query.getVectorized() != null && query.getVectorized() == 2, SampleQuery::getVectorized, 0)
                .orderByDesc(SampleQuery::getCreatedAt);
        List<SampleQuery> list = sampleQueryMapper.selectList(wrapper);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("样例查询");
            // 表头
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("问题");
            header.createCell(1).setCellValue("答案");
            header.createCell(2).setCellValue("来源");
            header.createCell(3).setCellValue("状态");
            header.createCell(4).setCellValue("向量化");
            sheet.setColumnWidth(0, 40 * 256);
            sheet.setColumnWidth(1, 60 * 256);

            for (int i = 0; i < list.size(); i++) {
                SampleQuery q = list.get(i);
                Row r = sheet.createRow(i + 1);
                r.createCell(0).setCellValue(q.getQuestion());
                r.createCell(1).setCellValue(q.getAnswer());
                r.createCell(2).setCellValue(q.getSource() == null ? "" : q.getSource());
                r.createCell(3).setCellValue(q.getStatus() != null && q.getStatus() == 1 ? "启用" : "禁用");
                r.createCell(4).setCellValue(q.getVectorized() != null && q.getVectorized() == 1 ? "是" : "否");
            }

            String fileName = URLEncoder.encode("样例查询导出.xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
            try (OutputStream os = response.getOutputStream()) {
                wb.write(os);
                os.flush();
            }
        } catch (Exception e) {
            log.error("[ExportSampleQuery] 导出失败: {}", e.getMessage(), e);
            throw new BusinessException("导出失败：" + e.getMessage());
        }
    }


    @Override
    public void vectorize(Long id) {
        if (id == null) {
            throw new BusinessException("样例 id 不能为空");
        }
        SampleQuery q = sampleQueryMapper.selectById(id);
        if (q == null) {
            throw new BusinessException("样例不存在");
        }
        SampleQueryConfig cfg = getConfig();
        // 向量直接回填到 sample_query 表（index 内部处理新建/重建，无需区分）
        sampleQueryIndexer.index(q.getId(), q.getQuestion(),
                cfg.getEmbeddingModelId(), cfg.getEmbeddingModelName());
    }

    @Override
    public TaskIdVo vectorizeBatch(SampleQueryVectorizeBatchValidate req) {
        List<Long> ids = req == null ? null : req.getIds();
        // ids 为空 → 全部启用项
        LambdaQueryWrapper<SampleQuery> wrapper = new LambdaQueryWrapper<SampleQuery>()
                .eq(SampleQuery::getStatus, 1);
        if (ids != null && !ids.isEmpty()) {
            wrapper.in(SampleQuery::getId, ids);
        }
        List<SampleQuery> targets = sampleQueryMapper.selectList(wrapper);
        if (targets.isEmpty()) {
            throw new BusinessException("没有可向量化的样例");
        }

        String taskId = java.util.UUID.randomUUID().toString().replace("-", "");
        RBucket<SampleQueryVectorizeProgressVo> bucket = progressBucket(taskId);
        bucket.set(SampleQueryVectorizeProgressVo.processing(targets.size()), PROGRESS_TTL);

        // 通过代理派发异步任务（保证 @Async 生效）
        List<Long> targetIds = targets.stream().map(SampleQuery::getId).toList();
        self.doVectorizeBatchAsync(targetIds, taskId);
        return new TaskIdVo(taskId);
    }

    @Override
    @Async("ragTaskExecutor")
    public void doVectorizeBatchAsync(List<Long> ids, String taskId) {
        RBucket<SampleQueryVectorizeProgressVo> bucket = progressBucket(taskId);
        SampleQueryVectorizeProgressVo progress = bucket.get();
        if (progress == null) {
            log.warn("[VectorizeSampleQuery] 进度桶不存在 taskId={}，放弃处理", taskId);
            return;
        }
        SampleQueryConfig cfg = getConfig();

        int success = 0, failed = 0;
        for (Long id : ids) {
            try {
                SampleQuery q = sampleQueryMapper.selectById(id);
                if (q == null) {
                    failed++;
                } else {
                    // 向量直接回填到 sample_query 表（index 内部处理新建/重建）
                    sampleQueryIndexer.index(q.getId(), q.getQuestion(),
                            cfg.getEmbeddingModelId(), cfg.getEmbeddingModelName());
                    success++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("[VectorizeSampleQuery] 单条向量化失败 id={} : {}", id, e.getMessage());
            }
            progress.setSuccess(success);
            progress.setFailed(failed);
            progress.setDone(success + failed);
            bucket.set(progress, PROGRESS_TTL);
        }
        progress.setStatus("done");
        bucket.set(progress, PROGRESS_TTL);
        log.info("[VectorizeSampleQuery] 任务 {} 完成 total={} success={} failed={}",
                taskId, progress.getTotal(), success, failed);
    }

    @Override
    public SampleQueryVectorizeProgressVo getVectorizeProgress(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return progressBucket(taskId).get();
    }


    @Override
    public SampleQueryConfig getConfig() {
        SampleQueryConfig cfg = sampleQueryConfigMapper.selectById(CONFIG_ID);
        if (cfg == null) {
            // 兜底：DDL 应已初始化 id=1，此处防御性补建
            cfg = new SampleQueryConfig();
            cfg.setId(CONFIG_ID);
            cfg.setSimilarityThreshold(DEFAULT_THRESHOLD);
            sampleQueryConfigMapper.insert(cfg);
        }
        return cfg;
    }

    @Override
    public void saveConfig(SampleQueryConfigValidate validate) {
        SampleQueryConfig cfg = getConfig();
        cfg.setEmbeddingModelId(validate.getEmbeddingModelId());
        cfg.setEmbeddingModelName(validate.getEmbeddingModelName());
        if (validate.getSimilarityThreshold() != null) {
            cfg.setSimilarityThreshold(validate.getSimilarityThreshold());
        }
        cfg.setUpdatedAt(LocalDateTime.now());
        sampleQueryConfigMapper.updateById(cfg);
    }


    private RBucket<SampleQueryVectorizeProgressVo> progressBucket(String taskId) {
        return redisson.getBucket(PROGRESS_KEY_PREFIX + taskId);
    }

    /**
     * 解析 Excel：跳过首行表头，取每行前两列（question, answer），清洗空白 + 过滤全空行。
     * 复用 {@link KnowledgeQuestionServiceImpl} 同款清洗思路（NBSP / ZERO-WIDTH 等不可见字符）。
     */
    private List<String[]> parseRows(InputStream is) throws Exception {
        List<String[]> raw = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(is)) {
            for (Iterator<Sheet> it = wb.sheetIterator(); it.hasNext(); ) {
                Sheet sheet = it.next();
                boolean firstRow = true;
                for (Row row : sheet) {
                    if (firstRow) {
                        firstRow = false;
                        continue;
                    }
                    if (row == null) {
                        continue;
                    }
                    String c0 = formatCell(row.getCell(0), formatter);
                    String c1 = formatCell(row.getCell(1), formatter);
                    if (c0.isEmpty() && c1.isEmpty()) {
                        continue;
                    }
                    raw.add(new String[]{c0, c1});
                }
            }
        }
        return raw;
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

    /** 去除首尾空白，包括常见的不可见空白字符（NBSP U+00A0 等）。 */
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
