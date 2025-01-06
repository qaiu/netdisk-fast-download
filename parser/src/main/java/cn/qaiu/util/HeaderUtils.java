package cn.qaiu.util;

import io.vertx.core.MultiMap;

public class HeaderUtils {

    /**
     * 将请求头字符串转换为Vert.x的MultiMap对象
     *
     * @param headerString 请求头字符串
     * @return MultiMap对象
     */
    public static MultiMap parseHeaders(String headerString) {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        if (headerString == null || headerString.isEmpty()) {
            return headers;
        }

        // 按行分割字符串
        String[] lines = headerString.split("\n");

        for (String line : lines) {
            // 按冒号分割键值对
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                headers.add(key, value);
            }
        }

        return headers;
    }
}
