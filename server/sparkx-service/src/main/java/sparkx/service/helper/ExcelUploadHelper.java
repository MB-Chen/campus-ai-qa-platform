// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.service.helper;

import cn.hutool.core.util.IdUtil;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import sparkx.common.enums.DocumentStatusEnum;
import sparkx.common.utils.Tool;
import sparkx.service.entity.dataset.KnowledgeParagraphEntity;
import sparkx.service.entity.dataset.KnowledgeQuestionEntity;
import sparkx.service.entity.dataset.KnowledgeQuestionParagraphEntity;
import sparkx.service.mapper.dataset.KnowledgeParagraphMapper;
import sparkx.service.mapper.dataset.KnowledgeQuestionMapper;
import sparkx.service.mapper.dataset.KnowledgeQuestionParagraphMapper;
import sparkx.service.vo.dataset.DocumentDataVo;
import sparkx.service.vo.document.PreviewVo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@Component
public class ExcelUploadHelper {

    @Autowired
    private KnowledgeQuestionMapper knowledgeQuestionMapper;

    @Autowired
    KnowledgeParagraphMapper knowledgeParagraphMapper;

    @Autowired
    KnowledgeQuestionParagraphMapper knowledgeQuestionParagraphMapper;

    /**
     * 异步执行导入
     * @param previewVo previewVo
     * @param documentId documentId
     */
    @Async
    public void inertToDb(PreviewVo previewVo, String documentId, DocumentDataVo documentDataVo) {

        String title = documentDataVo.getTitle();
        String content = documentDataVo.getContent();
        String question = documentDataVo.getQuestion();

        KnowledgeParagraphEntity paragraph = new KnowledgeParagraphEntity();
        paragraph.setParagraphId(IdUtil.randomUUID());
        paragraph.setTitle(title);
        paragraph.setContent(content);
        paragraph.setDatasetId(previewVo.getDatasetId());
        paragraph.setDocumentId(documentId);
        paragraph.setStatus(DocumentStatusEnum.PENDING.getCode());
        paragraph.setActive(DocumentStatusEnum.PENDING.getCode());
        paragraph.setCreateTime(Tool.nowDateTime());

        knowledgeParagraphMapper.insert(paragraph);

        // 如果是QA问题
        if (previewVo.getFileType().equals("qa") && !question.isBlank()) {
            List<String> queationList = Arrays.stream(question.split("\n")).toList();

            for (String questionItem : queationList) {
                // 写入问题
                KnowledgeQuestionEntity questionEntity = new KnowledgeQuestionEntity();
                questionEntity.setQuestionId(IdUtil.randomUUID());
                questionEntity.setContent(questionItem);
                questionEntity.setHitNums(0);
                questionEntity.setDatasetId(paragraph.getDatasetId());
                questionEntity.setCreateTime(Tool.nowDateTime());
                knowledgeQuestionMapper.insert(questionEntity);

                // 写入问题关联
                KnowledgeQuestionParagraphEntity questionParagraph = new KnowledgeQuestionParagraphEntity();
                questionParagraph.setUuid(IdUtil.randomUUID());
                questionParagraph.setDatasetId(paragraph.getDatasetId());
                questionParagraph.setDocumentId(paragraph.getDocumentId());
                questionParagraph.setParagraphId(paragraph.getParagraphId());
                questionParagraph.setQuestionId(questionEntity.getQuestionId());
                questionParagraph.setCreateTime(Tool.nowDateTime());
                knowledgeQuestionParagraphMapper.insert(questionParagraph);
            }
        }
    }

    /**
     * 获取文档的基础信息
     * @param row Map<String, Object>
     * @param previewVo PreviewVo
     * @return DocumentDataVo
     */
    public DocumentDataVo getDocumentData(Map<String, Object> row, PreviewVo previewVo) {

        StringBuilder content = new StringBuilder();
        String title = "";
        String question = "";

        if (previewVo.getFileType().equals("excel")) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                content.append(entry.getKey()).append(":").append(entry.getValue()).append(" ");
            }
        } else {
            int i = 0;
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (i == 0) {
                    title = String.valueOf(entry.getValue());
                } else if (i == 1) {
                    content.append(entry.getValue());
                } else if (i == 2) {
                    question = String.valueOf(entry.getValue());
                }

                i++;
            }
        }

        DocumentDataVo documentDataVo = new DocumentDataVo();
        documentDataVo.setTitle(title);
        documentDataVo.setQuestion(question);
        documentDataVo.setContent(content.toString());

        return documentDataVo;
    }
}