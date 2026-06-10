package cn.qaiu.lz.web.controller;

import cn.qaiu.lz.common.util.AuthParamCodec;
import cn.qaiu.lz.common.util.URLParamUtil;
import cn.qaiu.lz.web.model.AuthParam;
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

    private static final String SKIP_CLIENT_LINKS = "_skipClientLinks";

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
                    recordDonatedAccountFailureIfNeeded(otherParam, t);
                    promise.tryFail(t);
                });
        return promise.future();
    }

    @RouteMapping(value = "/json/parser", method = RouteMethod.GET, order = 1)
    public Future<CacheLinkInfo> parseJson(HttpServerRequest request, String pwd, String auth) {
        String url = URLParamUtil.parserParams(request);
        JsonObject otherParam = buildOtherParam(request, auth);
        return cacheService.getCachedByShareUrlAndPwd(url, pwd, otherParam)
                .onFailure(t -> recordDonatedAccountFailureIfNeeded(otherParam, t));
    }

    public Future<CacheLinkInfo> parseJsonForRedirect(HttpServerRequest request, String pwd, String auth) {
        String url = URLParamUtil.parserParams(request);
        JsonObject otherParam = buildOtherParam(request, auth, true);
        return cacheService.getCachedByShareUrlAndPwd(url, pwd, otherParam)
                .onFailure(t -> recordDonatedAccountFailureIfNeeded(otherParam, t));
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
        JsonObject otherParam = JsonObject.of("UA", request.headers().get("user-agent"), "_requestOrigin", resolveOrigin(request));
        if (skipClientLinks) {
            otherParam.put(SKIP_CLIENT_LINKS, true);
        }

        // 解码认证参数
        if (auth != null && !auth.isEmpty()) {
            AuthParam authParam = AuthParamCodec.decode(auth);
            if (authParam != null && authParam.hasValidAuth()) {
                // 将认证参数放入 otherParam
                otherParam.put("authType", authParam.getAuthType());
                otherParam.put("authToken", authParam.getPrimaryCredential());
                otherParam.put("authPassword", authParam.getPassword());
                otherParam.put("authInfo1", authParam.getExt1());
                otherParam.put("authInfo2", authParam.getExt2());
                otherParam.put("authInfo3", authParam.getExt3());
                otherParam.put("authInfo4", authParam.getExt4());
                otherParam.put("authInfo5", authParam.getExt5());
                if (authParam.getDonatedAccountToken() != null && !authParam.getDonatedAccountToken().isBlank()) {
                    otherParam.put("donatedAccountToken", authParam.getDonatedAccountToken());
                }
                log.debug("已解码认证参数: authType={}", authParam.getAuthType());
            }
        }

        return otherParam;
    }

    private void recordDonatedAccountFailureIfNeeded(JsonObject otherParam, Throwable cause) {
        if (!isLikelyAuthFailure(cause)) {
            return;
        }
        String donatedAccountToken = otherParam.getString("donatedAccountToken");
        if (donatedAccountToken == null || donatedAccountToken.isBlank()) {
            return;
        }
        dbService.recordDonatedAccountFailureByToken(donatedAccountToken)
                .onFailure(e -> log.warn("记录捐赠账号失败次数失败", e));
    }

    private boolean isLikelyAuthFailure(Throwable cause) {
        if (cause == null) {
            return false;
        }
        String msg = cause.getMessage();
        if (msg == null || msg.isBlank()) {
            return false;
        }
        String lower = msg.toLowerCase();
        return lower.contains("auth")
                || lower.contains("token")
                || lower.contains("cookie")
                || lower.contains("password")
                || lower.contains("credential")
                || lower.contains("401")
                || lower.contains("403")
                || lower.contains("unauthorized")
                || lower.contains("forbidden")
                || lower.contains("expired")
                || lower.contains("登录")
                || lower.contains("认证");
    }
}
