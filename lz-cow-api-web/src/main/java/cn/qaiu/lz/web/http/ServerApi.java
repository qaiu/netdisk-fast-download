package cn.qaiu.lz.web.http;

import cn.qaiu.lz.common.util.CowTool;
import cn.qaiu.lz.common.util.LzTool;
import cn.qaiu.lz.web.model.RealUser;
import cn.qaiu.lz.web.service.UserService;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.enums.RouteMethod;
import cn.qaiu.vx.core.model.JsonResult;
import cn.qaiu.vx.core.util.AsyncServiceUtil;
import io.vertx.core.Future;
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

    private final UserService userService = AsyncServiceUtil.getAsyncServiceInstance(UserService.class);

    @RouteMapping(value = "/login", method = RouteMethod.POST)
    public Future<String> login(RealUser user) {
        log.info("<------- login: {}", user.getUsername());
        return userService.login(user);
    }

    @RouteMapping(value = "/test2", method = RouteMethod.GET)
    public JsonResult<String> test01() {
        return JsonResult.data("ok");
    }

    @RouteMapping(value = "/parser", method = RouteMethod.GET)
    public void parse(HttpServerResponse response, String url) throws Exception {
        if (url.contains("lanzou")) {
            var urlDownload = LzTool.parse(url);
            log.info("url = {}", urlDownload);
            response.putHeader("location", urlDownload).setStatusCode(302).end();
        } else if (url.contains("cowtransfer.com")) {
            var urlDownload = CowTool.parse(url);
            response.putHeader("location", urlDownload).setStatusCode(302).end();
        }

    }

    @RouteMapping(value = "/lz/:id", method = RouteMethod.GET)
    public void lzParse(HttpServerResponse response, String id) throws Exception {
        var url = "https://wwa.lanzoux.com/" + id;
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
        var url = "https://wwa.lanzoux.com/" + id;
        var urlDownload = LzTool.parse(url);
        log.info("url = {}", urlDownload);
        return JsonResult.data(urlDownload);
    }

    @RouteMapping(value = "/json/cow/:id", method = RouteMethod.GET)
    public JsonResult<String> cowParseJson(HttpServerResponse response, String id) throws Exception {
        var url = "https://cowtransfer.com/s/" + id;
        return JsonResult.data(CowTool.parse(url));
    }
}
