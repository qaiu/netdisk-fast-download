package cn.qaiu.parser.clientlink.impl;

import cn.qaiu.parser.clientlink.ClientLinkGenerator;
import cn.qaiu.parser.clientlink.ClientLinkType;
import cn.qaiu.parser.clientlink.DownloadLinkMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PowerShell 命令生成器
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class PowerShellLinkGenerator implements ClientLinkGenerator {
    
    @Override
    public String generate(DownloadLinkMeta meta) {
        if (!supports(meta)) {
            return null;
        }
        
        List<String> lines = new ArrayList<>();
        
        // 创建 WebRequestSession
        lines.add("$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession");
        
        // 设置 User-Agent（如果存在）
        String userAgent = meta.getUserAgent();
        if (userAgent == null && meta.getHeaders() != null) {
            userAgent = meta.getHeaders().get("User-Agent");
        }
        if (userAgent != null && !userAgent.trim().isEmpty()) {
            lines.add("$session.UserAgent = \"" + escapePowerShellString(userAgent) + "\"");
        }
        
        // 构建 Invoke-WebRequest 命令
        List<String> invokeParams = new ArrayList<>();
        invokeParams.add("Invoke-WebRequest");
        invokeParams.add("-UseBasicParsing");
        invokeParams.add("-Uri \"" + escapePowerShellString(meta.getUrl()) + "\"");
        
        // 添加 WebSession
        invokeParams.add("-WebSession $session");
        
        // 添加请求头
        if (meta.getHeaders() != null && !meta.getHeaders().isEmpty()) {
            List<String> headerLines = new ArrayList<>();
            headerLines.add("-Headers @{");
            
            boolean first = true;
            for (Map.Entry<String, String> entry : meta.getHeaders().entrySet()) {
                if (!first) {
                    headerLines.add("");
                }
                headerLines.add("  \"" + escapePowerShellString(entry.getKey()) + "\"=\"" + 
                              escapePowerShellString(entry.getValue()) + "\"");
                first = false;
            }
            
            headerLines.add("}");
            
            // 将头部参数添加到主命令中
            invokeParams.add(String.join("`\n", headerLines));
        }
        
        // 设置输出文件（如果指定了文件名）
        if (meta.getFileName() != null && !meta.getFileName().trim().isEmpty()) {
            invokeParams.add("-OutFile \"" + escapePowerShellString(meta.getFileName()) + "\"");
        }
        
        // 将所有参数连接起来
        String invokeCommand = String.join(" `\n", invokeParams);
        lines.add(invokeCommand);
        
        return String.join("\n", lines);
    }
    
    /**
     * 转义 PowerShell 字符串中的特殊字符
     */
    private String escapePowerShellString(String str) {
        if (str == null) {
            return "";
        }
        
        return str.replace("`", "``")
                 .replace("\"", "`\"")
                 .replace("$", "`$");
    }
    
    @Override
    public ClientLinkType getType() {
        return ClientLinkType.POWERSHELL;
    }
}
