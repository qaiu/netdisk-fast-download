package cn.qaiu;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.qaiu.parser.custom.CustomParserRegistry;

public class WebClientVertxInit {
    private volatile Vertx vertx = null;
    private static final WebClientVertxInit INSTANCE = new WebClientVertxInit();

    private static final Logger log = LoggerFactory.getLogger(WebClientVertxInit.class);

    public static synchronized void init(Vertx vx) {
        if (vx == null) {
            throw new IllegalArgumentException("Vertx instance must not be null");
        }
        INSTANCE.vertx = vx;
        
        // 自动加载JavaScript解析器脚本
        try {
            CustomParserRegistry.autoLoadJsScripts();
        } catch (Exception e) {
            log.warn("自动加载JavaScript解析器脚本失败", e);
        }
    }

    public static synchronized Vertx get() {
        if (INSTANCE.vertx == null) {
            throw new IllegalStateException("Vertx实例未初始化，请先调用 WebClientVertxInit.init(vertx)");
        }
        return INSTANCE.vertx;
    }
}
