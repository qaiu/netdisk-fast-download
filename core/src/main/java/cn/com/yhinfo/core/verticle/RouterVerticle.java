package cn.com.yhinfo.core.verticle;

import cn.com.yhinfo.core.handlerfactory.RouterHandlerFactory;
import cn.com.yhinfo.core.util.CommonUtil;
import cn.com.yhinfo.core.util.SharedDataUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.stomp.StompServer;
import io.vertx.ext.stomp.StompServerHandler;
import io.vertx.ext.stomp.StompServerOptions;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Http服务 注册路由
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class RouterVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouterVerticle.class);

    private static final int port = SharedDataUtil.getValueForServerConfig("port");
    private static final Router router = new RouterHandlerFactory(
            SharedDataUtil.getJsonStringForCustomConfig("routerLocations"),
            SharedDataUtil.getJsonStringForServerConfig("contextPath")).createRouter();

    private static final JsonObject globalConfig = SharedDataUtil.getJsonConfig("globalConfig");

    private HttpServer server;

    static {
        LOGGER.info("To start listening to port {} ......", port);
    }

    @Override
    public void start(Promise<Void> startPromise) {
        // 端口是否占用
        if (CommonUtil.isPortUsing(port)) {
            throw new RuntimeException("Start fail: the '" + port + "' port is already in use...");
        }
        HttpServerOptions options;
        if (globalConfig.containsKey("http") && globalConfig.getValue("http") != null) {
            options = new HttpServerOptions(globalConfig.getJsonObject("http"));
        } else {
            options = new HttpServerOptions();
        }
        options.setPort(port);
        server = vertx.createHttpServer(options);

        server.requestHandler(router).webSocketHandler(s->{}).listen()
                .onSuccess(s -> startPromise.complete())
                .onFailure(e -> startPromise.fail(e.getCause()));
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (server == null) {
            stopPromise.complete();
            return;
        }
        server.close(result -> {
            if (result.failed()) {
                stopPromise.fail(result.cause());
            } else {
                stopPromise.complete();
            }
        });
    }
}
