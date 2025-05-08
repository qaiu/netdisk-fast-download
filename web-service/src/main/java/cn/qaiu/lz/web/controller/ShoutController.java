package cn.qaiu.lz.web.controller;

import cn.qaiu.lz.web.service.ShoutService;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.enums.RouteMethod;
import cn.qaiu.vx.core.model.JsonResult;
import cn.qaiu.vx.core.util.AsyncServiceUtil;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

@RouteHandler("/v2/shout")
public class ShoutController {

    private final ShoutService shoutService = AsyncServiceUtil.getAsyncServiceInstance(ShoutService.class);

    @RouteMapping(value = "/submit", method = RouteMethod.POST)
    public Future<JsonObject> submitMessage(RoutingContext ctx) {
        String content = ctx.body().asJsonObject().getString("content");
        if (content == null || content.trim().isEmpty()) {
            return Future.failedFuture("内容不能为空");
        }
        return shoutService.submitMessage(content, ctx.request().remoteAddress().host()).compose(code ->
                Future.succeededFuture(JsonResult.data(code).toJsonObject()));
    }

    @RouteMapping(value = "/retrieve", method = RouteMethod.GET)
    public Future<JsonObject> retrieveMessage(RoutingContext ctx) {
        String code = ctx.request().getParam("code");
        if (code == null || code.length() != 6) {
            return Future.failedFuture("提取码必须为6位数字");
        }
        return shoutService.retrieveMessage(code);
    }
}
