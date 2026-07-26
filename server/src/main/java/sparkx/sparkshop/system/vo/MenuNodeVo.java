// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.system.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 登录返回 / 菜单管理用的菜单节点。
 * 字段严格对齐前端 generator.ts 的消费契约：
 * <pre>
 * { id, pid, path, name, component, meta:{title, icon?}, children? }
 * </pre>
 * - name：根节点 = flag；子节点 = flag_path（由后端计算）
 * - meta.icon 仅在非空时输出（@JsonInclude）
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuNodeVo implements Serializable {

    /**
     * 节点 id
     */
    @Schema(description = "节点 id")
    private Integer id;

    /**
     * 父级 id，0 为根
     */
    @Schema(description = "父级 id，0 为根")
    private Integer pid;

    /**
     * 前端路径
     */
    @Schema(description = "前端路径")
    private String path;

    /**
     * 路由 name（根节点=flag，子节点=flag_path）
     */
    @Schema(description = "路由 name（根节点=flag，子节点=flag_path）")
    private String name;

    /**
     * 前端组件地址（父级为 LAYOUT）
     */
    @Schema(description = "前端组件地址（父级为 LAYOUT）")
    private String component;

    /**
     * 菜单信息（标题、图标）
     */
    @Schema(description = "菜单元信息（标题、图标）")
    private MetaVo meta;

    /**
     * 子节点
     */
    @Schema(description = "子节点")
    private List<MenuNodeVo> children;
}
