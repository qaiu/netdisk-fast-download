package cn.qaiu.util;

import java.net.MalformedURLException;
import java.net.URL;
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
}
