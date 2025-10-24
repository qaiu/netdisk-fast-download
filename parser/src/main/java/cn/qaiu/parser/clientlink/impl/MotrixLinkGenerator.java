package cn.qaiu.parser.clientlink.impl;

import cn.qaiu.parser.clientlink.ClientLinkGenerator;
import cn.qaiu.parser.clientlink.ClientLinkType;
import cn.qaiu.parser.clientlink.DownloadLinkMeta;
import io.vertx.core.json.JsonObject;

import java.util.Map;

/**
 * Motrix 导入格式生成器
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class MotrixLinkGenerator implements ClientLinkGenerator {
    
    @Override
    public String generate(DownloadLinkMeta meta) {
        if (!supports(meta)) {
            return null;
        }
        
        // 使用 Vert.x JsonObject 构建 JSON
        JsonObject taskJson = new JsonObject();
        taskJson.put("url", meta.getUrl());
        
        // 添加文件名
        if (meta.getFileName() != null && !meta.getFileName().trim().isEmpty()) {
            taskJson.put("filename", meta.getFileName());
        }
        
        // 添加请求头
        if (meta.getHeaders() != null && !meta.getHeaders().isEmpty()) {
            JsonObject headersJson = new JsonObject();
            for (Map.Entry<String, String> entry : meta.getHeaders().entrySet()) {
                headersJson.put(entry.getKey(), entry.getValue());
            }
            taskJson.put("headers", headersJson);
        }
        
        // 设置输出文件名
        String outputFile = meta.getFileName() != null ? meta.getFileName() : "";
        taskJson.put("out", outputFile);
        
        return taskJson.encodePrettily();
    }
    
    @Override
    public ClientLinkType getType() {
        return ClientLinkType.MOTRIX;
    }
}
