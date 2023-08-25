package cn.qaiu.vx.core.interceptor;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import static cn.qaiu.vx.core.util.ResponseUtil.sendError;

/**
 * 前置拦截器接口
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public interface BeforeInterceptor extends Handler<RoutingContext> {
    String IS_NEXT = "RoutingContextIsNext";

    default Handler<RoutingContext> doHandle() {

        return ctx -> {
            ctx.put(IS_NEXT, false);
            BeforeInterceptor.this.handle(ctx);
            if (!(Boolean) ctx.get(IS_NEXT) && !ctx.response().ended()) {
                sendError(ctx, 403);
            }
        };
    }

    default void doNext(RoutingContext context) {
        context.put(IS_NEXT, true);
        context.next();
    }

    void handle(RoutingContext context);

}
