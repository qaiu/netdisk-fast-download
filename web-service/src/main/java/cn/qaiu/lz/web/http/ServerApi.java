package cn.qaiu.lz.web.http;

import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.impl.EcTool;
import cn.qaiu.parser.impl.QQTool;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.enums.RouteMethod;
import cn.qaiu.vx.core.util.ResponseUtil;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
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

    @RouteMapping(value = "/parser", method = RouteMethod.GET, order = 4)
    public Future<Void> parse(HttpServerResponse response, HttpServerRequest request, String url, String pwd) {

        Promise<Void> promise = Promise.promise();
        if (url.contains(EcTool.SHARE_URL_PREFIX)) {
            // 默认读取Url参数会被截断手动获取一下其他参数
            url = EcTool.SHARE_URL_PREFIX + request.getParam("data");
        }
        if (url.contains(QQTool.SHARE_URL_PREFIX)) {
            // 默认读取Url参数会被截断手动获取一下其他参数
            url = url + "&key=" + request.getParam("key") +
                "&code=" + request.getParam("code") + "&k=" + request.getParam("k") +
                "&fweb=" + request.getParam("fweb") + "&cl=" + request.getParam("cl");
        }
        IPanTool.shareURLPrefixMatching(url, pwd).parse()
                .onSuccess(resUrl -> ResponseUtil.redirect(response, resUrl, promise))
                .onFailure(t -> promise.fail(t.fillInStackTrace()));
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

    @RouteMapping(value = "/:type/:key", method = RouteMethod.GET, order = 1)
    public Future<Void> parseKey(HttpServerResponse response, String type, String key) {
        Promise<Void> promise = Promise.promise();
        String code = "";
        if (key.contains("@")) {
            String[] keys = key.split("@");
            key = keys[0];
            code = keys[1];
        }

        IPanTool.typeMatching(type, key, code).parse()
                .onSuccess(resUrl -> ResponseUtil.redirect(response, resUrl, promise))
                .onFailure(t -> promise.fail(t.fillInStackTrace()));
        return promise.future();
    }

}
