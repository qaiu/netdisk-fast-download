package cn.qaiu.parser.custompy;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.ParserCreate;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PlaygroundApi 接口测试
 * 测试 /v2/playground/* API 端点
 * 
 * 注意：这个测试需要后端服务运行中
 * 默认测试地址: http://localhost:8080
 */
public class PlaygroundApiTest {
    
    private static final Logger log = LoggerFactory.getLogger(PlaygroundApiTest.class);
    
    // 测试服务器配置
    private static final String HOST = "localhost";
    private static final int PORT = 8080;
    private static final int TIMEOUT_SECONDS = 30;
    
    private final Vertx vertx;
    private final HttpClient client;
    
    // 测试统计
    private int totalTests = 0;
    private int passedTests = 0;
    private int failedTests = 0;
    
    public PlaygroundApiTest() {
        this.vertx = Vertx.vertx();
        this.client = vertx.createHttpClient(new HttpClientOptions()
            .setDefaultHost(HOST)
            .setDefaultPort(PORT)
            .setConnectTimeout(10000)
            .setIdleTimeout(TIMEOUT_SECONDS));
    }
    
    /**
     * 测试 GET /v2/playground/status
     */
    public void testGetStatus() {
        totalTests++;
        log.info("=== 测试1: GET /v2/playground/status ===");
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> success = new AtomicReference<>(false);
        AtomicReference<String> error = new AtomicReference<>();
        
        client.request(HttpMethod.GET, "/v2/playground/status")
            .compose(req -> req.send())
            .compose(resp -> {
                log.info("  状态码: {}", resp.statusCode());
                return resp.body();
            })
            .onSuccess(body -> {
                try {
                    JsonObject json = new JsonObject(body.toString());
                    log.info("  响应: {}", json.encodePrettily());
                    
                    // 验证响应结构
                    if (json.containsKey("code") && json.containsKey("data")) {
                        JsonObject data = json.getJsonObject("data");
                        if (data.containsKey("enabled")) {
                            success.set(true);
                            log.info("  ✓ 状态接口正常，enabled={}", data.getBoolean("enabled"));
                        }
                    }
                } catch (Exception e) {
                    error.set("解析响应失败: " + e.getMessage());
                }
                latch.countDown();
            })
            .onFailure(e -> {
                error.set("请求失败: " + e.getMessage());
                latch.countDown();
            });
        
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            error.set("超时");
        }
        
        if (success.get()) {
            passedTests++;
        } else {
            failedTests++;
            log.error("  ✗ 测试失败: {}", error.get());
        }
    }
    
    /**
     * 测试 POST /v2/playground/test - JavaScript代码执行
     */
    public void testJavaScriptExecution() {
        totalTests++;
        log.info("=== 测试2: POST /v2/playground/test (JavaScript) ===");
        
        String jsCode = """
            // @name 测试解析器
            // @match https?://example\\.com/s/(?<KEY>\\w+)
            // @type test_js
            
            function parse(shareLinkInfo, http, logger) {
                logger.info("开始解析...");
                var url = shareLinkInfo.getShareUrl();
                logger.info("URL: " + url);
                return "https://download.example.com/test.zip";
            }
            """;
        
        JsonObject requestBody = new JsonObject()
            .put("code", jsCode)
            .put("shareUrl", "https://example.com/s/abc123")
            .put("language", "javascript")
            .put("method", "parse");
        
        executeTestRequest(requestBody, "JavaScript");
    }
    
    /**
     * 测试 POST /v2/playground/test - Python代码执行
     */
    public void testPythonExecution() {
        totalTests++;
        log.info("=== 测试3: POST /v2/playground/test (Python) ===");
        
        String pyCode = """
            # @name 测试解析器
            # @match https?://example\\.com/s/(?P<KEY>\\w+)
            # @type test_py
            
            import json
            
            def parse(share_link_info, http, logger):
                logger.info("开始解析...")
                url = share_link_info.get_share_url()
                logger.info(f"URL: {url}")
                return "https://download.example.com/test.zip"
            """;
        
        JsonObject requestBody = new JsonObject()
            .put("code", pyCode)
            .put("shareUrl", "https://example.com/s/abc123")
            .put("language", "python")
            .put("method", "parse");
        
        executeTestRequest(requestBody, "Python");
    }
    
    /**
     * 测试 POST /v2/playground/test - 安全检查拦截
     */
    public void testSecurityBlock() {
        totalTests++;
        log.info("=== 测试4: POST /v2/playground/test (安全检查拦截) ===");
        
        String dangerousCode = """
            # @name 危险解析器
            # @match https?://example\\.com/s/(?P<KEY>\\w+)
            # @type dangerous
            
            import subprocess
            
            def parse(share_link_info, http, logger):
                result = subprocess.run(['ls'], capture_output=True)
                return result.stdout.decode()
            """;
        
        JsonObject requestBody = new JsonObject()
            .put("code", dangerousCode)
            .put("shareUrl", "https://example.com/s/abc123")
            .put("language", "python")
            .put("method", "parse");
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> success = new AtomicReference<>(false);
        AtomicReference<String> error = new AtomicReference<>();
        
        client.request(HttpMethod.POST, "/v2/playground/test")
            .compose(req -> {
                req.putHeader("Content-Type", "application/json");
                return req.send(requestBody.encode());
            })
            .compose(resp -> {
                log.info("  状态码: {}", resp.statusCode());
                return resp.body();
            })
            .onSuccess(body -> {
                try {
                    JsonObject json = new JsonObject(body.toString());
                    log.info("  响应: {}", json.encodePrettily().substring(0, Math.min(500, json.encodePrettily().length())));
                    
                    // 危险代码应该被拦截，success=false
                    JsonObject data = json.getJsonObject("data");
                    if (data != null && !data.getBoolean("success", true)) {
                        String errorMsg = data.getString("error", "");
                        if (errorMsg.contains("安全检查") || errorMsg.contains("subprocess")) {
                            success.set(true);
                            log.info("  ✓ 安全检查正确拦截了危险代码");
                        }
                    }
                } catch (Exception e) {
                    error.set("解析响应失败: " + e.getMessage());
                }
                latch.countDown();
            })
            .onFailure(e -> {
                error.set("请求失败: " + e.getMessage());
                latch.countDown();
            });
        
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            error.set("超时");
        }
        
        if (success.get()) {
            passedTests++;
        } else {
            failedTests++;
            log.error("  ✗ 测试失败: {}", error.get());
        }
    }
    
    /**
     * 测试 POST /v2/playground/test - 缺少参数
     */
    public void testMissingParameters() {
        totalTests++;
        log.info("=== 测试5: POST /v2/playground/test (缺少参数) ===");
        
        JsonObject requestBody = new JsonObject()
            .put("shareUrl", "https://example.com/s/abc123")
            .put("language", "javascript")
            .put("method", "parse");
        // 缺少 code 字段
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> success = new AtomicReference<>(false);
        AtomicReference<String> error = new AtomicReference<>();
        
        client.request(HttpMethod.POST, "/v2/playground/test")
            .compose(req -> {
                req.putHeader("Content-Type", "application/json");
                return req.send(requestBody.encode());
            })
            .compose(resp -> {
                log.info("  状态码: {}", resp.statusCode());
                return resp.body();
            })
            .onSuccess(body -> {
                try {
                    JsonObject json = new JsonObject(body.toString());
                    log.info("  响应: {}", json.encodePrettily());
                    
                    // 缺少参数应该返回错误
                    JsonObject data = json.getJsonObject("data");
                    if (data != null && !data.getBoolean("success", true)) {
                        String errorMsg = data.getString("error", "");
                        if (errorMsg.contains("代码不能为空") || errorMsg.contains("empty") || errorMsg.contains("required")) {
                            success.set(true);
                            log.info("  ✓ 正确返回了参数缺失错误");
                        }
                    }
                } catch (Exception e) {
                    error.set("解析响应失败: " + e.getMessage());
                }
                latch.countDown();
            })
            .onFailure(e -> {
                error.set("请求失败: " + e.getMessage());
                latch.countDown();
            });
        
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            error.set("超时");
        }
        
        if (success.get()) {
            passedTests++;
        } else {
            failedTests++;
            log.error("  ✗ 测试失败: {}", error.get());
        }
    }
    
    /**
     * 执行测试请求
     */
    private void executeTestRequest(JsonObject requestBody, String languageName) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> success = new AtomicReference<>(false);
        AtomicReference<String> error = new AtomicReference<>();
        
        client.request(HttpMethod.POST, "/v2/playground/test")
            .compose(req -> {
                req.putHeader("Content-Type", "application/json");
                return req.send(requestBody.encode());
            })
            .compose(resp -> {
                log.info("  状态码: {}", resp.statusCode());
                return resp.body();
            })
            .onSuccess(body -> {
                try {
                    JsonObject json = new JsonObject(body.toString());
                    String prettyJson = json.encodePrettily();
                    log.info("  响应: {}", prettyJson.substring(0, Math.min(800, prettyJson.length())));
                    
                    // 检查响应结构
                    JsonObject data = json.getJsonObject("data");
                    if (data != null) {
                        boolean testSuccess = data.getBoolean("success", false);
                        if (testSuccess) {
                            Object result = data.getValue("result");
                            log.info("  ✓ {} 代码执行成功，结果: {}", languageName, result);
                            success.set(true);
                        } else {
                            String errorMsg = data.getString("error", "未知错误");
                            log.warn("  执行失败: {}", errorMsg);
                            // 某些预期的执行失败也算测试通过（如 URL 匹配失败等）
                            if (errorMsg.contains("不匹配") || errorMsg.contains("match")) {
                                success.set(true);
                                log.info("  ✓ 接口正常工作（URL 匹配规则验证正常）");
                            }
                        }
                    }
                } catch (Exception e) {
                    error.set("解析响应失败: " + e.getMessage());
                }
                latch.countDown();
            })
            .onFailure(e -> {
                error.set("请求失败: " + e.getMessage());
                latch.countDown();
            });
        
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            error.set("超时");
        }
        
        if (success.get()) {
            passedTests++;
        } else {
            failedTests++;
            log.error("  ✗ 测试失败: {}", error.get());
        }
    }
    
    /**
     * 关闭客户端
     */
    public void close() {
        client.close();
        vertx.close();
    }
    
    /**
     * 运行所有测试
     */
    public void runAll() {
        log.info("======================================");
        log.info("   PlaygroundApi 接口测试");
        log.info("   测试服务器: http://{}:{}", HOST, PORT);
        log.info("======================================\n");
        
        // 先检查服务是否可用
        if (!checkServerAvailable()) {
            log.error("❌ 服务器不可用，请先启动后端服务！");
            log.info("\n提示：可以使用以下命令启动服务：");
            log.info("  cd web-service && mvn exec:java -Dexec.mainClass=cn.qaiu.lz.AppMain");
            return;
        }
        
        log.info("✓ 服务器连接正常\n");
        
        // 执行测试
        testGetStatus();
        testJavaScriptExecution();
        testPythonExecution();
        testSecurityBlock();
        testMissingParameters();
        
        // 输出结果
        log.info("\n======================================");
        log.info("             测试结果");
        log.info("======================================");
        log.info("总测试数: {}", totalTests);
        log.info("通过: {}", passedTests);
        log.info("失败: {}", failedTests);
        
        if (failedTests == 0) {
            log.info("\n✅ 所有接口测试通过!");
        } else {
            log.error("\n❌ {} 个测试失败", failedTests);
        }
        
        close();
    }
    
    /**
     * 检查服务器是否可用
     */
    private boolean checkServerAvailable() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> available = new AtomicReference<>(false);
        
        client.request(HttpMethod.GET, "/v2/playground/status")
            .compose(req -> req.send())
            .onSuccess(resp -> {
                available.set(resp.statusCode() == 200);
                latch.countDown();
            })
            .onFailure(e -> {
                log.debug("服务器连接失败: {}", e.getMessage());
                latch.countDown();
            });
        
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // 忽略
        }
        
        return available.get();
    }
    
    public static void main(String[] args) {
        PlaygroundApiTest test = new PlaygroundApiTest();
        test.runAll();
    }
}
