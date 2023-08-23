package cn.qaiu.vx.core.interceptor;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

/**
 * 后置拦截器接口
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public interface AfterInterceptor {

    void handle(HttpServerRequest request, JsonObject responseData);

}
