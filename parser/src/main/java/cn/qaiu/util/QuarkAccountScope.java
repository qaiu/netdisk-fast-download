package cn.qaiu.util;

import io.vertx.core.MultiMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 夸克 / UC 账号缓存范围。只产出不可逆短指纹，不包含 Cookie 原文。
 * 有 {@code _configId} 时按配置隔离；否则按过滤后的会话 Cookie 隔离。
 */
public final class QuarkAccountScope {

    private QuarkAccountScope() {
    }

    /**
     * @return 可放进缓存 key 的短指纹；没有账号时返回 null，调用方继续使用分享级缓存
     */
    public static String suffix(Map<String, Object> otherParam) {
        if (otherParam == null || !(otherParam.get("auths") instanceof MultiMap auths)) {
            return null;
        }
        String configId = auths.get("_configId");
        if (configId != null && !configId.isBlank()) {
            return "cfg-" + digest(configId.trim());
        }
        String cookie = CookieUtils.filterUcQuarkCookie(auths.get("cookie"));
        if (cookie == null || cookie.isBlank()) {
            return null;
        }
        return "ck-" + digest(cookie);
    }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                out.append(String.format("%02x", hash[i]));
            }
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
