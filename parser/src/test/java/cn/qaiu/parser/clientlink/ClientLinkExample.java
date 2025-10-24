package cn.qaiu.parser.clientlink;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.parser.clientlink.ClientLinkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 客户端下载链接生成器使用示例
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class ClientLinkExample {
    
    private static final Logger log = LoggerFactory.getLogger(ClientLinkExample.class);
    
    /**
     * 示例1：使用新的 parseWithClientLinks 方法
     */
    public static void example1() {
        try {
            // 创建解析器
            IPanTool tool = ParserCreate.fromShareUrl("https://cowtransfer.com/s/abc123")
                .createTool();
            
            // 解析并生成客户端链接
            Map<ClientLinkType, String> clientLinks = tool.parseWithClientLinksSync();
            
            // 输出生成的链接
            log.info("=== 生成的客户端下载链接 ===");
            for (Map.Entry<ClientLinkType, String> entry : clientLinks.entrySet()) {
                log.info("{}: {}", entry.getKey().getDisplayName(), entry.getValue());
            }
            
        } catch (Exception e) {
            log.error("示例1执行失败", e);
        }
    }
    
    /**
     * 示例2：传统方式 + 手动生成客户端链接
     */
    public static void example2() {
        try {
            // 创建解析器
            IPanTool tool = ParserCreate.fromShareUrl("https://cowtransfer.com/s/abc123")
                .createTool();
            
            // 解析获取直链
            String directLink = tool.parseSync();
            log.info("直链: {}", directLink);
            
            // 获取 ShareLinkInfo
            ShareLinkInfo shareLinkInfo = tool.getShareLinkInfo();
            
            // 手动生成客户端链接
            Map<ClientLinkType, String> clientLinks = 
                ClientLinkGeneratorFactory.generateAll(shareLinkInfo);
            
            // 输出生成的链接
            log.info("=== 手动生成的客户端下载链接 ===");
            for (Map.Entry<ClientLinkType, String> entry : clientLinks.entrySet()) {
                log.info("{}: {}", entry.getKey().getDisplayName(), entry.getValue());
            }
            
        } catch (Exception e) {
            log.error("示例2执行失败", e);
        }
    }
    
    /**
     * 示例3：生成特定类型的客户端链接
     */
    public static void example3() {
        try {
            // 创建解析器
            IPanTool tool = ParserCreate.fromShareUrl("https://cowtransfer.com/s/abc123")
                .createTool();
            
            // 解析获取直链
            String directLink = tool.parseSync();
            log.info("直链: {}", directLink);
            
            // 获取 ShareLinkInfo
            ShareLinkInfo shareLinkInfo = tool.getShareLinkInfo();
            
            // 生成特定类型的链接
            String curlCommand = ClientLinkGeneratorFactory.generate(shareLinkInfo, ClientLinkType.CURL);
            String thunderLink = ClientLinkGeneratorFactory.generate(shareLinkInfo, ClientLinkType.THUNDER);
            String aria2Command = ClientLinkGeneratorFactory.generate(shareLinkInfo, ClientLinkType.ARIA2);
            
            log.info("=== 特定类型的客户端链接 ===");
            log.info("cURL命令: {}", curlCommand);
            log.info("迅雷链接: {}", thunderLink);
            log.info("Aria2命令: {}", aria2Command);
            
        } catch (Exception e) {
            log.error("示例3执行失败", e);
        }
    }
    
    /**
     * 示例4：使用便捷工具类
     */
    public static void example4() {
        try {
            // 创建解析器
            IPanTool tool = ParserCreate.fromShareUrl("https://cowtransfer.com/s/abc123")
                .createTool();
            
            // 解析获取直链
            String directLink = tool.parseSync();
            log.info("直链: {}", directLink);
            
            // 获取 ShareLinkInfo
            ShareLinkInfo shareLinkInfo = tool.getShareLinkInfo();
            
            // 使用便捷工具类
            String curlCommand = ClientLinkUtils.generateCurlCommand(shareLinkInfo);
            String wgetCommand = ClientLinkUtils.generateWgetCommand(shareLinkInfo);
            String thunderLink = ClientLinkUtils.generateThunderLink(shareLinkInfo);
            
            log.info("=== 使用便捷工具类生成的链接 ===");
            log.info("cURL命令: {}", curlCommand);
            log.info("wget命令: {}", wgetCommand);
            log.info("迅雷链接: {}", thunderLink);
            
        } catch (Exception e) {
            log.error("示例4执行失败", e);
        }
    }
    
    public static void main(String[] args) {
        log.info("开始演示客户端下载链接生成器功能");
        
        example1();
        example2();
        example3();
        example4();
        
        log.info("演示完成");
    }
}
