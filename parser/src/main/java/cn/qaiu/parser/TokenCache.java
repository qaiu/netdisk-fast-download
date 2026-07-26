package cn.qaiu.parser;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 解析器 Token/Cookie 缓存 — 支持多账号隔离。
 * <p>
 * 以 (diskType + "#" + accountKey) 作为缓存 key，不同账号的 token 互不覆盖。
 * accountKey 优先使用 _configId，其次使用 username、cookie 前16位等可区分标识。
 * </p>
 */
public final class TokenCache {

    private TokenCache() {}

    /** token 缓存 */
    private static final ConcurrentHashMap<String, String> tokenMap = new ConcurrentHashMap<>();
    /** 过期时间缓存（毫秒时间戳） */
    private static final ConcurrentHashMap<String, Long> expireMap = new ConcurrentHashMap<>();
    /** 同一 key 下的额外字符串缓存（如 userId） */
    private static final ConcurrentHashMap<String, String> extraMap = new ConcurrentHashMap<>();
    /** 布尔标记缓存（如 authFlag） */
    private static final ConcurrentHashMap<String, Boolean> flagMap = new ConcurrentHashMap<>();

    // ============ key 构造 ============

    public static String key(String diskType, String accountKey) {
        return diskType + "#" + (accountKey == null ? "_default" : accountKey);
    }

    // ============ token ============

    public static String getToken(String cacheKey) {
        return tokenMap.get(cacheKey);
    }

    public static void putToken(String cacheKey, String token) {
        if (token == null) {
            tokenMap.remove(cacheKey);
        } else {
            tokenMap.put(cacheKey, token);
        }
    }

    // ============ expire ============

    public static long getExpire(String cacheKey) {
        return expireMap.getOrDefault(cacheKey, 0L);
    }

    public static void putExpire(String cacheKey, long expireMs) {
        expireMap.put(cacheKey, expireMs);
    }

    public static boolean isExpired(String cacheKey) {
        long exp = getExpire(cacheKey);
        return exp <= 0 || System.currentTimeMillis() > exp;
    }

    // ============ extra (userId 等) ============

    public static String getExtra(String cacheKey) {
        return extraMap.get(cacheKey);
    }

    public static void putExtra(String cacheKey, String value) {
        if (value == null) {
            extraMap.remove(cacheKey);
        } else {
            extraMap.put(cacheKey, value);
        }
    }

    // ============ flag (authFlag 等) ============

    public static boolean getFlag(String cacheKey, boolean defaultValue) {
        return flagMap.getOrDefault(cacheKey, defaultValue);
    }

    public static void putFlag(String cacheKey, boolean value) {
        flagMap.put(cacheKey, value);
    }

    // ============ 清除 ============

    public static void remove(String cacheKey) {
        tokenMap.remove(cacheKey);
        expireMap.remove(cacheKey);
        extraMap.remove(cacheKey);
        flagMap.remove(cacheKey);
    }

    /**
     * 清除指定网盘类型的所有缓存（精准清除，不影响其他网盘类型）
     */
    public static void removeByDiskType(String diskType) {
        String prefix = diskType + "#";
        tokenMap.keySet().removeIf(k -> k.startsWith(prefix));
        expireMap.keySet().removeIf(k -> k.startsWith(prefix));
        extraMap.keySet().removeIf(k -> k.startsWith(prefix));
        flagMap.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void clear() {
        tokenMap.clear();
        expireMap.clear();
        extraMap.clear();
        flagMap.clear();
    }

    // ============ Token 持久化队列 ============

    /** 待持久化的 cachedToken 数据 (cacheKey -> [token, expireMs]) */
    private static final ConcurrentHashMap<String, String[]> persistQueue = new ConcurrentHashMap<>();
    /** 待回写的凭据更新 (cacheKey -> newCredential)，如 PaliTool refresh_token 轮换 */
    private static final ConcurrentHashMap<String, String> credentialUpdateQueue = new ConcurrentHashMap<>();

    /**
     * 解析器登录成功后，将 token 加入持久化队列（下次 recordConfigUsage 回写 DB）
     */
    public static void queueCachedTokenPersist(String cacheKey, String token, long expireMs) {
        if (cacheKey != null && token != null) {
            persistQueue.put(cacheKey, new String[]{token, String.valueOf(expireMs)});
        }
    }

    /**
     * 凭据本身被替换（如 PaliTool refresh_token 轮换），加入回写队列
     */
    public static void queueCredentialUpdate(String cacheKey, String newCredential) {
        if (cacheKey != null && newCredential != null) {
            credentialUpdateQueue.put(cacheKey, newCredential);
        }
    }

    /**
     * 消费持久化队列：返回 [token, expireMs] 并移除；无数据返回 null
     */
    public static String[] pollCachedTokenPersist(String cacheKey) {
        return cacheKey == null ? null : persistQueue.remove(cacheKey);
    }

    /**
     * 消费凭据更新队列：返回新凭据并移除；无数据返回 null
     */
    public static String pollCredentialUpdate(String cacheKey) {
        return cacheKey == null ? null : credentialUpdateQueue.remove(cacheKey);
    }
}
