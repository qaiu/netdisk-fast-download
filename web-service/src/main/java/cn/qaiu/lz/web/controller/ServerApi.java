package cn.qaiu.lz.web.controller;

import cn.qaiu.lz.common.util.AuthParamCodec;
import cn.qaiu.lz.common.util.URLParamUtil;
import cn.qaiu.lz.web.model.AuthParam;
import cn.qaiu.lz.web.model.CacheLinkInfo;
import cn.qaiu.lz.web.service.CacheService;
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

    private final CacheService cacheService = AsyncServiceUtil.getAsyncServiceInstance(CacheService.class);

    @RouteMapping(value = "/parser", method = RouteMethod.GET, order = 1)
    public Future<Void> parse(HttpServerResponse response, HttpServerRequest request, RoutingContext rcx, String pwd, String auth) {
        Promise<Void> promise = Promise.promise();
        String url = URLParamUtil.parserParams(request);

        // 构建 otherParam，包含 UA 和解码后的认证参数
        JsonObject otherParam = buildOtherParam(request, auth);

        cacheService.getCachedByShareUrlAndPwd(url, pwd, otherParam)
                .onSuccess(res -> ResponseUtil.redirect(
                        response.putHeader("nfd-cache-hit", res.getCacheHit().toString())
                                .putHeader("nfd-cache-expires", res.getExpires()),
                                res.getDirectLink(), promise))
                .onFailure(t -> promise.fail(t.fillInStackTrace()));
        return promise.future();
    }

    @RouteMapping(value = "/json/parser", method = RouteMethod.GET, order = 1)
    public Future<CacheLinkInfo> parseJson(HttpServerRequest request, String pwd, String auth) {
        String url = URLParamUtil.parserParams(request);
        JsonObject otherParam = buildOtherParam(request, auth);
        return cacheService.getCachedByShareUrlAndPwd(url, pwd, otherParam);
    }

    @RouteMapping(value = "/json/:type/:key", method = RouteMethod.GET)
    public Future<CacheLinkInfo> parseKeyJson(HttpServerRequest request, String type, String key) {
        String pwd = "";
        if (key.contains("@")) {
            String[] keys = key.split("@");
            key = keys[0];
            pwd = keys[1];
        }
        return cacheService.getCachedByShareKeyAndPwd(type, key, pwd, JsonObject.of("UA",request.headers().get("user-agent")));
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
        cacheService.getCachedByShareKeyAndPwd(type, key, pwd, JsonObject.of("UA",request.headers().get("user-agent")))
                .onSuccess(res -> ResponseUtil.redirect(
                        response.putHeader("nfd-cache-hit", res.getCacheHit().toString())
                                .putHeader("nfd-cache-expires", res.getExpires()),
                        res.getDirectLink(), promise))
                .onFailure(t -> promise.fail(t.fillInStackTrace()));
        return promise.future();
    }

    /**
     * 构建 otherParam，包含 UA 和解码后的认证参数
     *
     * @param request HTTP请求
     * @param auth    加密的认证参数
     * @return JsonObject
     */
    private JsonObject buildOtherParam(HttpServerRequest request, String auth) {
        JsonObject otherParam = JsonObject.of("UA", request.headers().get("user-agent"));

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
                log.debug("已解码认证参数: authType={}", authParam.getAuthType());
            }
        }

        return otherParam;
    }
}
