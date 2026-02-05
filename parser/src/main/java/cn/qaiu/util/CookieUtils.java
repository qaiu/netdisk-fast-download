package cn.qaiu.util;

import java.util.*;

/**
 * Cookie 工具类
 * 用于过滤和处理 Cookie 字符串
 */
public class CookieUtils {
    
    /**
     * UC/夸克网盘常用的 Cookie 字段
     */
    public static final List<String> UC_QUARK_COOKIE_KEYS = Arrays.asList(
            "__pus",   // 主要的用户会话标识(最重要)
            "__kp",    // 用户标识
            "__kps",   // 会话密钥
            "__ktd",   // 会话令牌
            "__uid",   // 用户ID
            "__puus"   // 用户会话签名
    );
    
    /**
     * 根据指定的 key 列表过滤 cookie
     * 
     * @param cookieStr 原始 cookie 字符串，格式如 "key1=value1; key2=value2"
     * @param keys 需要保留的 cookie key 列表
     * @return 过滤后的 cookie 字符串，只包含指定的 key
     */
    public static String filterCookie(String cookieStr, List<String> keys) {
        if (cookieStr == null || cookieStr.isEmpty()) {
            return "";
        }
        if (keys == null || keys.isEmpty()) {
            return cookieStr;
        }
        
        // 将 keys 转为 Set 以提高查找效率
        Set<String> keySet = new HashSet<>(keys);
        
        StringBuilder result = new StringBuilder();
        String[] cookies = cookieStr.split(";\\s*");
        
        for (String cookie : cookies) {
            if (cookie.isEmpty()) {
                continue;
            }
            
            // 提取 cookie 的 key
            int equalIndex = cookie.indexOf('=');
            if (equalIndex > 0) {
                String key = cookie.substring(0, equalIndex).trim();
                
                // 如果 key 在需要的列表中，保留这个 cookie
                if (keySet.contains(key)) {
                    if (result.length() > 0) {
                        result.append("; ");
                    }
                    result.append(cookie);
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * 使用 UC/夸克网盘默认的 cookie 字段过滤
     * 
     * @param cookieStr 原始 cookie 字符串
     * @return 过滤后的 cookie 字符串
     */
    public static String filterUcQuarkCookie(String cookieStr) {
        return filterCookie(cookieStr, UC_QUARK_COOKIE_KEYS);
    }
    
    /**
     * 从 cookie 字符串中提取指定 key 的值
     * 
     * @param cookieStr cookie 字符串
     * @param key 要提取的 cookie key
     * @return cookie 值，如果不存在返回 null
     */
    public static String getCookieValue(String cookieStr, String key) {
        if (cookieStr == null || cookieStr.isEmpty() || key == null) {
            return null;
        }
        
        String[] cookies = cookieStr.split(";\\s*");
        for (String cookie : cookies) {
            if (cookie.startsWith(key + "=")) {
                int equalIndex = cookie.indexOf('=');
                if (equalIndex > 0 && equalIndex < cookie.length() - 1) {
                    return cookie.substring(equalIndex + 1);
                }
            }
        }
        
        return null;
    }
    
    /**
     * 检查 cookie 字符串中是否包含指定的 key
     * 
     * @param cookieStr cookie 字符串
     * @param key 要检查的 cookie key
     * @return true 表示包含该 key
     */
    public static boolean containsKey(String cookieStr, String key) {
        return getCookieValue(cookieStr, key) != null;
    }
    
    /**
     * 更新 cookie 字符串中的指定 cookie 值
     * 
     * @param cookieStr 原始 cookie 字符串
     * @param cookieName cookie 名称
     * @param newValue 新的完整 cookie 值（格式：cookieName=value）
     * @return 更新后的 cookie 字符串
     */
    public static String updateCookieValue(String cookieStr, String cookieName, String newValue) {
        if (cookieStr == null || cookieStr.isEmpty()) {
            return newValue;
        }
        
        StringBuilder result = new StringBuilder();
        String[] cookies = cookieStr.split(";\\s*");
        boolean found = false;
        
        for (String cookie : cookies) {
            if (cookie.startsWith(cookieName + "=")) {
                // 替换为新值
                if (result.length() > 0) result.append("; ");
                result.append(newValue);
                found = true;
            } else if (!cookie.isEmpty()) {
                if (result.length() > 0) result.append("; ");
                result.append(cookie);
            }
        }
        
        // 如果原来没有这个 cookie，添加它
        if (!found) {
            if (result.length() > 0) result.append("; ");
            result.append(newValue);
        }
        
        return result.toString();
    }
    
    /**
     * 合并多个 cookie 字符串，后面的会覆盖前面的同名 cookie
     * 
     * @param cookieStrings cookie 字符串数组
     * @return 合并后的 cookie 字符串
     */
    public static String mergeCookies(String... cookieStrings) {
        if (cookieStrings == null || cookieStrings.length == 0) {
            return "";
        }
        
        Map<String, String> cookieMap = new LinkedHashMap<>();
        
        for (String cookieStr : cookieStrings) {
            if (cookieStr == null || cookieStr.isEmpty()) {
                continue;
            }
            
            String[] cookies = cookieStr.split(";\\s*");
            for (String cookie : cookies) {
                if (cookie.isEmpty()) {
                    continue;
                }
                
                int equalIndex = cookie.indexOf('=');
                if (equalIndex > 0) {
                    String key = cookie.substring(0, equalIndex).trim();
                    cookieMap.put(key, cookie);
                }
            }
        }
        
        return String.join("; ", cookieMap.values());
    }
}
