package cn.qaiu.vx.core.util;

import cn.qaiu.vx.core.model.JsonResult;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class ResponseUtil {

    public static void redirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    public static void redirect(HttpServerResponse response, String url, Promise<?> promise) {
        redirect(response, url);
        promise.complete();
    }

    public static void fireJsonObjectResponse(RoutingContext ctx, JsonObject jsonObject) {
        ctx.response().putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
                .setStatusCode(200)
                .end(jsonObject.encode());
    }

    public static <T> void fireJsonResultResponse(RoutingContext ctx, JsonResult<T> jsonResult) {
        fireJsonObjectResponse(ctx, jsonResult.toJsonObject());
    }

    public static void fireTextResponse(RoutingContext ctx, String text) {
        ctx.response().putHeader(CONTENT_TYPE, "text/html; charset=utf-8").end(text);
    }

    public static void sendError(RoutingContext ctx, int statusCode) {
        ctx.response().setStatusCode(statusCode).end();
    }
}
