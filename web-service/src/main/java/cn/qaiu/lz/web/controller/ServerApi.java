package cn.qaiu.lz.web.controller;

import cn.qaiu.lz.common.util.ParserAuthUtil;
import cn.qaiu.lz.common.util.URLParamUtil;
import cn.qaiu.lz.web.model.CacheLinkInfo;
import cn.qaiu.lz.web.service.CacheService;
import cn.qaiu.lz.web.service.DbService;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.enums.RouteMethod;
import cn.qaiu.vx.core.util.AsyncServiceUtil;
import cn.qaiu.vx.core.util.ResponseUtil;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务API
 * <br>Create date 2021/4/28 9:15
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Slf4j
@RouteHandler("/")
public class ServerApi {

    private static final String SKIP_CLIENT_LINKS = ParserAuthUtil.SKIP_CLIENT_LINKS;

    private final CacheService cacheService = AsyncServiceUtil.getAsyncServiceInstance(CacheService.class);
    private final DbService dbService = AsyncServiceUtil.getAsyncServiceInstance(DbService.class);

    @RouteMapping(value = "/parser", method = RouteMethod.GET, order = 1)
    public Future<Void> parse(HttpServerResponse response, HttpServerRequest request, RoutingContext rcx, String pwd, String auth) {
        Promise<Void> promise = Promise.promise();
        String url = URLParamUtil.parserParams(request);

        // 构建 otherParam，包含 UA 和解码后的认证参数
        JsonObject otherParam = buildOtherParam(request, auth, true);

        cacheService.getCachedByShareUrlAndPwd(url, pwd, otherParam)
                .onSuccess(res -> ResponseUtil.redirect(
                        addCacheHeaders(response, res),
                                res.getDirectLink(), promise))
                .onFailure(t -> {
                    ParserAuthUtil.recordDonatedAccountFailureIfNeeded(dbService, otherParam, t);
                    promise.tryFail(t);
                });
        return promise.future();
    }

    @RouteMapping(value = "/json/parser", method = RouteMethod.GET, order = 1)
    public Future<CacheLinkInfo> parseJson(HttpServerRequest request, String pwd, String auth) {
        String url = URLParamUtil.parserParams(request);
        JsonObject otherParam = buildOtherParam(request, auth);
        return cacheService.getCachedByShareUrlAndPwd(url, pwd, otherParam)
            .onFailure(t -> ParserAuthUtil.recordDonatedAccountFailureIfNeeded(dbService, otherParam, t));
    }

    public Future<CacheLinkInfo> parseJsonForRedirect(HttpServerRequest request, String pwd, String auth) {
        String url = URLParamUtil.parserParams(request);
        JsonObject otherParam = buildOtherParam(request, auth, true);
        return cacheService.getCachedByShareUrlAndPwd(url, pwd, otherParam)
            .onFailure(t -> ParserAuthUtil.recordDonatedAccountFailureIfNeeded(dbService, otherParam, t));
    }

    @RouteMapping(value = "/json/:type/:key", method = RouteMethod.GET)
    public Future<CacheLinkInfo> parseKeyJson(HttpServerRequest request, String type, String key) {
        String pwd = "";
        if (key.contains("@")) {
            String[] keys = key.split("@");
            key = keys[0];
            pwd = keys[1];
        }
        String origin = resolveOrigin(request);
        return cacheService.getCachedByShareKeyAndPwd(type, key, pwd, JsonObject.of("UA",request.headers().get("user-agent"), "_requestOrigin", origin));
    }

    public Future<CacheLinkInfo> parseKeyJsonForRedirect(HttpServerRequest request, String type, String key) {
        String pwd = "";
        if (key.contains("@")) {
            String[] keys = key.split("@");
            key = keys[0];
            pwd = keys[1];
        }
        String origin = resolveOrigin(request);
        return cacheService.getCachedByShareKeyAndPwd(type, key, pwd,
                JsonObject.of("UA", request.headers().get("user-agent"), "_requestOrigin", origin, SKIP_CLIENT_LINKS, true));
    }

    @RouteMapping(value = "/:type/:key", method = RouteMethod.GET)
    public Future<Void> parseKey(HttpServerResponse response, HttpServerRequest request, String type, String key) {
        Promise<Void> promise = Promise.promise();
        String pwd = "";
        if (key.contains("@")) {
            String[] keys = key.split("@");
            key = keys[0];
            pwd = keys[1];
        }
        String origin = resolveOrigin(request);
        cacheService.getCachedByShareKeyAndPwd(type, key, pwd,
                        JsonObject.of("UA", request.headers().get("user-agent"), "_requestOrigin", origin, SKIP_CLIENT_LINKS, true))
                .onSuccess(res -> ResponseUtil.redirect(
                        addCacheHeaders(response, res),
                        res.getDirectLink(), promise))
                .onFailure(promise::tryFail);
        return promise.future();
    }

    private static HttpServerResponse addCacheHeaders(HttpServerResponse response, CacheLinkInfo cacheLinkInfo) {
        if (response.ended() || response.closed()) {
            return response;
        }
        return response.putHeader("nfd-cache-hit", cacheLinkInfo.getCacheHit().toString())
                .putHeader("nfd-cache-expires", cacheLinkInfo.getExpires());
    }

    /**
     * 解析请求来源地址，支持反向代理
     */
    private static String resolveOrigin(HttpServerRequest request) {
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null && !forwardedHost.isBlank()) {
            String proto = request.getHeader("X-Forwarded-Proto");
            if (proto == null || proto.isBlank()) {
                proto = request.scheme();
            }
            return proto + "://" + forwardedHost;
        }
        return request.scheme() + "://" + request.host();
    }

    /**
     * 构建 otherParam，包含 UA 和解码后的认证参数
     *
     * @param request HTTP请求
     * @param auth    加密的认证参数
     * @return JsonObject
     */
    private JsonObject buildOtherParam(HttpServerRequest request, String auth) {
        return buildOtherParam(request, auth, false);
    }

    private JsonObject buildOtherParam(HttpServerRequest request, String auth, boolean skipClientLinks) {
        return ParserAuthUtil.buildOtherParam(request, auth, resolveOrigin(request), skipClientLinks);
    }
}
