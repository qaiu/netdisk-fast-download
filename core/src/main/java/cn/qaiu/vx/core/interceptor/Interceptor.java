package cn.qaiu.vx.core.interceptor;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * 拦截器接口
 * <br>Create date 2021-05-06 09:20:37
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public interface Interceptor {

    default Handler<RoutingContext> doHandle() {
        return this::handle;
    }

    void handle(RoutingContext context);
}
