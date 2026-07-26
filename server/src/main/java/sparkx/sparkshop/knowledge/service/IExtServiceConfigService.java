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

import sparkx.sparkshop.knowledge.ingest.mineru.MinerUOptions;
import sparkx.sparkshop.knowledge.validate.ExtServiceConfigValidate;
import sparkx.sparkshop.knowledge.vo.ModelTestVo;

import java.util.List;
import java.util.Map;

/**
 * 外部服务配置业务接口（MinerU 等非 LLM 外部服务）
 *
 * <p>管 {@code ext_service_config} 表（category 分类 + config JSON），
 * 与 AI 模型管理区分：本接口管非 LLM 的外部服务（PDF 解析引擎、OCR 等）。
 * config JSON schema 按 category 不同，详见实现类注释。
 */
public interface IExtServiceConfigService {

    /** 配置列表（按 category 可选筛选，按 sort ASC） */
    List<Map<String, Object>> list(String category, Integer status);

    /** 配置详情 */
    Map<String, Object> info(Integer id);

    /** 新增配置 */
    void add(ExtServiceConfigValidate v);

    /** 编辑配置 */
    void edit(ExtServiceConfigValidate v);

    /** 删除配置 */
    void remove(Integer id);

    /** 切换启停 */
    void switchStatus(Integer id, Integer status);

    /**
     * 按 MinerU 引擎名查启用的配置记录，构造 MinerUOptions。
     *
     * @param engine "mineru"（自建）或 "mineru_cloud"（云端）
     */
    MinerUOptions getMineruConfig(String engine);

    /** 测试已保存配置的连通性（按 id） */
    ModelTestVo test(Integer id);

    /** 测试连通性（按表单参数，无需先保存；新建态用） */
    ModelTestVo testConnect(ExtServiceConfigValidate v);

    /**
     * 读取启用的 Neo4j 连接配置（仅 neo4j_self）。
     * @return {uri, username, password}，未配置返回 null
     */
    String[] getNeo4jConnection();
}
