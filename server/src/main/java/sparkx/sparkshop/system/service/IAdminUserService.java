// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.system.service;

import sparkx.sparkshop.system.validate.AdminUserSearchValidate;
import sparkx.sparkshop.system.validate.AdminUserValidate;
import sparkx.sparkshop.system.validate.ChangePasswordValidate;
import sparkx.sparkshop.system.vo.AdminUserVo;
import sparkx.sparkshop.system.vo.PageResult;

public interface IAdminUserService {

    /**
     * 分页查询
     *
     * @param search 查询参数（页码、每页条数、昵称、账号）
     * @return 分页结果
     */
    PageResult<AdminUserVo> getList(AdminUserSearchValidate search);

    /**
     * 新增
     */
    void add(AdminUserValidate validate);

    /**
     * 编辑
     */
    void edit(AdminUserValidate validate);

    /**
     * 删除
     */
    void del(Integer id);

    /**
     * 修改密码（当前登录人）
     */
    void changePassword(Integer adminId, ChangePasswordValidate validate);
}
