package cn.qaiu.parser;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.customjs.JsPlaygroundExecutor;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/**
 * JavaScript执行器安全测试
 * 用于验证JavaScript代码执行环境的安全性
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class SecurityTest {

    private static final Logger log = LoggerFactory.getLogger(SecurityTest.class);

    /**
     * 测试1: 尝试通过Java类执行系统命令
     */
    @Test
    public void testSystemCommandExecution() {
        String dangerousJs = """
                // ==UserScript==
                // @name         危险测试-系统命令执行
                // @type         security_test
                // @match        https://test.com/*
                // ==/UserScript==
                
                function parse(shareLinkInfo, http, logger) {
                    logger.info("尝试执行系统命令...");
                    
                    try {
                        // 尝试1: 直接访问Runtime类执行命令
                        var Runtime = Java.type('java.lang.Runtime');
                        var runtime = Runtime.getRuntime();
                        var process = runtime.exec("whoami");
                        var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
                        var output = reader.readLine();
                        logger.error("【安全漏洞】成功执行系统命令: " + output);
                        return "危险: 系统命令执行成功 - " + output;
                    } catch (e) {
                        logger.info("方法1失败: " + e.message);
                    }
                    
                    try {
                        // 尝试2: 使用ProcessBuilder
                        var ProcessBuilder = Java.type('java.lang.ProcessBuilder');
                        var pb = new ProcessBuilder(["ls", "-la"]);
                        var process = pb.start();
                        logger.error("【安全漏洞】ProcessBuilder执行成功");
                        return "危险: ProcessBuilder执行成功";
                    } catch (e) {
                        logger.info("方法2失败: " + e.message);
                    }
                    
                    return "安全: 无法执行系统命令";
                }
                """;

        testJavaScriptSecurity(dangerousJs, "系统命令执行测试");
    }

    /**
     * 测试2: 尝试文件系统访问
     */
    @Test
    public void testFileSystemAccess() {
        String dangerousJs = """
                // ==UserScript==
                // @name         危险测试-文件系统访问
                // @type         security_test
                // @match        https://test.com/*
                // ==/UserScript==
                
                function parse(shareLinkInfo, http, logger) {
                    logger.info("尝试访问文件系统...");
                    
                    try {
                        // 尝试读取敏感文件
                        var Files = Java.type('java.nio.file.Files');
                        var Paths = Java.type('java.nio.file.Paths');
                        var path = Paths.get("/etc/passwd");
                        var content = Files.readAllLines(path);
                        logger.error("【安全漏洞】成功读取文件: " + content.get(0));
                        return "危险: 文件读取成功";
                    } catch (e) {
                        logger.info("方法1失败: " + e.message);
                    }
                    
                    try {
                        // 尝试写入文件
                        var FileWriter = Java.type('java.io.FileWriter');
                        var writer = new FileWriter("/tmp/test.txt");
                        writer.write("test");
                        writer.close();
                        logger.error("【安全漏洞】成功写入文件");
                        return "危险: 文件写入成功";
                    } catch (e) {
                        logger.info("方法2失败: " + e.message);
                    }
                    
                    return "安全: 无法访问文件系统";
                }
                """;

        testJavaScriptSecurity(dangerousJs, "文件系统访问测试");
    }

    /**
     * 测试3: 尝试访问系统属性和环境变量
     */
    @Test
    public void testSystemPropertiesAccess() {
        String dangerousJs = """
                // ==UserScript==
                // @name         危险测试-系统属性访问
                // @type         security_test
                // @match        https://test.com/*
                // ==/UserScript==
                
                function parse(shareLinkInfo, http, logger) {
                    logger.info("尝试访问系统属性...");
                    
                    try {
                        // 尝试读取系统属性
                        var System = Java.type('java.lang.System');
                        var userHome = System.getProperty("user.home");
                        var userName = System.getProperty("user.name");
                        logger.error("【安全漏洞】获取到系统属性 - HOME: " + userHome + ", USER: " + userName);
                        return "危险: 系统属性访问成功 - " + userName;
                    } catch (e) {
                        logger.info("方法1失败: " + e.message);
                    }
                    
                    try {
                        // 尝试读取环境变量
                        var System = Java.type('java.lang.System');
                        var env = System.getenv();
                        var path = env.get("PATH");
                        logger.error("【安全漏洞】获取到环境变量 PATH: " + path);
                        return "危险: 环境变量访问成功";
                    } catch (e) {
                        logger.info("方法2失败: " + e.message);
                    }
                    
                    return "安全: 无法访问系统属性";
                }
                """;

        testJavaScriptSecurity(dangerousJs, "系统属性访问测试");
    }

    /**
     * 测试4: 尝试反射攻击
     */
    @Test
    public void testReflectionAttack() {
        String dangerousJs = """
                // ==UserScript==
                // @name         危险测试-反射攻击
                // @type         security_test
                // @match        https://test.com/*
                // ==/UserScript==
                
                function parse(shareLinkInfo, http, logger) {
                    logger.info("尝试使用反射...");
                    
                    try {
                        // 尝试通过反射访问私有字段
                        var Class = Java.type('java.lang.Class');
                        var Field = Java.type('java.lang.reflect.Field');
                        
                        var systemClass = Class.forName("java.lang.System");
                        var methods = systemClass.getDeclaredMethods();
                        
                        logger.error("【安全漏洞】反射访问成功，获取到 " + methods.length + " 个方法");
                        return "危险: 反射访问成功";
                    } catch (e) {
                        logger.info("方法1失败: " + e.message);
                    }
                    
                    try {
                        // 尝试获取ClassLoader
                        var Thread = Java.type('java.lang.Thread');
                        var classLoader = Thread.currentThread().getContextClassLoader();
                        logger.error("【安全漏洞】获取到ClassLoader: " + classLoader);
                        return "危险: ClassLoader访问成功";
                    } catch (e) {
                        logger.info("方法2失败: " + e.message);
                    }
                    
                    return "安全: 无法使用反射";
                }
                """;

        testJavaScriptSecurity(dangerousJs, "反射攻击测试");
    }

    /**
     * 测试5: 尝试网络攻击
     */
    @Test
    public void testNetworkAttack() {
        String dangerousJs = """
                // ==UserScript==
                // @name         危险测试-网络攻击
                // @type         security_test
                // @match        https://test.com/*
                // ==/UserScript==
                
                function parse(shareLinkInfo, http, logger) {
                    logger.info("尝试发起网络连接...");
                    
                    try {
                        // 尝试创建Socket连接
                        var Socket = Java.type('java.net.Socket');
                        var socket = new Socket("127.0.0.1", 22);
                        logger.error("【安全漏洞】Socket连接成功");
                        socket.close();
                        return "危险: Socket连接成功";
                    } catch (e) {
                        logger.info("方法1失败: " + e.message);
                    }
                    
                    try {
                        // 尝试使用URL访问
                        var URL = Java.type('java.net.URL');
                        var url = new URL("http://localhost:8080");
                        var conn = url.openConnection();
                        logger.error("【安全漏洞】URL连接成功");
                        return "危险: URL连接成功";
                    } catch (e) {
                        logger.info("方法2失败: " + e.message);
                    }
                    
                    return "安全: 无法创建网络连接";
                }
                """;

        testJavaScriptSecurity(dangerousJs, "网络攻击测试");
    }

    /**
     * 测试6: 尝试退出JVM
     */
    @Test
    public void testJvmExit() {
        String dangerousJs = """
                // ==UserScript==
                // @name         危险测试-JVM退出
                // @type         security_test
                // @match        https://test.com/*
                // ==/UserScript==
                
                function parse(shareLinkInfo, http, logger) {
                    logger.info("尝试退出JVM...");
                    
                    try {
                        // 尝试退出JVM
                        var System = Java.type('java.lang.System');
                        logger.warn("准备执行 System.exit(1)...");
                        System.exit(1);
                        return "危险: JVM退出成功";
                    } catch (e) {
                        logger.info("退出失败: " + e.message);
                    }
                    
                    try {
                        // 尝试终止运行时
                        var Runtime = Java.type('java.lang.Runtime');
                        Runtime.getRuntime().halt(1);
                        return "危险: Runtime.halt成功";
                    } catch (e) {
                        logger.info("halt失败: " + e.message);
                    }
                    
                    return "安全: 无法退出JVM";
                }
                """;

        testJavaScriptSecurity(dangerousJs, "JVM退出测试");
    }

    /**
     * 测试7: 尝试访问注入的httpClient执行任意HTTP请求
     */
    @Test
    public void testHttpClientAbuse() {
        String dangerousJs = """
                // ==UserScript==
                // @name         危险测试-HTTP客户端滥用
                // @type         security_test
                // @match        https://test.com/*
                // ==/UserScript==
                
                function parse(shareLinkInfo, http, logger) {
                    logger.info("测试HTTP客户端访问控制...");
                    
                    try {
                        // 尝试访问内网地址
                        logger.info("尝试访问内网地址...");
                        var response = http.get("http://127.0.0.1:8080/admin");
                        logger.warn("【潜在风险】可以访问内网地址: " + response.substring(0, 50));
                        return "警告: 可以通过HTTP访问内网";
                    } catch (e) {
                        logger.info("内网访问失败: " + e.message);
                    }
                    
                    try {
                        // 尝试访问敏感API
                        logger.info("尝试访问云服务元数据API...");
                        var response = http.get("http://169.254.169.254/latest/meta-data/");
                        logger.error("【严重漏洞】可以访问云服务元数据: " + response);
                        return "危险: 可以访问云服务元数据";
                    } catch (e) {
                        logger.info("元数据访问失败: " + e.message);
                    }
                    
                    return "提示: HTTP客户端访问受限";
                }
                """;

        testJavaScriptSecurity(dangerousJs, "HTTP客户端滥用测试");
    }

    /**
     * 执行JavaScript安全测试的辅助方法
     */
    private void testJavaScriptSecurity(String jsCode, String testName) {
        log.info("\n" + "=".repeat(80));
        log.info("开始执行安全测试: {}", testName);
        log.info("=".repeat(80));

        try {
            // 创建测试用的ShareLinkInfo
            ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                    .shareKey("test_key")
                    .sharePassword("test_pwd")
                    .type("security_test")
                    .shareUrl("https://test.com/share/test")
                    .standardUrl("https://test.com/share/test")
                    .otherParam(new HashMap<>())
                    .build();

            // 创建执行器并执行
            JsPlaygroundExecutor executor = new JsPlaygroundExecutor(shareLinkInfo, jsCode);
            
            executor.executeParseAsync()
                .onSuccess(result -> {
                    log.info("测试结果: {}", result);
                    
                    // 打印所有日志
                    log.info("\n执行日志:");
                    executor.getLogs().forEach(logEntry -> {
                        String logLevel = logEntry.getLevel();
                        String message = logEntry.getMessage();
                        log.info("[{}] [{}] {}", logLevel, logEntry.getSource(), message);
                        
                        // 检查是否有安全漏洞警告
                        if (message.contains("【安全漏洞】") || message.contains("【严重漏洞】")) {
                            log.error("!!! 发现安全漏洞 !!!");
                        }
                    });
                })
                .onFailure(e -> {
                    log.info("执行失败: {}", e.getMessage());
                    
                    // 打印所有日志
                    log.info("\n执行日志:");
                    executor.getLogs().forEach(logEntry -> {
                        log.info("[{}] [{}] {}", 
                            logEntry.getLevel(), 
                            logEntry.getSource(), 
                            logEntry.getMessage());
                    });
                })
                .toCompletionStage()
                .toCompletableFuture()
                .join(); // 等待异步执行完成

        } catch (Exception e) {
            log.error("测试执行异常", e);
        }

        log.info("=".repeat(80));
        log.info("测试完成: {}\n", testName);
    }
}

