// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.service.mapper.workflow;

import org.apache.ibatis.annotations.Mapper;
import sparkx.common.core.IBaseMapper;
import sparkx.service.entity.workflow.ApplicationWorkflowEntity;

/**
 * 编排流程表 Mapper
 */
@Mapper
public interface ApplicationWorkflowMapper extends IBaseMapper<ApplicationWorkflowEntity> {

}
