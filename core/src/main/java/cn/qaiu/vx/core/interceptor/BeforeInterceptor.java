package cn.qaiu.vx.core.interceptor;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * 前置拦截器接口
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public interface BeforeInterceptor {

    default Handler<RoutingContext> doHandle() {
        return this::handle;
    }

    void handle(RoutingContext context);

}
