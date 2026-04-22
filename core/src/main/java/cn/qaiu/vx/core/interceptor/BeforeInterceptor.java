package cn.qaiu.vx.core.interceptor;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * 前置拦截器接口
 * <p>
 * 注意：Vert.x是异步非阻塞框架，不能在Event Loop中使用synchronized等阻塞操作！
 * 所有操作都应该是非阻塞的，使用Vert.x的上下文数据存储机制保证线程安全。
 * </p>
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public interface BeforeInterceptor extends Handler<RoutingContext> {
    String IS_NEXT = "RoutingContextIsNext";

    default Handler<RoutingContext> doHandle() {
        return ctx -> {
            // 【优化】移除synchronized锁，Vert.x的RoutingContext本身就是线程安全的
            // 每个请求都有独立的RoutingContext，不需要额外加锁
            ctx.put(IS_NEXT, false);
            handle(ctx); // 调用具体的处理逻辑
            // 确保如果没有调用doNext()并且响应未结束，则返回错误
            // if (!(Boolean) ctx.get(IS_NEXT) && !ctx.response().ended()) {
            //     sendError(ctx, 403);
            // }
        };
    }

    default void doNext(RoutingContext context) {
        // 【优化】移除synchronized锁
        // RoutingContext的put和next操作是线程安全的，不需要额外同步
        context.put(IS_NEXT, true);
        context.next(); // 继续执行下一个处理器
    }

    void handle(RoutingContext context); // 实现具体的拦截处理逻辑
}

