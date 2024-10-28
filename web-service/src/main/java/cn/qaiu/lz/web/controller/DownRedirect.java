package cn.qaiu.lz.web.controller;


import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.enums.RouteMethod;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@RouteHandler(value = "/d", order = 99)
@Slf4j
public class DownRedirect {
    @RouteMapping(value = "/:type/:key", method = RouteMethod.GET)
    public void parseKey(RoutingContext ctx, String type, String key) {
        ctx.reroute("/" + type + "/" + key);
    }
}
