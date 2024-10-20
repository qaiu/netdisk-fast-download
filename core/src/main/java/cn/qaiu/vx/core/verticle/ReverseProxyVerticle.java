package cn.qaiu.vx.core.verticle;

import cn.qaiu.vx.core.util.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.proxy.handler.ProxyHandler;
import io.vertx.httpproxy.HttpProxy;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * <p>反向代理服务</p>
 * <p>可以根据配置文件自动生成代理服务</p>
 * <p>可以配置多个服务, 配置文件见示例</p>
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


    @Override
    public void start(Promise<Void> startPromise) {
        CONFIG.onSuccess(this::handleProxyConfList);
//        createFileListener
        startPromise.complete();
    }

    /**
     * 获取主配置文件
     *
     * @param config proxy config
     */
    private void handleProxyConfList(JsonObject config) {
        serverName = config.getString("server-name");
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

        final HttpClient httpClient = VertxHolder.getVertxInstance().createHttpClient();
        Router proxyRouter = Router.router(vertx);

        // Add Server name header
        proxyRouter.route().handler(ctx -> {
            ctx.response().putHeader("Server", serverName);
            ctx.next();
        });

        // http api proxy
        if (proxyConf.containsKey("location")) {
            handleLocation(proxyConf.getJsonArray("location"), httpClient, proxyRouter);
        }

        // static server
        if (proxyConf.containsKey("static")) {
            handleStatic(proxyConf.getJsonObject("static"), proxyRouter);
        }

        // Send page404 page
        proxyRouter.errorHandler(404, ctx -> ctx.response().sendFile(proxyConf.getString("page404")));

        HttpServer server = getHttpsServer(proxyConf);
        server.requestHandler(proxyRouter);

        Integer port = proxyConf.getInteger("listen");
        LOGGER.info("proxy server start on {} port", port);
        server.listen(port);
    }

    private HttpServer getHttpsServer(JsonObject proxyConf) {
        HttpServerOptions httpServerOptions = new HttpServerOptions();
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
     * @param httpClient    客户端
     * @param proxyRouter   代理路由
     */
    private void handleLocation(JsonArray locationsConf, HttpClient httpClient, Router proxyRouter) {

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
                LOGGER.info("Conf(path, originPath, host, port) ----> {},{},{},{}", path, originPath, host, port);

                // 注意这里不能origin多个代理地址, 一个实例只能代理一个origin
                final HttpProxy httpProxy = HttpProxy.reverseProxy(httpClient);
                httpProxy.origin(port, host);
                if (StringUtils.isEmpty(path)) {
                    return;
                }

                // 代理目标路径为空 就像nginx一样路径穿透 (相对路径)
                if (StringUtils.isEmpty(originPath) || path.equals(originPath)) {
                    Route route = path.startsWith("~") ? proxyRouter.routeWithRegex(path.substring(1))
                            : proxyRouter.route(path);
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
                }

            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }

        });
    }
}
