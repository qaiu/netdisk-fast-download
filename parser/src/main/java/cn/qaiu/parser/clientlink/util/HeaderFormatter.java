package cn.qaiu.parser.clientlink.util;

import java.util.Map;

/**
 * 请求头格式化工具类
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class HeaderFormatter {
    
    /**
     * 将请求头格式化为 curl 格式
     * 
     * @param headers 请求头Map
     * @return curl 格式的请求头字符串
     */
    public static String formatForCurl(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (result.length() > 0) {
                result.append(" \\\n  ");
            }
            result.append("-H \"").append(entry.getKey()).append(": ").append(entry.getValue()).append("\"");
        }
        return result.toString();
    }
    
    /**
     * 将请求头格式化为 wget 格式
     * 
     * @param headers 请求头Map
     * @return wget 格式的请求头字符串
     */
    public static String formatForWget(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (result.length() > 0) {
                result.append(" \\\n     ");
            }
            result.append("--header=\"").append(entry.getKey()).append(": ").append(entry.getValue()).append("\"");
        }
        return result.toString();
    }
    
    /**
     * 将请求头格式化为 aria2 格式
     * 
     * @param headers 请求头Map
     * @return aria2 格式的请求头字符串
     */
    public static String formatForAria2(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (result.length() > 0) {
                result.append(" \\\n       ");
            }
            result.append("--header=\"").append(entry.getKey()).append(": ").append(entry.getValue()).append("\"");
        }
        return result.toString();
    }
    
    /**
     * 将请求头格式化为 HTTP 头格式（用于 Base64 编码）
     * 
     * @param headers 请求头Map
     * @return HTTP 头格式的字符串
     */
    public static String formatForHttpHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (result.length() > 0) {
                result.append("\\r\\n");
            }
            result.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return result.toString();
    }
    
    /**
     * 将请求头格式化为 JSON 格式
     * 
     * @param headers 请求头Map
     * @return JSON 格式的请求头字符串
     */
    public static String formatForJson(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        
        StringBuilder result = new StringBuilder();
        result.append("{\n");
        
        boolean first = true;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!first) {
                result.append(",\n");
            }
            result.append("    \"").append(entry.getKey()).append("\": \"")
                  .append(entry.getValue()).append("\"");
            first = false;
        }
        
        result.append("\n  }");
        return result.toString();
    }
    
    /**
     * 将请求头格式化为简单键值对格式（用于 FDM）
     * 
     * @param headers 请求头Map
     * @return 简单键值对格式的字符串
     */
    public static String formatForSimple(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (result.length() > 0) {
                result.append("; ");
            }
            result.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return result.toString();
    }
}
