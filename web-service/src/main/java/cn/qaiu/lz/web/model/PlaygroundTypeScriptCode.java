package cn.qaiu.lz.web.model;

import cn.qaiu.db.ddl.Constraint;
import cn.qaiu.db.ddl.Length;
import cn.qaiu.db.ddl.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 演练场TypeScript代码实体
 * 用于保存用户编写的TypeScript源代码
 * 与PlaygroundParser关联，存储原始TS代码和编译后的ES5代码
 */
@Data
@Table("playground_typescript_code")
public class PlaygroundTypeScriptCode {

    private static final long serialVersionUID = 1L;

    @Constraint(autoIncrement = true, notNull = true)
    private Long id;

    @Constraint(notNull = true)
    private Long parserId; // 关联的解析器ID（外键）

    @Length(varcharSize = 65535)
    @Constraint(notNull = true)
    private String tsCode; // TypeScript原始代码

    @Length(varcharSize = 65535)
    @Constraint(notNull = true)
    private String es5Code; // 编译后的ES5代码

    @Length(varcharSize = 2000)
    private String compileErrors; // 编译错误信息（如果有）

    @Length(varcharSize = 32)
    private String compilerVersion; // 编译器版本

    @Length(varcharSize = 1000)
    private String compileOptions; // 编译选项（JSON格式）

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime = new Date(); // 创建时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime; // 更新时间

    private Boolean isValid = true; // 编译是否成功

    @Length(varcharSize = 64)
    private String ip; // 创建者IP
}
