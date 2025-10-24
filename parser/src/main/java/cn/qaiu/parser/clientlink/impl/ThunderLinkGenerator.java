package cn.qaiu.parser.clientlink.impl;

import cn.qaiu.parser.clientlink.ClientLinkGenerator;
import cn.qaiu.parser.clientlink.ClientLinkType;
import cn.qaiu.parser.clientlink.DownloadLinkMeta;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 迅雷协议链接生成器
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class ThunderLinkGenerator implements ClientLinkGenerator {
    
    @Override
    public String generate(DownloadLinkMeta meta) {
        if (!supports(meta)) {
            return null;
        }
        
        try {
            // 迅雷链接格式：thunder://Base64(AA + 原URL + ZZ)
            String originalUrl = meta.getUrl();
            String thunderUrl = "AA" + originalUrl + "ZZ";
            
            // Base64编码
            String encodedUrl = Base64.getEncoder().encodeToString(
                thunderUrl.getBytes(StandardCharsets.UTF_8)
            );
            
            return "thunder://" + encodedUrl;
            
        } catch (Exception e) {
            // 如果编码失败，返回null
            return null;
        }
    }
    
    @Override
    public ClientLinkType getType() {
        return ClientLinkType.THUNDER;
    }
}
