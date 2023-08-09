package cn.qaiu.lz.web.http;

import cn.qaiu.lz.common.parser.IPanTool;
import cn.qaiu.lz.common.parser.impl.*;
import cn.qaiu.lz.web.model.SysUser;
import cn.qaiu.lz.web.service.UserService;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.enums.RouteMethod;
import cn.qaiu.vx.core.util.AsyncServiceUtil;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import lombok.extern.slf4j.Slf4j;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * 服务API
 * <br>Create date 2021/4/28 9:15
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Slf4j
@RouteHandler("/")
public class ServerApi {

    private final UserService userService = AsyncServiceUtil.getAsyncServiceInstance(UserService.class);

    @RouteMapping(value = "/login", method = RouteMethod.POST)
    public Future<String> login(SysUser user) {
        log.info("<------- login: {}", user.getUsername());
        return userService.login(user);
    }

    @RouteMapping(value = "/parser", method = RouteMethod.GET, order = 4)
    public Future<Void> parse(HttpServerResponse response, HttpServerRequest request, String url, String pwd) {

        Promise<Void> promise = Promise.promise();
        if (url.contains(EcTool.SHARE_URL_PREFIX)) {
            // 默认读取Url参数会被截断手动获取一下其他参数
            url = EcTool.SHARE_URL_PREFIX + request.getParam("data");
        }
        try {
            IPanTool.shareURLPrefixMatching(url, pwd).parse().onSuccess(resUrl -> {
                response.putHeader("location", resUrl).setStatusCode(302).end();
                promise.complete();
            }).onFailure(t -> promise.fail(t.fillInStackTrace()));
        } catch (Exception e) {
            promise.fail(e);
        }
        return promise.future();
    }

    @RouteMapping(value = "/json/parser", method = RouteMethod.GET, order = 3)
    public Future<String> parseJson(HttpServerRequest request, String url, String pwd) {
        if (url.contains(EcTool.SHARE_URL_PREFIX)) {
            // 默认读取Url参数会被截断手动获取一下其他参数
            url = EcTool.SHARE_URL_PREFIX + request.getParam("data");
        }
        return IPanTool.shareURLPrefixMatching(url, pwd).parse();
    }


    @RouteMapping(value = "/:type/:key", method = RouteMethod.GET, order = 1)
    public void parseKey(HttpServerResponse response, String type, String key) {
        String code = "";
        if (key.contains("@")) {
            String[] keys = key.split("@");
            key = keys[0];
            code = keys[1];
        }

        IPanTool.typeMatching(type, key, code).parse().onSuccess(resUrl -> response.putHeader("location", resUrl)
                .setStatusCode(302).end()).onFailure(t -> {
            response.putHeader(CONTENT_TYPE, "text/html;charset=utf-8");
            response.end(t.getMessage());
        });
    }

    @RouteMapping(value = "/json/:type/:key", method = RouteMethod.GET, order = 2)
    public Future<String> parseKeyJson(String type, String key) {
        String code = "";
        if (key.contains("@")) {
            String[] keys = key.split("@");
            key = keys[0];
            code = keys[1];
        }
        return IPanTool.typeMatching(type, key, code).parse();
    }
}
