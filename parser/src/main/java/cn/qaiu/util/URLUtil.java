package cn.qaiu.util;

import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class URLUtil {

    private final Map<String, String> queryParams = new HashMap<>();

    // 构造函数，传入URL并解析参数
    private URLUtil(String url) {
        try {
            URL parsedUrl = new URL(url);
            String ref = parsedUrl.getRef();
            if (StringUtils.isNotEmpty(ref)) {
                parsedUrl = new URL(parsedUrl.getProtocol() + "://" + parsedUrl.getHost() + ref);
            }
            String query = parsedUrl.getQuery();
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                    queryParams.put(key, value);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 静态方法，用于创建UrlUtil实例
    public static URLUtil from(String url) {
        return new URLUtil(url);
    }

    // 获取参数的方法
    public String getParam(String param) {
        return queryParams.get(param);
    }
}
