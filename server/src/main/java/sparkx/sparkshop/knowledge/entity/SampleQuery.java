// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 样例查询问答对实体。
 *
 * <p>录入常用 Q&A：{@link #question} 参与向量化，{@link #answer} 命中后直接返回（不向量化）。
 *
 * <p>★ 向量独立存储：{@code embedding}（pgvector）+ {@code tsv}（tsvector）列直接挂在本表，
 * <b>不</b>写入 chunks 表，与知识库切片完全隔离。
 *
 * <p>⚠️ {@code embedding} / {@code tsv} 两列不通过 MyBatis-Plus 自动映射（pgvector/tsvector
 * 类型 JDBC 不认），向量化与检索走 {@link sparkx.sparkshop.knowledge.mapper.SampleQueryMapper}
 * 的原生 SQL。{@link #vectorized} 是普通 SMALLINT 字段，可正常参与 LambdaQueryWrapper 过滤。
 *
 * <p>问答时拿用户问题做 embedding，与本表的 {@code embedding} 算余弦相似度，
 * 高于 {@code sample_query_config.similarity_threshold} 即直接返回 {@link #answer}，不走大模型。
 */
@Data
@TableName("sample_query")
public class SampleQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 问题文本（参与向量化） */
    @TableField(value = "question")
    private String question;

    /** 答案文本（不向量化，命中后直接返回） */
    @TableField(value = "answer")
    private String answer;

    /** 0未向量化 1已向量化（embedding 是否已填充） */
    @TableField(value = "vectorized")
    private Integer vectorized;

    /** manual 手动录入 / import 批量导入 */
    @TableField(value = "source")
    private String source;

    /** 1启用 2禁用 */
    @TableField(value = "status")
    private Integer status;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
