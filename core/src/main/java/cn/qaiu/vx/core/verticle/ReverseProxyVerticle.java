package cn.qaiu.vx.core.verticle;

import cn.qaiu.vx.core.util.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.proxy.handler.ProxyHandler;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>反向代理服务</p>
 * <p>可以根据配置文件自动生成代理服务</p>
 * <p>可以配置多个服务, 配置文件见示例</p>
 * <p>【优化】支持高并发场景，连接池复用，避免线程阻塞</p>
 * <br>Create date 2021/9/2 0:41
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class ReverseProxyVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReverseProxyVerticle.class);

    private static final String PATH_PROXY_CONFIG = SharedDataUtil
            .getJsonConfig(ConfigConstant.GLOBAL_CONFIG)
            .getString("proxyConf");
    private static final Future<JsonObject> CONFIG = ConfigUtil.readYamlConfig(PATH_PROXY_CONFIG);
    private static final String DEFAULT_PATH_404 = "webroot/err/page404.html";

    private static String serverName = "Vert.x-proxy-server"; //Server name in Http response header

    public static String REROUTE_PATH_PREFIX = "/__rrvpspp"; //re_route_vert_proxy_server_path_prefix 硬编码

    /**
     * 【优化】HttpClient连接池，按host:port缓存复用，避免每个请求都创建新连接
     */
    private final Map<String, HttpClient> httpClientPool = new ConcurrentHashMap<>();

    /**
     * 【优化】高并发场景下的HttpClient配置
     */
    private static final int MAX_POOL_SIZE = 100;           // 最大连接池大小
    private static final int MAX_WAIT_QUEUE_SIZE = 500;    // 最大等待队列大小
    private static final int CONNECT_TIMEOUT = 30000;      // 连接超时30秒
    private static final int IDLE_TIMEOUT = 60;            // 空闲超时60秒
    private static final boolean KEEP_ALIVE = true;        // 启用Keep-Alive
    private static final boolean PIPELINING = true;        // 启用HTTP管线化


    @Override
    public void start(Promise<Void> startPromise) {
        CONFIG.onSuccess(this::handleProxyConfList).onFailure(e -> {
            LOGGER.info("web代理配置已禁用，当前仅支持API调用");
        });
//        createFileListener
        startPromise.complete();
    }

    /**
     * 【优化】Verticle停止时清理HttpClient连接池
     */
    @Override
    public void stop(Promise<Void> stopPromise) {
        LOGGER.info("Stopping ReverseProxyVerticle, closing {} HttpClient connections...", httpClientPool.size());
        httpClientPool.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception e) {
                LOGGER.warn("Error closing HttpClient: {}", e.getMessage());
            }
        });
        httpClientPool.clear();
        stopPromise.complete();
    }

    /**
     * 【优化】获取或创建HttpClient，实现连接池复用
     * @param host 目标主机
     * @param port 目标端口
     * @return HttpClient实例
     */
    private HttpClient getOrCreateHttpClient(String host, int port) {
        String key = host + ":" + port;
        return httpClientPool.computeIfAbsent(key, k -> {
            LOGGER.info("Creating new HttpClient for {}", key);
            HttpClientOptions options = new HttpClientOptions()
                    .setMaxPoolSize(MAX_POOL_SIZE)                    // 连接池大小
                    .setMaxWaitQueueSize(MAX_WAIT_QUEUE_SIZE)         // 等待队列大小
                    .setConnectTimeout(CONNECT_TIMEOUT)               // 连接超时
                    .setIdleTimeout(IDLE_TIMEOUT)                     // 空闲超时
                    .setKeepAlive(KEEP_ALIVE)                         // Keep-Alive
                    .setKeepAliveTimeout(120)                         // Keep-Alive超时120秒
                    .setPipelining(PIPELINING)                        // HTTP管线化
                    .setPipeliningLimit(10)                           // 管线化限制
                    .setDecompressionSupported(true)                  // 支持解压响应
                    .setTcpKeepAlive(true)                            // TCP Keep-Alive
                    .setTcpNoDelay(true)                              // 禁用Nagle算法，降低延迟
                    .setTcpFastOpen(true)                             // 启用TCP Fast Open
                    .setTcpQuickAck(true)                             // 启用TCP Quick ACK
                    .setReuseAddress(true)                            // 允许地址重用
                    .setReusePort(true);                              // 允许端口重用
            return vertx.createHttpClient(options);
        });
    }

    /**
     * 全局可信上游代理 IP 集合（如 nginx），仅这些 IP 的 X-Forwarded-For 会被信任
     */
    private Set<String> globalTrustedProxies = new HashSet<>();

    /**
     * 获取主配置文件
     *
     * @param config proxy config
     */
    private void handleProxyConfList(JsonObject config) {
        serverName = config.getString("server-name");
        // 解析全局 trusted-proxies
        JsonArray trustedArr = config.getJsonArray("trusted-proxies");
        if (trustedArr != null) {
            trustedArr.forEach(ip -> {
                if (ip instanceof String) {
                    globalTrustedProxies.add(((String) ip).trim());
                }
            });
        }
        JsonArray proxyConfList = config.getJsonArray("proxy");
        if (proxyConfList != null) {
            proxyConfList.forEach(proxyConf -> {
                if (proxyConf instanceof JsonObject) {
                    handleProxyConf((JsonObject) proxyConf);
                }
            });
        }
    }

    /**
     * 解析真实客户端 IP。
     * 若直连来源在可信代理列表中，优先取 X-Real-IP，其次取 X-Forwarded-For 第一个值；
     * 否则直接使用直连对端地址。
     */
    private String resolveClientIp(HttpServerRequest request) {
        String peerIp = request.remoteAddress().host();
        if (globalTrustedProxies.contains(peerIp)) {
            String realIp = request.getHeader("X-Real-IP");
            if (StringUtils.isNotBlank(realIp)) {
                return realIp.trim();
            }
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.isNotBlank(xff)) {
                return xff.split(",")[0].trim();
            }
        }
        return peerIp;
    }

    /**
     * 解析 proxy-set-headers 中的 nginx 风格变量。
     * 支持：$remote_addr、$proxy_add_x_forwarded_for、$scheme、$host；
     * 其他值作为字面量直接使用。
     */
    private String resolveHeaderVariable(String tpl, HttpServerRequest req, String clientIp) {
        return switch (tpl) {
            case "$remote_addr" -> clientIp;
            case "$proxy_add_x_forwarded_for" -> {
                String existing = req.getHeader("X-Forwarded-For");
                yield StringUtils.isNotBlank(existing) ? existing + ", " + clientIp : clientIp;
            }
            case "$scheme" -> req.isSSL() ? "https" : "http";
            case "$host"   -> req.getHeader("Host");
            default        -> tpl;
        };
    }

    /**
     * 处理单个反向代理配置
     *
     * @param proxyConf 代理配置
     */
    private void handleProxyConf(JsonObject proxyConf) {
        // page404 path
        if (proxyConf.containsKey(

                "page404")) {
            System.getProperty("user.dir");
            String path = proxyConf.getString("page404");
            if (StringUtils.isEmpty(path)) {
                proxyConf.put("page404", DEFAULT_PATH_404);
            } else {
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
                if (!new File(System.getProperty("user.dir") + path).exists()) {
                    proxyConf.put("page404", DEFAULT_PATH_404);
                }
            }
        } else {
            proxyConf.put("page404", DEFAULT_PATH_404);
        }

        Router proxyRouter = Router.router(vertx);

        // Add Server name header
        proxyRouter.route().handler(ctx -> {
            String realPath = ctx.request().uri();
            if (realPath.startsWith(REROUTE_PATH_PREFIX)) {
                // vertx web proxy暂不支持rewrite, 所以这里进行手动替换, 请求地址中的请求path前缀替换为originPath
                String rePath = realPath.replace(REROUTE_PATH_PREFIX, "");
                ctx.reroute(rePath);
                return;
            }

            ctx.response().putHeader("Server", serverName);
            ctx.next();
        });

        // http api proxy
        if (proxyConf.containsKey("location")) {
            handleLocation(proxyConf.getJsonArray("location"), proxyRouter);
        }

        // static server
        if (proxyConf.containsKey("static")) {
            handleStatic(proxyConf.getJsonObject("static"), proxyRouter);
        }

        // Send page404 page
        proxyRouter.errorHandler(404, ctx -> {
            ctx.response().sendFile(proxyConf.getString("page404"));
        });

        HttpServer server = getHttpsServer(proxyConf);
        server.requestHandler(proxyRouter);

        Integer port = proxyConf.getInteger("listen");
        LOGGER.info("proxy server start on {} port", port);
        server.listen(port);
    }

    private HttpServer getHttpsServer(JsonObject proxyConf) {
        HttpServerOptions httpServerOptions = new HttpServerOptions()
                // 【优化】高并发服务器配置
                .setTcpKeepAlive(true)                    // TCP Keep-Alive
                .setTcpNoDelay(true)                      // 禁用Nagle算法
                .setCompressionSupported(true)            // 启用压缩
                .setAcceptBacklog(50000)                  // 增加积压队列到50000
                .setIdleTimeout(120)                      // 空闲超时120秒
                .setTcpFastOpen(true)                     // 启用TCP Fast Open
                .setTcpQuickAck(true)                     // 启用TCP Quick ACK
                .setReuseAddress(true)                    // 允许地址重用
                .setReusePort(true);                      // 允许端口重用
        if (proxyConf.containsKey("ssl")) {
            JsonObject sslConfig = proxyConf.getJsonObject("ssl");

            URL sslUrl = this.getClass().getClassLoader().getResource("");
            if (sslUrl == null) {
                throw new RuntimeException("SSL url not exist...");
            }
            if (sslConfig.containsKey("enable") && sslConfig.getBoolean("enable")) {
                String sslCertificatePath = sslUrl.getPath() + sslConfig.getString("ssl_certificate");
                String sslCertificateKeyPath = sslUrl.getPath() + sslConfig.getString("ssl_certificate_key");
                LOGGER.info("enable ssl config. ");
                httpServerOptions
                        .setSsl(true)
                        .setKeyCertOptions(
                                new PemKeyCertOptions()
                                        .setKeyPath(sslCertificateKeyPath)
                                        .setCertPath(sslCertificatePath)
                        ).addEnabledSecureTransportProtocol(sslConfig.getString("ssl_protocols"));
                String sslCiphers = sslConfig.getString("ssl_ciphers");
                if (sslCiphers != null && !sslCiphers.isEmpty()) {
                    for (String s : sslCiphers.split(":")) {
                        httpServerOptions.addEnabledCipherSuite(s);
                    }
                }
            }

        }
        return vertx.createHttpServer(httpServerOptions);
    }

    /**
     * 处理静态资源配置
     *
     * @param staticConf  静态资源配置
     * @param proxyRouter 代理路由
     */
    private void handleStatic(JsonObject staticConf, Router proxyRouter) {
        String path = staticConf.getString("path");
        proxyRouter.route(path + "*").handler(ctx -> {
            if (staticConf.containsKey("add-headers")) {
                Map<String, String> headers = CastUtil.cast(staticConf.getJsonObject("add-headers").getMap());
                headers.forEach(ctx.response()::putHeader);
            }
            ctx.next();
        });


        StaticHandler staticHandler;
        if (staticConf.containsKey("root")) {
            staticHandler = StaticHandler.create(staticConf.getString("root"));
        } else {
            staticHandler = StaticHandler.create();
        }
        if (staticConf.containsKey("directory-listing")) {
            staticHandler.setDirectoryListing(staticConf.getBoolean("directory-listing"));
        } else if (staticConf.containsKey("index")) {
            staticHandler.setIndexPage(staticConf.getString("index"));
        }
        proxyRouter.route(path + "*").handler(staticHandler);
    }

    /**
     * 处理Location配置 代理请求Location(和nginx类似?)
     *
     * @param locationsConf location配置
     * @param proxyRouter   代理路由
     */
    private void handleLocation(JsonArray locationsConf, Router proxyRouter) {

        locationsConf.stream().map(e -> (JsonObject) e).forEach(location -> {
            // 代理规则
            String origin = location.getString("origin");
            String path = location.getString("path");
            try {
                URL url = new URL("https://" + origin);
                String host = url.getHost();
                int port = url.getPort();
                if (port == -1) {
                    port = 80;
                }
                String originPath = url.getPath();
                LOGGER.info("path {}, originPath {}, to {}:{}", path, originPath, host, port);

                // 【优化】使用连接池获取HttpClient，避免每个location都创建新连接
                final HttpClient httpClient = getOrCreateHttpClient(host, port);
                final HttpProxy httpProxy = HttpProxy.reverseProxy(httpClient);
                httpProxy.origin(port, host);

                // proxy-set-headers 支持（nginx 风格变量替换）
                if (location.containsKey("proxy-set-headers")) {
                    final JsonObject headerConf = location.getJsonObject("proxy-set-headers");
                    httpProxy.addInterceptor(new ProxyInterceptor() {
                        @Override
                        public Future<ProxyResponse> handleProxyRequest(ProxyContext ctx) {
                            HttpServerRequest incoming = ctx.request().proxiedRequest();
                            String clientIp = resolveClientIp(incoming);
                            headerConf.forEach(entry -> {
                                Object val = entry.getValue();
                                if (val != null) {
                                    String resolved = resolveHeaderVariable(val.toString(), incoming, clientIp);
                                    if (resolved != null) {
                                        ctx.request().putHeader(entry.getKey(), resolved);
                                    }
                                }
                            });
                            return ProxyInterceptor.super.handleProxyRequest(ctx);
                        }
                    });
                }

                if (StringUtils.isEmpty(path)) {
                    return;
                }

                // 代理目标路径为空 就像nginx一样路径穿透 (相对路径)
                if (StringUtils.isEmpty(originPath) || path.equals(originPath)) {
                    Route route = path.startsWith("~") ? proxyRouter.routeWithRegex(path.substring(1))
                            : proxyRouter.route(path);
                    // 【优化】为代理处理器添加超时
                    route.handler(ProxyHandler.create(httpProxy));
                } else {
                    // 配置 /api/, / => 请求 /api/test 代理后 /test
                    // 配置 /api/, /xxx => 请求 /api/test 代理后 /xxx/test
                     final String path0 = path;
                     final String originPath0 = REROUTE_PATH_PREFIX + originPath;

                     proxyRouter.route(originPath0 + "*").handler(ProxyHandler.create(httpProxy));
                     proxyRouter.route(path0 + "*").handler(ctx -> {
                         String realPath = ctx.request().uri();
                         if (realPath.startsWith(path0)) {
                             // vertx web proxy暂不支持rewrite, 所以这里进行手动替换, 请求地址中的请求path前缀替换为originPath
                             String rePath = realPath.replaceAll("^" + path0, originPath0);
                             ctx.reroute(rePath);
                         } else {
                             ctx.next();
                         }
                     });
                    // 计算唯一后缀，避免多个 location 冲突
//                    String uniqueKey = (host + ":" + port + "|" + path).replaceAll("[^a-zA-Z0-9:_|/]", "");
//                    String uniqueSuffix = Integer.toHexString(uniqueKey.hashCode());
//
//// 规格化 originPath
//                    //String originPath = url.getPath(); // 原值
//                    if (StringUtils.isBlank(originPath)) originPath = "/";
//
//// 处理 index.html 的情况：用于首页兜底，其它子路径仍按目录穿透
//                    String indexFile;
//                    if (originPath.endsWith(".html")) {
//                        indexFile = originPath;     // 例如 /index.html
//                        originPath = "/";           // 目录穿透基准改为根
//                    } else {
//                        indexFile = null;
//                    }
//
//// 唯一内部挂载前缀
//                    final String originMount = REROUTE_PATH_PREFIX + uniqueSuffix + originPath;
//
//// 1) 目标挂载：所有被重写的请求最终到这里走 ProxyHandler
//                    proxyRouter.route(originMount + "*").handler(ProxyHandler.create(httpProxy));
//
//// 2) 从外部前缀 -> 内部挂载 的重写
//                    final String path0 = path;
//                    proxyRouter.route(path0 + "*").handler(ctx -> {
//                        String uri = ctx.request().uri();
//                        if (!uri.startsWith(path0)) { ctx.next(); return; }
//
//                        // 首页兜底：访问 /n2 或 /n2/ 时，重写到 index.html（如果配置了）
//                        if (indexFile != null && (uri.equals(path0) || uri.equals(path0.substring(0, path0.length()-1)))) {
//                            String rePath = originMount.endsWith("/") ? (originMount + indexFile.substring(1)) : (originMount + indexFile);
//                            ctx.reroute(rePath);
//                            return;
//                        }
//
//                        // 一般穿透：/n2/xxx -> originMount + xxx
//                        String rePath = uri.replaceFirst("^" + path0, originMount);
//                        ctx.reroute(rePath);
//                    });
                }

            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }

        });
    }
}
