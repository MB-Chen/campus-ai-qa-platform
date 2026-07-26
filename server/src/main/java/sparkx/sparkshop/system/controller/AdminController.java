// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.system.controller;

import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.common.utils.AdminContextUtils;
import sparkx.sparkshop.system.service.IAdminUserService;
import sparkx.sparkshop.system.validate.AdminUserSearchValidate;
import sparkx.sparkshop.system.validate.AdminUserValidate;
import sparkx.sparkshop.system.validate.ChangePasswordValidate;
import sparkx.sparkshop.system.vo.AdminUserVo;
import sparkx.sparkshop.system.vo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "管理员管理")
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Resource
    private IAdminUserService adminUserService;

    /**
     * 管理员列表（分页，支持按昵称、账号、部门搜索）
     *
     * @param search 查询参数（页码、每页条数、昵称、账号）
     * @return 分页列表
     */
    @Operation(summary = "管理员列表")
    @GetMapping("/index")
    public AjaxResult<PageResult<AdminUserVo>> index(AdminUserSearchValidate search) {
        return AjaxResult.success(adminUserService.getList(search));
    }

    /**
     * 新增管理员
     *
     * @param validate 管理员参数
     * @return 操作结果
     */
    @Operation(summary = "新增管理员")
    @PostMapping("/add")
    public AjaxResult<Object> add(@RequestBody @Valid AdminUserValidate validate) {
        adminUserService.add(validate);
        return AjaxResult.success();
    }

    /**
     * 编辑管理员
     *
     * @param validate 管理员参数（含 id）
     * @return 操作结果
     */
    @Operation(summary = "编辑管理员")
    @PostMapping("/edit")
    public AjaxResult<Object> edit(@RequestBody @Valid AdminUserValidate validate) {
        adminUserService.edit(validate);
        return AjaxResult.success();
    }

    /**
     * 删除管理员（超管不可删）
     *
     * @param body 含 id
     * @return 操作结果
     */
    @Operation(summary = "删除管理员")
    @PostMapping("/del")
    public AjaxResult<Object> del(@RequestBody Map<String, Integer> body) {
        adminUserService.del(body.get("id"));
        return AjaxResult.success();
    }

    /**
     * 修改当前登录人的密码
     *
     * @param validate 原密码与新密码
     * @return 操作结果
     */
    @Operation(summary = "修改自己的密码")
    @PostMapping("/changePassword")
    public AjaxResult<Object> changePassword(@RequestBody @Valid ChangePasswordValidate validate) {
        adminUserService.changePassword(AdminContextUtils.getAdminId(), validate);
        return AjaxResult.success();
    }
}
