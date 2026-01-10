package cn.qaiu.parser;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.parser.custompy.PyHttpClient;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * PyHttpClient 测试类
 * 测试Python HTTP客户端功能是否正常
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2026/1/11
 */
public class PyHttpClientTest {

    private static Vertx vertx;
    private PyHttpClient httpClient;

    @BeforeClass
    public static void init() {
        // 初始化Vertx
        vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        System.out.println("=== PyHttpClient测试初始化完成 ===\n");
    }

    @Before
    public void setUp() {
        // 创建PyHttpClient实例
        httpClient = new PyHttpClient();
        System.out.println("--- 测试开始 ---");
    }

    @After
    public void tearDown() {
        System.out.println("--- 测试结束 ---\n");
    }

    @Test
    public void testSimpleGetRequest() {
        System.out.println("\n[测试1] 简单GET请求 - httpbin.org/get");
        
        try {
            String url = "https://httpbin.org/get";
            System.out.println("请求URL: " + url);
            
            long startTime = System.currentTimeMillis();
            PyHttpClient.PyHttpResponse response = httpClient.get(url);
            long endTime = System.currentTimeMillis();
            
            System.out.println("请求完成，耗时: " + (endTime - startTime) + "ms");
            System.out.println("状态码: " + response.status_code());
            
            String body = response.text();
            System.out.println("响应体长度: " + (body != null ? body.length() : 0) + " 字符");
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.status_code());
            assertTrue("请求应该成功", response.ok());
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
        System.out.println("\n[测试2] GET请求（跟随重定向）");
        
        try {
            String url = "https://httpbin.org/redirect/1";
            System.out.println("请求URL: " + url);
            
            PyHttpClient.PyHttpResponse response = httpClient.get_with_redirect(url);
            
            System.out.println("状态码: " + response.status_code());
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200（重定向后）", 200, response.status_code());
            assertTrue("请求应该成功", response.ok());
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("GET重定向请求失败: " + e.getMessage());
        }
    }

    @Test
    public void testGetNoRedirect() {
        System.out.println("\n[测试3] GET请求（不跟随重定向）");
        
        try {
            String url = "https://httpbin.org/redirect/1";
            System.out.println("请求URL: " + url);
            
            PyHttpClient.PyHttpResponse response = httpClient.get_no_redirect(url);
            
            System.out.println("状态码: " + response.status_code());
            String location = response.header("Location");
            System.out.println("Location头: " + location);
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertTrue("状态码应该是3xx重定向", 
                    response.status_code() >= 300 && response.status_code() < 400);
            assertFalse("ok()应该返回false", response.ok());
            assertNotNull("应该有Location头", location);
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("GET不重定向请求失败: " + e.getMessage());
        }
    }

    @Test
    public void testPostFormData() {
        System.out.println("\n[测试4] POST表单数据");
        
        try {
            String url = "https://httpbin.org/post";
            Map<String, String> formData = new HashMap<>();
            formData.put("username", "testuser");
            formData.put("password", "testpass");
            
            System.out.println("请求URL: " + url);
            System.out.println("表单数据: " + formData);
            
            PyHttpClient.PyHttpResponse response = httpClient.post(url, formData);
            
            System.out.println("状态码: " + response.status_code());
            
            String body = response.text();
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.status_code());
            assertTrue("响应体应该包含username", body.contains("testuser"));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("POST表单数据失败: " + e.getMessage());
        }
    }

    @Test
    public void testPostJson() {
        System.out.println("\n[测试5] POST JSON数据");
        
        try {
            String url = "https://httpbin.org/post";
            Map<String, Object> jsonData = new HashMap<>();
            jsonData.put("name", "测试用户");
            jsonData.put("age", 25);
            jsonData.put("active", true);
            
            System.out.println("请求URL: " + url);
            System.out.println("JSON数据: " + jsonData);
            
            PyHttpClient.PyHttpResponse response = httpClient.post_json(url, jsonData);
            
            System.out.println("状态码: " + response.status_code());
            
            String body = response.text();
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.status_code());
            assertTrue("响应体应该包含json数据", body.contains("测试用户") || body.contains("name"));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("POST JSON数据失败: " + e.getMessage());
        }
    }

    @Test
    public void testCustomHeaders() {
        System.out.println("\n[测试6] 自定义请求头");
        
        try {
            String url = "https://httpbin.org/headers";
            
            // 设置自定义请求头
            httpClient.put_header("X-Custom-Header", "CustomValue")
                      .put_header("X-Another-Header", "AnotherValue");
            
            System.out.println("请求URL: " + url);
            
            PyHttpClient.PyHttpResponse response = httpClient.get(url);
            
            System.out.println("状态码: " + response.status_code());
            
            String body = response.text();
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.status_code());
            assertTrue("响应体应该包含自定义头", body.contains("X-Custom-Header"));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("自定义请求头测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testBatchHeaders() {
        System.out.println("\n[测试7] 批量设置请求头");
        
        try {
            String url = "https://httpbin.org/headers";
            
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Header-1", "Value1");
            headers.put("X-Header-2", "Value2");
            headers.put("X-Header-3", "Value3");
            
            // 先清除之前的头
            httpClient.clear_headers();
            httpClient.put_headers(headers);
            
            System.out.println("请求URL: " + url);
            System.out.println("批量设置 " + headers.size() + " 个请求头");
            
            PyHttpClient.PyHttpResponse response = httpClient.get(url);
            
            System.out.println("状态码: " + response.status_code());
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.status_code());
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("批量设置请求头测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testResponseJson() {
        System.out.println("\n[测试8] 解析JSON响应");
        
        try {
            String url = "https://httpbin.org/json";
            
            System.out.println("请求URL: " + url);
            
            // 清除之前设置的头
            httpClient.clear_headers();
            
            PyHttpClient.PyHttpResponse response = httpClient.get(url);
            
            System.out.println("状态码: " + response.status_code());
            
            Object jsonObj = response.json();
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.status_code());
            assertNotNull("JSON对象不能为null", jsonObj);
            
            System.out.println("JSON类型: " + jsonObj.getClass().getSimpleName());
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("解析JSON响应失败: " + e.getMessage());
        }
    }

    @Test
    public void testResponseHeader() {
        System.out.println("\n[测试9] 获取响应头");
        
        try {
            String url = "https://httpbin.org/response-headers?X-Test-Header=TestValue";
            
            System.out.println("请求URL: " + url);
            
            httpClient.clear_headers();
            PyHttpClient.PyHttpResponse response = httpClient.get(url);
            
            System.out.println("状态码: " + response.status_code());
            
            String contentType = response.header("Content-Type");
            System.out.println("Content-Type: " + contentType);
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.status_code());
            assertNotNull("应该有Content-Type头", contentType);
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("获取响应头失败: " + e.getMessage());
        }
    }

    @Test
    public void testContentLength() {
        System.out.println("\n[测试10] 获取内容长度");
        
        try {
            String url = "https://httpbin.org/bytes/1024";
            
            System.out.println("请求URL: " + url);
            
            httpClient.clear_headers();
            PyHttpClient.PyHttpResponse response = httpClient.get(url);
            
            System.out.println("状态码: " + response.status_code());
            
            long contentLength = response.content_length();
            System.out.println("Content-Length: " + contentLength);
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.status_code());
            assertTrue("内容长度应该大于0", contentLength > 0);
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("获取内容长度失败: " + e.getMessage());
        }
    }

    @Test
    public void testPutRequest() {
        System.out.println("\n[测试11] PUT请求");
        
        try {
            String url = "https://httpbin.org/put";
            Map<String, String> data = new HashMap<>();
            data.put("key", "value");
            
            System.out.println("请求URL: " + url);
            
            httpClient.clear_headers();
            PyHttpClient.PyHttpResponse response = httpClient.put(url, data);
            
            System.out.println("状态码: " + response.status_code());
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.status_code());
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("PUT请求失败: " + e.getMessage());
        }
    }

    @Test
    public void testDeleteRequest() {
        System.out.println("\n[测试12] DELETE请求");
        
        try {
            String url = "https://httpbin.org/delete";
            
            System.out.println("请求URL: " + url);
            
            httpClient.clear_headers();
            PyHttpClient.PyHttpResponse response = httpClient.delete(url);
            
            System.out.println("状态码: " + response.status_code());
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.status_code());
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("DELETE请求失败: " + e.getMessage());
        }
    }

    @Test
    public void testPatchRequest() {
        System.out.println("\n[测试13] PATCH请求");
        
        try {
            String url = "https://httpbin.org/patch";
            Map<String, String> data = new HashMap<>();
            data.put("field", "updated");
            
            System.out.println("请求URL: " + url);
            
            httpClient.clear_headers();
            PyHttpClient.PyHttpResponse response = httpClient.patch(url, data);
            
            System.out.println("状态码: " + response.status_code());
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.status_code());
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("PATCH请求失败: " + e.getMessage());
        }
    }

    @Test
    public void testMethodChaining() {
        System.out.println("\n[测试14] 方法链式调用");
        
        try {
            String url = "https://httpbin.org/headers";
            
            System.out.println("请求URL: " + url);
            
            // 测试链式调用
            PyHttpClient.PyHttpResponse response = new PyHttpClient()
                    .put_header("X-Chain-1", "Value1")
                    .put_header("X-Chain-2", "Value2")
                    .set_timeout(30)
                    .get(url);
            
            System.out.println("状态码: " + response.status_code());
            
            String body = response.text();
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.status_code());
            assertTrue("响应体应该包含链式设置的头", body.contains("X-Chain"));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("方法链式调用测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testBodyAndTextEquivalent() {
        System.out.println("\n[测试15] body()和text()方法等价性");
        
        try {
            String url = "https://httpbin.org/get";
            
            System.out.println("请求URL: " + url);
            
            httpClient.clear_headers();
            PyHttpClient.PyHttpResponse response = httpClient.get(url);
            
            String body = response.body();
            String text = response.text();
            
            // 验证结果
            assertEquals("body()和text()应该返回相同的结果", body, text);
            
            System.out.println("✓ 测试通过");
            System.out.println("  body() == text(): " + body.equals(text));
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("body()和text()等价性测试失败: " + e.getMessage());
        }
    }
}
