// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.validate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;

/**
 * 文档上传入参（multipart 表单字段对象绑定，不写 @RequestBody）。
 *
 * <p>前端用 FormData 传参：
 * <pre>
 * formData.append('file', file)
 * formData.append('kbId', kbId)
 * formData.append('engine', 'tika')
 * </pre>
 */
@Data
@Schema(description = "文档上传入参")
public class DocumentUploadValidate implements Serializable {

    @Schema(description = "上传文件（multipart）")
    @NotNull(message = "请选择文件")
    private MultipartFile file;

    @Schema(description = "知识库 id")
    @NotBlank(message = "知识库 id 不能为空")
    private String kbId;

    @Schema(description = "解析引擎（默认 tika）")
    private String engine;
}
