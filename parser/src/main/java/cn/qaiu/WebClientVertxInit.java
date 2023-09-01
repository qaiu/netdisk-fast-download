package cn.qaiu;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebClientVertxInit {
    private Vertx vertx = null;
    private static final WebClientVertxInit INSTANCE = new WebClientVertxInit();

    private static final Logger log = LoggerFactory.getLogger(WebClientVertxInit.class);

    public static void init(Vertx vx) {
        INSTANCE.vertx = vx;
    }

    public static Vertx get() {
        if (INSTANCE.vertx == null) {
            log.info("getVertx: Vertx实例不存在, 创建Vertx实例.");
            INSTANCE.vertx = Vertx.vertx();
        }
        return INSTANCE.vertx;
    }
}
