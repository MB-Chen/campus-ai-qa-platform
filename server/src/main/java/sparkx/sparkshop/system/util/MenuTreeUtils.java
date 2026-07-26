// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.system.util;

import sparkx.sparkshop.system.entity.AdminMenu;
import sparkx.sparkshop.system.vo.MenuNodeVo;
import sparkx.sparkshop.system.vo.MetaVo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单树构建，严格对齐参考项目 government LoginService 的输出契约：
 * <ul>
 *   <li>name：pid==0 时取 flag；否则 flag + "_" + path</li>
 *   <li>meta.title 恒为菜单名；meta.icon 仅在非空时设置</li>
 *   <li>按 pid 组装 children[]，pid==0 为根</li>
 * </ul>
 */
public class MenuTreeUtils {

    private MenuTreeUtils() {
    }

    /**
     * 把扁平菜单列表组装成树形 VO（包含全部节点：菜单+按钮）。
     * 输入建议已按 sort DESC 排序，保证 children 顺序稳定。
     * 角色分配权限页使用（需要显示按钮节点）。
     */
    public static List<MenuNodeVo> build(List<AdminMenu> list) {
        return build(list, false);
    }

    /**
     * 把扁平菜单列表组装成树形 VO。
     *
     * @param list            扁平菜单列表
     * @param menuOnly        true: 仅保留 type=1 的菜单节点（登录返回的动态路由用）；
     *                        false: 保留全部节点（角色分配权限页用）
     */
    public static List<MenuNodeVo> build(List<AdminMenu> list, boolean menuOnly) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }

        // 先把所有节点转成 VO，并用 id 建立索引
        Map<Integer, MenuNodeVo> indexed = new HashMap<>();
        List<MenuNodeVo> vos = new ArrayList<>(list.size());
        for (AdminMenu m : list) {
            if (menuOnly && m.getType() != null && m.getType() == 2) {
                // 菜单模式：跳过按钮节点
                continue;
            }
            MenuNodeVo vo = toVo(m);
            indexed.put(m.getId(), vo);
            vos.add(vo);
        }

        // 组装 children
        List<MenuNodeVo> roots = new ArrayList<>();
        for (MenuNodeVo vo : vos) {
            Integer pid = vo.getPid();
            if (pid == null || pid == 0) {
                roots.add(vo);
            } else {
                MenuNodeVo parent = indexed.get(pid);
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(vo);
                } else {
                    // 父节点不在本次结果中（如父级被过滤），作为根节点兜底
                    roots.add(vo);
                }
            }
        }
        return roots;
    }

    /**
     * 将单个菜单实体转换为前端菜单节点 VO，并计算 name / meta 字段。
     *
     * @param m 菜单实体
     * @return 前端菜单节点
     */
    private static MenuNodeVo toVo(AdminMenu m) {
        MenuNodeVo vo = new MenuNodeVo();
        vo.setId(m.getId());
        vo.setPid(m.getPid());

        // name 计算：根取 flag，子取 flag_path
        String flag = m.getFlag() == null ? "" : m.getFlag();
        String path = m.getPath() == null ? "" : m.getPath();
        if (m.getPid() == null || m.getPid() == 0) {
            vo.setName(flag);
        } else {
            vo.setName(flag + "_" + path);
        }

        vo.setPath(m.getPath());
        vo.setComponent(m.getComponent());

        MetaVo meta = new MetaVo();
        meta.setTitle(m.getName());
        if (m.getIcon() != null && !m.getIcon().isEmpty()) {
            meta.setIcon(m.getIcon());
        }
        // hidden=1 的菜单不渲染到侧边栏，但仍注册为路由（前端 filterRouter 检查 meta.hidden）
        if (m.getHidden() != null && m.getHidden() == 1) {
            meta.setHidden(true);
        }
        vo.setMeta(meta);
        return vo;
    }
}
