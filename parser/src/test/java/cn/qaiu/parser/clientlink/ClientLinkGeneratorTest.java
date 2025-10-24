package cn.qaiu.parser.clientlink;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.clientlink.ClientLinkType;
import cn.qaiu.parser.clientlink.DownloadLinkMeta;
import cn.qaiu.parser.clientlink.impl.CurlLinkGenerator;
import cn.qaiu.parser.clientlink.impl.ThunderLinkGenerator;
import cn.qaiu.parser.clientlink.impl.Aria2LinkGenerator;
import cn.qaiu.parser.clientlink.impl.PowerShellLinkGenerator;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 客户端链接生成器功能测试
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class ClientLinkGeneratorTest {
    
    private ShareLinkInfo shareLinkInfo;
    private DownloadLinkMeta meta;
    
    @Before
    public void setUp() {
        // 创建测试用的 ShareLinkInfo
        shareLinkInfo = ShareLinkInfo.newBuilder()
                .type("test")
                .panName("测试网盘")
                .shareUrl("https://example.com/share/test")
                .build();
        
        Map<String, Object> otherParam = new HashMap<>();
        otherParam.put("downloadUrl", "https://example.com/file.zip");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Test Browser)");
        headers.put("Referer", "https://example.com/share/test");
        headers.put("Cookie", "session=abc123");
        otherParam.put("downloadHeaders", headers);
        
        shareLinkInfo.setOtherParam(otherParam);
        
        // 创建测试用的 DownloadLinkMeta
        meta = new DownloadLinkMeta("https://example.com/file.zip");
        meta.setFileName("test-file.zip");
        meta.setHeaders(headers);
    }
    
    @Test
    public void testCurlLinkGenerator() {
        CurlLinkGenerator generator = new CurlLinkGenerator();
        
        String result = generator.generate(meta);
        
        assertNotNull("cURL命令不应为空", result);
        assertTrue("应包含curl命令", result.contains("curl"));
        assertTrue("应包含下载URL", result.contains("https://example.com/file.zip"));
        assertTrue("应包含User-Agent头", result.contains("\"User-Agent: Mozilla/5.0 (Test Browser)\""));
        assertTrue("应包含Referer头", result.contains("\"Referer: https://example.com/share/test\""));
        assertTrue("应包含Cookie头", result.contains("\"Cookie: session=abc123\""));
        assertTrue("应包含输出文件名", result.contains("\"test-file.zip\""));
        assertTrue("应包含跟随重定向", result.contains("-L"));
        
        assertEquals("类型应为CURL", ClientLinkType.CURL, generator.getType());
    }
    
    @Test
    public void testThunderLinkGenerator() {
        ThunderLinkGenerator generator = new ThunderLinkGenerator();
        
        String result = generator.generate(meta);
        
        assertNotNull("迅雷链接不应为空", result);
        assertTrue("应以thunder://开头", result.startsWith("thunder://"));
        
        // 验证Base64编码格式
        String encodedPart = result.substring("thunder://".length());
        assertNotNull("编码部分不应为空", encodedPart);
        assertFalse("编码部分不应为空字符串", encodedPart.isEmpty());
        
        assertEquals("类型应为THUNDER", ClientLinkType.THUNDER, generator.getType());
    }
    
    @Test
    public void testAria2LinkGenerator() {
        Aria2LinkGenerator generator = new Aria2LinkGenerator();
        
        String result = generator.generate(meta);
        
        assertNotNull("Aria2命令不应为空", result);
        assertTrue("应包含aria2c命令", result.contains("aria2c"));
        assertTrue("应包含下载URL", result.contains("https://example.com/file.zip"));
        assertTrue("应包含User-Agent头", result.contains("--header=\"User-Agent: Mozilla/5.0 (Test Browser)\""));
        assertTrue("应包含Referer头", result.contains("--header=\"Referer: https://example.com/share/test\""));
        assertTrue("应包含输出文件名", result.contains("--out=\"test-file.zip\""));
        assertTrue("应包含断点续传", result.contains("--continue"));
        
        assertEquals("类型应为ARIA2", ClientLinkType.ARIA2, generator.getType());
    }
    
    @Test
    public void testPowerShellLinkGenerator() {
        PowerShellLinkGenerator generator = new PowerShellLinkGenerator();
        
        String result = generator.generate(meta);
        
        assertNotNull("PowerShell命令不应为空", result);
        assertTrue("应包含WebRequestSession", result.contains("$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession"));
        assertTrue("应包含Invoke-WebRequest", result.contains("Invoke-WebRequest"));
        assertTrue("应包含-UseBasicParsing", result.contains("-UseBasicParsing"));
        assertTrue("应包含下载URL", result.contains("https://example.com/file.zip"));
        assertTrue("应包含User-Agent", result.contains("User-Agent"));
        assertTrue("应包含Referer", result.contains("Referer"));
        assertTrue("应包含Cookie", result.contains("Cookie"));
        assertTrue("应包含输出文件", result.contains("test-file.zip"));
        
        assertEquals("类型应为POWERSHELL", ClientLinkType.POWERSHELL, generator.getType());
    }
    
    @Test
    public void testPowerShellLinkGeneratorWithoutHeaders() {
        PowerShellLinkGenerator generator = new PowerShellLinkGenerator();
        
        meta.setHeaders(new HashMap<>());
        String result = generator.generate(meta);
        
        assertNotNull("PowerShell命令不应为空", result);
        assertTrue("应包含WebRequestSession", result.contains("$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession"));
        assertTrue("应包含Invoke-WebRequest", result.contains("Invoke-WebRequest"));
        assertTrue("应包含下载URL", result.contains("https://example.com/file.zip"));
        assertFalse("不应包含Headers", result.contains("-Headers @{"));
    }
    
    @Test
    public void testPowerShellLinkGeneratorWithoutFileName() {
        PowerShellLinkGenerator generator = new PowerShellLinkGenerator();
        
        meta.setFileName(null);
        String result = generator.generate(meta);
        
        assertNotNull("PowerShell命令不应为空", result);
        assertTrue("应包含WebRequestSession", result.contains("$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession"));
        assertTrue("应包含Invoke-WebRequest", result.contains("Invoke-WebRequest"));
        assertTrue("应包含下载URL", result.contains("https://example.com/file.zip"));
        assertFalse("不应包含OutFile", result.contains("-OutFile"));
    }
    
    @Test
    public void testPowerShellLinkGeneratorWithSpecialCharacters() {
        PowerShellLinkGenerator generator = new PowerShellLinkGenerator();
        
        // 测试包含特殊字符的URL和请求头
        meta.setUrl("https://example.com/file with spaces.zip");
        Map<String, String> specialHeaders = new HashMap<>();
        specialHeaders.put("Custom-Header", "Value with \"quotes\" and $variables");
        meta.setHeaders(specialHeaders);
        
        String result = generator.generate(meta);
        
        assertNotNull("PowerShell命令不应为空", result);
        assertTrue("应包含转义的URL", result.contains("https://example.com/file with spaces.zip"));
        assertTrue("应包含转义的请求头", result.contains("Custom-Header"));
        assertTrue("应包含转义的引号", result.contains("`\""));
    }
    
    @Test
    public void testDownloadLinkMetaFromShareLinkInfo() {
        DownloadLinkMeta metaFromInfo = DownloadLinkMeta.fromShareLinkInfo(shareLinkInfo);
        
        assertNotNull("从ShareLinkInfo创建的DownloadLinkMeta不应为空", metaFromInfo);
        assertEquals("URL应匹配", "https://example.com/file.zip", metaFromInfo.getUrl());
        assertEquals("Referer应匹配", "https://example.com/share/test", metaFromInfo.getReferer());
        assertEquals("User-Agent应匹配", "Mozilla/5.0 (Test Browser)", metaFromInfo.getUserAgent());
        
        Map<String, String> headers = metaFromInfo.getHeaders();
        assertNotNull("请求头不应为空", headers);
        assertEquals("请求头数量应匹配", 3, headers.size());
        assertEquals("User-Agent应匹配", "Mozilla/5.0 (Test Browser)", headers.get("User-Agent"));
        assertEquals("Referer应匹配", "https://example.com/share/test", headers.get("Referer"));
        assertEquals("Cookie应匹配", "session=abc123", headers.get("Cookie"));
    }
    
    @Test
    public void testClientLinkGeneratorFactory() {
        Map<ClientLinkType, String> allLinks = ClientLinkGeneratorFactory.generateAll(shareLinkInfo);
        
        assertNotNull("生成的链接集合不应为空", allLinks);
        assertFalse("生成的链接集合不应为空", allLinks.isEmpty());
        
        // 检查是否生成了主要类型的链接
        assertTrue("应生成cURL链接", allLinks.containsKey(ClientLinkType.CURL));
        assertTrue("应生成迅雷链接", allLinks.containsKey(ClientLinkType.THUNDER));
        assertTrue("应生成Aria2链接", allLinks.containsKey(ClientLinkType.ARIA2));
        assertTrue("应生成wget链接", allLinks.containsKey(ClientLinkType.WGET));
        assertTrue("应生成PowerShell链接", allLinks.containsKey(ClientLinkType.POWERSHELL));
        
        // 验证生成的链接不为空
        assertNotNull("cURL链接不应为空", allLinks.get(ClientLinkType.CURL));
        assertNotNull("迅雷链接不应为空", allLinks.get(ClientLinkType.THUNDER));
        assertNotNull("Aria2链接不应为空", allLinks.get(ClientLinkType.ARIA2));
        assertNotNull("wget链接不应为空", allLinks.get(ClientLinkType.WGET));
        assertNotNull("PowerShell链接不应为空", allLinks.get(ClientLinkType.POWERSHELL));
        
        assertFalse("cURL链接不应为空字符串", allLinks.get(ClientLinkType.CURL).trim().isEmpty());
        assertFalse("迅雷链接不应为空字符串", allLinks.get(ClientLinkType.THUNDER).trim().isEmpty());
        assertFalse("Aria2链接不应为空字符串", allLinks.get(ClientLinkType.ARIA2).trim().isEmpty());
        assertFalse("wget链接不应为空字符串", allLinks.get(ClientLinkType.WGET).trim().isEmpty());
        assertFalse("PowerShell链接不应为空字符串", allLinks.get(ClientLinkType.POWERSHELL).trim().isEmpty());
    }
    
    @Test
    public void testClientLinkUtils() {
        String curlCommand = ClientLinkUtils.generateCurlCommand(shareLinkInfo);
        String thunderLink = ClientLinkUtils.generateThunderLink(shareLinkInfo);
        String aria2Command = ClientLinkUtils.generateAria2Command(shareLinkInfo);
        String powershellCommand = ClientLinkUtils.generatePowerShellCommand(shareLinkInfo);
        
        assertNotNull("cURL命令不应为空", curlCommand);
        assertNotNull("迅雷链接不应为空", thunderLink);
        assertNotNull("Aria2命令不应为空", aria2Command);
        assertNotNull("PowerShell命令不应为空", powershellCommand);
        
        assertTrue("cURL命令应包含curl", curlCommand.contains("curl"));
        assertTrue("迅雷链接应以thunder://开头", thunderLink.startsWith("thunder://"));
        assertTrue("Aria2命令应包含aria2c", aria2Command.contains("aria2c"));
        assertTrue("PowerShell命令应包含Invoke-WebRequest", powershellCommand.contains("Invoke-WebRequest"));
        
        // 测试元数据有效性检查
        assertTrue("应检测到有效的下载元数据", ClientLinkUtils.hasValidDownloadMeta(shareLinkInfo));
        
        // 测试无效元数据
        ShareLinkInfo emptyInfo = ShareLinkInfo.newBuilder().build();
        assertFalse("应检测到无效的下载元数据", ClientLinkUtils.hasValidDownloadMeta(emptyInfo));
    }
    
    @Test
    public void testNullAndEmptyHandling() {
        // 测试空URL
        DownloadLinkMeta emptyMeta = new DownloadLinkMeta("");
        CurlLinkGenerator generator = new CurlLinkGenerator();
        
        String result = generator.generate(emptyMeta);
        assertNull("空URL应返回null", result);
        
        // 测试null元数据
        result = generator.generate(null);
        assertNull("null元数据应返回null", result);
        
        // 测试null ShareLinkInfo
        String curlResult = ClientLinkUtils.generateCurlCommand(null);
        assertNull("null ShareLinkInfo应返回null", curlResult);
        
        Map<ClientLinkType, String> allResult = ClientLinkUtils.generateAllClientLinks(null);
        assertTrue("null ShareLinkInfo应返回空集合", allResult.isEmpty());
    }
}
