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
 * 外部服务配置实体（MinerU 等非 LLM 外部服务，页面可编辑）。
 *
 * <p>与 {@link AiModel} 区分：
 * <ul>
 *   <li>{@code ai_model} 管 LLM 模型（对话/向量/重排/视觉），统一 provider+credential+options+models+priority，
 *       参与容错降级链。</li>
 *   <li>本表管非 LLM 的外部服务（PDF 解析引擎、OCR 等），配置形态差异大，用 {@code category} 分类，
 *       具体字段存 {@code config} JSON（schema 按 category 不同，详见
 *       {@link sparkx.sparkshop.knowledge.service.IExtServiceConfigService}）。</li>
 * </ul>
 *
 * <p>首批支持 category：
 * <ul>
 *   <li>{@code mineru_self}：自建 MinerU（engine=mineru）。</li>
 *   <li>{@code mineru_cloud}：云端 MinerU（engine=mineru_cloud）。</li>
 * </ul>
 * 未来加其他外部服务只需加 category，不改表结构。
 */
@Data
@TableName("ext_service_config")
public class ExtServiceConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 配置名称（如「自建MinerU」） */
    @TableField(value = "name")
    private String name;

    /** 服务类别：mineru_self / mineru_cloud / ... */
    @TableField(value = "category")
    private String category;

    /** 配置 JSON 文本（schema 按 category 不同） */
    @TableField(value = "config")
    private String config;

    /** 备注 */
    @TableField(value = "remark")
    private String remark;

    /** 1启用 2禁用 */
    @TableField(value = "status")
    private Integer status;

    /** 排序（数值小者靠前） */
    @TableField(value = "sort")
    private Integer sort;

    @TableField(value = "create_time")
    private LocalDateTime createTime;

    @TableField(value = "update_time")
    private LocalDateTime updateTime;
}
