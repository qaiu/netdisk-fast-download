package cn.com.yhinfo.core.base;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * 统一响应处理
 * <br>Create date 2021-05-06 09:20:37
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public interface BaseHttpApi {

    default void fireJsonResponse(RoutingContext ctx, JsonObject jsonResult) {
        ctx.response().putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
                .setStatusCode(200)
                .end(jsonResult.encode());
    }

    default <T> void fireJsonResponse(RoutingContext ctx, T jsonResult) {
        JsonObject jsonObject = JsonObject.mapFrom(jsonResult);
        fireJsonResponse(ctx, jsonObject);
    }

    default void fireTextResponse(RoutingContext ctx, String text) {
        ctx.response().putHeader("content-type", "text/html; charset=utf-8").end(text);
    }

    default void sendError(int statusCode, RoutingContext ctx) {
        ctx.response().setStatusCode(statusCode).end();
    }
}
