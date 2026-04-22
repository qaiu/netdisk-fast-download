package cn.qaiu.vx.core.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.ProxyOptions;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Base64;

import static cn.qaiu.vx.core.util.ConfigConstant.GLOBAL_CONFIG;
import static cn.qaiu.vx.core.util.ConfigConstant.LOCAL;

/**
 *
 */
public class HttpProxyVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProxyVerticle.class);

    private HttpClient httpClient;
    private NetClient netClient;

    private JsonObject proxyPreConf;
    private JsonObject proxyServerConf;


    @Override
    public void start() {
        proxyServerConf = ((JsonObject)vertx.sharedData().getLocalMap(LOCAL).get(GLOBAL_CONFIG)).getJsonObject("proxy-server");
        proxyPreConf = ((JsonObject)vertx.sharedData().getLocalMap(LOCAL).get(GLOBAL_CONFIG)).getJsonObject("proxy-pre");
        Integer serverPort = proxyServerConf.getInteger("port");

        ProxyOptions proxyOptions = null;
        if (proxyPreConf != null && StringUtils.isNotBlank(proxyPreConf.getString("ip"))) {
            proxyOptions = new ProxyOptions(proxyPreConf);
        }

        // 初始化 HTTP 客户端，用于向目标服务器发送 HTTP 请求
        HttpClientOptions httpClientOptions = new HttpClientOptions();
        if (proxyOptions != null) {
            httpClientOptions.setProxyOptions(proxyOptions);
        }
        httpClient = vertx.createHttpClient(httpClientOptions);

        // 创建并启动 HTTP 代理服务器，监听指定端口
        HttpServerOptions httpServerOptions = new HttpServerOptions();
        if (proxyServerConf.containsKey("username") &&
                StringUtils.isNotBlank(proxyServerConf.getString("username"))) {
            httpServerOptions.setClientAuth(ClientAuth.REQUIRED);
        }

        HttpServer server = vertx.createHttpServer();
        server.requestHandler(this::handleClientRequest);

        // 初始化 NetClient，用于在 CONNECT 请求中建立 TCP 连接隧道
        NetClientOptions netClientOptions = new NetClientOptions();

        if (proxyOptions != null) {
            httpClientOptions.setProxyOptions(proxyOptions);
        }

        netClient = vertx.createNetClient(netClientOptions
                .setConnectTimeout(15000)
                .setTrustAll(true));

        // 启动 HTTP 代理服务器
        server.listen(serverPort)
                .onSuccess(res-> LOGGER.info("HTTP Proxy server started on port {}", serverPort))
                .onFailure(err-> LOGGER.error("Failed to start HTTP Proxy server: " + err.getMessage()));
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
        netClient.connect(targetPort, targetHost)
                .onSuccess(targetSocket -> {
                    // Upgrade client connection to NetSocket and implement bidirectional data flow
                    clientRequest.toNetSocket()
                            .onSuccess(clientSocket -> {
                                // Set up bidirectional data forwarding
                                clientSocket.handler(targetSocket::write);
                                targetSocket.handler(clientSocket::write);

                                // Close the other socket when one side closes
                                clientSocket.closeHandler(v -> targetSocket.close());
                                targetSocket.closeHandler(v -> clientSocket.close());
                            })
                            .onFailure(clientSocketAttempt -> {
                                System.err.println("Failed to upgrade client connection to socket: " + clientSocketAttempt.getMessage());
                                targetSocket.close();
                                clientRequest.response().setStatusCode(500).end("Internal Server Error");
                            });
                })
                .onFailure(connectionAttempt -> {
                    System.err.println("Failed to connect to target: " + connectionAttempt.getMessage());
                    clientRequest.response().setStatusCode(502).end("Bad Gateway: Unable to connect to target");
                });
    }

    // 处理客户端的 HTTP 请求
    private void handleClientRequest(HttpServerRequest clientRequest) {
        // 打印来源ip和访问目标URI
        LOGGER.debug("source: {}, target: {}", clientRequest.remoteAddress().toString(), clientRequest.uri());
        if (proxyServerConf.containsKey("username") &&
                StringUtils.isNotBlank(proxyServerConf.getString("username"))) {
            String s = clientRequest.headers().get("Proxy-Authorization");
            if (s == null) {
                clientRequest.response().setStatusCode(403).end();
                return;
            }
            String[] split = new String(Base64.getDecoder().decode(s.replace("Basic ", ""))).split(":");
            if (split.length > 1) {
                // TODO
                String username = proxyServerConf.getString("username");
                String password = proxyServerConf.getString("password");
                if (!split[0].equals(username) || !split[1].equals(password)) {
                    LOGGER.info("-----auth failed------\nusername: {}\npassword: {}", username, password);
                    clientRequest.response().setStatusCode(403).end();
                    return;
                }
            }
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
        int targetPort = extractPortFromUrl(clientRequest.uri()); // 默认为 HTTP 的端口
        clientRequest.pause(); // 暂停客户端请求的读取，避免数据丢失

        httpClient.request(clientRequest.method(), targetPort, targetHost, clientRequest.uri())
                .onSuccess(request -> {
                    clientRequest.resume(); // 恢复客户端请求的读取

                    // 逐个设置请求头
                    clientRequest.headers().forEach(header -> request.putHeader(header.getKey(), header.getValue()));

                    // 将客户端请求的 body 转发给目标服务器
                    clientRequest.bodyHandler(body ->
                            request.send(body)
                                    .onSuccess(response -> {
                                        clientRequest.response().setStatusCode(response.statusCode());
                                        clientRequest.response().headers().setAll(response.headers());
                                        response.body()
                                                .onSuccess(b -> clientRequest.response().end(b))
                                                .onFailure(err -> clientRequest.response()
                                                        .setStatusCode(502).end("Bad Gateway: Unable to reach target"));
                                    })
                                    .onFailure(err -> clientRequest.response()
                                            .setStatusCode(502).end("Bad Gateway: Unable to reach target"))
                    );
                })
                .onFailure(err -> {
                    err.printStackTrace();
                    clientRequest.response().setStatusCode(502).end("Bad Gateway: Request failed");
                });
    }


    /**
     * 从 URL 中提取端口号
     *
     * @param urlString URL 字符串
     * @return 提取的端口号，如果没有指定端口，则返回默认端口
     */
    public static int extractPortFromUrl(String urlString) {
        try {
            URI uri = new URI(urlString);
            int port = uri.getPort();
            // 如果 URL 没有指定端口，使用默认端口
            if (port == -1) {
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    port = 443; // HTTPS 默认端口
                } else {
                    port = 80; // HTTP 默认端口
                }
            }
            return port;
        } catch (Exception e) {
            e.printStackTrace();
            // 出现异常时返回 -1，表示提取失败
            return -1;
        }
    }


    @Override
    public void stop() {
        // 停止 HTTP 客户端以释放资源
        if (httpClient != null) {
            httpClient.close();
        }
        if (netClient != null) {
            netClient.close();
        }
    }

}
