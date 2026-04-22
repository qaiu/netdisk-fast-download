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
        response.putHeader(CONTENT_TYPE, "text/html; charset=utf-8")
                .putHeader("Referrer-Policy", "no-referrer")
                .putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    public static void redirect(HttpServerResponse response, String url, Promise<?> promise) {
        redirect(response, url);
        promise.complete();
    }

    public static void fireJsonObjectResponse(RoutingContext ctx, JsonObject jsonObject) {
        fireJsonObjectResponse(ctx, jsonObject, 200);
    }

    public static void fireJsonObjectResponse(HttpServerResponse ctx, JsonObject jsonObject) {
        fireJsonObjectResponse(ctx, jsonObject, 200);
    }

    public static void fireJsonObjectResponse(RoutingContext ctx, JsonObject jsonObject, int statusCode) {
        ctx.response().putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
                .setStatusCode(statusCode)
                .end(jsonObject.encode());
    }

    public static void fireJsonObjectResponse(HttpServerResponse ctx, JsonObject jsonObject, int statusCode) {
        ctx.putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
                .setStatusCode(statusCode)
                .end(jsonObject.encode());
    }

    public static <T> void fireJsonResultResponse(RoutingContext ctx, JsonResult<T> jsonResult) {
        fireJsonObjectResponse(ctx, jsonResult.toJsonObject());
    }

    public static <T> void fireJsonResultResponse(RoutingContext ctx, JsonResult<T> jsonResult, int statusCode) {
        fireJsonObjectResponse(ctx, jsonResult.toJsonObject(), statusCode);
    }

    public static <T> void fireJsonResultResponse(HttpServerResponse ctx, JsonResult<T> jsonResult) {
        fireJsonObjectResponse(ctx, jsonResult.toJsonObject());
    }

    public static void fireTextResponse(RoutingContext ctx, String text) {
        ctx.response().putHeader(CONTENT_TYPE, "text/html; charset=utf-8").end(text);
    }

    public static void sendError(RoutingContext ctx, int statusCode) {
        ctx.response().setStatusCode(statusCode).end();
    }
}
