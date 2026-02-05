package cn.qaiu.lz.web.model;

import cn.qaiu.lz.common.ToJson;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证参数模型
 * 用于接口传参时携带临时认证信息
 * <p>
 * 传参格式: auth=URL_ENCODE(BASE64(AES_ENCRYPT(JSON)))
 * JSON格式: {"username":"xxx","password":"xxx","token":"xxx","cookie":"xxx",...}
 * </p>
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * @date 2026/2/5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DataObject
public class AuthParam implements ToJson {

    /**
     * 认证类型
     * <ul>
     *   <li>accesstoken - 使用 accessToken 认证</li>
     *   <li>cookie - 使用 Cookie 认证</li>
     *   <li>authorization - 使用 Authorization 头认证</li>
     *   <li>password/username_password - 使用用户名密码认证</li>
     *   <li>custom - 自定义认证（使用 ext1-ext5 扩展字段）</li>
     * </ul>
     */
    private String authType;

    /**
     * 用户名（用于 password/username_password 认证类型）
     */
    private String username;

    /**
     * 密码（用于 password/username_password 认证类型）
     */
    private String password;

    /**
     * Token（accessToken/authorization/通用token）
     */
    private String token;

    /**
     * Cookie 字符串
     */
    private String cookie;

    /**
     * 授权信息（Authorization 头内容）
     */
    private String auth;

    /**
     * 扩展字段1（用于 custom 认证类型）
     * 格式: key:value
     */
    private String ext1;

    /**
     * 扩展字段2（用于 custom 认证类型）
     * 格式: key:value
     */
    private String ext2;

    /**
     * 扩展字段3（用于 custom 认证类型）
     * 格式: key:value
     */
    private String ext3;

    /**
     * 扩展字段4（用于 custom 认证类型）
     * 格式: key:value
     */
    private String ext4;

    /**
     * 扩展字段5（用于 custom 认证类型）
     * 格式: key:value
     */
    private String ext5;

    /**
     * 从 JsonObject 构造
     */
    public AuthParam(JsonObject json) {
        if (json == null) {
            return;
        }
        this.authType = json.getString("authType");
        this.username = json.getString("username");
        this.password = json.getString("password");
        this.token = json.getString("token");
        this.cookie = json.getString("cookie");
        this.auth = json.getString("auth");
        this.ext1 = json.getString("ext1");
        this.ext2 = json.getString("ext2");
        this.ext3 = json.getString("ext3");
        this.ext4 = json.getString("ext4");
        this.ext5 = json.getString("ext5");
    }

    /**
     * 转换为 JsonObject
     */
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        if (authType != null) json.put("authType", authType);
        if (username != null) json.put("username", username);
        if (password != null) json.put("password", password);
        if (token != null) json.put("token", token);
        if (cookie != null) json.put("cookie", cookie);
        if (auth != null) json.put("auth", auth);
        if (ext1 != null) json.put("ext1", ext1);
        if (ext2 != null) json.put("ext2", ext2);
        if (ext3 != null) json.put("ext3", ext3);
        if (ext4 != null) json.put("ext4", ext4);
        if (ext5 != null) json.put("ext5", ext5);
        return json;
    }

    /**
     * 检查是否有有效的认证信息
     */
    public boolean hasValidAuth() {
        return authType != null ||
               username != null ||
               password != null ||
               token != null ||
               cookie != null ||
               auth != null;
    }

    /**
     * 获取主要的认证凭证（token/cookie/auth）
     * 优先级: token > cookie > auth > username
     */
    public String getPrimaryCredential() {
        if (token != null && !token.isEmpty()) {
            return token;
        }
        if (cookie != null && !cookie.isEmpty()) {
            return cookie;
        }
        if (auth != null && !auth.isEmpty()) {
            return auth;
        }
        if (username != null && !username.isEmpty()) {
            return username;
        }
        return null;
    }
}
