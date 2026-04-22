package cn.qaiu.vx.core.util;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
        // 支持 classpath: 前缀从类路径读取，否则从文件系统读取
        if (path != null && path.startsWith("classpath:")) {
            String resource = path.substring("classpath:".length());
            // 使用 executeBlocking(Callable) 直接返回 Future<JsonObject>
            return vertx.executeBlocking(() -> {
                InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
                if (is == null) {
                    throw new RuntimeException("classpath resource not found: " + resource);
                }
                try (InputStream in = is) {
                    byte[] bytes = in.readAllBytes();
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    if ("json".equalsIgnoreCase(format)) {
                        return new JsonObject(content);
                    } else {
                        throw new RuntimeException("unsupported classpath format: " + format);
                    }
                }
            });
        }

        Promise<JsonObject> promise = Promise.promise();

        ConfigStoreOptions store = new ConfigStoreOptions()
                .setType("file")
                .setFormat(format)
                .setConfig(new JsonObject().put("path", path));

        ConfigRetriever retriever = ConfigRetriever
                .create(vertx, new ConfigRetrieverOptions().addStore(store));

        // 异步获取配置
        // 成功直接完成 promise
        retriever.getConfig()
                .onSuccess(promise::complete)
                .onFailure(err -> {
                    // 配置读取失败，直接返回失败 Future
                    promise.fail(new RuntimeException(
                            "读取配置文件失败: " + path, err));
                    retriever.close();
                });

        return promise.future();
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
