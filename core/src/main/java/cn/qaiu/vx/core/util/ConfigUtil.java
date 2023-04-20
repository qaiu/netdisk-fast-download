package cn.qaiu.vx.core.util;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * 异步读取配置工具类
 * <br>Create date 2021/9/2 1:23
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class ConfigUtil {

    /**
     * 异步读取配置文件
     *
     * @param format 配置文件格式
     * @param path 路径
     * @param vertx vertx
     * @return JsonObject的Future
     */
    public static Future<JsonObject> readConfig(String format, String path, Vertx vertx) {
        // 读取yml配置
        ConfigStoreOptions store = new ConfigStoreOptions()
                .setType("file")
                .setFormat(format)
                .setConfig(new JsonObject().put("path", path));

        ConfigRetriever retriever = ConfigRetriever
                .create(vertx, new ConfigRetrieverOptions().addStore(store));

        return retriever.getConfig();
    }


    /**
     * 异步读取Yaml配置文件
     *
     * @param path 路径
     * @param vertx vertx
     * @return JsonObject的Future
     */
    synchronized public static Future<JsonObject> readYamlConfig(String path, Vertx vertx) {
        return readConfig("yaml", path+".yml", vertx);
    }

    /**
     * 异步读取Yaml配置文件
     *
     * @param path 路径
     * @return JsonObject的Future
     */
    synchronized public static Future<JsonObject> readYamlConfig(String path) {
        return readConfig("yaml", path+".yml", VertxHolder.getVertxInstance());
    }
}
