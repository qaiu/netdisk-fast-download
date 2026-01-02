package cn.qaiu.parser.customjs;

import cn.qaiu.parser.customjs.JsHttpClient.JsHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * JavaScript Fetch API桥接类
 * 将标准的fetch API调用桥接到现有的JsHttpClient实现
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/12/06
 */
public class JsFetchBridge {
    
    private static final Logger log = LoggerFactory.getLogger(JsFetchBridge.class);
    
    private final JsHttpClient httpClient;
    
    public JsFetchBridge(JsHttpClient httpClient) {
        this.httpClient = httpClient;
    }
    
    /**
     * Fetch API实现
     * 接收fetch API调用并转换为JsHttpClient调用
     * 
     * @param url 请求URL
     * @param options 请求选项（包含method、headers、body等）
     * @return JsHttpResponse响应对象
     */
    public JsHttpResponse fetch(String url, Map<String, Object> options) {
        try {
            // 解析请求方法
            String method = "GET";
            if (options != null && options.containsKey("method")) {
                method = options.get("method").toString().toUpperCase();
            }
            
            // 解析并设置请求头
            if (options != null && options.containsKey("headers")) {
                Object headersObj = options.get("headers");
                if (headersObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> headersMap = (Map<String, Object>) headersObj;
                    for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
                        if (entry.getValue() != null) {
                            httpClient.putHeader(entry.getKey(), entry.getValue().toString());
                        }
                    }
                }
            }
            
            // 解析请求体
            Object body = null;
            if (options != null && options.containsKey("body")) {
                body = options.get("body");
            }
            
            // 根据方法执行请求
            JsHttpResponse response;
            switch (method) {
                case "GET":
                    response = httpClient.get(url);
                    break;
                case "POST":
                    response = httpClient.post(url, body);
                    break;
                case "PUT":
                    response = httpClient.put(url, body);
                    break;
                case "DELETE":
                    response = httpClient.delete(url);
                    break;
                case "PATCH":
                    response = httpClient.patch(url, body);
                    break;
                case "HEAD":
                    response = httpClient.getNoRedirect(url);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            }
            
            log.debug("Fetch请求完成: {} {} - 状态码: {}", method, url, response.statusCode());
            return response;
            
        } catch (Exception e) {
            log.error("Fetch请求失败: {} - {}", url, e.getMessage());
            throw new RuntimeException("Fetch请求失败: " + e.getMessage(), e);
        }
    }
}
