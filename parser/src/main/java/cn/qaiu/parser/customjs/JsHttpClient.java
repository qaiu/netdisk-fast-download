package cn.qaiu.parser.customjs;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.util.HttpResponseHelper;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JavaScript HTTP客户端封装
 * 为JavaScript提供同步API风格的HTTP请求功能
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/10/17
 */
public class JsHttpClient {
    
    private static final Logger log = LoggerFactory.getLogger(JsHttpClient.class);
    
    private final WebClient client;
    private final WebClientSession clientSession;
    private MultiMap headers;
    
    public JsHttpClient() {
        this.client = WebClient.create(WebClientVertxInit.get());
        this.clientSession = WebClientSession.create(client);
        this.headers = MultiMap.caseInsensitiveMultiMap();
        // 设置默认的Accept-Encoding头以支持压缩响应
        this.headers.set("Accept-Encoding", "gzip, deflate, br, zstd");
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
        // 设置默认的Accept-Encoding头以支持压缩响应
        this.headers.set("Accept-Encoding", "gzip, deflate, br, zstd");
        // 设置默认的User-Agent头
        this.headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0");
        // 设置默认的Accept-Language头
        this.headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6");
    }
    
    /**
     * 发起GET请求
     * @param url 请求URL
     * @return HTTP响应
     */
    public JsHttpResponse get(String url) {
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
    public JsHttpResponse getWithRedirect(String url) {
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.getAbs(url);
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            // 设置跟随重定向
            request.followRedirects(true);
            return request.send();
        });
    }
    
    /**
     * 发起GET请求但不跟随重定向（用于获取Location头）
     * @param url 请求URL
     * @return HTTP响应
     */
    public JsHttpResponse getNoRedirect(String url) {
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.getAbs(url);
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            // 设置不跟随重定向
            request.followRedirects(false);
            return request.send();
        });
    }
    
    /**
     * 发起POST请求
     * @param url 请求URL
     * @param data 请求数据
     * @return HTTP响应
     */
    public JsHttpResponse post(String url, Object data) {
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.postAbs(url);
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            
            if (data != null) {
                if (data instanceof String) {
                    request.sendBuffer(Buffer.buffer((String) data));
                } else if (data instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> mapData = (Map<String, String>) data;
                    request.sendForm(MultiMap.caseInsensitiveMultiMap().addAll(mapData));
                } else {
                    request.sendJson(data);
                }
            } else {
                request.send();
            }
            
            return request.send();
        });
    }
    
    /**
     * 设置请求头
     * @param name 头名称
     * @param value 头值
     * @return 当前客户端实例（支持链式调用）
     */
    public JsHttpClient putHeader(String name, String value) {
        if (name != null && value != null) {
            headers.set(name, value);
        }
        return this;
    }
    
    /**
     * 发送表单数据（简单键值对）
     * @param data 表单数据
     * @return HTTP响应
     */
    public JsHttpResponse sendForm(Map<String, String> data) {
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.postAbs("");
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            
            MultiMap formData = MultiMap.caseInsensitiveMultiMap();
            if (data != null) {
                formData.addAll(data);
            }
            
            return request.sendForm(formData);
        });
    }
    
    /**
     * 发送multipart表单数据（支持文件上传）
     * @param url 请求URL
     * @param data 表单数据，支持：
     *             - Map<String, String>: 文本字段
     *             - Map<String, Object>: 混合字段，Object可以是String、byte[]或Buffer
     * @return HTTP响应
     */
    public JsHttpResponse sendMultipartForm(String url, Map<String, Object> data) {
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.postAbs(url);
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            
            MultipartForm form = MultipartForm.create();
            
            if (data != null) {
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    
                    if (value instanceof String) {
                        form.attribute(key, (String) value);
                    } else if (value instanceof byte[]) {
                        form.binaryFileUpload(key, key, Buffer.buffer((byte[]) value), "application/octet-stream");
                    } else if (value instanceof Buffer) {
                        form.binaryFileUpload(key, key, (Buffer) value, "application/octet-stream");
                    } else if (value != null) {
                        // 其他类型转换为字符串
                        form.attribute(key, value.toString());
                    }
                }
            }
            
            return request.sendMultipartForm(form);
        });
    }
    
    /**
     * 发送JSON数据
     * @param data JSON数据
     * @return HTTP响应
     */
    public JsHttpResponse sendJson(Object data) {
        return executeRequest(() -> {
            HttpRequest<Buffer> request = client.postAbs("");
            if (!headers.isEmpty()) {
                request.putHeaders(headers);
            }
            
            return request.sendJson(data);
        });
    }
    
    /**
     * 执行HTTP请求（同步）
     */
    private JsHttpResponse executeRequest(RequestExecutor executor) {
        try {
            Promise<HttpResponse<Buffer>> promise = Promise.promise();
            Future<HttpResponse<Buffer>> future = executor.execute();
            
            future.onComplete(promise);
            
            // 等待响应完成（最多30秒）
            HttpResponse<Buffer> response = promise.future().toCompletionStage()
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            
            return new JsHttpResponse(response);
            
        } catch (Exception e) {
            log.error("HTTP请求执行失败", e);
            throw new RuntimeException("HTTP请求执行失败: " + e.getMessage(), e);
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
     * JavaScript HTTP响应封装
     */
    public static class JsHttpResponse {
        
        private final HttpResponse<Buffer> response;
        
        public JsHttpResponse(HttpResponse<Buffer> response) {
            this.response = response;
        }
        
        /**
         * 获取响应体（字符串）
         * @return 响应体字符串
         */
        public String body() {
            return HttpResponseHelper.asText(response);
        }
        
        /**
         * 解析JSON响应
         * @return JSON对象或数组
         */
        public Object json() {
            try {
                JsonObject jsonObject = HttpResponseHelper.asJson(response);
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
            return response.statusCode();
        }
        
        /**
         * 获取响应头
         * @param name 头名称
         * @return 头值
         */
        public String header(String name) {
            return response.getHeader(name);
        }
        
        /**
         * 获取所有响应头
         * @return 响应头Map
         */
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
        public HttpResponse<Buffer> getOriginalResponse() {
            return response;
        }
    }
}
