package cn.qaiu.parser.clientlink.impl;

import cn.qaiu.parser.clientlink.ClientLinkGenerator;
import cn.qaiu.parser.clientlink.ClientLinkType;
import cn.qaiu.parser.clientlink.DownloadLinkMeta;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 比特彗星协议链接生成器
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class BitCometLinkGenerator implements ClientLinkGenerator {
    
    @Override
    public String generate(DownloadLinkMeta meta) {
        if (!supports(meta)) {
            return null;
        }
        
        try {
            // 比特彗星支持 HTTP 下载，格式类似 IDM
            String encodedUrl = Base64.getEncoder().encodeToString(
                meta.getUrl().getBytes(StandardCharsets.UTF_8)
            );
            
            StringBuilder link = new StringBuilder("bitcomet:///?url=").append(encodedUrl);
            
            // 添加请求头
            if (meta.getHeaders() != null && !meta.getHeaders().isEmpty()) {
                StringBuilder headerStr = new StringBuilder();
                for (Map.Entry<String, String> entry : meta.getHeaders().entrySet()) {
                    if (headerStr.length() > 0) {
                        headerStr.append("\\r\\n");
                    }
                    headerStr.append(entry.getKey()).append(": ").append(entry.getValue());
                }
                
                String encodedHeaders = Base64.getEncoder().encodeToString(
                    headerStr.toString().getBytes(StandardCharsets.UTF_8)
                );
                link.append("&header=").append(encodedHeaders);
            }
            
            // 添加文件名
            if (meta.getFileName() != null && !meta.getFileName().trim().isEmpty()) {
                String encodedFileName = Base64.getEncoder().encodeToString(
                    meta.getFileName().getBytes(StandardCharsets.UTF_8)
                );
                link.append("&filename=").append(encodedFileName);
            }
            
            return link.toString();
            
        } catch (Exception e) {
            // 如果编码失败，返回简单的URL
            return "bitcomet:///?url=" + meta.getUrl();
        }
    }
    
    @Override
    public ClientLinkType getType() {
        return ClientLinkType.BITCOMET;
    }
}
