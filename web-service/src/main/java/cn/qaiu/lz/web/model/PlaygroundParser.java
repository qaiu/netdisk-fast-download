package cn.qaiu.lz.web.model;

import cn.qaiu.db.ddl.Constraint;
import cn.qaiu.db.ddl.Length;
import cn.qaiu.db.ddl.NewField;
import cn.qaiu.db.ddl.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 演练场解析器实体
 * 用于保存用户创建的临时JS解析器
 */
@Data
@Table("playground_parser")
public class PlaygroundParser {

    private static final long serialVersionUID = 1L;

    @Constraint(autoIncrement = true, notNull = true)
    private Long id;

    @Length(varcharSize = 64)
    @Constraint(notNull = true)
    private String name; // 解析器名称

    @Length(varcharSize = 64)
    @Constraint(notNull = true, uniqueKey = "uk_type")
    private String type; // 解析器类型标识（唯一）

    @Length(varcharSize = 128)
    private String displayName; // 显示名称

    @Length(varcharSize = 512)
    private String description; // 描述

    @Length(varcharSize = 64)
    private String author; // 作者

    @Length(varcharSize = 32)
    private String version; // 版本号

    @Length(varcharSize = 512)
    private String matchPattern; // URL匹配正则

    @Length(varcharSize = 65535)
    @Constraint(notNull = true)
    private String jsCode; // JavaScript/Python代码

    @NewField("脚本语言类型")
    @Length(varcharSize = 32)
    @Constraint(defaultValue = "javascript")
    private String language; // 脚本语言: javascript 或 python

    @Length(varcharSize = 64)
    private String ip; // 创建者IP

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime = new Date(); // 创建时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime; // 更新时间

    private Boolean enabled = true; // 是否启用
}

