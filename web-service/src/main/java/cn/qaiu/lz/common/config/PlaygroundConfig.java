package cn.qaiu.lz.common.config;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

/**
 * Playground配置加载器
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class PlaygroundConfig {
    private static boolean enabled = false;
    private static String password = "";

    /**
     * 初始化配置
     * @param config 配置对象
     */
    public static void init(JsonObject config) {
        if (config == null) {
            return;
        }
        enabled = config.getBoolean("enabled", false);
        password = config.getString("password", "");
    }

    /**
     * 是否启用Playground
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取密码
     */
    public static String getPassword() {
        return password;
    }

    /**
     * 是否需要密码
     */
    public static boolean hasPassword() {
        return StringUtils.isNotBlank(password);
    }
}
