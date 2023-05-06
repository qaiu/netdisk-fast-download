package cn.qaiu.lz.web.model;

import cn.qaiu.db.ddl.Table;
import cn.qaiu.lz.common.ToJson;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@DataObject
@Table("t_user")
public class RealUser implements ToJson {
    private String id;

    private String username;
    private String password;

    public RealUser(JsonObject json) {
        this.username = json.getString("username");
        this.password = json.getString("password");
    }
}
