package cn.qaiu.vx.core.util;

import io.vertx.core.Vertx;

import java.util.Objects;

/**
 * 保存vertx实例
 * <br>Create date 2021-04-30 09:22:18
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public final class VertxHolder {

    private static volatile Vertx singletonVertx;

    public static synchronized void init(Vertx vertx) {
        Objects.requireNonNull(vertx, "未初始化Vertx");
        singletonVertx = vertx;
    }

    public static Vertx getVertxInstance() {
        Objects.requireNonNull(singletonVertx, "未初始化Vertx");
        return singletonVertx;
    }
}
