package cn.qaiu.vx.core.base;

import cn.qaiu.vx.core.interceptor.AfterInterceptor;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.Set;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * 统一响应处理
 * <br>Create date 2021-05-06 09:20:37
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public interface BaseHttpApi {

    default Set<AfterInterceptor> getAfterInterceptor() {
        return null;
    }

    default void fireJsonResponse(RoutingContext ctx, JsonObject jsonResult) {
        ctx.response().putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
                .setStatusCode(200)
                .end(jsonResult.encode());
    }

    default <T> void fireJsonResponse(RoutingContext ctx, T jsonResult) {
        JsonObject jsonObject = JsonObject.mapFrom(jsonResult);
        if (!ctx.response().ended()) {
            fireJsonResponse(ctx, jsonObject);
        }
        handleAfterInterceptor(ctx, jsonObject);
    }

    default void handleAfterInterceptor(RoutingContext ctx, JsonObject jsonObject){
        Set<AfterInterceptor> afterInterceptor = getAfterInterceptor();
        if (afterInterceptor != null) {
            afterInterceptor.forEach(ai -> ai.handle(ctx.request(), jsonObject));
        }
        if (!ctx.response().ended()) {
            fireJsonResponse(ctx, "handleAfterInterceptor end.");
        }
    }

    default void fireTextResponse(RoutingContext ctx, String text) {
        ctx.response().putHeader(CONTENT_TYPE, "text/html; charset=utf-8").end(text);
        Set<AfterInterceptor> afterInterceptor = getAfterInterceptor();
        if (afterInterceptor != null) {
            afterInterceptor.forEach(ai -> ai.handle(ctx.request(), new JsonObject().put("text", text)));
        }
    }

    default void sendError(int statusCode, RoutingContext ctx) {
        ctx.response().setStatusCode(statusCode).end();
    }
}
