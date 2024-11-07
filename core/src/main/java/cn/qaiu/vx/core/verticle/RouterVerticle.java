package cn.qaiu.vx.core.verticle;

import cn.qaiu.vx.core.handlerfactory.RouterHandlerFactory;
import cn.qaiu.vx.core.util.CommonUtil;
import cn.qaiu.vx.core.util.JacksonConfig;
import cn.qaiu.vx.core.util.SharedDataUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
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
            SharedDataUtil.getJsonStringForServerConfig("contextPath")).createRouter();

    private static final JsonObject globalConfig = SharedDataUtil.getJsonConfig("globalConfig");

    private HttpServer server;

    static {
        LOGGER.info(JacksonConfig.class.getSimpleName() + " >> ");
        JacksonConfig.nothing();
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
