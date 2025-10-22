package cn.qaiu;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.qaiu.parser.custom.CustomParserRegistry;

public class WebClientVertxInit {
    private Vertx vertx = null;
    private static final WebClientVertxInit INSTANCE = new WebClientVertxInit();

    private static final Logger log = LoggerFactory.getLogger(WebClientVertxInit.class);

    public static void init(Vertx vx) {
        INSTANCE.vertx = vx;
        
        // 自动加载JavaScript解析器脚本
        try {
            CustomParserRegistry.autoLoadJsScripts();
        } catch (Exception e) {
            log.warn("自动加载JavaScript解析器脚本失败", e);
        }
    }

    public static Vertx get() {
        if (INSTANCE.vertx == null) {
            log.info("getVertx: Vertx实例不存在, 创建Vertx实例.");
            INSTANCE.vertx = Vertx.vertx();
            
            // 如果Vertx实例是新创建的，也尝试加载JavaScript脚本
            try {
                CustomParserRegistry.autoLoadJsScripts();
            } catch (Exception e) {
                log.warn("自动加载JavaScript解析器脚本失败", e);
            }
        }
        return INSTANCE.vertx;
    }
}