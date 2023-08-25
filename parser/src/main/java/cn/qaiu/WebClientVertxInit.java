package cn.qaiu;

import io.vertx.core.Vertx;

public class WebClientVertxInit {
    private Vertx vertx = null;
    private static final WebClientVertxInit INSTANCE = new WebClientVertxInit();

    public static void init(Vertx vx) {
        INSTANCE.vertx = vx;
    }

    public static Vertx get() {
        if (INSTANCE.vertx == null) {
            throw new IllegalArgumentException("VertxInit getVertx: vertx is null");
        }
        return INSTANCE.vertx;
    }
}
