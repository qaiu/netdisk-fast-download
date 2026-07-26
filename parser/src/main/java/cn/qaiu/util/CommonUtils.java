package cn.qaiu.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommonUtils {

    /**
     * 获取分享key 比如: https://www.ilanzou.com/s/xxx -> xxx
     * @param urlPrefix 不包含key的URL前缀
     * @param url 完整URL
     * @return 分享key
     */
    public static String adaptShortPaths(String urlPrefix, String url) {
        if (url.endsWith(".html")) {
            url = url.substring(0, url.length() - 5);
        }
        String prefix = "https://";
        if (!url.startsWith(urlPrefix) && url.startsWith(prefix)) {
            urlPrefix = urlPrefix.substring(prefix.length());
            return url.substring(url.indexOf(urlPrefix) + urlPrefix.length());
        } else if (!url.startsWith(urlPrefix)) {
            url = urlPrefix + url;
        }
        return url.substring(urlPrefix.length());
    }

    public static Map<String, String> getURLParams(String url) throws MalformedURLException {
        URL fullUrl = new URL(url);
        String query = fullUrl.getQuery();
        if (query == null || query.isEmpty()) {
            return new HashMap<>();
        }
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>();
        for (String param : params) {
            if (!param.contains("=")) {
                throw new RuntimeException("解析URL异常: 匹配不到参数中的=");
            }
            int endIndex = param.indexOf('=');
            String key = param.substring(0, endIndex);
            String value = param.substring(endIndex + 1);
            map.put(key, value);
        }
        return map;
    }

    /**
     * 提取第一个匹配的非空捕捉组
     * @param matcher 已创建的 Matcher
     * @return 第一个非空 group，或 "" 如果没有
     */
    public static String firstNonEmptyGroup(Matcher matcher) {
        if (!matcher.find()) {
            return "";
        }
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String g = matcher.group(i);
            if (g != null && !g.trim().isEmpty()) {
                return g.trim();
            }
        }
        return "";
    }

    /**
     * 直接传 html 和 regex，返回第一个非空捕捉组
     */
    public static String extract(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        return firstNonEmptyGroup(matcher);
    }

    /**
     * 解码路径参数中的 Base64。
     * <p>优先按 URL-Safe Base64 解；兼容历史「标准 Base64 + URLEncode」以及重复 encode。</p>
     */
    public static String urlBase64Decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new RuntimeException("URL Base64 解码失败: empty");
        }
        String s = encoded.trim().replace(' ', '+');
        // 兼容历史 URLEncode / 误二次 encode：有 % 则解到不再变化
        for (int i = 0; i < 3 && s.contains("%"); i++) {
            try {
                String next = java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
                if (next.equals(s)) {
                    break;
                }
                s = next;
            } catch (Exception e) {
                break;
            }
        }
        Exception last = null;
        for (String candidate : new String[]{s, padBase64(s)}) {
            try {
                return new String(java.util.Base64.getUrlDecoder().decode(candidate), StandardCharsets.UTF_8);
            } catch (Exception e) {
                last = e;
            }
            try {
                return new String(java.util.Base64.getDecoder().decode(candidate), StandardCharsets.UTF_8);
            } catch (Exception e) {
                last = e;
            }
        }
        throw new RuntimeException("URL Base64 解码失败", last);
    }

    /**
     * 编码为可直接放进 URL path 的 Base64（URL-Safe，无 padding）。
     * <p>不再做 URLEncoder，避免前端/代理再 encode 时变成 %253D。</p>
     */
    public static String urlBase64Encode(String str) {
        return java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    private static String padBase64(String s) {
        int mod = s.length() % 4;
        if (mod == 0) {
            return s;
        }
        return s + "====".substring(mod);
    }
}
