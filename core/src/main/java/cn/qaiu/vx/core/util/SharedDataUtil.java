package cn.qaiu.vx.core.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;

/**
 * vertx 共享数据
 * <br>Create date 2021-05-07 10:26:54
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class SharedDataUtil {

    private static final SharedData sharedData = VertxHolder.getVertxInstance().sharedData();

    public static SharedData shareData() {
        return sharedData;
    }

    public static LocalMap<String, Object> getLocalMap(String key) {
        return shareData().getLocalMap(key);
    }

    public static <T> LocalMap<String, T> getLocalMapWithCast(String key) {
        return  sharedData.getLocalMap(key);
    }

    public static JsonObject getJsonConfig(String key) {
        LocalMap<String, Object> localMap = getLocalMap("local");
        return (JsonObject) localMap.get(key);
    }

    public static JsonObject getCustomConfig() {
        return getJsonConfig("customConfig");
    }

    public static String getStringForCustomConfig(String key) {
        return getJsonConfig("customConfig").getString(key);
    }

    public static JsonArray getJsonArrayForCustomConfig(String key) {
        return getJsonConfig("customConfig").getJsonArray(key);
    }

    public static <T> T getValueForCustomConfig(String key) {
        return CastUtil.cast(getJsonConfig("customConfig").getValue(key));
    }

    public static JsonObject getJsonObjectForServerConfig(String key) {
        return getJsonConfig("server").getJsonObject(key);
    }

    public static JsonArray getJsonArrayForServerConfig(String key) {
        return getJsonConfig("server").getJsonArray(key);
    }

    public static String getJsonStringForServerConfig(String key) {
        return getJsonConfig("server").getString(key);
    }

    public static <T> T getValueForServerConfig(String key) {
        return CastUtil.cast(getJsonConfig("server").getValue(key));
    }

}
