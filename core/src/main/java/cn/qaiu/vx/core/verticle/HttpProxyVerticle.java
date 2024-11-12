package cn.qaiu.vx.core.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.http.*;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.ProxyOptions;

import java.util.Base64;

/**
 *
 */
public class HttpProxyVerticle extends AbstractVerticle {

    private HttpClient httpClient;
    private NetClient netClient;

    @Override
    public void start() {
        ProxyOptions proxyOptions = new ProxyOptions().setHost("127.0.0.1").setPort(7890);
        // 初始化 HTTP 客户端，用于向目标服务器发送 HTTP 请求
        HttpClientOptions httpClientOptions = new HttpClientOptions();
        httpClient = vertx.createHttpClient(httpClientOptions.setProxyOptions(proxyOptions));

        // 创建并启动 HTTP 代理服务器，监听指定端口
        HttpServer server = vertx.createHttpServer(new HttpServerOptions().setClientAuth(ClientAuth.REQUIRED));
        server.requestHandler(this::handleClientRequest);

        // 初始化 NetClient，用于在 CONNECT 请求中建立 TCP 连接隧道
        netClient = vertx.createNetClient(new NetClientOptions()
                .setProxyOptions(proxyOptions)
                .setConnectTimeout(15000)
                .setTrustAll(true));

        // 启动 HTTP 代理服务器
        server.listen(7891, ar -> {
            if (ar.succeeded()) {
                System.out.println("HTTP Proxy server started on port 7891");
            } else {
                System.err.println("Failed to start HTTP Proxy server: " + ar.cause());
            }
        });
    }

    // 处理 HTTP CONNECT 请求，用于代理 HTTPS 流量
    private void handleConnectRequest(HttpServerRequest clientRequest) {
        String[] uriParts = clientRequest.uri().split(":");
        if (uriParts.length != 2) {
            clientRequest.response().setStatusCode(400).end("Bad Request: Invalid URI format");
            return;
        }

        // 解析目标主机和端口
        String targetHost = uriParts[0];
        int targetPort;
        try {
            targetPort = Integer.parseInt(uriParts[1]);
        } catch (NumberFormatException e) {
            clientRequest.response().setStatusCode(400).end("Bad Request: Invalid port");
            return;
        }
        clientRequest.pause();
        // 通过 NetClient 连接目标服务器并创建隧道
        netClient.connect(targetPort, targetHost, connectionAttempt -> {
            if (connectionAttempt.succeeded()) {
                NetSocket targetSocket = connectionAttempt.result();

                // 升级客户端连接到 NetSocket 并实现双向数据流
                clientRequest.toNetSocket().onComplete(clientSocketAttempt -> {
                    if (clientSocketAttempt.succeeded()) {
                        NetSocket clientSocket = clientSocketAttempt.result();

                        // 设置双向数据流转发
                        clientSocket.handler(targetSocket::write);
                        targetSocket.handler(clientSocket::write);

                        // 关闭其中一方时关闭另一方
                        clientSocket.closeHandler(v -> targetSocket.close());
                        targetSocket.closeHandler(v -> clientSocket.close());
                    } else {
                        System.err.println("Failed to upgrade client connection to socket: " + clientSocketAttempt.cause().getMessage());
                        targetSocket.close();
                        clientRequest.response().setStatusCode(500).end("Internal Server Error");
                    }
                });
            } else {
                System.err.println("Failed to connect to target: " + connectionAttempt.cause().getMessage());
                clientRequest.response().setStatusCode(502).end("Bad Gateway: Unable to connect to target");
            }
        });
    }

    // 处理客户端的 HTTP 请求
    private void handleClientRequest(HttpServerRequest clientRequest) {
        String s = clientRequest.headers().get("Proxy-Authorization");
        if (s == null) {
            clientRequest.response().setStatusCode(403).end();
            return;
        }
        String[] split = new String(Base64.getDecoder().decode(s.replace("Basic ", ""))).split(":");
        if (split.length > 1) {
            System.out.println(split[0]);
            System.out.println(split[1]);
            // TODO
        }


        if (clientRequest.method() == HttpMethod.CONNECT) {
            // 处理 CONNECT 请求
            handleConnectRequest(clientRequest);
        } else {
            // 处理普通的 HTTP 请求
            handleHttpRequest(clientRequest);
        }
    }

    // 处理 HTTP 请求，转发至目标服务器并返回响应
    private void handleHttpRequest(HttpServerRequest clientRequest) {
        // 获取目标主机
        String hostHeader = clientRequest.getHeader("Host");
        if (hostHeader == null) {
            clientRequest.response().setStatusCode(400).end("Host header is missing");
            return;
        }

        String targetHost = hostHeader.split(":")[0];
        int targetPort = 80; // 默认为 HTTP 的端口
        clientRequest.pause(); // 暂停客户端请求的读取，避免数据丢失

        httpClient.request(clientRequest.method(), targetPort, targetHost, clientRequest.uri())
                .onSuccess(request -> {
                    clientRequest.resume(); // 恢复客户端请求的读取

                    // 逐个设置请求头
                    clientRequest.headers().forEach(header -> request.putHeader(header.getKey(), header.getValue()));

                    // 将客户端请求的 body 转发给目标服务器
                    clientRequest.bodyHandler(body -> request.send(body, ar -> {
                        if (ar.succeeded()) {
                            var response = ar.result();
                            clientRequest.response().setStatusCode(response.statusCode());
                            clientRequest.response().headers().setAll(response.headers());
                            response.body().onSuccess(b-> clientRequest.response().end(b));
                        } else {
                            clientRequest.response().setStatusCode(502).end("Bad Gateway: Unable to reach target");
                        }
                    }));
                })
                .onFailure(err -> {
                    err.printStackTrace();
                    clientRequest.response().setStatusCode(502).end("Bad Gateway: Request failed");
                });
    }

    @Override
    public void stop() {
        // 停止 HTTP 客户端以释放资源
        if (httpClient != null) {
            httpClient.close();
        }
    }

    /**
     * TODO add Deploy
     * @param args
     */
    public static void main(String[] args) {
        // 配置 DNS 解析器，使用多个 DNS 服务器来提升解析速度
        Vertx vertx = Vertx.vertx(new VertxOptions()
                .setAddressResolverOptions(new AddressResolverOptions()
                        .addServer("114.114.114.114")
                        .addServer("114.114.115.115")
                        .addServer("8.8.8.8")
                        .addServer("8.8.4.4")));

        // 部署 Verticle 并启动动态 HTTP 代理服务器
        vertx.deployVerticle(new HttpProxyVerticle());
    }
}
