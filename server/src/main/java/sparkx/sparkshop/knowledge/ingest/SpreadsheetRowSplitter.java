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
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 表格类文件切分器（仅 csv / xls / xlsx）。
 *
 * <p>两种切分模式：
 * <ul>
 *   <li>{@link #split(InputStream, String, int, Document)} —— 表格行感知：多行按 chunkSize 打包，
 *       字段用 Excel 列字母（A/B/C...）标记，每个分块预置表头行。适合普通结构化表格。</li>
 *   <li>{@link #splitQa(InputStream, String)} —— QA 切分：每行一个切片，
 *       第一列 = 问题（title），第二列 = 答案（content）。适合问答对照表。</li>
 * </ul>
 *
 * <p>通用规则：首行视为表头，不单独成块；空值字段省略；整行空白跳过；
 * 清除 NBSP / ZERO-WIDTH 等不可见空白。
 *
 * <p>调用方需自行按扩展名分支到本类（本类不再判断文件类型）。
 */
public final class SpreadsheetRowSplitter {

    private SpreadsheetRowSplitter() {}


    /**
     * 按行感知拆分：多行打包到 chunkSize，每个切片预置表头。
     *
     * @param is        文件输入流（调用方负责关闭外层流；本方法只读取）
     * @param ext       小写扩展名：csv / xls / xlsx
     * @param chunkSize 每个切片的目标字符数上限（&lt;=0 时按 512 兜底）
     * @param doc       原始 Document（用于继承 metadata，可传 null）
     * @return 切片列表；无数据返回空列表
     */
    public static List<TextSegment> split(InputStream is, String ext, int chunkSize, Document doc) throws Exception {
        if (is == null) return List.of();
        int size = chunkSize > 0 ? chunkSize : 512;
        List<TextSegment> result = new ArrayList<>();
        for (RowSource rs : readAll(is, ext)) {
            result.addAll(packRows(rs.rows, size, rs.breadcrumb, doc));
        }
        return result;
    }

    /** 多行打包核心：见 packRows 的策略注释 */
    private static List<TextSegment> packRows(List<List<String>> rows, int chunkSize,
                                              String breadcrumb, Document doc) {
        List<TextSegment> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) return result;

        // 找首个非空行作为表头
        List<String> headers = null;
        int dataStart = 0;
        for (int i = 0; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (r != null && !r.isEmpty() && r.stream().anyMatch(v -> v != null && !v.isBlank())) {
                headers = r;
                dataStart = i + 1;
                break;
            }
        }
        if (headers == null) return result; // 整表全空

        int colCount = headers.size();
        String headerLine = buildColLetterLine(headers, colCount);

        StringBuilder buf = new StringBuilder();
        for (int i = dataStart; i < rows.size(); i++) {
            List<String> cells = rows.get(i);
            if (cells == null) continue;
            if (cells.stream().allMatch(v -> v == null || v.isBlank())) continue; // 整行空白跳过
            String line = buildColLetterLine(cells, colCount);
            if (line == null) continue;

            // 当前 buf 为空 → 新切片起点：表头 + 这一行
            if (buf.length() == 0) {
                buf.append(headerLine).append('\n').append(line);
                continue;
            }
            // 累加这一行会超限 → flush 当前 buf，开新切片（+1 是换行符）
            if (buf.length() + 1 + line.length() > chunkSize) {
                result.add(toSegment(buf.toString(), breadcrumb, doc));
                buf.setLength(0);
                buf.append(headerLine).append('\n').append(line);
            } else {
                buf.append('\n').append(line);
            }
        }
        if (buf.length() > 0) result.add(toSegment(buf.toString(), breadcrumb, doc));
        return result;
    }

    /** 组装「列字母: 值」逗号分隔单行；空值省略；全空返回 null */
    private static String buildColLetterLine(List<String> cells, int colCount) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (int i = 0; i < colCount; i++) {
            String value = (cells != null && i < cells.size()) ? cells.get(i) : "";
            if (value == null || value.isBlank()) continue;
            any = true;
            if (sb.length() > 0) sb.append(',');
            sb.append(colLetter(i)).append(": ").append(value);
        }
        return any ? sb.toString() : null;
    }


    /**
     * QA 切分：每行一个 QA 条目，第一列 = 问题，第二列 = 答案。
     *
     * <p>首行视为表头跳过；任一列缺失则该行跳过。
     *
     * @param is  文件输入流（调用方负责关闭外层流）
     * @param ext 小写扩展名：csv / xls / xlsx
     * @return QA 条目列表（title/content 已做空白清理）；无数据返回空列表
     */
    public static List<QaItem> splitQa(InputStream is, String ext) throws Exception {
        List<QaItem> result = new ArrayList<>();
        if (is == null) return result;
        for (RowSource rs : readAll(is, ext)) {
            result.addAll(qaFromRows(rs.rows));
        }
        return result;
    }

    /**
     * 从已读行抽 QA：首个非空行作表头跳过（不管几列有值），其后每行 col0→title, col1→content。
     * Q 或 A 任一为空则该行跳过。
     */
    private static List<QaItem> qaFromRows(List<List<String>> rows) {
        List<QaItem> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) return result;
        boolean skippedHeader = false;
        for (List<String> cells : rows) {
            if (cells == null || cells.size() < 2) continue;
            String q = stripWs(cells.get(0));
            String a = stripWs(cells.get(1));
            // 整行空白不视为表头，直接跳过等下一个非空行
            if (q.isBlank() && a.isBlank()) continue;
            // 第一个非空行作表头，跳过一次
            if (!skippedHeader) {
                skippedHeader = true;
                continue;
            }
            if (q.isBlank() || a.isBlank()) continue; // Q 或 A 缺失，跳过
            result.add(new QaItem(q, a));
        }
        return result;
    }

    /** QA 条目：title = 问题，content = 答案 */
    public record QaItem(String title, String content) {}


    /** 一个 sheet/csv 的行数据 + 面包屑（sheet 名） */
    private record RowSource(List<List<String>> rows, String breadcrumb) {}

    /**
     * 把表格文件读成若干 RowSource（csv = 1 个，xlsx = 每个 sheet 一个）。
     * 调用方负责关闭传入的 InputStream；本方法内部关闭 Reader/Workbook。
     */
    private static List<RowSource> readAll(InputStream is, String ext) throws Exception {
        if (ext == null) ext = "";
        return switch (ext.toLowerCase()) {
            case "csv" -> readCsv(is);
            case "xls", "xlsx" -> readExcel(is);
            default -> List.of();
        };
    }

    private static List<RowSource> readCsv(InputStream is) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        CSVFormat fmt = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setIgnoreEmptyLines(true)
                .setTrim(false)
                .get();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = fmt.parse(reader)) {
            for (CSVRecord rec : parser) {
                rows.add(rec.toList());
            }
        }
        return List.of(new RowSource(rows, ""));
    }

    private static List<RowSource> readExcel(InputStream is) throws Exception {
        List<RowSource> sources = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(is)) {
            for (Iterator<Sheet> it = wb.sheetIterator(); it.hasNext(); ) {
                Sheet sheet = it.next();
                List<List<String>> rows = new ArrayList<>();
                for (Row row : sheet) {
                    rows.add(readRow(row, formatter));
                }
                sources.add(new RowSource(rows, sheet.getSheetName()));
            }
        }
        return sources;
    }

    /** 读一行 Cell → 字符串列表（空 cell → 空串，保持列对齐） */
    private static List<String> readRow(Row row, DataFormatter formatter) {
        short last = row.getLastCellNum();
        if (last < 0) return List.of();
        List<String> cells = new ArrayList<>(last);
        for (int i = 0; i < last; i++) {
            Cell cell = row.getCell(i);
            cells.add(cell == null ? "" : formatCell(cell, formatter));
        }
        return cells;
    }

    /** 公式取计算值；其余按 DataFormatter 输出（保留原显示格式） */
    private static String formatCell(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
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

    /**
     * 去除首尾空白，包括常见的不可见空白字符（NBSP U+00A0 等），
     * 这些常混入从 Excel 复制来的文本，String.trim() 处理不到。
     */
    private static String stripWs(String s) {
        if (s == null || s.isEmpty()) return "";
        int start = 0;
        int end = s.length();
        while (start < end && isWs(s.charAt(start))) start++;
        while (end > start && isWs(s.charAt(end - 1))) end--;
        return s.substring(start, end);
    }

    private static boolean isWs(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r'
                || c == '\u00A0' // NBSP
                || c == '\u2007' // NARROW NBSP
                || c == '\u202F' // NARROW NBSP
                || c == '\uFEFF'; // BOM / ZERO WIDTH NO-BREAK SPACE
    }

    /** 列序号（0-based）→ Excel 列字母：0→A, 1→B, 25→Z, 26→AA, 27→AB... */
    private static String colLetter(int index) {
        if (index < 0) return "";
        StringBuilder sb = new StringBuilder();
        int n = index;
        do {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    private static TextSegment toSegment(String text, String breadcrumb, Document doc) {
        Metadata meta = doc != null && doc.metadata() != null
                ? Metadata.from(doc.metadata().toMap()) : new Metadata();
        meta.put("breadcrumb", breadcrumb);
        return TextSegment.from(text, meta);
    }
}
