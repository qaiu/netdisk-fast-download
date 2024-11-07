package cn.qaiu.vx.core.util;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 参数 工具类
 * <br>Create date 2021-04-30 09:22:18
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public final class ParamUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParamUtil.class);

    public static Map<String, Object> multiMapToMap(MultiMap multiMap) {
        if (multiMap == null) return null;
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, String> entry : multiMap.entries()) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    public static <T> T multiMapToEntity(MultiMap multiMap, Class<T> tClass) {
        Map<String, Object> map = multiMapToMap(multiMap);
        if (map == null) {
            return null;
        }
        return new JsonObject(map).mapTo(tClass);
    }

    public static MultiMap paramsToMap(String paramString) {
        MultiMap entries = MultiMap.caseInsensitiveMultiMap();
        if (paramString == null) return entries;
        String[] params = paramString.split("&");
        if (params.length == 0) return entries;
        for (String param : params) {
            String[] kv = param.split("=");
            if (kv.length == 2) {
                entries.set(kv[0], kv[1]);
            } else {
                entries.set(kv[0], "");
            }
        }
        return entries;
    }

}
