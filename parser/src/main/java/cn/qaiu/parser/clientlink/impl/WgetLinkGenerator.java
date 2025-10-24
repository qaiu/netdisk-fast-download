package cn.qaiu.parser.clientlink.impl;

import cn.qaiu.parser.clientlink.ClientLinkGenerator;
import cn.qaiu.parser.clientlink.ClientLinkType;
import cn.qaiu.parser.clientlink.DownloadLinkMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * wget 命令生成器
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class WgetLinkGenerator implements ClientLinkGenerator {
    
    @Override
    public String generate(DownloadLinkMeta meta) {
        if (!supports(meta)) {
            return null;
        }
        
        List<String> parts = new ArrayList<>();
        parts.add("wget");
        
        // 添加请求头
        if (meta.getHeaders() != null && !meta.getHeaders().isEmpty()) {
            for (Map.Entry<String, String> entry : meta.getHeaders().entrySet()) {
                parts.add("--header=\"" + entry.getKey() + ": " + entry.getValue() + "\"");
            }
        }
        
        // 设置输出文件名
        if (meta.getFileName() != null && !meta.getFileName().trim().isEmpty()) {
            parts.add("-O");
            parts.add("\"" + meta.getFileName() + "\"");
        }
        
        // 添加URL
        parts.add("\"" + meta.getUrl() + "\"");
        
        return String.join(" \\\n     ", parts);
    }
    
    @Override
    public ClientLinkType getType() {
        return ClientLinkType.WGET;
    }
}
