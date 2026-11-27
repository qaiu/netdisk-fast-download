package cn.qaiu.util;

import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从分享链接中整理 shareKey、提取常见提取码查询参数。
 * 许多网盘模板用贪婪的 KEY 分组匹配路径，会把 ?pwd= / ?code= 或 # 片段吞进 shareKey。
 */
public final class ShareUrlParams {

    private static final String[] PASSWORD_QUERY_KEYS = {"pwd", "password", "code"};

    private ShareUrlParams() {
    }

    /**
     * 去掉误捕获的查询串和 hash 片段，保留路径中的 shareKey。
     * 若清理后为空则返回原始值，避免把合法 key 清掉。
     */
    public static String cleanShareKey(String key) {
        if (StringUtils.isEmpty(key)) {
            return key;
        }
        String cleaned = key;
        int hashIndex = cleaned.indexOf('#');
        if (hashIndex >= 0) {
            cleaned = cleaned.substring(0, hashIndex);
        }
        int queryIndex = cleaned.indexOf('?');
        if (queryIndex >= 0) {
            cleaned = cleaned.substring(0, queryIndex);
        }
        return StringUtils.isEmpty(cleaned) ? key : cleaned;
    }

    /**
     * 从完整分享 URL（或误带在 key 里的查询串）提取提取码。
     * 优先 {@code pwd}，其次 {@code password}、{@code code}（小飞机等）。
     */
    public static String extractSharePassword(String shareUrl) {
        if (StringUtils.isBlank(shareUrl)) {
            return null;
        }
        String query = queryString(shareUrl);
        if (StringUtils.isEmpty(query)) {
            return null;
        }
        Map<String, String> params = parseQuery(query);
        for (String name : PASSWORD_QUERY_KEYS) {
            String value = params.get(name);
            if (StringUtils.isNotEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    static String queryString(String url) {
        String noHash = url;
        int hashIndex = url.indexOf('#');
        if (hashIndex >= 0) {
            noHash = url.substring(0, hashIndex);
        }
        int queryIndex = noHash.indexOf('?');
        if (queryIndex < 0 || queryIndex == noHash.length() - 1) {
            return null;
        }
        return noHash.substring(queryIndex + 1);
    }

    static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String rawName = eq >= 0 ? pair.substring(0, eq) : pair;
            String rawValue = eq >= 0 ? pair.substring(eq + 1) : "";
            String name = decode(rawName).toLowerCase();
            if (name.isEmpty() || params.containsKey(name)) {
                continue;
            }
            params.put(name, decode(rawValue));
        }
        return params;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
