package cn.qaiu.lz.web.model;

import cn.qaiu.db.ddl.Table;
import cn.qaiu.lz.common.ToJson;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@DataObject
@NoArgsConstructor
@Table("sys_user")
public class SysUser implements ToJson {
    private String id;
    private String username;

    private String password;

    private String email;
    private String phone;
    private String avatar;

    // 用户状态：0-禁用，1-正常
    private Integer status;

    // 用户角色：admin-管理员，user-普通用户
    private String role;

    // 最后登录时间
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastLoginTime;

    private Integer age;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createTime;

    public SysUser(JsonObject json) {
        this.id = json.getString("id");
        this.username = json.getString("username");
        this.password = json.getString("password");
        this.email = json.getString("email");
        this.phone = json.getString("phone");
        this.avatar = json.getString("avatar");
        this.status = json.getInteger("status");
        this.role = json.getString("role");
        this.age = json.getInteger("age");
        if (json.getString("createTime") != null) {
            this.createTime = LocalDateTime.parse(json.getString("createTime"));
        }
        if (json.getString("lastLoginTime") != null) {
            this.lastLoginTime = LocalDateTime.parse(json.getString("lastLoginTime"));
        }
    }
}
