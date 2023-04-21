package cn.qaiu.lz.common.model;

import cn.qaiu.lz.common.ToJson;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * lz-web
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * <br>Create date 2021/8/10 11:10
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@DataObject
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
