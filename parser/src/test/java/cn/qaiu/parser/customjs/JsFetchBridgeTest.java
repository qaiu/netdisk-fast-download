package cn.qaiu.parser.customjs;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.parser.custom.CustomParserConfig;
import cn.qaiu.parser.custom.CustomParserRegistry;
import io.vertx.core.Vertx;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetch Bridge测试
 * 测试fetch API和Promise polyfill功能
 */
public class JsFetchBridgeTest {
    
    private static final Logger log = LoggerFactory.getLogger(JsFetchBridgeTest.class);
    
    @Test
    public void testFetchPolyfillLoaded() {
        // 初始化Vertx
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        // 清理注册表
        CustomParserRegistry.clear();
        
        // 创建一个简单的解析器配置
        String jsCode = """
            // 测试Promise是否可用
            function parse(shareLinkInfo, http, logger) {
                logger.info("测试开始");
                
                // 检查Promise是否存在
                if (typeof Promise === 'undefined') {
                    throw new Error("Promise未定义");
                }
                
                // 检查fetch是否存在
                if (typeof fetch === 'undefined') {
                    throw new Error("fetch未定义");
                }
                
                logger.info("✓ Promise已定义");
                logger.info("✓ fetch已定义");
                
                return "https://example.com/success";
            }
            """;
        
        CustomParserConfig config = CustomParserConfig.builder()
                .type("test_fetch")
                .displayName("Fetch测试")
                .matchPattern("https://example.com/s/(?<KEY>\\w+)")
                .jsCode(jsCode)
                .isJsParser(true)
                .build();
        
        // 注册到注册表
        CustomParserRegistry.register(config);
        
        try {
            // 使用ParserCreate创建工具
            IPanTool tool = ParserCreate.fromType("test_fetch")
                    .shareKey("test123")
                    .createTool();
            
            String result = tool.parseSync();
            
            log.info("测试结果: {}", result);
            assert "https://example.com/success".equals(result) : "结果不匹配";
            
            System.out.println("✓ Fetch polyfill加载测试通过");
            
        } catch (Exception e) {
            log.error("测试失败", e);
            throw new RuntimeException("Fetch polyfill加载失败: " + e.getMessage(), e);
        }
    }
    
    @Test
    public void testPromiseBasicUsage() {
        // 初始化Vertx
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        // 清理注册表
        CustomParserRegistry.clear();
        
        String jsCode = """
            function parse(shareLinkInfo, http, logger) {
                logger.info("测试Promise基本用法");
                
                // 创建一个Promise
                var testPromise = new Promise(function(resolve, reject) {
                    resolve("Promise成功");
                });
                
                var result = null;
                testPromise.then(function(value) {
                    logger.info("Promise结果: " + value);
                    result = value;
                });
                
                // 等待Promise完成（简单同步等待）
                var timeout = 1000;
                var start = Date.now();
                while (result === null && (Date.now() - start) < timeout) {
                    java.lang.Thread.sleep(10);
                }
                
                if (result === null) {
                    throw new Error("Promise未完成");
                }
                
                return "https://example.com/" + result;
            }
            """;
        
        CustomParserConfig config = CustomParserConfig.builder()
                .type("test_promise")
                .displayName("Promise测试")
                .matchPattern("https://example.com/s/(?<KEY>\\w+)")
                .jsCode(jsCode)
                .isJsParser(true)
                .build();
        
        // 注册到注册表
        CustomParserRegistry.register(config);
        
        try {
            // 使用ParserCreate创建工具
            IPanTool tool = ParserCreate.fromType("test_promise")
                    .shareKey("test456")
                    .createTool();
            
            String result = tool.parseSync();
            
            log.info("测试结果: {}", result);
            assert result.contains("Promise成功") : "结果不包含'Promise成功'";
            
            System.out.println("✓ Promise测试通过");
            
        } catch (Exception e) {
            log.error("测试失败", e);
            throw new RuntimeException("Promise测试失败: " + e.getMessage(), e);
        }
    }
}
