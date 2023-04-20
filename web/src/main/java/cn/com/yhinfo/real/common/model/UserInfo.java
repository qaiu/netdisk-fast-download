package cn.com.yhinfo.real.common.model;

import cn.com.yhinfo.real.common.ToJson;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.format.SnakeCase;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.templates.annotations.ParametersMapped;
import io.vertx.sqlclient.templates.annotations.RowMapped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * sinoreal2-web
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * <br>Create date 2021/8/10 11:10
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@DataObject
@RowMapped(formatter = SnakeCase.class)
@ParametersMapped(formatter = SnakeCase.class)
public class UserInfo implements ToJson {

    private String username;

    private String permission;

    private String pwdCrc32;

    private String uuid;

    public UserInfo(JsonObject jsonObject) {
        this.username = jsonObject.getString("username");
        this.permission = jsonObject.getString("permission");
        this.pwdCrc32 = jsonObject.getString("pwdCrc32");
    }
}
