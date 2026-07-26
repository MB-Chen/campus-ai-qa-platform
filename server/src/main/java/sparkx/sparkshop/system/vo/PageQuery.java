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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用分页入参基类（与 {@link PageResult} 出参配套）。
 *
 * <p>各业务列表入参对象继承本类，按需追加业务过滤字段。
 * 字段命名沿用项目既有约定 page/size（不要用 current/pageSize），
 * 兜底默认 page=1 / size=10，对齐各 service 内历史写法 {@code page==null?1:page}。
 *
 * <p>GET 请求由 Spring 按字段名自动绑定到对象，前端 {@code params:{...}} 传参无需改动。
 */
@Data
@Schema(description = "通用分页入参")
public class PageQuery implements Serializable {

    /**
     * 关键词（可选，按名称模糊匹配等场景复用）
     */
    @Schema(description = "关键词")
    private String keyword;

    /**
     * 页码（默认 1）
     */
    @Schema(description = "页码", defaultValue = "1")
    private Integer page;

    /**
     * 每页条数（默认 10）
     */
    @Schema(description = "每页条数", defaultValue = "10")
    private Integer size;

    /**
     * 取页码，null 兜底为 1。
     */
    public Integer safePage() {
        return page == null ? 1 : page;
    }

    /**
     * 取每页条数，null 兜底为 10。
     */
    public Integer safeSize() {
        return size == null ? 10 : size;
    }
}
