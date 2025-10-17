package cn.qaiu.lz.common.cache;

import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2024/9/12 7:38
 */
public class CacheConfigLoader {
    private static final Map<String, Integer> CONFIGS = new HashMap<>();
    public static String TYPE;
    public static Integer DEFAULT_DURATION;

    public static void init(JsonObject config) {
        TYPE =  config.getString("type");
        Integer defaultDuration = config.getInteger("defaultDuration");
        DEFAULT_DURATION = defaultDuration == null ? 60 : defaultDuration;
        JsonObject duration = config.getJsonObject("duration");
        if (duration == null) return;
        duration.getMap().forEach((k, v) -> {
            if (v == null) {
                CONFIGS.put(k, DEFAULT_DURATION);
            } else {
                CONFIGS.put(k, (Integer) v);
            }
        });
    }

    public static Integer getDuration(String type) {
        String key = type.toLowerCase();
        return CONFIGS.getOrDefault(key, -1);
    }
}
