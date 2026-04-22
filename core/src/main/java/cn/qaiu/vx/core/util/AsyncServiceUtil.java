package cn.qaiu.vx.core.util;

import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceProxyBuilder;

/**
 * @author Xu Haidong
 * @date 2018/8/15
 */
public final class AsyncServiceUtil {

    public static <T> T getAsyncServiceInstance(Class<T> asClazz, Vertx vertx) {
        String address = asClazz.getName();
        return new ServiceProxyBuilder(vertx).setAddress(address).build(asClazz);
    }

    public static <T> T getAsyncServiceInstance(Class<T> asClazz) {
        return getAsyncServiceInstance(asClazz, VertxHolder.getVertxInstance());
    }
}
