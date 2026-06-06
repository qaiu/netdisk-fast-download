package cn.qaiu.parser.customjs;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.util.HttpResponseHelper;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.ext.web.client.HttpResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * JavaScript HTTP客户端封装
 * 为JavaScript提供同步API风格的HTTP请求功能
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/10/17
 */
public class JsHttpClient {

    private static final Logger log = LoggerFactory.getLogger(JsHttpClient.class);
    private static final int MAX_RESPONSE_BODY_BYTES = 8 * 1024 * 1024;
    private static final int MAX_REQUEST_BODY_BYTES = 8 * 1024 * 1024;
    private static final int MAX_HEADER_COUNT = 64;
    private static final int MAX_HEADER_VALUE_LENGTH = 4096;
    private static final int MAX_TIMEOUT_SECONDS = 120;
    private static final int MAX_REDIRECTS = 5;
    private static final String DEFAULT_ACCEPT_ENCODING = "gzip, deflate, br";

    // 共享 HttpClient 实例（非代理模式），避免每次请求创建新连接池。
    private static final HttpClient SHARED_CLIENT = WebClientVertxInit.get().createHttpClient(
            new HttpClientOptions()
                    .setConnectTimeout(10000)
                    .setIdleTimeout(30)
                    .setIdleTimeoutUnit(TimeUnit.SECONDS)
                    .setMaxPoolSize(64));

    /**
     * 关闭共享 HttpClient（应用关闭时调用）
     */
    public static void shutdownSharedClient() {
        if (SHARED_CLIENT != null) {
            SHARED_CLIENT.close();
        }
    }

    private final HttpClient client;
    private final boolean ownClient; // 标记是否为自建 client（需要 close）
    private MultiMap headers;
    private int timeoutSeconds = 30; // 默认超时时间30秒
    
    // SSRF防护：内网IP正则表达式
    private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
        "^(127\\..*|10\\..*|172\\.(1[6-9]|2[0-9]|3[01])\\..*|192\\.168\\..*|169\\.254\\..*|::1|[fF][cCdD].*)"
    );
    
    // SSRF防护：危险域名黑名单
    private static final String[] DANGEROUS_HOSTS = {
        "localhost",
        "169.254.169.254", // AWS/阿里云等云服务元数据API
        "metadata.google.internal", // GCP元数据
        "100.100.100.200" // 阿里云元数据
    };
    
    public JsHttpClient() {
        this.client = SHARED_CLIENT;
        this.ownClient = false;
        this.headers = MultiMap.caseInsensitiveMultiMap();
        // 设置默认的Accept-Encoding头以支持压缩响应
        this.headers.set("Accept-Encoding", DEFAULT_ACCEPT_ENCODING);
        // 设置默认的User-Agent头
        this.headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0");
        // 设置默认的Accept-Language头
        this.headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6");
    }
    
    /**
     * 带代理配置的构造函数
     * @param proxyConfig 代理配置JsonObject，包含type、host、port、username、password
     */
    public JsHttpClient(JsonObject proxyConfig) {
        if (proxyConfig != null && proxyConfig.containsKey("type")) {
            ProxyOptions proxyOptions = new ProxyOptions()
                    .setType(ProxyType.valueOf(proxyConfig.getString("type").toUpperCase()))
                    .setHost(proxyConfig.getString("host"))
                    .setPort(proxyConfig.getInteger("port"));

            if (StringUtils.isNotEmpty(proxyConfig.getString("username"))) {
                proxyOptions.setUsername(proxyConfig.getString("username"));
            }
            if (StringUtils.isNotEmpty(proxyConfig.getString("password"))) {
                proxyOptions.setPassword(proxyConfig.getString("password"));
            }

            this.client = WebClientVertxInit.get().createHttpClient(
                    new HttpClientOptions()
                            .setConnectTimeout(10000)
                            .setIdleTimeout(30)
                            .setIdleTimeoutUnit(TimeUnit.SECONDS)
                            .setMaxPoolSize(16)
                            .setProxyOptions(proxyOptions));
            this.ownClient = true;
        } else {
            this.client = SHARED_CLIENT;
            this.ownClient = false;
        }
        this.headers = MultiMap.caseInsensitiveMultiMap();
        // 设置默认的Accept-Encoding头以支持压缩响应
        this.headers.set("Accept-Encoding", DEFAULT_ACCEPT_ENCODING);
        // 设置默认的User-Agent头
        this.headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0");
        // 设置默认的Accept-Language头
        this.headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6");
    }
    
    /**
     * 验证URL安全性（SSRF防护）- 仅拦截明显的内网攻击
     * @param url 待验证的URL
     * @throws SecurityException 如果URL不安全
     */
    private void validateUrlSecurity(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            
            if (host == null) {
                log.debug("URL没有host信息: {}", url);
                return; // 允许继续，可能是相对路径
            }
            
            String lowerHost = host.toLowerCase();
            
            // 1. 检查明确的危险域名（云服务元数据API等）
            for (String dangerous : DANGEROUS_HOSTS) {
                if (lowerHost.equals(dangerous)) {
                    log.warn("🔒 安全拦截: 尝试访问云服务元数据API - {}", host);
                    throw new SecurityException("🔒 安全拦截: 禁止访问云服务元数据API");
                }
            }
            
            // 2. 如果host是IP地址格式，检查是否为内网IP
            if (isIpAddress(lowerHost)) {
                if (PRIVATE_IP_PATTERN.matcher(lowerHost).find()) {
                    log.warn("🔒 安全拦截: 尝试访问内网IP - {}", host);
                    throw new SecurityException("🔒 安全拦截: 禁止访问内网IP地址");
                }
            }
            
            // 3. 对于域名，尝试解析IP（但不因解析失败而拦截）
            if (!isIpAddress(lowerHost)) {
                try {
                    InetAddress addr = InetAddress.getByName(host);
                    String ip = addr.getHostAddress();
                    
                    // 只拦截解析到内网IP的域名
                    if (PRIVATE_IP_PATTERN.matcher(ip).find()) {
                        log.warn("🔒 安全拦截: 域名解析到内网IP - {} -> {}", host, ip);
                        throw new SecurityException("🔒 安全拦截: 该域名指向内网地址");
                    }
                } catch (UnknownHostException e) {
                    // DNS解析失败，允许继续（可能是外网域名暂时无法解析）
                    log.debug("DNS解析失败，允许继续: {}", host);
                }
            }
            
            log.debug("URL安全检查通过: {}", url);
            
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            // 其他异常不拦截，只记录日志
            log.debug("URL验证异常，允许继续: {}", url, e);
        }
    }
    
    /**
     * 判断字符串是否为IP地址格式
     */
    private boolean isIpAddress(String host) {
        // 简单判断是否为IPv4地址格式
        return host.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$") || host.contains(":");
    }
    
    /**
     * 发起GET请求
     * @param url 请求URL
     * @return HTTP响应
     */
    public JsHttpResponse get(String url) {
        validateUrlSecurity(url);
        return executeRequest(HttpMethod.GET, url, null, false);
    }
    
    /**
     * 发起GET请求并跟随重定向
     * @param url 请求URL
     * @return HTTP响应
     */
    public JsHttpResponse getWithRedirect(String url) {
        String currentUrl = url;
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            validateUrlSecurity(currentUrl);
            JsHttpResponse response = executeRequest(HttpMethod.GET, currentUrl, null, false);
            if (!isRedirectStatus(response.statusCode())) {
                return response;
            }

            if (redirectCount == MAX_REDIRECTS) {
                throw new RuntimeException("重定向次数超过限制: " + MAX_REDIRECTS);
            }

            String location = response.header(HttpHeaders.LOCATION.toString());
            if (StringUtils.isBlank(location)) {
                throw new RuntimeException("重定向响应缺少Location头");
            }
            currentUrl = resolveRedirectUrl(currentUrl, location);
        }
        throw new RuntimeException("重定向处理失败");
    }
    
    /**
     * 发起GET请求但不跟随重定向（用于获取Location头）
     * @param url 请求URL
     * @return HTTP响应
     */
    public JsHttpResponse getNoRedirect(String url) {
        validateUrlSecurity(url);
        return executeRequest(HttpMethod.GET, url, null, false);
    }
    
    /**
     * 发起POST请求
     * @param url 请求URL
     * @param data 请求数据
     * @return HTTP响应
     */
    public JsHttpResponse post(String url, Object data) {
        validateUrlSecurity(url);
        return executeRequest(HttpMethod.POST, url, bodyFromData(data), false);
    }
    
    /**
     * 发起PUT请求
     * @param url 请求URL
     * @param data 请求数据
     * @return HTTP响应
     */
    public JsHttpResponse put(String url, Object data) {
        validateUrlSecurity(url);
        return executeRequest(HttpMethod.PUT, url, bodyFromData(data), false);
    }
    
    /**
     * 发起DELETE请求
     * @param url 请求URL
     * @return HTTP响应
     */
    public JsHttpResponse delete(String url) {
        validateUrlSecurity(url);
        return executeRequest(HttpMethod.DELETE, url, null, false);
    }
    
    /**
     * 发起PATCH请求
     * @param url 请求URL
     * @param data 请求数据
     * @return HTTP响应
     */
    public JsHttpResponse patch(String url, Object data) {
        validateUrlSecurity(url);
        return executeRequest(HttpMethod.PATCH, url, bodyFromData(data), false);
    }
    
    /**
     * 设置请求头
     * @param name 头名称
     * @param value 头值
     * @return 当前客户端实例（支持链式调用）
     */
    public JsHttpClient putHeader(String name, String value) {
        if (name != null && value != null) {
            if (headers.size() >= MAX_HEADER_COUNT && !headers.contains(name)) {
                throw new IllegalArgumentException("请求头数量超过限制");
            }
            if (value.length() > MAX_HEADER_VALUE_LENGTH) {
                throw new IllegalArgumentException("请求头过长: " + name);
            }
            headers.set(name, value);
        }
        return this;
    }
    
    /**
     * 批量设置请求头
     * @param headersMap 请求头Map
     * @return 当前客户端实例（支持链式调用）
     */
    public JsHttpClient putHeaders(Map<String, String> headersMap) {
        if (headersMap != null) {
            for (Map.Entry<String, String> entry : headersMap.entrySet()) {
                putHeader(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }
    
    /**
     * 删除指定请求头
     * @param name 头名称
     * @return 当前客户端实例（支持链式调用）
     */
    public JsHttpClient removeHeader(String name) {
        if (name != null) {
            headers.remove(name);
        }
        return this;
    }
    
    /**
     * 清空所有请求头（保留默认头）
     * @return 当前客户端实例（支持链式调用）
     */
    public JsHttpClient clearHeaders() {
        headers.clear();
        // 重新设置默认头
        headers.set("Accept-Encoding", DEFAULT_ACCEPT_ENCODING);
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0");
        headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6");
        return this;
    }
    
    /**
     * 获取所有请求头
     * @return 请求头Map
     */
    public Map<String, String> getHeaders() {
        Map<String, String> result = new HashMap<>();
        for (String name : headers.names()) {
            result.put(name, headers.get(name));
        }
        return result;
    }
    
    /**
     * 设置请求超时时间
     * @param seconds 超时时间（秒）
     * @return 当前客户端实例（支持链式调用）
     */
    public JsHttpClient setTimeout(int seconds) {
        if (seconds > 0) {
            this.timeoutSeconds = Math.min(seconds, MAX_TIMEOUT_SECONDS);
        }
        return this;
    }
    
    /**
     * URL编码
     * @param str 要编码的字符串
     * @return 编码后的字符串
     */
    public static String urlEncode(String str) {
        if (str == null) {
            return null;
        }
        try {
            return URLEncoder.encode(str, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            log.error("URL编码失败", e);
            return str;
        }
    }
    
    /**
     * URL解码
     * @param str 要解码的字符串
     * @return 解码后的字符串
     */
    public static String urlDecode(String str) {
        if (str == null) {
            return null;
        }
        try {
            return URLDecoder.decode(str, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            log.error("URL解码失败", e);
            return str;
        }
    }
    
    /**
     * 发送表单数据（简单键值对）
     * @param data 表单数据
     * @return HTTP响应
     */
    public JsHttpResponse sendForm(Map<String, String> data) {
        throw new IllegalArgumentException("sendForm(data) 缺少请求URL，请使用 post(url, data)");
    }

    public JsHttpResponse sendForm(String url, Map<String, String> data) {
        validateUrlSecurity(url);
        return executeRequest(HttpMethod.POST, url, formBody(data), false);
    }
    
    /**
     * 发送multipart表单数据（仅支持文本字段）
     * @param url 请求URL
     * @param data 表单数据，支持：
     *             - Map<String, String>: 文本字段
     *             - Map<String, Object>: 混合字段，Object可以是String、byte[]或Buffer
     * @return HTTP响应
     */
    public JsHttpResponse sendMultipartForm(String url, Map<String, Object> data) {
        validateUrlSecurity(url);
        return executeRequest(HttpMethod.POST, url, multipartBody(data), false);
    }
    
    /**
     * 发送JSON数据
     * @param data JSON数据
     * @return HTTP响应
     */
    public JsHttpResponse sendJson(Object data) {
        throw new IllegalArgumentException("sendJson(data) 缺少请求URL，请使用 post(url, data)");
    }

    public JsHttpResponse sendJson(String url, Object data) {
        validateUrlSecurity(url);
        return executeRequest(HttpMethod.POST, url, jsonBody(data), false);
    }
    
    /**
     * 执行HTTP请求（同步）
     */
    private JsHttpResponse executeRequest(HttpMethod method, String url, RequestBody requestBody, boolean followRedirects) {
        AtomicReference<HttpClientRequest> requestRef = new AtomicReference<>();
        try {
            Promise<JsHttpResponse> promise = Promise.promise();

            RequestOptions options = new RequestOptions()
                    .setMethod(method)
                    .setAbsoluteURI(url)
                    .setFollowRedirects(followRedirects)
                    .setTimeout(TimeUnit.SECONDS.toMillis(timeoutSeconds))
                    .setHeaders(MultiMap.caseInsensitiveMultiMap().setAll(headers));

            client.request(options).onComplete(ar -> {
                if (ar.failed()) {
                    promise.tryFail(ar.cause());
                    return;
                }

                HttpClientRequest request = ar.result();
                requestRef.set(request);
                request.exceptionHandler(promise::tryFail);
                request.response().onComplete(responseAr -> {
                    if (responseAr.succeeded()) {
                        collectResponse(request, responseAr.result(), promise);
                    } else {
                        promise.tryFail(responseAr.cause());
                    }
                });

                if (requestBody == null || requestBody.body() == null) {
                    request.end().onFailure(promise::tryFail);
                } else {
                    request.headers().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(requestBody.body().length()));
                    if (StringUtils.isNotEmpty(requestBody.contentType())) {
                        request.headers().set(HttpHeaders.CONTENT_TYPE, requestBody.contentType());
                    }
                    request.end(requestBody.body()).onFailure(promise::tryFail);
                }
            });

            return promise.future().toCompletionStage()
                    .toCompletableFuture()
                    .get(timeoutSeconds, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            // RequestOptions timeout 通常会先触发；这里再兜底，避免等待线程返回后请求还在后台下载。
            String errorMsg = "HTTP请求超时（" + timeoutSeconds + "秒）";
            HttpClientRequest request = requestRef.get();
            if (request != null) {
                request.reset();
            }
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.trim().isEmpty()) {
                errorMsg = e.getClass().getSimpleName();
                if (e.getCause() != null && e.getCause().getMessage() != null) {
                    errorMsg += ": " + e.getCause().getMessage();
                }
            }
            log.error("HTTP请求执行失败: " + errorMsg, e);
            throw new RuntimeException("HTTP请求执行失败: " + errorMsg, e);
        }
    }

    private static boolean isRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    private String resolveRedirectUrl(String currentUrl, String location) {
        try {
            URI redirectUri = new URI(currentUrl).resolve(location.trim());
            String scheme = redirectUri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new SecurityException("🔒 安全拦截: 重定向协议不被允许");
            }
            String redirectUrl = redirectUri.toString();
            validateUrlSecurity(redirectUrl);
            return redirectUrl;
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析重定向地址失败: " + e.getMessage(), e);
        }
    }

    private void collectResponse(HttpClientRequest request, HttpClientResponse response, Promise<JsHttpResponse> promise) {
        Buffer body = Buffer.buffer();
        AtomicBoolean done = new AtomicBoolean(false);

        String contentLengthHeader = response.getHeader(HttpHeaders.CONTENT_LENGTH.toString());
        if (StringUtils.isNumeric(contentLengthHeader)) {
            long contentLength = Long.parseLong(contentLengthHeader);
            if (contentLength > MAX_RESPONSE_BODY_BYTES) {
                done.set(true);
                request.reset();
                promise.tryFail("响应体过大: " + contentLength + " bytes");
                return;
            }
        }

        response.exceptionHandler(e -> {
            if (done.compareAndSet(false, true)) {
                promise.tryFail(e);
            }
        });
        response.handler(chunk -> {
            if (done.get()) {
                return;
            }
            if (body.length() + chunk.length() > MAX_RESPONSE_BODY_BYTES) {
                if (done.compareAndSet(false, true)) {
                    request.reset();
                    promise.tryFail("响应体过大: " + (body.length() + chunk.length()) + " bytes");
                }
                return;
            }
            body.appendBuffer(chunk);
        });
        response.endHandler(v -> {
            if (done.compareAndSet(false, true)) {
                promise.tryComplete(new JsHttpResponse(
                        response.statusCode(),
                        MultiMap.caseInsensitiveMultiMap().setAll(response.headers()),
                        body,
                        response.statusMessage(),
                        null
                ));
            }
        });
        response.resume();
    }

    private RequestBody bodyFromData(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof String str) {
            return plainTextBody(str);
        }
        if (data instanceof Buffer buffer) {
            return limitedBody(buffer, null);
        }
        if (data instanceof byte[] bytes) {
            return limitedBody(Buffer.buffer(bytes), null);
        }
        if (data instanceof Map<?, ?> map) {
            Map<String, String> formMap = new HashMap<>();
            map.forEach((key, value) -> {
                if (key != null && value != null) {
                    formMap.put(String.valueOf(key), String.valueOf(value));
                }
            });
            return formBody(formMap);
        }
        return jsonBody(data);
    }

    private RequestBody plainTextBody(String data) {
        return limitedBody(Buffer.buffer(data, StandardCharsets.UTF_8.name()), null);
    }

    private RequestBody jsonBody(Object data) {
        Buffer body = data == null ? Buffer.buffer() : Buffer.buffer(Json.encode(data), StandardCharsets.UTF_8.name());
        return limitedBody(body, "application/json; charset=utf-8");
    }

    private RequestBody formBody(Map<String, String> data) {
        StringBuilder encoded = new StringBuilder();
        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (encoded.length() > 0) {
                    encoded.append('&');
                }
                encoded.append(urlEncode(entry.getKey()));
                encoded.append('=');
                encoded.append(urlEncode(entry.getValue()));
            }
        }
        return limitedBody(Buffer.buffer(encoded.toString(), StandardCharsets.UTF_8.name()),
                "application/x-www-form-urlencoded; charset=utf-8");
    }

    private RequestBody multipartBody(Map<String, Object> data) {
        String boundary = "----NetdiskJsHttpClientBoundary" + UUID.randomUUID().toString().replace("-", "");
        Buffer body = Buffer.buffer();
        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key == null || value == null) {
                    continue;
                }
                appendAscii(body, "--" + boundary + "\r\n");
                if (value instanceof byte[] bytes) {
                    appendAscii(body, "Content-Disposition: form-data; name=\"" + escapeMultipart(key)
                            + "\"; filename=\"" + escapeMultipart(key) + "\"\r\n");
                    appendAscii(body, "Content-Type: application/octet-stream\r\n\r\n");
                    body.appendBytes(bytes);
                    appendAscii(body, "\r\n");
                } else if (value instanceof Buffer buffer) {
                    appendAscii(body, "Content-Disposition: form-data; name=\"" + escapeMultipart(key)
                            + "\"; filename=\"" + escapeMultipart(key) + "\"\r\n");
                    appendAscii(body, "Content-Type: application/octet-stream\r\n\r\n");
                    body.appendBuffer(buffer);
                    appendAscii(body, "\r\n");
                } else {
                    appendAscii(body, "Content-Disposition: form-data; name=\"" + escapeMultipart(key) + "\"\r\n\r\n");
                    body.appendString(String.valueOf(value), StandardCharsets.UTF_8.name());
                    appendAscii(body, "\r\n");
                }
                ensureRequestBodyLimit(body);
            }
        }
        appendAscii(body, "--" + boundary + "--\r\n");
        return limitedBody(body, "multipart/form-data; boundary=" + boundary);
    }

    private static void appendAscii(Buffer body, String value) {
        body.appendString(value, StandardCharsets.US_ASCII.name());
    }

    private static String escapeMultipart(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static RequestBody limitedBody(Buffer body, String contentType) {
        ensureRequestBodyLimit(body);
        return new RequestBody(body, contentType);
    }

    private record RequestBody(Buffer body, String contentType) {
    }
    
    /**
     * JavaScript HTTP响应封装
     */
    public static class JsHttpResponse {
        
        private final int statusCode;
        private final MultiMap headers;
        private final Buffer body;
        private final String statusMessage;
        private final HttpResponse<Buffer> originalResponse;
        
        public JsHttpResponse(HttpResponse<Buffer> response) {
            this(
                    response.statusCode(),
                    MultiMap.caseInsensitiveMultiMap().setAll(response.headers()),
                    response.body(),
                    response.statusMessage(),
                    response
            );
        }

        public JsHttpResponse(int statusCode, MultiMap headers, Buffer body, String statusMessage,
                              HttpResponse<Buffer> originalResponse) {
            this.statusCode = statusCode;
            this.headers = headers == null ? MultiMap.caseInsensitiveMultiMap() : headers;
            this.body = body == null ? Buffer.buffer() : body;
            this.statusMessage = statusMessage;
            this.originalResponse = originalResponse;
        }
        
        /**
         * 获取响应体（字符串）
         * @return 响应体字符串
         */
        public String body() {
            return HttpResponseHelper.asText(body, header(HttpHeaders.CONTENT_ENCODING.toString()));
        }
        
        /**
         * 解析JSON响应
         * @return JSON对象或数组
         */
        public Object json() {
            try {
                JsonObject jsonObject = HttpResponseHelper.asJson(body, header(HttpHeaders.CONTENT_ENCODING.toString()));
                if (jsonObject == null || jsonObject.isEmpty()) {
                    return null;
                }
                
                // 将JsonObject转换为Map，这样JavaScript可以正确访问
                return jsonObject.getMap();
            } catch (Exception e) {
                log.error("解析JSON响应失败", e);
                throw new RuntimeException("解析JSON响应失败: " + e.getMessage(), e);
            }
        }
        
        /**
         * 获取HTTP状态码
         * @return 状态码
         */
        public int statusCode() {
            return statusCode;
        }
        
        /**
         * 获取响应头
         * @param name 头名称
         * @return 头值
         */
        public String header(String name) {
            return headers.get(name);
        }
        
        /**
         * 获取所有响应头
         * @return 响应头Map
         */
        public Map<String, String> headers() {
            Map<String, String> result = new HashMap<>();
            for (String name : headers.names()) {
                result.put(name, headers.get(name));
            }
            return result;
        }
        
        /**
         * 检查请求是否成功
         * @return true表示成功（2xx状态码），false表示失败
         */
        public boolean isSuccess() {
            int status = statusCode();
            return status >= 200 && status < 300;
        }
        
        /**
         * 获取原始响应对象
         * @return HttpResponse对象
         */
        @Deprecated
        public HttpResponse<Buffer> getOriginalResponse() {
            if (originalResponse == null) {
                throw new UnsupportedOperationException(
                        "流式HTTP客户端不再保留原始Vert.x HttpResponse，请使用statusCode/header/headers/body/bodyBytes方法"
                );
            }
            return originalResponse;
        }
        
        /**
         * 获取响应体字节数组
         * @return 响应体字节数组
         */
        public byte[] bodyBytes() {
            ensureResponseBodyLimit(body);
            return body.getBytes();
        }
        
        /**
         * 获取响应体大小
         * @return 响应体大小（字节）
         */
        public long bodySize() {
            return body.length();
        }

        public String statusMessage() {
            return statusMessage;
        }
    }

    /**
     * 关闭 HttpClient 释放连接池资源
     * 仅关闭自建的 client（代理模式），共享实例不关闭
     */
    public void close() {
        if (ownClient && client != null) {
            client.close();
        }
    }

    private static void ensureResponseBodyLimit(Buffer buffer) {
        if (buffer != null && buffer.length() > MAX_RESPONSE_BODY_BYTES) {
            throw new IllegalArgumentException("响应体过大: " + buffer.length() + " bytes");
        }
    }

    private static void ensureRequestBodyLimit(Buffer buffer) {
        if (buffer != null && buffer.length() > MAX_REQUEST_BODY_BYTES) {
            throw new IllegalArgumentException("请求体过大: " + buffer.length() + " bytes");
        }
    }
}
