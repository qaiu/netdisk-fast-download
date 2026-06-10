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
        if (response.ended() || response.closed()) {
            return;
        }
        response.putHeader(CONTENT_TYPE, "text/html; charset=utf-8")
                .putHeader("Referrer-Policy", "no-referrer")
                .putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    public static void redirect(HttpServerResponse response, String url, Promise<?> promise) {
        try {
            redirect(response, url);
            promise.tryComplete();
        } catch (Throwable t) {
            promise.tryFail(t);
        }
    }

    public static void fireJsonObjectResponse(RoutingContext ctx, JsonObject jsonObject) {
        fireJsonObjectResponse(ctx, jsonObject, 200);
    }

    public static void fireJsonObjectResponse(HttpServerResponse ctx, JsonObject jsonObject) {
        fireJsonObjectResponse(ctx, jsonObject, 200);
    }

    public static void fireJsonObjectResponse(RoutingContext ctx, JsonObject jsonObject, int statusCode) {
        if (ctx.response().ended() || ctx.response().closed()) {
            return;
        }
        ctx.response().putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
                .setStatusCode(statusCode)
                .end(jsonObject.encode());
    }

    public static void fireJsonObjectResponse(HttpServerResponse ctx, JsonObject jsonObject, int statusCode) {
        if (ctx.ended() || ctx.closed()) {
            return;
        }
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
        if (ctx.response().ended() || ctx.response().closed()) {
            return;
        }
        ctx.response().putHeader(CONTENT_TYPE, "text/html; charset=utf-8").end(text);
    }

    public static void sendError(RoutingContext ctx, int statusCode) {
        if (ctx.response().ended() || ctx.response().closed()) {
            return;
        }
        ctx.response().setStatusCode(statusCode).end();
    }
}
