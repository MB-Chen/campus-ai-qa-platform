// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import sparkx.sparkshop.common.constant.SparkxConstant;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.common.utils.ToolUtils;
import sparkx.sparkshop.system.entity.AdminRole;
import sparkx.sparkshop.system.entity.AdminUser;
import sparkx.sparkshop.system.entity.AdminDepartment;
import sparkx.sparkshop.system.mapper.AdminRoleMapper;
import sparkx.sparkshop.system.mapper.AdminUserMapper;
import sparkx.sparkshop.system.mapper.AdminDepartmentMapper;
import sparkx.sparkshop.system.service.IAdminUserService;
import sparkx.sparkshop.system.validate.AdminUserSearchValidate;
import sparkx.sparkshop.system.validate.AdminUserValidate;
import sparkx.sparkshop.system.validate.ChangePasswordValidate;
import sparkx.sparkshop.system.vo.AdminUserVo;
import sparkx.sparkshop.system.vo.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminUserServiceImpl implements IAdminUserService {

    @Resource
    private AdminUserMapper adminUserMapper;

    @Resource
    private AdminRoleMapper adminRoleMapper;

    @Resource
    private AdminDepartmentMapper adminDepartmentMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 分页查询管理员列表，并关联角色名
     *
     * @param search 查询参数（页码、每页条数、昵称、账号）
     * @return 分页结果
     */
    @Override
    public PageResult<AdminUserVo> getList(AdminUserSearchValidate search) {
        QueryWrapper<AdminUser> qw = new QueryWrapper<>();
        if (StrUtil.isNotBlank(search.getNickname())) {
            qw.like("nickname", search.getNickname());
        }
        if (StrUtil.isNotBlank(search.getAccount())) {
            qw.like("account", search.getAccount());
        }
        if (search.getDeptId() != null) {
            qw.eq("dept_id", search.getDeptId());
        }
        qw.orderByDesc("id");

        int page = search.getPage() == null ? 1 : search.getPage();
        int limit = search.getLimit() == null ? 10 : search.getLimit();
        IPage<AdminUser> p = new Page<>(page, limit);
        IPage<AdminUser> result = adminUserMapper.selectPage(p, qw);

        // 批量取角色名
        Set<Integer> roleIds = result.getRecords().stream()
                .map(AdminUser::getRoleId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> roleNameMap = new HashMap<>();
        if (!roleIds.isEmpty()) {
            List<AdminRole> roles = adminRoleMapper.selectBatchIds(roleIds);
            for (AdminRole r : roles) {
                roleNameMap.put(r.getId(), r.getName());
            }
        }

        // 批量取部门名
        Set<Integer> deptIds = result.getRecords().stream()
                .map(AdminUser::getDeptId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> deptNameMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            List<AdminDepartment> depts = adminDepartmentMapper.selectBatchIds(deptIds);
            for (AdminDepartment d : depts) {
                deptNameMap.put(d.getId(), d.getName());
            }
        }

        List<AdminUserVo> data = new ArrayList<>();
        for (AdminUser u : result.getRecords()) {
            AdminUserVo vo = new AdminUserVo();
            vo.setId(u.getId());
            vo.setAccount(u.getAccount());
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
            vo.setRoleId(u.getRoleId());
            vo.setRoleName(roleNameMap.getOrDefault(u.getRoleId(), ""));
            vo.setDeptId(u.getDeptId());
            vo.setDeptName(deptNameMap.getOrDefault(u.getDeptId(), ""));
            vo.setStatus(u.getStatus());
            vo.setLastLoginIp(u.getLastLoginIp());
            vo.setLastLoginTime(u.getLastLoginTime() == null ? null : u.getLastLoginTime().format(FMT));
            vo.setCreateTime(u.getCreateTime() == null ? null : u.getCreateTime().format(FMT));
            vo.setUpdateTime(u.getUpdateTime() == null ? null : u.getUpdateTime().format(FMT));
            data.add(vo);
        }
        return new PageResult<>(data, result.getTotal());
    }

    /**
     * 新增管理员，校验账号唯一性，密码默认 123456
     *
     * @param validate 管理员参数
     */
    @Override
    public void add(AdminUserValidate validate) {
        // 账号唯一性
        Long count = adminUserMapper.selectCount(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getAccount, validate.getAccount()));
        if (count != null && count > 0) {
            throw new BusinessException("账号已存在");
        }
        AdminUser user = new AdminUser();
        BeanUtil.copyProperties(validate, user);
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        if (StrUtil.isBlank(user.getAvatar())) {
            user.setAvatar(SparkxConstant.DEFAULT_AVATAR);
        }

        String pwd = StrUtil.isBlank(validate.getPassword()) ? SparkxConstant.DEFAULT_PASSWORD : validate.getPassword();
        String salt = ToolUtils.makeSalt();
        user.setSalt(salt);
        user.setPassword(ToolUtils.makePassword(pwd, salt));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        adminUserMapper.insert(user);
    }

    /**
     * 编辑管理员，传了密码才更新密码
     *
     * @param validate 管理员参数（含 id）
     */
    @Override
    public void edit(AdminUserValidate validate) {
        if (validate.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        AdminUser exists = adminUserMapper.selectById(validate.getId());
        if (exists == null) {
            throw new BusinessException("管理员不存在");
        }
        // 账号唯一性（排除自己）
        Long count = adminUserMapper.selectCount(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getAccount, validate.getAccount())
                .ne(AdminUser::getId, validate.getId()));
        if (count != null && count > 0) {
            throw new BusinessException("账号已存在");
        }

        AdminUser user = new AdminUser();
        BeanUtil.copyProperties(validate, user);
        // 传了密码才改
        if (StrUtil.isNotBlank(validate.getPassword())) {
            String salt = ToolUtils.makeSalt();
            user.setSalt(salt);
            user.setPassword(ToolUtils.makePassword(validate.getPassword(), salt));
        }
        user.setUpdateTime(LocalDateTime.now());
        adminUserMapper.updateById(user);
    }

    /**
     * 删除管理员，超级管理员（id=1）不可删除
     *
     * @param id 管理员 id
     */
    @Override
    public void del(Integer id) {
        if (id != null && id == 1) {
            throw new BusinessException("超级管理员不可删除");
        }
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("管理员不存在");
        }
        adminUserMapper.deleteById(id);
    }

    /**
     * 修改当前登录人密码，校验原密码后重新生成盐与密码
     *
     * @param adminId  管理员 id
     * @param validate 原密码与新密码
     */
    @Override
    public void changePassword(Integer adminId, ChangePasswordValidate validate) {
        AdminUser user = adminUserMapper.selectById(adminId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!ToolUtils.verifyPassword(user.getPassword(), validate.getOldPwd(), user.getSalt())) {
            throw new BusinessException("原密码错误");
        }
        String salt = ToolUtils.makeSalt();
        AdminUser update = new AdminUser();
        update.setId(adminId);
        update.setSalt(salt);
        update.setPassword(ToolUtils.makePassword(validate.getNewPwd(), salt));
        update.setUpdateTime(LocalDateTime.now());
        adminUserMapper.updateById(update);
    }
}
