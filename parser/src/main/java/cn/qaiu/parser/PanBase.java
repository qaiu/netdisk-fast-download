package cn.qaiu.parser;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.util.HttpResponseHelper;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.WebClientSession;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 解析器抽象类包含promise, HTTP Client, 默认失败方法等;
 * 新增网盘解析器需要继承该类. <br>
 * <h2>实现类命名规则: </h2>
 * <p>{网盘标识}Tool, 网盘标识不超过5个字符, 可以取网盘名称首字母缩写或拼音首字母, <br>
 * 音乐类型的解析以M开头, 例如网易云音乐Mne</p>
 */
public abstract class PanBase implements IPanTool, Closeable {
    protected Logger log = LoggerFactory.getLogger(this.getClass());

    protected Promise<String> promise = Promise.promise();

    /**
     * 共享的 WebClient 配置（设置超时避免连接无限期占用）
     */
    private static final WebClientOptions SHARED_OPTIONS = new WebClientOptions()
            .setConnectTimeout(10000)      // 连接超时 10 秒
            .setIdleTimeout(30)            // 空闲超时 30 秒
            .setIdleTimeoutUnit(java.util.concurrent.TimeUnit.SECONDS);

    /**
     * 共享的 WebClient 实例（线程安全，避免每请求创建导致资源泄漏）
     */
    private static final WebClient SHARED_CLIENT = WebClient.create(WebClientVertxInit.get(),
            new WebClientOptions(SHARED_OPTIONS));
    private static final WebClient SHARED_CLIENT_NO_REDIRECTS = WebClient.create(WebClientVertxInit.get(),
            new WebClientOptions(SHARED_OPTIONS).setFollowRedirects(false));
    private static final WebClient SHARED_CLIENT_DISABLE_UA = WebClient.create(WebClientVertxInit.get(),
            new WebClientOptions(SHARED_OPTIONS).setUserAgentEnabled(false));

    /**
     * Http client (默认使用共享实例，代理模式下使用独立实例)
     */
    protected WebClient client = SHARED_CLIENT;

    /**
     * Http client session (会话管理, 带cookie请求, 每实例独立)
     */
    protected WebClientSession clientSession = WebClientSession.create(client);

    /**
     * Http client 不自动跳转
     */
    protected WebClient clientNoRedirects = SHARED_CLIENT_NO_REDIRECTS;

    /**
     * Http client disable UserAgent
     */
    protected WebClient clientDisableUA = SHARED_CLIENT_DISABLE_UA;

    protected ShareLinkInfo shareLinkInfo;

    /**
     * 标记是否为代理模式（代理模式创建的 WebClient 需要手动关闭）
     */
    private boolean isProxyMode = false;

    /**
     * 代理模式下创建的独立 WebClient 实例（需要在 close 时释放）
     */
    private WebClient proxyClient = null;
    private WebClient proxyClientNoRedirects = null;
    

    /**
     * 子类重写此构造方法不需要添加额外逻辑
     * 如:
     * <blockquote><pre>
     *  public XxTool(String key, String pwd) {
     *      super(key, pwd);
     *  }
     * </pre></blockquote>
     *
     * @param shareLinkInfo 分享链接信息
     */
    public PanBase(ShareLinkInfo shareLinkInfo) {
        this.shareLinkInfo = shareLinkInfo;
        if (shareLinkInfo.getOtherParam().containsKey("proxy")) {
            this.isProxyMode = true;
            JsonObject proxy = (JsonObject) shareLinkInfo.getOtherParam().get("proxy");
            ProxyOptions proxyOptions = new ProxyOptions()
                    .setType(ProxyType.valueOf(proxy.getString("type").toUpperCase()))
                    .setHost(proxy.getString("host"))
                    .setPort(proxy.getInteger("port"));
            if (StringUtils.isNotEmpty(proxy.getString("username"))) {
                proxyOptions.setUsername(proxy.getString("username"));
            }
            if (StringUtils.isNotEmpty(proxy.getString("password"))) {
                proxyOptions.setPassword(proxy.getString("password"));
            }
            // 代理模式下创建独立的 WebClient 实例（应用超时配置）
            this.proxyClient = WebClient.create(WebClientVertxInit.get(),
                    new WebClientOptions(SHARED_OPTIONS)
                            .setUserAgentEnabled(false)
                            .setProxyOptions(proxyOptions));
            this.proxyClientNoRedirects = WebClient.create(WebClientVertxInit.get(),
                    new WebClientOptions(SHARED_OPTIONS).setFollowRedirects(false)
                            .setUserAgentEnabled(false)
                            .setProxyOptions(proxyOptions));

            this.client = proxyClient;
            this.clientSession = WebClientSession.create(client);
            this.clientNoRedirects = proxyClientNoRedirects;
        }
    }

    protected PanBase() {
    }

    protected String baseMsg() {
        if (shareLinkInfo.getShareUrl() != null) {
            return shareLinkInfo.getPanName() + "-" + shareLinkInfo.getType() + ": url=" + shareLinkInfo.getShareUrl();
        }
        return shareLinkInfo.getPanName() + "-" + shareLinkInfo.getType() + ": key=" + shareLinkInfo.getShareKey() +
                ";pwd=" + shareLinkInfo.getSharePassword();
    }


    /**
     * 失败时生成异常消息
     *
     * @param t        异常实例
     * @param errorMsg 提示消息
     * @param args     log参数变量
     */
    protected void fail(Throwable t, String errorMsg, Object... args) {
        try {
            // 判断是否已经完成
            if (promise.future().isComplete()) {
                log.warn("Promise 已经完成, 无法再次失败: {}, {}", errorMsg, promise.future().cause());
                return;
            }
            String s = String.format(errorMsg.replaceAll("\\{}", "%s"), args);
            // 只记录异常消息和类型，不调用 fillInStackTrace 避免产生巨大栈信息
            log.error("解析异常: {} - {}: {}", s, t.getClass().getSimpleName(), t.getMessage());
            // 只传递异常消息，不传递完整异常对象，减少内存占用
            String failMsg = baseMsg() + ": 解析异常: " + s + " -> " + t.getClass().getSimpleName() + ": " + t.getMessage();
            promise.fail(failMsg);
        } catch (Exception e) {
            log.error("ErrorMsg format fail. The parameter has been discarded", e);
            log.error("解析异常: {} - {}: {}", errorMsg, t.getClass().getSimpleName(), t.getMessage());
            if (promise.future().isComplete()) {
                log.warn("ErrorMsg format. Promise 已经完成, 无法再次失败: {}", errorMsg);
                return;
            }
            promise.fail(baseMsg() + ": 解析异常: " + errorMsg + " -> " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /**
     * 失败时生成异常消息
     *
     * @param errorMsg 提示消息
     * @param args     log参数变量
     */
    protected void fail(String errorMsg, Object... args) {
        try {
            // 判断是否已经完成
            if (promise.future().isComplete()) {
                log.warn("Promise 已经完成, 无法再次失败: {}, {}", errorMsg, promise.future().cause());
                return;
            }
            String s = String.format(errorMsg.replaceAll("\\{}", "%s"), args);
            promise.fail(baseMsg() + " - 解析异常: " + s);
        } catch (Exception e) {
            if (promise.future().isComplete()) {
                log.warn("ErrorMsg format. Promise 已经完成, 无法再次失败: {}", errorMsg);
                return;
            }
            log.error("ErrorMsg format fail. The parameter has been discarded", e);
            promise.fail(baseMsg() + " - 解析异常: " + errorMsg);
        }
    }

    protected void fail() {
        fail("");
    }

    /**
     * 生成失败Future的处理器
     *
     * @param errorMsg 提示消息
     * @return Handler
     */
    protected Handler<Throwable> handleFail(String errorMsg) {
        return t -> fail(baseMsg() + " - 请求异常 {}: -> {}", errorMsg, t.getClass().getSimpleName() + ": " + t.getMessage());
    }

    protected Handler<Throwable> handleFail() {
        return handleFail("");
    }


    /**
     * bodyAsJsonObject的封装, 会自动处理异常
     *
     * @param res HttpResponse
     * @return JsonObject
     */
    protected JsonObject asJson(HttpResponse<?> res) {
        // 检查响应头中的Content-Encoding是否为gzip
        String contentEncoding = res.getHeader("Content-Encoding");
        try {
            if ("gzip".equalsIgnoreCase(contentEncoding)) {
                // 如果是gzip压缩的响应体，解压（只解压一次，缓存结果）
                String decompressed = decompressGzip((Buffer) res.body());
                return new JsonObject(decompressed);
            } else {
                return res.bodyAsJsonObject();
            }

        } catch (Exception e) {
            if ("gzip".equalsIgnoreCase(contentEncoding)) {
                // gzip解压失败，记录错误
                log.error("响应gzip解压或JSON解析失败: {}", e.getMessage());
                fail("响应gzip解压或JSON解析失败: {}", e.getMessage());
            } else {
                log.error("解析失败: json格式异常: {}", res.bodyAsString());
                fail("解析失败: json格式异常: {}", res.bodyAsString());
            }
            return JsonObject.of();
        }
    }

    /**
     * body To text的封装, 会自动处理异常, 会自动解压gzip
     * @param res HttpResponse
     * @return String
     */
    protected String asText(HttpResponse<?> res) {
        return HttpResponseHelper.asText(res);
    }

    protected void complete(String url) {
        // 自动将直链存储到 otherParam 中，以便客户端链接生成器使用
        shareLinkInfo.getOtherParam().put("downloadUrl", url);
        promise.complete(url);
    }

    /**
     * 完成解析并存储下载元数据
     * 
     * @param url 下载直链
     * @param headers 请求头Map
     */
    protected void completeWithMeta(String url, Map<String, String> headers) {
        shareLinkInfo.getOtherParam().put("downloadUrl", url);
        if (headers != null && !headers.isEmpty()) {
            shareLinkInfo.getOtherParam().put("downloadHeaders", headers);
        }
        promise.complete(url);
    }

    /**
     * 完成解析并存储下载元数据（MultiMap版本）
     * 
     * @param url 下载直链
     * @param headers MultiMap格式的请求头
     */
    protected void completeWithMeta(String url, MultiMap headers) {
        Map<String, String> headerMap = new HashMap<>();
        if (headers != null) {
            headers.forEach(entry -> headerMap.put(entry.getKey(), entry.getValue()));
        }
        completeWithMeta(url, headerMap);
    }

    protected Future<String> future() {
        return promise.future();
    }

    /**
     * 调用下一个解析器, 通用域名解析
     */
    protected void nextParser() {
        Iterator<PanDomainTemplate> iterator = Arrays.asList(PanDomainTemplate.values()).iterator();
        while (iterator.hasNext()) {
            if (iterator.next().name().equalsIgnoreCase(shareLinkInfo.getType())) {
                if (iterator.hasNext()) {
                    PanDomainTemplate next = iterator.next();
                    log.debug("规则不匹配, 处理解析器转发: {} -> {}", shareLinkInfo.getPanName(), next.getDisplayName());
                    try {
                        IPanTool nextTool = ParserCreate.fromType(next.name())
                                .fromAnyShareUrl(shareLinkInfo.getShareUrl())
                                .createTool();
                        IPanTool.closeAfter(nextTool, nextTool::parse)
                                .onComplete(promise);
                    } catch (Exception e) {
                        fail(e, "转发到下一个解析器失败: {}", next.getDisplayName());
                    }
                } else {
                    fail("error: 没有下一个解析处理器");
                }
            }
        }
    }


    /**
     * 解压gzip数据
     * @param compressedData compressedData
     * @return String
     * @throws IOException IOException
     */
    private String decompressGzip(Buffer compressedData) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData.getBytes());
             GZIPInputStream gzis = new GZIPInputStream(bais);
             InputStreamReader isr = new InputStreamReader(gzis, StandardCharsets.UTF_8);
             StringWriter writer = new StringWriter()) {

            char[] buffer = new char[4096];
            int n;
            while ((n = isr.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }
            return writer.toString();
        }
    }

    protected String getDomainName(){
        return shareLinkInfo.getOtherParam().getOrDefault("domainName", "").toString();
    }

    @Override
    public ShareLinkInfo getShareLinkInfo() {
        return shareLinkInfo;
    }

    /**
     * 关闭代理模式下创建的 WebClient 资源
     * 非代理模式使用共享实例，不需要关闭
     */
    @Override
    public void close() {
        if (isProxyMode) {
            try {
                if (proxyClient != null) {
                    proxyClient.close();
                }
            } catch (Exception e) {
                log.warn("关闭代理 WebClient 失败: {}", e.getMessage());
            }
            try {
                if (proxyClientNoRedirects != null) {
                    proxyClientNoRedirects.close();
                }
            } catch (Exception e) {
                log.warn("关闭代理 WebClientNoRedirects 失败: {}", e.getMessage());
            }
        }
    }
}
