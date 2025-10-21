package cn.qaiu.parser;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.util.HttpResponseHelper;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * 发送表单数据
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
                String body = response.bodyAsString();
                if (body == null || body.trim().isEmpty()) {
                    return null;
                }
                
                // 尝试解析为JSON对象
                try {
                    JsonObject jsonObject = response.bodyAsJsonObject();
                    // 将JsonObject转换为Map，这样JavaScript可以正确访问
                    return jsonObject.getMap();
                } catch (Exception e) {
                    // 如果解析为对象失败，尝试解析为数组
                    try {
                        return response.bodyAsJsonArray().getList();
                    } catch (Exception e2) {
                        // 如果都失败了，返回原始字符串
                        log.warn("无法解析为JSON，返回原始字符串: {}", body);
                        return body;
                    }
                }
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
            Map<String, String> result = new java.util.HashMap<>();
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
