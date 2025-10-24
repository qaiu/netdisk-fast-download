package cn.qaiu.parser.clientlink;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.clientlink.ClientLinkType;
import cn.qaiu.parser.clientlink.DownloadLinkMeta;
import cn.qaiu.parser.clientlink.impl.PowerShellLinkGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * PowerShell 生成器示例
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class PowerShellExample {
    
    public static void main(String[] args) {
        // 创建测试数据
        DownloadLinkMeta meta = new DownloadLinkMeta("https://example.com/file.zip");
        meta.setFileName("test-file.zip");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Referer", "https://example.com/share/test");
        headers.put("Cookie", "session=abc123");
        headers.put("Accept", "text/html,application/xhtml+xml");
        meta.setHeaders(headers);
        
        // 生成 PowerShell 命令
        PowerShellLinkGenerator generator = new PowerShellLinkGenerator();
        String powershellCommand = generator.generate(meta);
        
        System.out.println("=== 生成的 PowerShell 命令 ===");
        System.out.println(powershellCommand);
        System.out.println();
        
        // 测试特殊字符转义
        meta.setUrl("https://example.com/file with spaces.zip");
        Map<String, String> specialHeaders = new HashMap<>();
        specialHeaders.put("Custom-Header", "Value with \"quotes\" and $variables");
        meta.setHeaders(specialHeaders);
        
        String escapedCommand = generator.generate(meta);
        
        System.out.println("=== 包含特殊字符的 PowerShell 命令 ===");
        System.out.println(escapedCommand);
        System.out.println();
        
        // 使用 ClientLinkUtils
        ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                .type("test")
                .panName("测试网盘")
                .shareUrl("https://example.com/share/test")
                .build();
        
        Map<String, Object> otherParam = new HashMap<>();
        otherParam.put("downloadUrl", "https://example.com/file.zip");
        otherParam.put("downloadHeaders", headers);
        shareLinkInfo.setOtherParam(otherParam);
        
        String utilsCommand = ClientLinkUtils.generatePowerShellCommand(shareLinkInfo);
        
        System.out.println("=== 使用 ClientLinkUtils 生成的 PowerShell 命令 ===");
        System.out.println(utilsCommand);
    }
}
