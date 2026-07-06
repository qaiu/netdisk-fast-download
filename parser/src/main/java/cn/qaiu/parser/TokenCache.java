package cn.qaiu.parser;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parser token cache keyed by parser type and account identity.
 */
public final class TokenCache {

    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();
    private static final Map<String, Long> EXPIRES = new ConcurrentHashMap<>();

    private TokenCache() {
    }

    public static String key(String type, String accountId) {
        return type + ":" + (StringUtils.isBlank(accountId) ? "_default" : accountId);
    }

    public static void putToken(String key, String token) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(token)) {
            return;
        }
        TOKENS.put(key, token);
    }

    public static String getToken(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        if (isExpired(key)) {
            TOKENS.remove(key);
            EXPIRES.remove(key);
            return null;
        }
        return TOKENS.get(key);
    }

    public static void putExpire(String key, long expireTimeMillis) {
        if (StringUtils.isBlank(key)) {
            return;
        }
        EXPIRES.put(key, expireTimeMillis);
    }

    public static boolean isExpired(String key) {
        Long expireTimeMillis = EXPIRES.get(key);
        return expireTimeMillis != null && System.currentTimeMillis() > expireTimeMillis;
    }
}
