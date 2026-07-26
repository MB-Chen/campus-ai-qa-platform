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

/**
 * 菜单元信息
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetaVo implements Serializable {

    /**
     * 标题（必填）
     */
    @Schema(description = "标题")
    private String title;

    /**
     * 图标（仅非空输出）
     */
    @Schema(description = "图标")
    private String icon;

    /**
     * 是否在侧边栏隐藏。true 时不渲染到侧边栏，但仍注册为路由，可通过 URL 直接访问。
     * 为 null 时等同于 false（@JsonInclude NON_NULL 不输出）。
     */
    @Schema(description = "是否隐藏侧边栏")
    private Boolean hidden;

    /** 默认无参构造 */
    public MetaVo() {
    }

    /**
     * 全参构造
     *
     * @param title 标题
     * @param icon  图标
     */
    public MetaVo(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }
}
