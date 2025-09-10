package sparkx.service.validate.tool;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class WorkflowNodeDataValidate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 资源类型
     */
    private int type;

    /**
     * 数据库名称
     */
    @NotEmpty(message = "数据库名称不能为空")
    @Length(min = 2, max = 50)
    private String name;

    /**
     * 数据库描述
     */
    private String description;

    /**
     * 节点状态
     */
    private int status;

    /**
     * 数据库字段
     */
    @NotEmpty(message = "类型不能为空")
    private List<TableField> nodeData;

    @Data
    public static class TableField implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 字段名称
         */
        private String name;

        /**
         * 字段描述
         */
        private String desc;

        /**
         * 字段类型
         */
        private String type;

        /**
         * 是否可以编辑
         */
        private boolean edit;
    }
}