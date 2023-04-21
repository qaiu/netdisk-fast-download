package cn.qaiu.lz.web.http;

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
@RouteHandler("serverApi")
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

    @RouteMapping(value = "/test3", method = RouteMethod.GET)
    public void test03(HttpServerResponse response, String fullUrl) throws Exception {
        String url = LzTool.parse(fullUrl);
        log.info("url = {}", url);

        response.putHeader("location", "http://baidu.com").setStatusCode(302).end();
    }

}
