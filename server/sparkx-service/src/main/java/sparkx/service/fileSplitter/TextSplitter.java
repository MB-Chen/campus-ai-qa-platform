// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.service.fileSplitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 自定义文本拆分类，支持根据正则表达式进行初次拆分，
 * 然后根据指定长度和重合度进行二次拆分
 */
public class TextSplitter implements DocumentSplitter {

    private final String regex;
    private final int maxSegmentLength;
    private final int overlapLength;

    /**
     * 构造函数
     * @param regex 用于初次拆分的正则表达式
     * @param maxSegmentLength 二次拆分的最大长度
     * @param overlapLength 二次拆分的重合度
     */
    public TextSplitter(String regex, int maxSegmentLength, int overlapLength) {
        this.regex = regex;
        this.maxSegmentLength = maxSegmentLength;
        this.overlapLength = overlapLength;
    }

    /**
     * 拆分文档
     * @param document 要拆分的文档
     * @return 拆分后的文本段列表
     */
    @Override
    public List<TextSegment> split(Document document) {
        String text = document.text();

        // 第一步：根据正则表达式进行初次拆分
        List<String> primarySegments = splitByRegex(text);

        // 第二步：对每个初次拆分的段进行基于长度和重合度的二次拆分
        List<String> finalSegments = new ArrayList<>();
        for (String segment : primarySegments) {
            finalSegments.addAll(splitByLengthAndOverlap(segment));
        }

        // 将字符串转换为 TextSegment 对象
        List<TextSegment> textSegments = new ArrayList<>();
        for (String segment : finalSegments) {
            textSegments.add(TextSegment.from(segment));
        }

        return textSegments;
    }

    /**
     * 拆分文本（保持向后兼容性）
     * @param text 要拆分的文本
     * @return 拆分后的文本段列表
     */
    public List<String> split(String text) {
        // 第一步：根据正则表达式进行初次拆分
        List<String> primarySegments = splitByRegex(text);

        // 第二步：对每个初次拆分的段进行基于长度和重合度的二次拆分
        List<String> finalSegments = new ArrayList<>();
        for (String segment : primarySegments) {
            finalSegments.addAll(splitByLengthAndOverlap(segment));
        }

        return finalSegments;
    }

    /**
     * 根据正则表达式拆分文本
     * @param text 要拆分的文本
     * @return 拆分后的文本段列表
     */
    private List<String> splitByRegex(String text) {
        List<String> segments = new ArrayList<>();

        // 如果正则表达式为空或null，直接返回整个文本作为一个段
        if (regex == null || regex.trim().isEmpty()) {
            segments.add(text);
            return segments;
        }

        // 编译正则表达式
        Pattern pattern = Pattern.compile(regex);

        // 使用正则表达式拆分文本
        String[] parts = pattern.split(text);

        // 将拆分后的部分添加到结果列表中
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                segments.add(part);
            }
        }

        // 如果没有拆分出任何内容，返回整个文本
        if (segments.isEmpty()) {
            segments.add(text);
        }

        return segments;
    }

    /**
     * 根据长度和重合度拆分文本
     * @param text 要拆分的文本
     * @return 拆分后的文本段列表
     */
    private List<String> splitByLengthAndOverlap(String text) {
        List<String> segments = new ArrayList<>();

        // 如果文本长度小于等于最大段长度，直接返回整个文本
        if (text.length() <= maxSegmentLength) {
            segments.add(text);
            return segments;
        }

        // 计算实际的重合长度，确保不超过最大段长度的一半
        int actualOverlap = Math.min(overlapLength, maxSegmentLength / 2);

        // 计算步长（最大段长度减去重合长度）
        int stepSize = maxSegmentLength - actualOverlap;

        // 按步长拆分文本
        for (int i = 0; i < text.length(); i += stepSize) {
            int end = Math.min(i + maxSegmentLength, text.length());

            // 如果这是最后一段，且长度小于最大段长度的一半，则将其合并到前一段
            if (end == text.length() && (end - i) < maxSegmentLength / 2 && !segments.isEmpty()) {
                String lastSegment = segments.get(segments.size() - 1);
                segments.set(segments.size() - 1, lastSegment + text.substring(i));
                break;
            }

            segments.add(text.substring(i, end));

            // 如果已经到达文本末尾，退出循环
            if (end == text.length()) {
                break;
            }
        }

        return segments;
    }

    /**
     * 获取正则表达式
     * @return 正则表达式
     */
    public String getRegex() {
        return regex;
    }

    /**
     * 获取最大段长度
     * @return 最大段长度
     */
    public int getMaxSegmentLength() {
        return maxSegmentLength;
    }

    /**
     * 获取重合长度
     * @return 重合长度
     */
    public int getOverlapLength() {
        return overlapLength;
    }
}
