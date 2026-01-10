package cn.qaiu.parser.custompy;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.util.HttpResponseHelper;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.WebClientSession;
import io.vertx.ext.web.multipart.MultipartForm;
import org.apache.commons.lang3.StringUtils;
import org.graalvm.polyglot.HostAccess;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Python HTTP客户端封装
 * 为Python脚本提供类似requests库的HTTP请求功能
 * 基于Vert.x WebClient实现，提供同步API风格
 *
 * @author QAIU
 */
public class PyHttpClient {
    
    private static final Logger log = LoggerFactory.getLogger(PyHttpClient.class);
    
    private final WebClient client;
    private final WebClientSession clientSession;
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
    
    public PyHttpClient() {
        this.client = WebClient.create(WebClientVertxInit.get(), new WebClientOptions());
        this.clientSession = WebClientSession.create(client);
        this.headers = MultiMap.caseInsensitiveMultiMap();
        initDefaultHeaders();
    }
    
    /**
     * 带代理配置的构造函数
     * @param proxyConfig 代理配置JsonObject，包含type、host、port、username、password
     */
    public PyHttpClient(JsonObject proxyConfig) {
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
            
            this.client = WebClient.create(WebClientVertxInit.get(),
                    new WebClientOptions()
                            .setUserAgentEnabled(false)
                            .setProxyOptions(proxyOptions));
            this.clientSession = WebClientSession.create(client);
        } else {
            this.client = WebClient.create(WebClientVertxInit.get());
            this.clientSession = WebClientSession.create(client);
        }
        this.headers = MultiMap.caseInsensitiveMultiMap();
        initDefaultHeaders();
    }
    
    private void initDefaultHeaders() {
        // 设置默认的Accept-Encoding头以支持压缩响应
        this.headers.set("Accept-Encoding", "gzip, deflate, br, zstd");
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
                return;
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
                    
                    if (PRIVATE_IP_PATTERN.matcher(ip).find()) {
                        log.warn("🔒 安全拦截: 域名解析到内网IP - {} -> {}", host, ip);
                        throw new SecurityException("🔒 安全拦截: 该域名指向内网地址");
                    }
                } catch (UnknownHostException e) {
                    log.debug("DNS解析失败，允许继续: {}", host);
                }
            }
            
            log.debug("URL安全检查通过: {}", url);
            
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.debug("URL验证异常，允许继续: {}", url, e);
        }
    }
    
    /**
     * 判断字符串是否为IP地址格式
     */
    private boolean isIpAddress(String host) {
        return host.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$") || host.contains(":");
    }
    
    /**
     * 发起GET请求
     * @param url 请求URL
     * @return HTTP响应
     */
    @HostAccess.Export
    public PyHttpResponse get(String url) {
        validateUrlSecurity(url);
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.getAbs(url);
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            return request.send();
        });
    }
    
    /**
     * 发起GET请求并跟随重定向
     * @param url 请求URL
     * @return HTTP响应
     */
    @HostAccess.Export
    public PyHttpResponse get_with_redirect(String url) {
        validateUrlSecurity(url);
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.getAbs(url);
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            request.followRedirects(true);
            return request.send();
        });
    }
    
    /**
     * 发起GET请求但不跟随重定向（用于获取Location头）
     * @param url 请求URL
     * @return HTTP响应
     */
    @HostAccess.Export
    public PyHttpResponse get_no_redirect(String url) {
        validateUrlSecurity(url);
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.getAbs(url);
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            request.followRedirects(false);
            return request.send();
        });
    }
    
    /**
     * 发起POST请求
     * @param url 请求URL
     * @param data 请求数据（支持String、Map）
     * @return HTTP响应
     */
    @HostAccess.Export
    public PyHttpResponse post(String url, Object data) {
        validateUrlSecurity(url);
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.postAbs(url);
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            
            if (data != null) {
                if (data instanceof String) {
                    return request.sendBuffer(Buffer.buffer((String) data));
                } else if (data instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> mapData = (Map<String, String>) data;
                    return request.sendForm(MultiMap.caseInsensitiveMultiMap().addAll(mapData));
                } else {
                    return request.sendJson(data);
                }
            } else {
                return request.send();
            }
        });
    }
    
    /**
     * 发起POST请求（JSON数据）
     * @param url 请求URL
     * @param jsonData JSON字符串或Map
     * @return HTTP响应
     */
    @HostAccess.Export
    public PyHttpResponse post_json(String url, Object jsonData) {
        validateUrlSecurity(url);
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.postAbs(url);
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            headers.set("Content-Type", "application/json");
            
            if (jsonData instanceof String) {
                return request.sendBuffer(Buffer.buffer((String) jsonData));
            } else {
                return request.sendJson(jsonData);
            }
        });
    }
    
    /**
     * 发起PUT请求
     * @param url 请求URL
     * @param data 请求数据
     * @return HTTP响应
     */
    @HostAccess.Export
    public PyHttpResponse put(String url, Object data) {
        validateUrlSecurity(url);
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.putAbs(url);
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            
            if (data != null) {
                if (data instanceof String) {
                    return request.sendBuffer(Buffer.buffer((String) data));
                } else if (data instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> mapData = (Map<String, String>) data;
                    return request.sendForm(MultiMap.caseInsensitiveMultiMap().addAll(mapData));
                } else {
                    return request.sendJson(data);
                }
            } else {
                return request.send();
            }
        });
    }
    
    /**
     * 发起DELETE请求
     * @param url 请求URL
     * @return HTTP响应
     */
    @HostAccess.Export
    public PyHttpResponse delete(String url) {
        validateUrlSecurity(url);
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.deleteAbs(url);
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            return request.send();
        });
    }
    
    /**
     * 发起PATCH请求
     * @param url 请求URL
     * @param data 请求数据
     * @return HTTP响应
     */
    @HostAccess.Export
    public PyHttpResponse patch(String url, Object data) {
        validateUrlSecurity(url);
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.patchAbs(url);
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            
            if (data != null) {
                if (data instanceof String) {
                    return request.sendBuffer(Buffer.buffer((String) data));
                } else if (data instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> mapData = (Map<String, String>) data;
                    return request.sendForm(MultiMap.caseInsensitiveMultiMap().addAll(mapData));
                } else {
                    return request.sendJson(data);
                }
            } else {
                return request.send();
            }
        });
    }
    
    /**
     * 设置请求头
     * @param name 头名称
     * @param value 头值
     * @return 当前客户端实例（支持链式调用）
     */
    @HostAccess.Export
    public PyHttpClient put_header(String name, String value) {
        if (name != null && value != null) {
            headers.set(name, value);
        }
        return this;
    }
    
    /**
     * 批量设置请求头
     * @param headersMap 请求头Map
     * @return 当前客户端实例（支持链式调用）
     */
    @HostAccess.Export
    public PyHttpClient put_headers(Map<String, String> headersMap) {
        if (headersMap != null) {
            for (Map.Entry<String, String> entry : headersMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    headers.set(entry.getKey(), entry.getValue());
                }
            }
        }
        return this;
    }
    
    /**
     * 删除指定请求头
     * @param name 头名称
     * @return 当前客户端实例（支持链式调用）
     */
    @HostAccess.Export
    public PyHttpClient remove_header(String name) {
        if (name != null) {
            headers.remove(name);
        }
        return this;
    }
    
    /**
     * 清空所有请求头（保留默认头）
     * @return 当前客户端实例（支持链式调用）
     */
    @HostAccess.Export
    public PyHttpClient clear_headers() {
        headers.clear();
        initDefaultHeaders();
        return this;
    }
    
    /**
     * 获取所有请求头
     * @return 请求头Map
     */
    @HostAccess.Export
    public Map<String, String> get_headers() {
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
    @HostAccess.Export
    public PyHttpClient set_timeout(int seconds) {
        if (seconds > 0) {
            this.timeoutSeconds = seconds;
        }
        return this;
    }
    
    /**
     * URL编码
     * @param str 要编码的字符串
     * @return 编码后的字符串
     */
    @HostAccess.Export
    public static String url_encode(String str) {
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
    @HostAccess.Export
    public static String url_decode(String str) {
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
     * 执行HTTP请求（同步）
     */
    private PyHttpResponse executeRequest(RequestExecutor executor) {
        try {
            Promise<HttpResponse<Buffer>> promise = Promise.promise();
            Future<HttpResponse<Buffer>> future = executor.execute();
            
            future.onComplete(result -> {
                if (result.succeeded()) {
                    promise.complete(result.result());
                } else {
                    promise.fail(result.cause());
                }
            }).onFailure(Throwable::printStackTrace);
            
            // 等待响应完成（使用配置的超时时间）
            HttpResponse<Buffer> response = promise.future().toCompletionStage()
                    .toCompletableFuture()
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            
            return new PyHttpResponse(response);
            
        } catch (TimeoutException e) {
            String errorMsg = "HTTP请求超时（" + timeoutSeconds + "秒）";
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
    
    /**
     * 请求执行器接口
     */
    @FunctionalInterface
    private interface RequestExecutor {
        Future<HttpResponse<Buffer>> execute();
    }
    
    /**
     * Python HTTP响应封装
     */
    public static class PyHttpResponse {
        
        private final HttpResponse<Buffer> response;
        
        public PyHttpResponse(HttpResponse<Buffer> response) {
            this.response = response;
        }
        
        /**
         * 获取响应体（字符串）
         * @return 响应体字符串
         */
        @HostAccess.Export
        public String text() {
            return HttpResponseHelper.asText(response);
        }
        
        /**
         * 获取响应体（字符串）- 别名
         */
        @HostAccess.Export
        public String body() {
            return text();
        }
        
        /**
         * 解析JSON响应
         * @return JSON对象的Map表示
         */
        @HostAccess.Export
        public Object json() {
            try {
                JsonObject jsonObject = HttpResponseHelper.asJson(response);
                if (jsonObject == null || jsonObject.isEmpty()) {
                    return null;
                }
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
        @HostAccess.Export
        public int status_code() {
            return response.statusCode();
        }
        
        /**
         * 获取响应头
         * @param name 头名称
         * @return 头值
         */
        @HostAccess.Export
        public String header(String name) {
            return response.getHeader(name);
        }
        
        /**
         * 获取所有响应头
         * @return 响应头Map
         */
        @HostAccess.Export
        public Map<String, String> headers() {
            MultiMap responseHeaders = response.headers();
            Map<String, String> result = new HashMap<>();
            for (String name : responseHeaders.names()) {
                result.put(name, responseHeaders.get(name));
            }
            return result;
        }
        
        /**
         * 检查请求是否成功
         * @return true表示成功（2xx状态码）
         */
        @HostAccess.Export
        public boolean ok() {
            int status = status_code();
            return status >= 200 && status < 300;
        }
        
        /**
         * 获取响应体字节数组
         * @return 响应体字节数组
         */
        @HostAccess.Export
        public byte[] content() {
            Buffer buffer = response.body();
            if (buffer == null) {
                return new byte[0];
            }
            return buffer.getBytes();
        }
        
        /**
         * 获取响应体大小
         * @return 响应体大小（字节）
         */
        @HostAccess.Export
        public long content_length() {
            Buffer buffer = response.body();
            if (buffer == null) {
                return 0;
            }
            return buffer.length();
        }
        
        /**
         * 获取原始响应对象
         */
        public HttpResponse<Buffer> getOriginalResponse() {
            return response;
        }
    }
}
