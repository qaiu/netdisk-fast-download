package cn.qaiu.vx.core.util;

import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;

public class ResponseUtil {

    public static void redirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    public static void redirect(HttpServerResponse response, String url, Promise<?> promise) {
        redirect(response, url);
        promise.complete();
    }
}
