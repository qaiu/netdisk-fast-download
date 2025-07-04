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
            // 加同步锁
            synchronized (BeforeInterceptor.class) {
                ctx.put(IS_NEXT, false);
                BeforeInterceptor.this.handle(ctx);
                if (!(Boolean) ctx.get(IS_NEXT) && !ctx.response().ended()) {
                    sendError(ctx, 403);
                }
            }
        };
    }

    default void doNext(RoutingContext context) {
        // 设置上下文状态为可以继续执行
        // 添加同步锁保障多线程下执行时序
        synchronized (BeforeInterceptor.class) {
            context.put(IS_NEXT, true);
            context.next();
        }
    }

    void handle(RoutingContext context);

}
