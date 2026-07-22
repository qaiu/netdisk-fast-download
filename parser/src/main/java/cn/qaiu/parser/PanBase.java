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
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
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

    private static final int MAX_COMPRESSED_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_RESPONSE_CHARS = 16 * 1024 * 1024;
    private static final int MAX_ERROR_BODY_CHARS = 4096;

    /**
     * 共享的 WebClient 配置（设置超时避免连接无限期占用）
     */
    private static final WebClientOptions SHARED_OPTIONS = new WebClientOptions()
            .setConnectTimeout(10000)      // 连接超时 10 秒
            .setIdleTimeout(30)            // 空闲超时 30 秒
            .setIdleTimeoutUnit(java.util.concurrent.TimeUnit.SECONDS);

    private static final Object SHARED_CLIENT_LOCK = new Object();

    /**
     * 共享的 WebClient 实例（线程安全，避免每请求创建导致资源泄漏）
     */
    private static volatile WebClient sharedClient;
    private static volatile WebClient sharedClientNoRedirects;
    private static volatile WebClient sharedClientDisableUA;
    private static volatile boolean sharedClientsShutdown = false;

    /**
     * Http client (默认使用共享实例，代理模式下使用独立实例)
     */
    protected WebClient client = sharedClient();

    /**
     * Http client session (会话管理, 带cookie请求, 每实例独立)
     */
    protected WebClientSession clientSession = WebClientSession.create(client);

    /**
     * Http client 不自动跳转
     */
    protected WebClient clientNoRedirects = sharedClientNoRedirects();

    /**
     * Http client disable UserAgent
     */
    protected WebClient clientDisableUA = sharedClientDisableUA();

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

    private static WebClient sharedClient() {
        synchronized (SHARED_CLIENT_LOCK) {
            if (sharedClientsShutdown) {
                throw new IllegalStateException("共享 WebClient 已关闭");
            }
            if (sharedClient == null) {
                sharedClient = WebClient.create(WebClientVertxInit.get(), new WebClientOptions(SHARED_OPTIONS));
            }
            return sharedClient;
        }
    }

    private static WebClient sharedClientNoRedirects() {
        synchronized (SHARED_CLIENT_LOCK) {
            if (sharedClientsShutdown) {
                throw new IllegalStateException("共享 WebClient 已关闭");
            }
            if (sharedClientNoRedirects == null) {
                sharedClientNoRedirects = WebClient.create(WebClientVertxInit.get(),
                        new WebClientOptions(SHARED_OPTIONS).setFollowRedirects(false));
            }
            return sharedClientNoRedirects;
        }
    }

    private static WebClient sharedClientDisableUA() {
        synchronized (SHARED_CLIENT_LOCK) {
            if (sharedClientsShutdown) {
                throw new IllegalStateException("共享 WebClient 已关闭");
            }
            if (sharedClientDisableUA == null) {
                sharedClientDisableUA = WebClient.create(WebClientVertxInit.get(),
                        new WebClientOptions(SHARED_OPTIONS).setUserAgentEnabled(false));
            }
            return sharedClientDisableUA;
        }
    }

    public static void shutdownSharedClients() {
        synchronized (SHARED_CLIENT_LOCK) {
            sharedClientsShutdown = true;
            closeSharedClient(sharedClient, "shared WebClient");
            closeSharedClient(sharedClientNoRedirects, "shared WebClientNoRedirects");
            closeSharedClient(sharedClientDisableUA, "shared WebClientDisableUA");
            sharedClient = null;
            sharedClientNoRedirects = null;
            sharedClientDisableUA = null;
        }
    }

    private static void closeSharedClient(WebClient client, String name) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            LoggerFactory.getLogger(PanBase.class).warn("关闭 {} 失败: {}", name, e.getMessage());
        }
    }

    /**
     * SSRF 防护: 校验通用自定义域名解析器 (CE/Ce4/Kd/Other 等) 即将请求的目标主机,
     * 拒绝解析到回环/内网/链路本地/组播等非公网地址的域名, 阻止攻击者通过可控 DNS
     * 记录 (或直接填写内网域名) 让服务端向内网/云元数据接口发起请求。
     * <p>
     * 必须在子类 parse() 中构造出 baseUrl/发起任何 clientSession 请求之前调用。
     *
     * @param url 从 shareLinkInfo.getShareUrl() 解析出的 URL
     * @throws IOException 当主机无法解析或解析结果落入禁止的地址段时抛出
     */
    protected static void assertPublicHost(URL url) throws IOException {
        String host = url.getHost();
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IOException("无法解析目标主机: " + host, e);
        }
        if (addresses.length == 0) {
            throw new IOException("无法解析目标主机: " + host);
        }
        for (InetAddress addr : addresses) {
            if (isDisallowedAddress(addr)) {
                throw new IOException("目标地址不允许访问(内网/回环/链路本地/组播): "
                        + host + " -> " + addr.getHostAddress());
            }
        }
    }

    /**
     * 判断地址是否落入禁止访问的范围: 0.0.0.0/8, 10/8, 127/8, 169.254/16(含云元数据
     * 169.254.169.254), 172.16/12, 192.168/16, 100.64/10(CGNAT), 224/4~255/4(组播/保留),
     * 以及对应的 IPv6 回环/链路本地/唯一本地地址(fc00::/7)/组播地址; IPv4-映射的 IPv6
     * 地址(::ffff:a.b.c.d) 会先还原为 IPv4 再判断，避免绕过。
     */
    private static boolean isDisallowedAddress(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        if (bytes.length == 16 && isIPv4Mapped(bytes)) {
            byte[] v4 = new byte[4];
            System.arraycopy(bytes, 12, v4, 0, 4);
            bytes = v4;
        }
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            if (b0 == 0) return true;                          // 0.0.0.0/8
            if (b0 == 10) return true;                         // 10.0.0.0/8
            if (b0 == 127) return true;                        // 127.0.0.0/8 loopback
            if (b0 == 169 && b1 == 254) return true;            // 169.254.0.0/16 (含云元数据 169.254.169.254)
            if (b0 == 172 && b1 >= 16 && b1 <= 31) return true; // 172.16.0.0/12
            if (b0 == 192 && b1 == 168) return true;            // 192.168.0.0/16
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return true;// 100.64.0.0/10 CGNAT
            return b0 >= 224;                                   // 224.0.0.0/4 组播 + 240.0.0.0/4 保留
        }
        // IPv6
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC; // fc00::/7 unique local
    }

    private static boolean isIPv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) return false;
        }
        return (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
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
                String bodyPreview = responseBodyPreview(res);
                log.error("解析失败: json格式异常: {}", bodyPreview);
                fail("解析失败: json格式异常: {}", bodyPreview);
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
        if (compressedData == null) {
            return "";
        }
        if (compressedData.length() > MAX_COMPRESSED_RESPONSE_BYTES) {
            throw new IOException("gzip响应体过大: " + compressedData.length() + " bytes");
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData.getBytes());
             GZIPInputStream gzis = new GZIPInputStream(bais);
             InputStreamReader isr = new InputStreamReader(gzis, StandardCharsets.UTF_8);
             StringWriter writer = new StringWriter()) {

            char[] buffer = new char[4096];
            int n;
            while ((n = isr.read(buffer)) != -1) {
                writeLimited(writer, buffer, n);
            }
            return writer.toString();
        }
    }

    private void writeLimited(StringWriter writer, char[] buffer, int len) throws IOException {
        if (writer.getBuffer().length() + len > MAX_DECOMPRESSED_RESPONSE_CHARS) {
            throw new IOException("gzip解压后响应体过大");
        }
        writer.write(buffer, 0, len);
    }

    private String responseBodyPreview(HttpResponse<?> res) {
        if (res == null || res.body() == null) {
            return "";
        }
        try {
            if (res.body() instanceof Buffer body) {
                int length = Math.min(body.length(), MAX_ERROR_BODY_CHARS);
                String preview = new String(body.getBytes(0, length), StandardCharsets.UTF_8);
                return body.length() > length ? preview + "...(truncated " + body.length() + " bytes)" : preview;
            }
            String text = res.bodyAsString();
            if (text == null || text.length() <= MAX_ERROR_BODY_CHARS) {
                return text;
            }
            return text.substring(0, MAX_ERROR_BODY_CHARS) + "...(truncated " + text.length() + " chars)";
        } catch (Exception e) {
            return "<body preview failed: " + e.getMessage() + ">";
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
