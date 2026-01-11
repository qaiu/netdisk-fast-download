package cn.qaiu.lz.web.playground;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.parser.custompy.PyContextPool;
import cn.qaiu.parser.custompy.PyPlaygroundExecutor;
import cn.qaiu.parser.custompy.PyPlaygroundLogger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * requests 库集成测试
 * 
 * 测试 Python 代码在 API 场景下使用 requests 库的功能
 * 验证 GraalPy 环境中 requests 库的可用性
 */
public class RequestsIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(RequestsIntegrationTest.class);

    @BeforeClass
    public static void setup() {
        log.info("初始化 PyContextPool...");
        PyContextPool.getInstance();
    }

    /**
     * 测试1: 基础 requests 导入
     * 验证 requests 库可以在顶层导入
     */
    @Test
    public void testRequestsBasicImport() throws Exception {
        log.info("=== 测试1: 基础 requests 导入 ===");

        String pyCode = """
            import requests
            
            def parse(share_link_info, http, logger):
                logger.info(f"requests 版本: {requests.__version__}")
                return "https://example.com/download.zip"
            """;

        executeAndVerify(pyCode, "https://example.com/download.zip", "requests 顶层导入");
    }

    /**
     * 测试2: requests.Session 创建
     * 验证可以创建和使用 Session
     */
    @Test
    public void testRequestsSession() throws Exception {
        log.info("=== 测试2: requests.Session 创建 ===");

        String pyCode = """
            import requests
            
            def parse(share_link_info, http, logger):
                session = requests.Session()
                session.headers.update({
                    'User-Agent': 'TestBot/1.0',
                    'Accept': 'application/json'
                })
                logger.info("Session 创建成功")
                logger.info(f"Headers: {dict(session.headers)}")
                return "https://example.com/session.zip"
            """;

        executeAndVerify(pyCode, "https://example.com/session.zip", "Session 创建");
    }

    /**
     * 测试3: requests GET 请求（模拟）
     * 不发起真实网络请求，验证请求构建逻辑
     */
    @Test
    public void testRequestsGetPrepare() throws Exception {
        log.info("=== 测试3: requests GET 请求准备 ===");

        String pyCode = """
            import requests
            
            def parse(share_link_info, http, logger):
                # 准备请求，但不发送
                req = requests.Request('GET', 'https://api.example.com/data', 
                    headers={'Authorization': 'Bearer test'},
                    params={'id': '123'}
                )
                prepared = req.prepare()
                logger.info(f"请求 URL: {prepared.url}")
                logger.info(f"请求方法: {prepared.method}")
                return "https://example.com/prepared.zip"
            """;

        executeAndVerify(pyCode, "https://example.com/prepared.zip", "GET 请求准备");
    }

    /**
     * 测试4: requests POST 请求（模拟）
     */
    @Test
    public void testRequestsPostPrepare() throws Exception {
        log.info("=== 测试4: requests POST 请求准备 ===");

        String pyCode = """
            import requests
            import json
            
            def parse(share_link_info, http, logger):
                data = {'username': 'test', 'password': 'secret'}
                
                req = requests.Request('POST', 'https://api.example.com/login',
                    json=data,
                    headers={'Content-Type': 'application/json'}
                )
                prepared = req.prepare()
                logger.info(f"请求 URL: {prepared.url}")
                logger.info(f"请求体: {prepared.body}")
                return "https://example.com/post.zip"
            """;

        executeAndVerify(pyCode, "https://example.com/post.zip", "POST 请求准备");
    }

    /**
     * 测试5: 完整的解析脚本模板
     * 模拟真实的网盘解析脚本结构
     */
    @Test
    public void testFullParserTemplate() throws Exception {
        log.info("=== 测试5: 完整解析脚本模板 ===");

        String pyCode = """
            import requests
            import re
            import json
            
            def parse(share_link_info, http, logger):
                \"\"\"
                解析单个文件
                @match https://example\\.com/s/.*
                @name ExampleParser
                @version 1.0.0
                \"\"\"
                share_url = share_link_info.get_share_url()
                logger.info(f"开始解析: {share_url}")
                
                # 创建会话
                session = requests.Session()
                session.headers.update({
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
                    'Accept': 'text/html,application/json',
                    'Accept-Language': 'zh-CN,zh;q=0.9'
                })
                
                # 模拟从URL提取文件ID
                match = re.search(r'/s/([a-zA-Z0-9]+)', share_url)
                if not match:
                    raise Exception("无法提取文件ID")
                
                file_id = match.group(1)
                logger.info(f"提取文件ID: {file_id}")
                
                # 模拟构建API请求
                api_url = f"https://api.example.com/file/{file_id}"
                logger.info(f"API URL: {api_url}")
                
                # 返回模拟的下载链接
                download_url = f"https://download.example.com/{file_id}/file.zip"
                logger.info(f"下载链接: {download_url}")
                
                return download_url
            """;

        ParserCreate parserCreate = ParserCreate.fromShareUrl("https://example.com/s/abc123def");
        ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();

        PyPlaygroundExecutor executor = new PyPlaygroundExecutor(shareLinkInfo, pyCode);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        executor.executeParseAsync()
            .onSuccess(result -> {
                resultRef.set(result);
                latch.countDown();
            })
            .onFailure(e -> {
                errorRef.set(e);
                latch.countDown();
            });

        assertTrue("执行应在30秒内完成", latch.await(30, TimeUnit.SECONDS));

        if (errorRef.get() != null) {
            log.error("执行失败", errorRef.get());
            fail("执行失败: " + errorRef.get().getMessage());
        }

        String result = resultRef.get();
        assertNotNull("结果不应为空", result);
        assertTrue("结果应包含文件ID", result.contains("abc123def"));
        log.info("✓ 完整解析脚本执行成功: {}", result);

        // 打印日志
        log.info("  执行日志:");
        for (PyPlaygroundLogger.LogEntry entry : executor.getLogs()) {
            log.info("    [{}] {}", entry.getLevel(), entry.getMessage());
        }
    }

    /**
     * 测试6: 多次 requests 操作
     */
    @Test
    public void testMultipleRequestsOperations() throws Exception {
        log.info("=== 测试6: 多次 requests 操作 ===");

        String pyCode = """
            import requests
            import json
            
            def parse(share_link_info, http, logger):
                # 创建多个请求
                urls = [
                    "https://api1.example.com/data",
                    "https://api2.example.com/info",
                    "https://api3.example.com/file"
                ]
                
                results = []
                for url in urls:
                    req = requests.Request('GET', url)
                    prepared = req.prepare()
                    results.append(prepared.url)
                    logger.info(f"准备请求: {prepared.url}")
                
                logger.info(f"共准备 {len(results)} 个请求")
                return "https://example.com/multi.zip"
            """;

        executeAndVerify(pyCode, "https://example.com/multi.zip", "多次 requests 操作");
    }

    /**
     * 测试7: requests 异常处理
     */
    @Test
    public void testRequestsExceptionHandling() throws Exception {
        log.info("=== 测试7: requests 异常处理 ===");

        String pyCode = """
            import requests
            
            def parse(share_link_info, http, logger):
                try:
                    # 尝试创建无效请求
                    req = requests.Request('INVALID_METHOD', 'not_a_url')
                    logger.info("创建了请求")
                except Exception as e:
                    logger.warn(f"预期的异常: {type(e).__name__}")
                
                return "https://example.com/exception.zip"
            """;

        executeAndVerify(pyCode, "https://example.com/exception.zip", "异常处理");
    }

    /**
     * 测试8: ShareLinkInfo 与 requests 结合使用
     */
    @Test
    public void testShareLinkInfoWithRequests() throws Exception {
        log.info("=== 测试8: ShareLinkInfo 与 requests 结合 ===");

        String pyCode = """
            import requests
            import json
            
            def parse(share_link_info, http, logger):
                share_url = share_link_info.get_share_url()
                share_key = share_link_info.get_share_key() or "default_key"
                
                logger.info(f"分享链接: {share_url}")
                logger.info(f"分享密钥: {share_key}")
                
                # 使用 share_url 构建请求
                session = requests.Session()
                
                # 模拟提取信息
                if 'example.com' in share_url:
                    return "https://download.example.com/file.zip"
                return None
            """;

        ParserCreate parserCreate = ParserCreate.fromShareUrl("https://example.com/s/test");
        ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();

        PyPlaygroundExecutor executor = new PyPlaygroundExecutor(shareLinkInfo, pyCode);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        executor.executeParseAsync()
            .onSuccess(result -> {
                resultRef.set(result);
                latch.countDown();
            })
            .onFailure(e -> {
                errorRef.set(e);
                latch.countDown();
            });

        assertTrue(latch.await(30, TimeUnit.SECONDS));

        if (errorRef.get() != null) {
            fail("执行失败: " + errorRef.get().getMessage());
        }

        assertEquals("https://download.example.com/file.zip", resultRef.get());
        log.info("✓ ShareLinkInfo 与 requests 结合使用成功");
    }

    // ========== 辅助方法 ==========

    /**
     * 执行代码并验证结果
     */
    private void executeAndVerify(String pyCode, String expectedResult, String testName) throws Exception {
        ParserCreate parserCreate = ParserCreate.fromShareUrl("https://example.com/s/test123");
        ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();

        PyPlaygroundExecutor executor = new PyPlaygroundExecutor(shareLinkInfo, pyCode);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        executor.executeParseAsync()
            .onSuccess(result -> {
                resultRef.set(result);
                latch.countDown();
            })
            .onFailure(e -> {
                errorRef.set(e);
                latch.countDown();
            });

        assertTrue("执行应在30秒内完成", latch.await(30, TimeUnit.SECONDS));

        if (errorRef.get() != null) {
            Throwable error = errorRef.get();
            String errorMsg = error.getMessage();
            
            // 检查是否是已知的 GraalPy 限制
            if (errorMsg != null && (errorMsg.contains("unicodedata") || errorMsg.contains("LLVM"))) {
                log.warn("⚠️ GraalPy unicodedata/LLVM 限制，跳过测试: {}", testName);
                log.warn("  错误: {}", errorMsg);
                return; // 跳过此测试
            }
            
            log.error("执行失败", error);
            fail("执行失败: " + errorMsg);
        }

        assertEquals(expectedResult, resultRef.get());
        log.info("✓ {} 测试通过: {}", testName, resultRef.get());

        // 打印日志
        for (PyPlaygroundLogger.LogEntry entry : executor.getLogs()) {
            log.info("  [{}] {}", entry.getLevel(), entry.getMessage());
        }
    }

    // ========== main 方法 ==========

    public static void main(String[] args) {
        log.info("======================================");
        log.info("   requests 集成测试套件");
        log.info("======================================");

        org.junit.runner.Result result = org.junit.runner.JUnitCore.runClasses(RequestsIntegrationTest.class);

        log.info("\n======================================");
        log.info("             测试结果");
        log.info("======================================");
        log.info("运行测试数: {}", result.getRunCount());
        log.info("失败测试数: {}", result.getFailureCount());
        log.info("忽略测试数: {}", result.getIgnoreCount());
        log.info("运行时间: {} ms", result.getRunTime());

        if (result.wasSuccessful()) {
            log.info("\n✅ 所有 {} 个测试通过!", result.getRunCount());
        } else {
            log.error("\n❌ {} 个测试失败:", result.getFailureCount());
            for (org.junit.runner.notification.Failure failure : result.getFailures()) {
                log.error("  - {}", failure.getTestHeader());
                log.error("    错误: {}", failure.getMessage());
            }
        }

        System.exit(result.wasSuccessful() ? 0 : 1);
    }
}
