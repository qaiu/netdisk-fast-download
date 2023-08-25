package cn.qaiu.vx.core.interceptor;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * 后置拦截器接口
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public interface AfterInterceptor {

    void handle(RoutingContext ctx, JsonObject responseData);

}
