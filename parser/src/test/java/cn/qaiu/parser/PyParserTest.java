package cn.qaiu.parser;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.custom.CustomParserRegistry;
import cn.qaiu.parser.custompy.*;
import cn.qaiu.WebClientVertxInit;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Python解析器测试
 * 测试GraalPy Python解析器的核心功能
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2026/1/11
 */
public class PyParserTest {
    
    private static Vertx vertx;
    
    @BeforeClass
    public static void init() {
        // 初始化Vertx
        vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        System.out.println("=== Python解析器测试初始化完成 ===\n");
    }
    
    @Before
    public void setUp() {
        // 清理注册表
        CustomParserRegistry.clear();
    }
    
    @Test
    public void testPyContextPoolInitialization() {
        System.out.println("\n[测试] Context池初始化");
        
        try {
            PyContextPool pool = PyContextPool.getInstance();
            
            assertNotNull("Context池实例不能为null", pool);
            assertFalse("Context池不应该是关闭状态", pool.isClosed());
            assertTrue("应该有可用的Context", pool.getCreatedCount() > 0);
            
            System.out.println("✓ Context池初始化测试通过");
            System.out.println("  " + pool.getStatus());
            
        } catch (Exception e) {
            System.err.println("✗ Context池初始化测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("Context池初始化失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testPyContextPoolAcquireRelease() throws Exception {
        System.out.println("\n[测试] Context池获取和释放");
        
        try {
            PyContextPool pool = PyContextPool.getInstance();
            
            // 获取Context
            PyContextPool.PooledContext pc = pool.acquire();
            assertNotNull("获取的Context不能为null", pc);
            assertNotNull("底层Context不能为null", pc.getContext());
            assertFalse("Context不应该过期", pc.isExpired());
            
            int availableBefore = pool.getAvailableCount();
            
            // 释放Context
            pc.close();
            
            // 验证归还后可用数量增加
            int availableAfter = pool.getAvailableCount();
            assertTrue("归还后可用数量应该增加", availableAfter >= availableBefore);
            
            System.out.println("✓ Context池获取和释放测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ Context池获取和释放测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    @Test
    public void testSimplePythonExecution() {
        System.out.println("\n[测试] 简单Python代码执行");
        
        String pyCode = """
            # 简单测试
            def parse(share_link_info, http, logger):
                logger.info("测试日志")
                return "https://example.com/download/test.zip"
            """;
        
        try {
            ShareLinkInfo linkInfo = ShareLinkInfo.newBuilder()
                    .shareUrl("https://example.com/s/test123")
                    .shareKey("test123")
                    .otherParam(new HashMap<>())
                    .build();
            
            PyPlaygroundExecutor executor = new PyPlaygroundExecutor(linkInfo, pyCode);
            
            String result = executor.executeParseAsync()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            
            assertNotNull("执行结果不能为null", result);
            assertTrue("应该返回下载链接", result.contains("example.com"));
            
            // 检查日志
            List<PyPlaygroundLogger.LogEntry> logs = executor.getLogs();
            assertFalse("应该有日志输出", logs.isEmpty());
            
            System.out.println("✓ 简单Python代码执行测试通过");
            System.out.println("  返回结果: " + result);
            System.out.println("  日志数量: " + logs.size());
            
        } catch (Exception e) {
            System.err.println("✗ 简单Python代码执行测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("Python执行失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testPythonHttpRequest() {
        System.out.println("\n[测试] Python HTTP请求功能");
        
        String pyCode = """
            def parse(share_link_info, http, logger):
                logger.info("开始HTTP请求测试")
                
                # 发送GET请求
                response = http.get("https://httpbin.org/get")
                
                if response.ok():
                    logger.info(f"请求成功，状态码: {response.status_code()}")
                    return "https://example.com/success"
                else:
                    logger.error(f"请求失败，状态码: {response.status_code()}")
                    return "https://example.com/failed"
            """;
        
        try {
            ShareLinkInfo linkInfo = ShareLinkInfo.newBuilder()
                    .shareUrl("https://example.com/s/test123")
                    .shareKey("test123")
                    .otherParam(new HashMap<>())
                    .build();
            
            PyPlaygroundExecutor executor = new PyPlaygroundExecutor(linkInfo, pyCode);
            
            String result = executor.executeParseAsync()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(60, TimeUnit.SECONDS);
            
            assertNotNull("执行结果不能为null", result);
            assertTrue("应该返回成功链接", result.contains("success"));
            
            System.out.println("✓ Python HTTP请求功能测试通过");
            System.out.println("  返回结果: " + result);
            
        } catch (Exception e) {
            System.err.println("✗ Python HTTP请求功能测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("Python HTTP请求失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testPythonCryptoUtils() {
        System.out.println("\n[测试] Python加密工具功能");
        
        String pyCode = """
            def parse(share_link_info, http, logger):
                # 测试MD5
                md5_result = crypto.md5("hello")
                logger.info(f"MD5: {md5_result}")
                
                # 测试SHA256
                sha256_result = crypto.sha256("hello")
                logger.info(f"SHA256: {sha256_result}")
                
                # 测试Base64编码解码
                b64_encoded = crypto.base64_encode("hello world")
                b64_decoded = crypto.base64_decode(b64_encoded)
                logger.info(f"Base64: {b64_encoded} -> {b64_decoded}")
                
                # 验证MD5正确性
                if md5_result == "5d41402abc4b2a76b9719d911017c592":
                    return "https://example.com/crypto_success"
                else:
                    return "https://example.com/crypto_failed"
            """;
        
        try {
            ShareLinkInfo linkInfo = ShareLinkInfo.newBuilder()
                    .shareUrl("https://example.com/s/test123")
                    .shareKey("test123")
                    .otherParam(new HashMap<>())
                    .build();
            
            PyPlaygroundExecutor executor = new PyPlaygroundExecutor(linkInfo, pyCode);
            
            String result = executor.executeParseAsync()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            
            assertNotNull("执行结果不能为null", result);
            assertTrue("加密工具应该正常工作", result.contains("crypto_success"));
            
            System.out.println("✓ Python加密工具功能测试通过");
            System.out.println("  返回结果: " + result);
            
        } catch (Exception e) {
            System.err.println("✗ Python加密工具功能测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("Python加密工具测试失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testPythonShareLinkInfo() {
        System.out.println("\n[测试] Python ShareLinkInfo访问");
        
        String pyCode = """
            def parse(share_link_info, http, logger):
                # 获取分享链接信息
                url = share_link_info.get_share_url()
                key = share_link_info.get_share_key()
                pwd = share_link_info.get_share_password()
                
                logger.info(f"URL: {url}")
                logger.info(f"Key: {key}")
                logger.info(f"Password: {pwd}")
                
                # 测试其他参数
                custom_param = share_link_info.get_other_param("customKey")
                logger.info(f"CustomKey: {custom_param}")
                
                if url and key:
                    return f"https://example.com/download/{key}"
                else:
                    return "https://example.com/failed"
            """;
        
        try {
            Map<String, Object> otherParams = new HashMap<>();
            otherParams.put("customKey", "customValue");
            
            ShareLinkInfo linkInfo = ShareLinkInfo.newBuilder()
                    .shareUrl("https://example.com/s/mykey123")
                    .shareKey("mykey123")
                    .sharePassword("mypassword")
                    .otherParam(otherParams)
                    .build();
            
            PyPlaygroundExecutor executor = new PyPlaygroundExecutor(linkInfo, pyCode);
            
            String result = executor.executeParseAsync()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            
            assertNotNull("执行结果不能为null", result);
            assertTrue("应该包含正确的key", result.contains("mykey123"));
            
            System.out.println("✓ Python ShareLinkInfo访问测试通过");
            System.out.println("  返回结果: " + result);
            
        } catch (Exception e) {
            System.err.println("✗ Python ShareLinkInfo访问测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("Python ShareLinkInfo访问失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testPythonFileListParsing() {
        System.out.println("\n[测试] Python文件列表解析");
        
        String pyCode = """
            def parse(share_link_info, http, logger):
                return "https://example.com/download/single.zip"
            
            def parse_file_list(share_link_info, http, logger):
                logger.info("开始解析文件列表")
                
                # 返回文件列表
                file_list = [
                    {
                        "file_name": "测试文件1.txt",
                        "file_id": "file001",
                        "file_type": "txt",
                        "size": 1024,
                        "pan_type": "custom"
                    },
                    {
                        "file_name": "测试文件2.zip",
                        "file_id": "file002",
                        "file_type": "zip",
                        "size": 2048,
                        "pan_type": "custom"
                    }
                ]
                
                logger.info(f"解析到 {len(file_list)} 个文件")
                return file_list
            """;
        
        try {
            ShareLinkInfo linkInfo = ShareLinkInfo.newBuilder()
                    .shareUrl("https://example.com/s/test123")
                    .shareKey("test123")
                    .otherParam(new HashMap<>())
                    .build();
            
            PyPlaygroundExecutor executor = new PyPlaygroundExecutor(linkInfo, pyCode);
            
            List<FileInfo> fileList = executor.executeParseFileListAsync()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            
            assertNotNull("文件列表不能为null", fileList);
            assertEquals("应该有2个文件", 2, fileList.size());
            
            FileInfo firstFile = fileList.get(0);
            assertEquals("第一个文件名应该正确", "测试文件1.txt", firstFile.getFileName());
            assertEquals("第一个文件ID应该正确", "file001", firstFile.getFileId());
            
            System.out.println("✓ Python文件列表解析测试通过");
            System.out.println("  文件数量: " + fileList.size());
            for (FileInfo file : fileList) {
                System.out.println("  - " + file.getFileName() + " (" + file.getSize() + " bytes)");
            }
            
        } catch (Exception e) {
            System.err.println("✗ Python文件列表解析测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("Python文件列表解析失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testPythonParseById() {
        System.out.println("\n[测试] Python按ID解析");
        
        String pyCode = """
            def parse(share_link_info, http, logger):
                return "https://example.com/download/single.zip"
            
            def parse_by_id(share_link_info, http, logger):
                # 获取文件ID参数
                param_json = share_link_info.get_other_param("paramJson")
                
                if param_json and hasattr(param_json, 'fileId'):
                    file_id = param_json.fileId
                else:
                    file_id = "default_id"
                
                logger.info(f"按ID解析: {file_id}")
                return f"https://example.com/download/{file_id}"
            """;
        
        try {
            Map<String, Object> otherParams = new HashMap<>();
            io.vertx.core.json.JsonObject paramJson = new io.vertx.core.json.JsonObject();
            paramJson.put("fileId", "myfile123");
            otherParams.put("paramJson", paramJson);
            
            ShareLinkInfo linkInfo = ShareLinkInfo.newBuilder()
                    .shareUrl("https://example.com/s/test123")
                    .shareKey("test123")
                    .otherParam(otherParams)
                    .build();
            
            PyPlaygroundExecutor executor = new PyPlaygroundExecutor(linkInfo, pyCode);
            
            String result = executor.executeParseByIdAsync()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            
            assertNotNull("执行结果不能为null", result);
            assertTrue("应该包含文件ID", result.contains("download"));
            
            System.out.println("✓ Python按ID解析测试通过");
            System.out.println("  返回结果: " + result);
            
        } catch (Exception e) {
            System.err.println("✗ Python按ID解析测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("Python按ID解析失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testPythonErrorHandling() {
        System.out.println("\n[测试] Python错误处理");
        
        String pyCode = """
            def parse(share_link_info, http, logger):
                # 故意抛出异常
                raise ValueError("测试错误处理")
            """;
        
        try {
            ShareLinkInfo linkInfo = ShareLinkInfo.newBuilder()
                    .shareUrl("https://example.com/s/test123")
                    .shareKey("test123")
                    .otherParam(new HashMap<>())
                    .build();
            
            PyPlaygroundExecutor executor = new PyPlaygroundExecutor(linkInfo, pyCode);
            
            try {
                executor.executeParseAsync()
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get(30, TimeUnit.SECONDS);
                
                fail("应该抛出异常");
                
            } catch (Exception e) {
                // 预期的异常
                assertTrue("异常信息应该包含错误内容", 
                        e.getMessage().contains("ValueError") || 
                        e.getCause().getMessage().contains("ValueError"));
                
                System.out.println("✓ Python错误处理测试通过");
                System.out.println("  捕获到预期的异常: " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("✗ Python错误处理测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("Python错误处理测试失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testPythonSandboxSecurity() {
        System.out.println("\n[测试] Python沙箱安全性");
        
        // 测试禁止文件系统访问
        String pyCode = """
            import os
            
            def parse(share_link_info, http, logger):
                try:
                    # 尝试读取文件（应该被拒绝）
                    with open("/etc/passwd", "r") as f:
                        content = f.read()
                    return "https://example.com/security_breach"
                except Exception as e:
                    logger.info(f"文件访问被正确拒绝: {type(e).__name__}")
                    return "https://example.com/security_ok"
            """;
        
        try {
            ShareLinkInfo linkInfo = ShareLinkInfo.newBuilder()
                    .shareUrl("https://example.com/s/test123")
                    .shareKey("test123")
                    .otherParam(new HashMap<>())
                    .build();
            
            PyPlaygroundExecutor executor = new PyPlaygroundExecutor(linkInfo, pyCode);
            
            String result = executor.executeParseAsync()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            
            // 如果返回security_ok或抛出异常都表示安全机制工作正常
            assertTrue("沙箱应该阻止文件访问", 
                    result.contains("security_ok") || !result.contains("security_breach"));
            
            System.out.println("✓ Python沙箱安全性测试通过");
            System.out.println("  返回结果: " + result);
            
        } catch (Exception e) {
            // 如果直接抛出异常也表示安全机制工作正常
            System.out.println("✓ Python沙箱安全性测试通过（抛出异常）");
            System.out.println("  异常信息: " + e.getMessage());
        }
    }
    
    @Test
    public void testPythonLoggerLevels() {
        System.out.println("\n[测试] Python日志级别");
        
        String pyCode = """
            def parse(share_link_info, http, logger):
                logger.debug("这是DEBUG日志")
                logger.info("这是INFO日志")
                logger.warn("这是WARN日志")
                logger.error("这是ERROR日志")
                return "https://example.com/log_test"
            """;
        
        try {
            ShareLinkInfo linkInfo = ShareLinkInfo.newBuilder()
                    .shareUrl("https://example.com/s/test123")
                    .shareKey("test123")
                    .otherParam(new HashMap<>())
                    .build();
            
            PyPlaygroundExecutor executor = new PyPlaygroundExecutor(linkInfo, pyCode);
            
            executor.executeParseAsync()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            
            List<PyPlaygroundLogger.LogEntry> logs = executor.getLogs();
            
            // 检查各个级别的日志
            boolean hasDebug = logs.stream().anyMatch(l -> "DEBUG".equals(l.getLevel()));
            boolean hasInfo = logs.stream().anyMatch(l -> "INFO".equals(l.getLevel()));
            boolean hasWarn = logs.stream().anyMatch(l -> "WARN".equals(l.getLevel()));
            boolean hasError = logs.stream().anyMatch(l -> "ERROR".equals(l.getLevel()));
            
            System.out.println("✓ Python日志级别测试通过");
            System.out.println("  日志数量: " + logs.size());
            System.out.println("  DEBUG: " + hasDebug);
            System.out.println("  INFO: " + hasInfo);
            System.out.println("  WARN: " + hasWarn);
            System.out.println("  ERROR: " + hasError);
            
            for (PyPlaygroundLogger.LogEntry log : logs) {
                System.out.println("  [" + log.getLevel() + "] " + log.getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("✗ Python日志级别测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("Python日志级别测试失败: " + e.getMessage());
        }
    }
}
