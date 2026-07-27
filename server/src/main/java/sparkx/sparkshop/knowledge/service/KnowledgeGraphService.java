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

import sparkx.sparkshop.knowledge.entity.KgConfig;
import sparkx.sparkshop.knowledge.entity.KgExtractionRecord;
import sparkx.sparkshop.knowledge.validate.KgConfigValidate;
import sparkx.sparkshop.knowledge.validate.KgExtractValidate;
import sparkx.sparkshop.knowledge.validate.KgHitTestValidate;
import sparkx.sparkshop.knowledge.validate.KgKbSettingValidate;
import sparkx.sparkshop.knowledge.vo.KgExtractionProgressVo;
import sparkx.sparkshop.knowledge.vo.KgKbSettingVo;
import sparkx.sparkshop.knowledge.vo.TaskIdVo;
import sparkx.sparkshop.system.vo.PageResult;

import java.util.Map;

/**
 * 知识图谱业务接口。
 *
 * <p>仅做编排，逻辑下沉至实现类；Controller 只调本接口。
 */
public interface KnowledgeGraphService {

    /** 读取全局配置（kg_config id=1） */
    KgConfig getConfig();

    /** 保存全局配置（upsert） */
    void saveConfig(KgConfigValidate validate);

    /** 测试 Neo4j 连通性（调 GraphRepository.getSchema()） */
    String testConnect();

    /** 查询 KB 级 KG 开关（组装成 {kbId, kgEnabled} 回执） */
    KgKbSettingVo getKbSetting(String kbId);

    /** 保存 KB 级 KG 开关 */
    void saveKbSetting(KgKbSettingValidate validate);

    /**
     * 触发抽取（异步，返回 TaskIdVo 供前端轮询）。
     * documentIds 为空时对该 KB 下所有未抽取完成的文档补抽取。
     */
    TaskIdVo triggerExtract(KgExtractValidate validate);

    /** 查询抽取进度 */
    KgExtractionProgressVo getExtractProgress(String taskId);

    /** 抽取记录分页列表 */
    PageResult<KgExtractionRecord> getRecords(String kbId, String documentId,
                                               String status, Integer page, Integer size);

    /** 检索测试（阶段 2：接入 KnowledgeGraphChannel 后完善） */
    Map<String, Object> hitTest(KgHitTestValidate validate);

    /** 图谱可视化数据（nodes/edges）。documentId 非空时只返回该文档贡献的子图 */
    Map<String, Object> visualization(String kbId, String documentId);

    /** 删除某文档的图谱数据（Neo4j + kg_entity + record） */
    void deleteByDocument(String kbId, String documentId);

    /**
     * ★ 第四期：触发社区检测 + 社区摘要生成（异步）。
     * global/hybrid 检索模式的前置数据准备，建议在 KB 文档抽取完成后手动触发。
     */
    void triggerCommunityDetect(String kbId);
}
