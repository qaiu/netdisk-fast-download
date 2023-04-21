package cn.qaiu.web.test;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.proxy.handler.ProxyHandler;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author <a href="mailto:emad.albloushi@gmail.com">Emad Alblueshi</a>
 */

public class WebProxyExamples {

    public void origin() {
        HttpServer backendServer = vertx.createHttpServer();

        Router backendRouter = Router.router(vertx);
        backendRouter.route().handler(ctx -> {
            System.out.println(ctx.request().path());
            ctx.next();
        });

        backendRouter.route(HttpMethod.GET, "/demo/foo").handler(rc -> rc.response()
                .putHeader("content-type", "text/html")
                .end("<html><body><h1>I'm the target resource111!</h1></body></html>"));
        backendRouter.route(HttpMethod.GET, "/demo/a")
                .handler(rc -> rc.response().putHeader("content-type", "text/html").end("AAA"));
        backendRouter.route(HttpMethod.GET, "/demo/b")
                .handler(rc -> rc.response().putHeader("content-type", "text/html").end("BBB"));

        backendServer.requestHandler(backendRouter).listen(7070);
    }

    /*

    /a -> 7070/foo/a
    /aaa/b -> '7070/foo/' -> 7070/foo/b
    /aaa/b -> /foo/b -> 7070/foo/b

    /aaa/b -> '7070/foo' -> 7070/foob
    /aaa/a -> '7070/' -> 7070/aaa/a
    /aaa/a -> '7070/aaa/' -> 7070/aaa/a


     */

    public Vertx vertx = Vertx.vertx();
    public HttpClient proxyClient = vertx.createHttpClient();
    // 创建 http代理处理器
    HttpProxy httpProxy = HttpProxy.reverseProxy(proxyClient);
    // 代理处理器绑定到路由
    Router proxyRouter = Router.router(vertx);

    public void route() {
        httpProxy.origin(7070, "localhost");

        proxyRouter.route("/demo/*").handler(ProxyHandler.create(httpProxy));
        proxyRouter.route("/api/*").handler(ctx -> ctx.reroute(ctx.request().path().replaceAll("^/api/", "/demo/")));

//        Router r1 = Router.router(vertx);
//        r1.route().handler(ctx -> {
//            int statusCode = ctx.response().getStatusCode();
//            if (statusCode == 404) {
//                ctx.response().write("subRouter ---------------> 404");
//                ctx.end();
//            }
//        });

        proxyRouter.route("/*").handler(StaticHandler.create("webroot/test"));

        proxyRouter.errorHandler(404, this::handle404);

//        proxyRouter.route("/api/*").handler(ctx -> ctx.end("123123"));
        // 路由绑定到代理服务器
        HttpServer proxyServer = vertx.createHttpServer();
        proxyServer.requestHandler(proxyRouter);
        proxyServer.listen(1080);
    }

    private void handle404(RoutingContext routingContext) {
        routingContext.end(routingContext.request().path() + "-------> 404");
    }

    public void routeShort(Vertx vertx, Router proxyRouter) {
        HttpClient proxyClient = vertx.createHttpClient();

        HttpProxy httpProxy = HttpProxy.reverseProxy(proxyClient);

        proxyRouter
                .route(HttpMethod.GET, "/*")
                .handler(ProxyHandler.create(httpProxy, 7070, "localhost"));


    }


    public void lowLevel() {
        HttpServer proxyServer = vertx.createHttpServer();
        proxyServer.requestHandler(outboundRequest -> {
            ProxyRequest proxyRequest = ProxyRequest.reverseProxy(outboundRequest);

            proxyClient.request(proxyRequest.getMethod(), 443, "qaiu.top", proxyRequest.getURI())
                    .compose(proxyRequest::send)
                    // Send the proxy response
                    .onSuccess(ProxyResponse::send)
                    .onFailure(err -> {
                        // Release the request
                        proxyRequest.release();

                        // Send error
                        outboundRequest.response().setStatusCode(500)
                                .send();
                    });
        }).listen(8181);
    }


    public void multi(Vertx vertx, Router proxyRouter) {
        HttpClient proxyClient = vertx.createHttpClient();

        HttpProxy httpProxy1 = HttpProxy.reverseProxy(proxyClient);
        httpProxy1.origin(7070, "localhost");

        HttpProxy httpProxy2 = HttpProxy.reverseProxy(proxyClient);
        httpProxy2.origin(6060, "localhost");

        proxyRouter
                .route(HttpMethod.GET, "/foo").handler(ProxyHandler.create(httpProxy1));

        proxyRouter
                .route(HttpMethod.GET, "/bar").handler(ProxyHandler.create(httpProxy2));
    }

    @Test
    public void test1() throws IOException, URISyntaxException {
//        URL url = new URL("www.runoob.com/html/html-tutorial.html");
        URI uri = new URI("http://www.runoob.com");

        System.out.println(StringUtils.isEmpty(uri.getPath()));
    }

    public static void main(String[] args) {
        final WebProxyExamples examples = new WebProxyExamples();
        examples.vertx.executeBlocking(rs -> {
            rs.complete();
            examples.origin();
        });
        examples.vertx.executeBlocking(rs -> {
            rs.complete();
            examples.route();
        });
        System.out.println("ok");
    }

}

