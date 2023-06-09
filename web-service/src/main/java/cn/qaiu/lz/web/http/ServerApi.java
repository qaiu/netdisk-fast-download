package cn.qaiu.lz.web.http;

import cn.qaiu.lz.common.util.*;
import cn.qaiu.lz.web.model.SysUser;
import cn.qaiu.lz.web.service.UserService;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.enums.RouteMethod;
import cn.qaiu.vx.core.model.JsonResult;
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

    @RouteMapping(value = "/test2", method = RouteMethod.GET)
    public JsonResult<String> test01() {
        return JsonResult.data("ok");
    }

    @RouteMapping(value = "/parser", method = RouteMethod.GET)
    public Future<Void> parse(HttpServerResponse response, HttpServerRequest request, String url, String pwd) {
        Promise<Void> promise = Promise.promise();
        if (url.contains("lanzou")) {
            String urlDownload = null;
            try {
                urlDownload = LzTool.parse(url);
                log.info("url = {}", urlDownload);
                response.putHeader("location", urlDownload).setStatusCode(302).end();
                promise.complete();
            } catch (Exception e) {
                promise.fail(e);
            }
        } else if (url.contains("cowtransfer.com")) {
            String urlDownload = null;
            try {
                urlDownload = CowTool.parse(url);
                response.putHeader("location", urlDownload).setStatusCode(302).end();
                promise.complete();
            } catch (Exception e) {
                promise.fail(e);
            }

        } else if (url.contains(EcTool.EC_HOST)) {
            // 默认读取Url参数会被截断手动获取一下其他参数
            String data = request.getParam("data");
            EcTool.parse(data).onSuccess(resUrl -> {
                response.putHeader("location", resUrl).setStatusCode(302).end();
                promise.complete();
            }).onFailure(t -> {
                promise.fail(t.fillInStackTrace());
            });
        }  else if (url.contains(UcTool.SHARE_URL_PREFIX)) {
            UcTool.parse(url, pwd).onSuccess(resUrl -> {
                response.putHeader("location", resUrl).setStatusCode(302).end();
                promise.complete();
            }).onFailure(t -> {
                promise.fail(t.fillInStackTrace());
            });
        } else if (url.contains(FjTool.SHARE_URL_PREFIX)) {
            FjTool.parse(url).onSuccess(resUrl -> {
                response.putHeader("location", resUrl).setStatusCode(302).end();
                promise.complete();
            }).onFailure(t -> {
                promise.fail(t.fillInStackTrace());
            });
        }
        return promise.future();
    }

    @RouteMapping(value = "/lz/:id", method = RouteMethod.GET)
    public void lzParse(HttpServerResponse response, String id) throws Exception {
        var url = "https://wwsd.lanzoue.com/" + id;
        var urlDownload = LzTool.parse(url);
        log.info("url = {}", urlDownload);
        response.putHeader("location", urlDownload).setStatusCode(302).end();
    }

    @RouteMapping(value = "/cow/:id", method = RouteMethod.GET)
    public void cowParse(HttpServerResponse response, String id) throws Exception {
        var url = "https://cowtransfer.com/s/" + id;
        var urlDownload = CowTool.parse(url);
        response.putHeader("location", urlDownload).setStatusCode(302).end();
    }

    @RouteMapping(value = "/json/lz/:id", method = RouteMethod.GET)
    public JsonResult<String> lzParseJson(HttpServerResponse response, String id) throws Exception {
        var url = "https://wwsd.lanzoue.com/" + id;
        var urlDownload = LzTool.parse(url);
        log.info("url = {}", urlDownload);
        return JsonResult.data(urlDownload);
    }

    @RouteMapping(value = "/json/cow/:id", method = RouteMethod.GET)
    public JsonResult<String> cowParseJson(HttpServerResponse response, String id) throws Exception {
        var url = "https://cowtransfer.com/s/" + id;
        return JsonResult.data(CowTool.parse(url));
    }

    @RouteMapping(value = "/ec/:id", method = RouteMethod.GET)
    public void ecParse(HttpServerResponse response, String id) {
        EcTool.parse(id).onSuccess(resUrl -> {
            response.putHeader("location", resUrl).setStatusCode(302).end();
        }).onFailure(t -> {
            response.putHeader(CONTENT_TYPE, "text/html;charset=utf-8");
            response.end(t.getMessage());
        });
    }

    @RouteMapping(value = "/json/ec/:id", method = RouteMethod.GET)
    public Future<String> ecParseJson(HttpServerResponse response, String id) {
        return EcTool.parse(id);
    }

    @RouteMapping(value = "/uc/:id", method = RouteMethod.GET)
    public void ucParse(HttpServerResponse response, String id) {
        String code = "";
        if (id.contains("#")) {
            String[] ids = id.split("#");
            id = ids[0];
            code = ids[1];
        }
        UcTool.parse(id, code).onSuccess(resUrl -> {
            response.putHeader("location", resUrl).setStatusCode(302).end();
        }).onFailure(t -> {
            response.putHeader(CONTENT_TYPE, "text/html;charset=utf-8");
            response.end(t.getMessage());
        });
    }

    @RouteMapping(value = "/json/uc/:id", method = RouteMethod.GET)
    public Future<String> ucParseJson(String id) {
        String code = "";
        if (id.contains("#")) {
            String[] ids = id.split("#");
            id = ids[0];
            code = ids[1];
        }
        return UcTool.parse(id, code);
    }

    @RouteMapping(value = "/fj/:id", method = RouteMethod.GET)
    public void fjParse(HttpServerResponse response, String id) {
        FjTool.parse(id).onSuccess(resUrl -> {
            response.putHeader("location", resUrl).setStatusCode(302).end();
        }).onFailure(t -> {
            response.putHeader(CONTENT_TYPE, "text/html;charset=utf-8");
            response.end(t.getMessage());
        });
    }

    @RouteMapping(value = "/json/fj/:id", method = RouteMethod.GET)
    public Future<String> fjParseJson(HttpServerResponse response, String id) {
        return FjTool.parse(id);
    }
}
