package cn.qaiu.parser;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.parser.customjs.JsHttpClient;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * JsHttpClient 测试类
 * 测试HTTP请求功能是否正常
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/11/15
 */
public class JsHttpClientTest {

    private Vertx vertx;
    private JsHttpClient httpClient;

    @Before
    public void setUp() {
        // 初始化Vertx
        vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        // 创建JsHttpClient实例
        httpClient = new JsHttpClient();
        
        System.out.println("=== 测试开始 ===");
    }

    @After
    public void tearDown() {
        // 清理资源
        if (vertx != null) {
            vertx.close();
        }
        System.out.println("=== 测试结束 ===\n");
    }

    @Test
    public void testSimpleGetRequest() {
        System.out.println("\n[测试1] 简单GET请求 - httpbin.org/get");
        
        try {
            String url = "https://httpbin.org/get";
            System.out.println("请求URL: " + url);
            System.out.println("开始请求...");
            
            long startTime = System.currentTimeMillis();
            JsHttpClient.JsHttpResponse response = httpClient.get(url);
            long endTime = System.currentTimeMillis();
            
            System.out.println("请求完成，耗时: " + (endTime - startTime) + "ms");
            System.out.println("状态码: " + response.statusCode());
            System.out.println("响应头数量: " + response.headers().size());
            
            String body = response.body();
            System.out.println("响应体长度: " + (body != null ? body.length() : 0) + " 字符");
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.statusCode());
            assertNotNull("响应体不能为null", body);
            assertTrue("响应体应该包含url字段", body.contains("\"url\""));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("GET请求失败: " + e.getMessage());
        }
    }

    @Test
    public void testGetWithRedirect() {
        System.out.println("\n[测试2] GET请求（跟随重定向） - httpbin.org/redirect/1");
        
        try {
            String url = "https://httpbin.org/redirect/1";
            System.out.println("请求URL: " + url);
            System.out.println("开始请求（会自动跟随重定向）...");
            
            long startTime = System.currentTimeMillis();
            JsHttpClient.JsHttpResponse response = httpClient.getWithRedirect(url);
            long endTime = System.currentTimeMillis();
            
            System.out.println("请求完成，耗时: " + (endTime - startTime) + "ms");
            System.out.println("状态码: " + response.statusCode());
            
            String body = response.body();
            System.out.println("响应体长度: " + (body != null ? body.length() : 0) + " 字符");
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200（重定向后）", 200, response.statusCode());
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("GET重定向请求失败: " + e.getMessage());
        }
    }

    @Test
    public void testGetNoRedirect() {
        System.out.println("\n[测试3] GET请求（不跟随重定向） - httpbin.org/redirect/1");
        
        try {
            String url = "https://httpbin.org/redirect/1";
            System.out.println("请求URL: " + url);
            System.out.println("开始请求（不跟随重定向）...");
            
            long startTime = System.currentTimeMillis();
            JsHttpClient.JsHttpResponse response = httpClient.getNoRedirect(url);
            long endTime = System.currentTimeMillis();
            
            System.out.println("请求完成，耗时: " + (endTime - startTime) + "ms");
            System.out.println("状态码: " + response.statusCode());
            
            String location = response.header("Location");
            System.out.println("Location头: " + location);
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertTrue("状态码应该是3xx重定向", 
                    response.statusCode() >= 300 && response.statusCode() < 400);
            assertNotNull("应该有Location头", location);
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("GET不重定向请求失败: " + e.getMessage());
        }
    }

    @Test
    public void testGetWithHeaders() {
        System.out.println("\n[测试4] GET请求（带自定义请求头） - httpbin.org/headers");
        
        try {
            String url = "https://httpbin.org/headers";
            System.out.println("请求URL: " + url);
            
            // 设置自定义请求头
            httpClient.putHeader("X-Custom-Header", "test-value");
            httpClient.putHeader("X-Another-Header", "another-value");
            
            System.out.println("设置请求头: X-Custom-Header=test-value, X-Another-Header=another-value");
            System.out.println("开始请求...");
            
            long startTime = System.currentTimeMillis();
            JsHttpClient.JsHttpResponse response = httpClient.get(url);
            long endTime = System.currentTimeMillis();
            
            System.out.println("请求完成，耗时: " + (endTime - startTime) + "ms");
            System.out.println("状态码: " + response.statusCode());
            
            String body = response.body();
            System.out.println("响应体长度: " + (body != null ? body.length() : 0) + " 字符");
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.statusCode());
            assertNotNull("响应体不能为null", body);
            assertTrue("响应体应该包含自定义请求头", 
                    body.contains("X-Custom-Header") || body.contains("test-value"));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("带请求头的GET请求失败: " + e.getMessage());
        }
    }

    @Test
    public void testGetJsonResponse() {
        System.out.println("\n[测试5] GET请求（JSON响应） - jsonplaceholder.typicode.com/posts/1");
        
        try {
            String url = "https://jsonplaceholder.typicode.com/posts/1";
            System.out.println("请求URL: " + url);
            System.out.println("开始请求...");
            
            long startTime = System.currentTimeMillis();
            JsHttpClient.JsHttpResponse response = httpClient.get(url);
            long endTime = System.currentTimeMillis();
            
            System.out.println("请求完成，耗时: " + (endTime - startTime) + "ms");
            System.out.println("状态码: " + response.statusCode());
            
            // 测试JSON解析
            Object jsonData = response.json();
            System.out.println("JSON数据: " + jsonData);
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.statusCode());
            assertNotNull("JSON数据不能为null", jsonData);
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("JSON响应请求失败: " + e.getMessage());
        }
    }

    @Test
    public void testTimeout() {
        System.out.println("\n[测试6] 超时测试 - httpbin.org/delay/5");
        System.out.println("注意：这个请求会延迟5秒，应该在30秒内完成");
        
        try {
            String url = "https://httpbin.org/delay/5";
            System.out.println("请求URL: " + url);
            System.out.println("开始请求（延迟5秒）...");
            
            long startTime = System.currentTimeMillis();
            JsHttpClient.JsHttpResponse response = httpClient.get(url);
            long endTime = System.currentTimeMillis();
            
            long duration = endTime - startTime;
            System.out.println("请求完成，耗时: " + duration + "ms");
            System.out.println("状态码: " + response.statusCode());
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.statusCode());
            assertTrue("应该在合理时间内完成（5-10秒）", duration >= 5000 && duration < 10000);
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("超时测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testErrorResponse() {
        System.out.println("\n[测试7] 错误响应测试 - httpbin.org/status/404");
        
        try {
            String url = "https://httpbin.org/status/404";
            System.out.println("请求URL: " + url);
            System.out.println("开始请求（预期404错误）...");
            
            long startTime = System.currentTimeMillis();
            JsHttpClient.JsHttpResponse response = httpClient.get(url);
            long endTime = System.currentTimeMillis();
            
            System.out.println("请求完成，耗时: " + (endTime - startTime) + "ms");
            System.out.println("状态码: " + response.statusCode());
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是404", 404, response.statusCode());
            assertFalse("不应该成功", response.isSuccess());
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("错误响应测试失败: " + e.getMessage());
        }
    }
}