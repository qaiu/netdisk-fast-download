package cn.qaiu.parser.clientlink;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 下载链接元数据封装类
 * 包含生成客户端下载链接所需的所有信息
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class DownloadLinkMeta {
    
    private String url;                      // 直链
    private Map<String, String> headers;     // 请求头
    private String referer;                  // Referer
    private String userAgent;                // User-Agent
    private String fileName;                 // 文件名（可选）
    private Map<String, Object> extParams;   // 扩展参数
    
    public DownloadLinkMeta() {
        this.headers = new HashMap<>();
        this.extParams = new HashMap<>();
    }
    
    public DownloadLinkMeta(String url) {
        this();
        this.url = url;
    }
    
    /**
     * 从 ShareLinkInfo.otherParam 构建 DownloadLinkMeta
     * 
     * @param info ShareLinkInfo 对象
     * @return DownloadLinkMeta 实例
     */
    public static DownloadLinkMeta fromShareLinkInfo(ShareLinkInfo info) {
        DownloadLinkMeta meta = new DownloadLinkMeta();
        
        // 从 otherParam 中提取元数据
        Map<String, Object> otherParam = info.getOtherParam();
        
        // 获取直链 - 优先从 downloadUrl 获取，如果没有则尝试从解析结果获取
        Object downloadUrl = otherParam.get("downloadUrl");
        if (downloadUrl instanceof String && StringUtils.isNotEmpty((String) downloadUrl)) {
            meta.setUrl((String) downloadUrl);
        } else {
            // 如果没有存储的 downloadUrl，尝试从解析结果中获取
            // 这里假设解析器会将直链存储在 otherParam 的某个字段中
            // 或者我们可以从 ShareLinkInfo 的其他字段中获取
            String directLink = extractDirectLinkFromInfo(info);
            if (StringUtils.isNotEmpty(directLink)) {
                meta.setUrl(directLink);
            } else {
                // 如果仍然没有找到直链，使用分享链接作为默认下载链接
                String shareUrl = info.getShareUrl();
                if (StringUtils.isNotEmpty(shareUrl)) {
                    meta.setUrl(shareUrl);
                }
            }
        }
        
        // 获取请求头
        Object downloadHeaders = otherParam.get("downloadHeaders");
        if (downloadHeaders instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> headerMap = (Map<String, String>) downloadHeaders;
            meta.setHeaders(headerMap);
        }
        
        // 获取 Referer
        Object downloadReferer = otherParam.get("downloadReferer");
        if (downloadReferer instanceof String) {
            meta.setReferer((String) downloadReferer);
        }
        
        // 获取文件名（从 fileInfo 中提取）
        Object fileInfo = otherParam.get("fileInfo");
        if (fileInfo instanceof FileInfo) {
            FileInfo fi = (FileInfo) fileInfo;
            if (StringUtils.isNotEmpty(fi.getFileName())) {
                meta.setFileName(fi.getFileName());
            }
        }
        
        // 从请求头中提取 User-Agent 和 Referer（如果单独存储的话）
        if (meta.getHeaders() != null) {
            String ua = meta.getHeaders().get("User-Agent");
            if (StringUtils.isNotEmpty(ua)) {
                meta.setUserAgent(ua);
            }
            
            String ref = meta.getHeaders().get("Referer");
            if (StringUtils.isNotEmpty(ref) && StringUtils.isEmpty(meta.getReferer())) {
                meta.setReferer(ref);
            }
        }
        
        // 如果没有 User-Agent，设置默认的 User-Agent
        if (StringUtils.isEmpty(meta.getUserAgent())) {
            meta.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        }
        
        return meta;
    }
    
    /**
     * 从 ShareLinkInfo 中提取直链
     * 尝试从各种可能的字段中获取直链
     * 
     * @param info ShareLinkInfo 对象
     * @return 直链URL，如果找不到则返回 null
     */
    private static String extractDirectLinkFromInfo(ShareLinkInfo info) {
        Map<String, Object> otherParam = info.getOtherParam();
        
        // 尝试从各种可能的字段中获取直链
        String[] possibleKeys = {
            "directLink", "downloadUrl", "url", "link", 
            "download_link", "direct_link", "fileUrl", "file_url"
        };
        
        for (String key : possibleKeys) {
            Object value = otherParam.get(key);
            if (value instanceof String && StringUtils.isNotEmpty((String) value)) {
                return (String) value;
            }
        }
        
        return null;
    }
    
    // Getter 和 Setter 方法
    
    public String getUrl() {
        return url;
    }
    
    public DownloadLinkMeta setUrl(String url) {
        this.url = url;
        return this;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public DownloadLinkMeta setHeaders(Map<String, String> headers) {
        this.headers = headers != null ? headers : new HashMap<>();
        return this;
    }
    
    public String getReferer() {
        return referer;
    }
    
    public DownloadLinkMeta setReferer(String referer) {
        this.referer = referer;
        return this;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public DownloadLinkMeta setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public DownloadLinkMeta setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }
    
    public Map<String, Object> getExtParams() {
        return extParams;
    }
    
    public DownloadLinkMeta setExtParams(Map<String, Object> extParams) {
        this.extParams = extParams != null ? extParams : new HashMap<>();
        return this;
    }
    
    /**
     * 添加请求头
     */
    public DownloadLinkMeta addHeader(String name, String value) {
        if (this.headers == null) {
            this.headers = new HashMap<>();
        }
        this.headers.put(name, value);
        return this;
    }
    
    /**
     * 添加扩展参数
     */
    public DownloadLinkMeta addExtParam(String key, Object value) {
        if (this.extParams == null) {
            this.extParams = new HashMap<>();
        }
        this.extParams.put(key, value);
        return this;
    }
    
    /**
     * 检查是否有有效的下载链接
     */
    public boolean hasValidUrl() {
        return StringUtils.isNotEmpty(url);
    }
    
    @Override
    public String toString() {
        return "DownloadLinkMeta{" +
                "url='" + url + '\'' +
                ", fileName='" + fileName + '\'' +
                ", headers=" + headers +
                ", referer='" + referer + '\'' +
                ", userAgent='" + userAgent + '\'' +
                '}';
    }
}
