package cn.qaiu.vx.core.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
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
    private HttpServer httpServer;
    private volatile boolean stopping = false;

    private JsonObject proxyPreConf;
    private JsonObject proxyServerConf;


    @Override
    public void start(io.vertx.core.Promise<Void> startPromise) {
        stopping = false;
        proxyServerConf = ((JsonObject)vertx.sharedData().getLocalMap(LOCAL).get(GLOBAL_CONFIG)).getJsonObject("proxy-server");
        proxyPreConf = ((JsonObject)vertx.sharedData().getLocalMap(LOCAL).get(GLOBAL_CONFIG)).getJsonObject("proxy-pre");
        Integer serverPort = proxyServerConf.getInteger("port");

        ProxyOptions proxyOptions = null;
        if (proxyPreConf != null && StringUtils.isNotBlank(proxyPreConf.getString("ip"))) {
            proxyOptions = new ProxyOptions(proxyPreConf);
        }

        // 初始化 HTTP 客户端，用于向目标服务器发送 HTTP 请求
        HttpClientOptions httpClientOptions = new HttpClientOptions()
                .setMaxPoolSize(64)
                .setMaxWaitQueueSize(256)
                .setConnectTimeout(15000)
                .setIdleTimeout(60)
                .setKeepAlive(true);
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

        httpServer = vertx.createHttpServer(httpServerOptions);
        httpServer.requestHandler(this::handleClientRequest);

        // 初始化 NetClient，用于在 CONNECT 请求中建立 TCP 连接隧道
        NetClientOptions netClientOptions = new NetClientOptions();

        if (proxyOptions != null) {
            netClientOptions.setProxyOptions(proxyOptions);
        }

        netClient = vertx.createNetClient(netClientOptions
                .setConnectTimeout(15000)
                .setTrustAll(true));

        // 启动 HTTP 代理服务器
        httpServer.listen(serverPort)
                .onSuccess(res -> {
                    LOGGER.info("HTTP Proxy server started on port {}", serverPort);
                    startPromise.complete();
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to start HTTP Proxy server: " + err.getMessage(), err);
                    closeClients().onComplete(close -> startPromise.fail(err));
                });
    }

    // 处理 HTTP CONNECT 请求，用于代理 HTTPS 流量
    private void handleConnectRequest(HttpServerRequest clientRequest) {
        String[] uriParts = clientRequest.uri().split(":");
        if (uriParts.length != 2) {
            failClientResponse(clientRequest.response(), 400, "Bad Request: Invalid URI format");
            return;
        }

        // 解析目标主机和端口
        String targetHost = uriParts[0];
        int targetPort;
        try {
            targetPort = Integer.parseInt(uriParts[1]);
        } catch (NumberFormatException e) {
            failClientResponse(clientRequest.response(), 400, "Bad Request: Invalid port");
            return;
        }
        clientRequest.pause();
        // 通过 NetClient 连接目标服务器并创建隧道
        try {
            netClient.connect(targetPort, targetHost)
                    .onSuccess(targetSocket -> {
                        // Upgrade client connection to NetSocket and implement bidirectional data flow
                        clientRequest.toNetSocket()
                                .onSuccess(clientSocket -> {
                                    clientSocket.pipeTo(targetSocket)
                                            .onFailure(err -> {
                                                LOGGER.debug("CONNECT client -> target pipe closed", err);
                                                closeTunnelSockets(clientSocket, targetSocket);
                                            });
                                    targetSocket.pipeTo(clientSocket)
                                            .onFailure(err -> {
                                                LOGGER.debug("CONNECT target -> client pipe closed", err);
                                                closeTunnelSockets(clientSocket, targetSocket);
                                            });

                                    // Close the other socket when one side closes
                                    clientSocket.closeHandler(v -> targetSocket.close());
                                    targetSocket.closeHandler(v -> clientSocket.close());
                                })
                                .onFailure(clientSocketAttempt -> {
                                    System.err.println("Failed to upgrade client connection to socket: " + clientSocketAttempt.getMessage());
                                    targetSocket.close();
                                    failClientRequestAndClose(clientRequest, 500, "Internal Server Error");
                                });
                    })
                    .onFailure(connectionAttempt -> {
                        LOGGER.warn("Failed to connect to target: {}", connectionAttempt.getMessage());
                        failClientRequestAndClose(clientRequest, 502, "Bad Gateway: Unable to connect to target");
                    });
        } catch (Exception e) {
            LOGGER.warn("CONNECT 请求创建失败", e);
            failClientRequestAndClose(clientRequest, 502, "Bad Gateway: Unable to connect to target");
        }
    }

    // 处理客户端的 HTTP 请求
    private void handleClientRequest(HttpServerRequest clientRequest) {
        if (stopping) {
            failClientResponse(clientRequest.response(), 503, "Service Unavailable");
            return;
        }
        // 打印来源ip和访问目标URI
        LOGGER.debug("source: {}, target: {}", clientRequest.remoteAddress().toString(), clientRequest.uri());
        if (proxyServerConf.containsKey("username") &&
                StringUtils.isNotBlank(proxyServerConf.getString("username"))) {
            String s = clientRequest.headers().get("Proxy-Authorization");
            if (s == null) {
                failClientResponse(clientRequest.response(), 403, null);
                return;
            }
            String[] split;
            try {
                split = new String(Base64.getDecoder().decode(s.replace("Basic ", ""))).split(":");
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Proxy-Authorization header is not valid Base64");
                failClientResponse(clientRequest.response(), 403, null);
                return;
            }
            if (split.length <= 1) {
                LOGGER.warn("Proxy-Authorization header format invalid: missing username:password separator");
                failClientResponse(clientRequest.response(), 403, null);
                return;
            }
            String username = proxyServerConf.getString("username");
            String password = proxyServerConf.getString("password");
            if (!split[0].equals(username) || !split[1].equals(password)) {
                LOGGER.info("-----auth failed------\nusername: {}", split[0]);
                failClientResponse(clientRequest.response(), 403, null);
                return;
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
            failClientResponse(clientRequest.response(), 400, "Host header is missing");
            return;
        }

        HostAndPort target;
        try {
            target = parseHostHeader(hostHeader);
        } catch (IllegalArgumentException e) {
            failClientResponse(clientRequest.response(), 400, "Bad Request: Invalid Host header");
            return;
        }
        String targetHost = target.host();
        int targetPort = extractPortFromUrl(clientRequest.uri(), target.port()); // 默认为 HTTP 的端口
        if (targetPort <= 0) {
            failClientResponse(clientRequest.response(), 400, "Bad Request: Invalid target port");
            return;
        }
        clientRequest.pause(); // 暂停客户端请求的读取，等上游请求创建完成

        try {
            httpClient.request(clientRequest.method(), targetPort, targetHost, clientRequest.uri())
                    .onSuccess(request -> {
                        // 逐个设置请求头
                        clientRequest.headers().forEach(header -> request.putHeader(header.getKey(), header.getValue()));

                        request.response()
                                .onSuccess(response -> {
                                    HttpServerResponse clientResponse = clientRequest.response();
                                    if (clientResponse.ended() || clientResponse.closed()) {
                                        response.resume();
                                        return;
                                    }
                                    clientResponse.setStatusCode(response.statusCode());
                                    clientResponse.headers().setAll(response.headers());
                                    response.pipeTo(clientResponse)
                                            .onFailure(err -> {
                                                LOGGER.error("HTTP代理响应转发失败", err);
                                                try {
                                                    response.request().reset();
                                                } catch (Exception e) {
                                                    LOGGER.debug("HTTP代理上游响应已关闭", e);
                                                }
                                                failClientRequestAndClose(clientRequest, 502, "Bad Gateway: Unable to reach target");
                                            });
                                })
                                .onFailure(err -> {
                                    LOGGER.error("HTTP代理响应失败", err);
                                    try {
                                        request.reset();
                                    } catch (Exception e) {
                                        LOGGER.debug("HTTP代理上游请求已关闭", e);
                                    }
                                    failClientRequestAndClose(clientRequest, 502, "Bad Gateway: Unable to reach target");
                                });

                        clientRequest.pipeTo(request)
                                .onFailure(err -> {
                                    LOGGER.error("HTTP代理请求转发失败", err);
                                    try {
                                        request.reset();
                                    } catch (Exception e) {
                                        LOGGER.debug("HTTP代理上游请求已关闭", e);
                                    }
                                    failClientRequestAndClose(clientRequest, 502, "Bad Gateway: Unable to reach target");
                                });
                        clientRequest.resume();
                    })
                    .onFailure(err -> {
                        LOGGER.error("HTTP请求失败", err);
                        failClientRequestAndClose(clientRequest, 502, "Bad Gateway: Request failed");
                    });
        } catch (Exception e) {
            LOGGER.error("HTTP请求创建失败", e);
            failClientRequestAndClose(clientRequest, 502, "Bad Gateway: Request failed");
        }
    }

    private void failClientResponse(HttpServerResponse response, String message) {
        failClientResponse(response, 502, message);
    }

    private void failClientResponse(HttpServerResponse response, int statusCode, String message) {
        if (response.ended() || response.closed()) {
            return;
        }
        try {
            if (!response.headWritten()) {
                response.setStatusCode(statusCode);
                if (message == null) {
                    response.end();
                } else {
                    response.end(message);
                }
            } else {
                response.reset();
            }
        } catch (Exception e) {
            LOGGER.debug("客户端响应已关闭，忽略代理错误响应", e);
        }
    }

    private void failClientRequestAndClose(HttpServerRequest request, int statusCode, String message) {
        HttpServerResponse response = request.response();
        if (response.ended() || response.closed()) {
            closeClientConnection(request);
            return;
        }
        try {
            if (!response.headWritten()) {
                response.setStatusCode(statusCode);
                Future<Void> endFuture = message == null ? response.end() : response.end(message);
                endFuture.onComplete(v -> closeClientConnection(request));
            } else {
                response.reset();
                closeClientConnection(request);
            }
        } catch (Exception e) {
            LOGGER.debug("客户端响应已关闭，关闭代理连接", e);
            closeClientConnection(request);
        }
    }

    private void closeClientConnection(HttpServerRequest request) {
        try {
            request.connection().close();
        } catch (Exception e) {
            LOGGER.debug("关闭客户端代理连接失败", e);
        }
    }

    private void closeTunnelSockets(NetSocket clientSocket, NetSocket targetSocket) {
        try {
            clientSocket.close();
        } catch (Exception e) {
            LOGGER.debug("关闭CONNECT客户端socket失败", e);
        }
        try {
            targetSocket.close();
        } catch (Exception e) {
            LOGGER.debug("关闭CONNECT目标socket失败", e);
        }
    }


    /**
     * 从 URL 中提取端口号
     *
     * @param urlString URL 字符串
     * @return 提取的端口号，如果没有指定端口，则返回默认端口
     */
    public static int extractPortFromUrl(String urlString) {
        return extractPortFromUrl(urlString, 80);
    }

    public static int extractPortFromUrl(String urlString, int defaultPort) {
        try {
            URI uri = new URI(urlString);
            int port = uri.getPort();
            // 如果 URL 没有指定端口，使用默认端口
            if (port == -1) {
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    port = 443; // HTTPS 默认端口
                } else {
                    port = defaultPort; // HTTP 默认端口
                }
            }
            return port;
        } catch (Exception e) {
            LOGGER.error("提取端口失败: {}", urlString, e);
            // 出现异常时返回 -1，表示提取失败
            return -1;
        }
    }

    private HostAndPort parseHostHeader(String hostHeader) {
        if (hostHeader.startsWith("[")) {
            int end = hostHeader.indexOf(']');
            if (end > 0) {
                String host = hostHeader.substring(1, end);
                int port = 80;
                if (hostHeader.length() > end + 2 && hostHeader.charAt(end + 1) == ':') {
                    port = Integer.parseInt(hostHeader.substring(end + 2));
                }
                return new HostAndPort(host, port);
            }
        }
        int lastColon = hostHeader.lastIndexOf(':');
        if (lastColon > 0 && hostHeader.indexOf(':') == lastColon) {
            return new HostAndPort(hostHeader.substring(0, lastColon), Integer.parseInt(hostHeader.substring(lastColon + 1)));
        }
        return new HostAndPort(hostHeader, 80);
    }

    private record HostAndPort(String host, int port) {
    }


    @Override
    public void stop(Promise<Void> stopPromise) {
        stopping = true;
        Future<Void> serverClose = httpServer == null ? Future.succeededFuture() : httpServer.close();
        serverClose.onComplete(serverResult -> closeClients().onComplete(clientResult -> {
                    if (serverResult.failed()) {
                        stopPromise.fail(serverResult.cause());
                    } else if (clientResult.failed()) {
                        stopPromise.fail(clientResult.cause());
                    } else {
                        stopPromise.complete();
                    }
                }));
    }

    private Future<Void> closeClients() {
        Future<Void> httpClientClose = httpClient == null ? Future.succeededFuture() : httpClient.close();
        Future<Void> netClientClose = netClient == null ? Future.succeededFuture() : netClient.close();
        return Future.all(httpClientClose, netClientClose).mapEmpty();
    }

}
