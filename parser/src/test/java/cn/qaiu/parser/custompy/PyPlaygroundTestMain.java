package cn.qaiu.parser.custompy;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.ParserCreate;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Python 演练场测试主类
 * 直接运行此类来测试 GraalPy 环境
 */
public class PyPlaygroundTestMain {
    
    private static final Logger log = LoggerFactory.getLogger(PyPlaygroundTestMain.class);
    
    public static void main(String[] args) throws Exception {
        log.info("======= Python 演练场测试开始 =======");
        
        int passed = 0;
        int failed = 0;
        
        // 测试 1: 基础 Python 执行
        try {
            testBasicPythonExecution();
            passed++;
            log.info("✓ 测试1: 基础 Python 执行 - 通过");
        } catch (Exception e) {
            failed++;
            log.error("✗ 测试1: 基础 Python 执行 - 失败", e);
        }
        
        // 测试 2: requests 库导入
        try {
            testRequestsImport();
            passed++;
            log.info("✓ 测试2: requests 库导入 - 通过");
        } catch (Exception e) {
            failed++;
            log.error("✗ 测试2: requests 库导入 - 失败", e);
        }
        
        // 测试 3: 简单 parse 函数
        try {
            testSimpleParseFunction();
            passed++;
            log.info("✓ 测试3: 简单 parse 函数 - 通过");
        } catch (Exception e) {
            failed++;
            log.error("✗ 测试3: 简单 parse 函数 - 失败", e);
        }
        
        // 测试 4: PyPlaygroundExecutor
        try {
            testPyPlaygroundExecutor();
            passed++;
            log.info("✓ 测试4: PyPlaygroundExecutor - 通过");
        } catch (Exception e) {
            failed++;
            log.error("✗ 测试4: PyPlaygroundExecutor - 失败", e);
        }
        
        // 测试 5: 安全检查
        try {
            testSecurityChecker();
            passed++;
            log.info("✓ 测试5: 安全检查 - 通过");
        } catch (Exception e) {
            failed++;
            log.error("✗ 测试5: 安全检查 - 失败", e);
        }
        
        log.info("======= 测试完成 =======");
        log.info("通过: {}, 失败: {}", passed, failed);
        
        if (failed > 0) {
            System.exit(1);
        }
    }
    
    /**
     * 测试基础的 Context 创建和 Python 代码执行
     */
    private static void testBasicPythonExecution() {
        log.info("=== 测试基础 Python 执行 ===");
        
        PyContextPool pool = PyContextPool.getInstance();
        
        try (Context context = pool.createFreshContext()) {
            // 测试简单的 Python 表达式
            Value result = context.eval("python", "1 + 2");
            if (result.asInt() != 3) {
                throw new AssertionError("期望 3, 实际 " + result.asInt());
            }
            log.info("  基础表达式: 1 + 2 = {}", result.asInt());
            
            // 测试字符串操作
            Value strResult = context.eval("python", "'hello'.upper()");
            if (!"HELLO".equals(strResult.asString())) {
                throw new AssertionError("期望 HELLO, 实际 " + strResult.asString());
            }
            log.info("  字符串操作: 'hello'.upper() = {}", strResult.asString());
        }
    }
    
    /**
     * 测试 requests 库导入
     */
    private static void testRequestsImport() {
        log.info("=== 测试 requests 库导入 ===");
        
        PyContextPool pool = PyContextPool.getInstance();
        
        try (Context context = pool.createFreshContext()) {
            // 测试 requests 导入
            context.eval("python", "import requests");
            log.info("  requests 导入成功");
            
            // 验证 requests 版本
            Value version = context.eval("python", "requests.__version__");
            log.info("  requests 版本: {}", version.asString());
            
            if (version.asString() == null) {
                throw new AssertionError("requests 版本为空");
            }
        }
    }
    
    /**
     * 测试简单的 parse 函数执行
     */
    private static void testSimpleParseFunction() {
        log.info("=== 测试简单 parse 函数 ===");
        
        String pyCode = """
            def parse(share_link_info, http, logger):
                logger.info("测试开始")
                return "https://example.com/download/test.zip"
            """;
        
        PyContextPool pool = PyContextPool.getInstance();
        
        try (Context context = pool.createFreshContext()) {
            PyPlaygroundLogger logger = new PyPlaygroundLogger();
            
            // 注入对象
            Value bindings = context.getBindings("python");
            bindings.putMember("logger", logger);
            
            // 执行代码定义函数
            context.eval("python", pyCode);
            
            // 获取并调用 parse 函数
            Value parseFunc = bindings.getMember("parse");
            if (parseFunc == null || !parseFunc.canExecute()) {
                throw new AssertionError("parse 函数不存在或不可执行");
            }
            
            // 执行函数
            Value result = parseFunc.execute(null, null, logger);
            
            if (!"https://example.com/download/test.zip".equals(result.asString())) {
                throw new AssertionError("期望 https://example.com/download/test.zip, 实际 " + result.asString());
            }
            log.info("  parse 函数返回: {}", result.asString());
            
            // 检查日志
            if (logger.getLogs().isEmpty()) {
                throw new AssertionError("没有日志记录");
            }
            log.info("  日志记录数: {}", logger.getLogs().size());
        }
    }
    
    /**
     * 测试完整的 PyPlaygroundExecutor
     */
    private static void testPyPlaygroundExecutor() throws Exception {
        log.info("=== 测试 PyPlaygroundExecutor ===");
        
        String pyCode = """
            import json
            
            def parse(share_link_info, http, logger):
                url = share_link_info.get_share_url()
                logger.info(f"解析链接: {url}")
                return "https://example.com/download/test.zip"
            """;
        
        // 创建 ShareLinkInfo
        ParserCreate parserCreate = ParserCreate.fromShareUrl("https://example.com/s/abc");
        ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();
        
        // 创建执行器
        PyPlaygroundExecutor executor = new PyPlaygroundExecutor(shareLinkInfo, pyCode);
        
        // 异步执行
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
        
        // 等待结果
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new AssertionError("执行超时");
        }
        
        // 检查结果
        if (errorRef.get() != null) {
            throw new AssertionError("执行失败: " + errorRef.get().getMessage(), errorRef.get());
        }
        
        if (!"https://example.com/download/test.zip".equals(resultRef.get())) {
            throw new AssertionError("期望 https://example.com/download/test.zip, 实际 " + resultRef.get());
        }
        
        log.info("  PyPlaygroundExecutor 返回: {}", resultRef.get());
        log.info("  执行日志:");
        for (PyPlaygroundLogger.LogEntry entry : executor.getLogs()) {
            log.info("    [{}] {}", entry.getLevel(), entry.getMessage());
        }
    }
    
    /**
     * 测试安全检查器拦截危险代码
     */
    private static void testSecurityChecker() throws Exception {
        log.info("=== 测试安全检查器 ===");
        
        String dangerousCode = """
            import subprocess
            
            def parse(share_link_info, http, logger):
                result = subprocess.run(['ls'], capture_output=True)
                return result.stdout.decode()
            """;
        
        ParserCreate parserCreate = ParserCreate.fromShareUrl("https://example.com/s/abc");
        ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();
        
        PyPlaygroundExecutor executor = new PyPlaygroundExecutor(shareLinkInfo, dangerousCode);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<String> resultRef = new AtomicReference<>();
        
        executor.executeParseAsync()
            .onSuccess(result -> {
                resultRef.set(result);
                latch.countDown();
            })
            .onFailure(e -> {
                errorRef.set(e);
                latch.countDown();
            });
        
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new AssertionError("执行超时");
        }
        
        // 应该被安全检查器拦截
        if (errorRef.get() == null) {
            throw new AssertionError("危险代码应该被拦截，但执行成功了: " + resultRef.get());
        }
        
        String errorMsg = errorRef.get().getMessage();
        if (!errorMsg.contains("安全检查") && !errorMsg.contains("subprocess")) {
            throw new AssertionError("错误消息不包含预期内容: " + errorMsg);
        }
        
        log.info("  安全检查器正确拦截: {}", errorMsg);
    }
}
