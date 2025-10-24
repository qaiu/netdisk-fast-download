package cn.qaiu.parser.clientlink.impl;

import cn.qaiu.parser.clientlink.ClientLinkGenerator;
import cn.qaiu.parser.clientlink.ClientLinkType;
import cn.qaiu.parser.clientlink.DownloadLinkMeta;

import java.util.Map;

/**
 * Free Download Manager 导入格式生成器
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class FdmLinkGenerator implements ClientLinkGenerator {
    
    @Override
    public String generate(DownloadLinkMeta meta) {
        if (!supports(meta)) {
            return null;
        }
        
        // FDM 支持简单的文本格式导入
        StringBuilder result = new StringBuilder();
        result.append("URL=").append(meta.getUrl()).append("\n");
        
        // 添加文件名
        if (meta.getFileName() != null && !meta.getFileName().trim().isEmpty()) {
            result.append("Filename=").append(meta.getFileName()).append("\n");
        }
        
        // 添加请求头
        if (meta.getHeaders() != null && !meta.getHeaders().isEmpty()) {
            result.append("Headers=");
            boolean first = true;
            for (Map.Entry<String, String> entry : meta.getHeaders().entrySet()) {
                if (!first) {
                    result.append("; ");
                }
                result.append(entry.getKey()).append(": ").append(entry.getValue());
                first = false;
            }
            result.append("\n");
        }
        
        result.append("Referer=").append(meta.getReferer() != null ? meta.getReferer() : "").append("\n");
        result.append("User-Agent=").append(meta.getUserAgent() != null ? meta.getUserAgent() : "").append("\n");
        
        return result.toString();
    }
    
    @Override
    public ClientLinkType getType() {
        return ClientLinkType.FDM;
    }
}
