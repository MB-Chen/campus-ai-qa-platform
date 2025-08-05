// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.service.mapper.tool;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import sparkx.common.core.IBaseMapper;
import sparkx.service.entity.tool.WorkflowNodeEntity;

/**
 * 编排资源表 Mapper
 */
@Mapper
public interface WorkflowNodeMapper extends IBaseMapper<WorkflowNodeEntity> {

    @Select("SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_schema = 'public' AND table_name = #{tableName}")
    int existsTable(@Param("tableName") String tableName);

    @Insert("${ddl}")
    void createTable(@Param("ddl") String ddl);
}
