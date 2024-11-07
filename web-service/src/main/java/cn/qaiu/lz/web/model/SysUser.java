package cn.qaiu.lz.web.model;

import cn.qaiu.db.ddl.Table;
import cn.qaiu.lz.common.ToJson;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@DataObject
@NoArgsConstructor
@Table("t_user")
public class SysUser implements ToJson {
    private String id;
    private String username;
    private String password;

    private Integer age;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createTime;

    public SysUser(JsonObject json) {
        this.id = json.getString("id");
        this.username = json.getString("username");
        this.password = json.getString("password");
        this.age = json.getInteger("age");
        if (json.getString("createTime") != null) {
            this.createTime = LocalDateTime.parse(json.getString("createTime"));
        }
    }
}
