package cn.qaiu.web.test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.stomp.StompServer;
import io.vertx.ext.stomp.StompServerHandler;
import io.vertx.ext.stomp.StompServerOptions;

import java.util.Arrays;

/**
 * sinoreal2-web
 * <p>create 2021/9/18 12:10</p>
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class StompTest {



    public static void main(String[] args) {

        Vertx vertx = Vertx.vertx();

        StompServer stompServer = StompServer.create(vertx, new StompServerOptions()
                        .setPort(-1) // 禁用 tcp 端口，这一项是可选的
                        .setWebsocketBridge(true) // 开启 websocket 支持
                        .setWebsocketPath("/stomp")) // 配置 websocket 路径，默认是 /stomp
                .handler(StompServerHandler.create(vertx));
        Future<HttpServer> http = vertx.createHttpServer(
                        new HttpServerOptions().setWebSocketSubProtocols(Arrays.asList("v10.stomp", "v11.stomp"))
                )
                .webSocketHandler(stompServer.webSocketHandler())
                .listen(8080);
        http.onSuccess(res->{
            System.out.println("okk");
        });

    }
}
