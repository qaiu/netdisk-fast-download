package cn.qaiu.parser.clientlink.impl;

import cn.qaiu.parser.clientlink.ClientLinkGenerator;
import cn.qaiu.parser.clientlink.ClientLinkType;
import cn.qaiu.parser.clientlink.DownloadLinkMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aria2 命令生成器
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class Aria2LinkGenerator implements ClientLinkGenerator {
    
    @Override
    public String generate(DownloadLinkMeta meta) {
        if (!supports(meta)) {
            return null;
        }
        
        List<String> parts = new ArrayList<>();
        parts.add("aria2c");
        
        // 添加请求头
        if (meta.getHeaders() != null && !meta.getHeaders().isEmpty()) {
            for (Map.Entry<String, String> entry : meta.getHeaders().entrySet()) {
                parts.add("--header=\"" + entry.getKey() + ": " + entry.getValue() + "\"");
            }
        }
        
        // 设置输出文件名
        if (meta.getFileName() != null && !meta.getFileName().trim().isEmpty()) {
            parts.add("--out=\"" + meta.getFileName() + "\"");
        }
        
        // 添加其他常用参数
        parts.add("--continue"); // 支持断点续传
        parts.add("--max-tries=3"); // 最大重试次数
        parts.add("--retry-wait=5"); // 重试等待时间
        parts.add("-s 8"); // 分成8片段下载
        parts.add("-x 8"); // 每个服务器使用8个连接
        
        // 添加URL
        parts.add("\"" + meta.getUrl() + "\"");
        
        return String.join(" \\\n       ", parts);
    }
    
    @Override
    public ClientLinkType getType() {
        return ClientLinkType.ARIA2;
    }
}
