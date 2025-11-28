package cn.qaiu.parser;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.parser.customjs.JsHttpClient;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

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

    // ==================== 新增方法测试 ====================

    @Test
    public void testPutHeaders() {
        System.out.println("\n[测试8] 批量设置请求头 - putHeaders方法");
        
        try {
            String url = "https://httpbin.org/headers";
            System.out.println("请求URL: " + url);
            
            // 批量设置请求头
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Test-Header-1", "value1");
            headers.put("X-Test-Header-2", "value2");
            headers.put("X-Test-Header-3", "value3");
            
            httpClient.putHeaders(headers);
            System.out.println("批量设置请求头: " + headers);
            
            long startTime = System.currentTimeMillis();
            JsHttpClient.JsHttpResponse response = httpClient.get(url);
            long endTime = System.currentTimeMillis();
            
            System.out.println("请求完成，耗时: " + (endTime - startTime) + "ms");
            System.out.println("状态码: " + response.statusCode());
            
            String body = response.body();
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.statusCode());
            assertNotNull("响应体不能为null", body);
            assertTrue("响应体应该包含设置的请求头", 
                    body.contains("X-Test-Header-1") || body.contains("value1"));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("批量设置请求头测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testRemoveHeader() {
        System.out.println("\n[测试9] 删除请求头 - removeHeader方法");
        
        try {
            String url = "https://httpbin.org/headers";
            System.out.println("请求URL: " + url);
            
            // 先设置请求头
            httpClient.putHeader("X-To-Be-Removed", "test-value");
            httpClient.putHeader("X-To-Keep", "keep-value");
            
            // 获取所有请求头
            Map<String, String> headersBefore = httpClient.getHeaders();
            System.out.println("删除前请求头数量: " + headersBefore.size());
            assertTrue("应该包含要删除的请求头", headersBefore.containsKey("X-To-Be-Removed"));
            
            // 删除指定请求头
            httpClient.removeHeader("X-To-Be-Removed");
            System.out.println("删除请求头: X-To-Be-Removed");
            
            // 获取所有请求头
            Map<String, String> headersAfter = httpClient.getHeaders();
            System.out.println("删除后请求头数量: " + headersAfter.size());
            
            // 验证结果
            assertFalse("不应该包含已删除的请求头", headersAfter.containsKey("X-To-Be-Removed"));
            assertTrue("应该保留未删除的请求头", headersAfter.containsKey("X-To-Keep"));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("删除请求头测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testClearHeaders() {
        System.out.println("\n[测试10] 清空请求头 - clearHeaders方法");
        
        try {
            // 先设置一些自定义请求头
            httpClient.putHeader("X-Custom-1", "value1");
            httpClient.putHeader("X-Custom-2", "value2");
            
            Map<String, String> headersBefore = httpClient.getHeaders();
            System.out.println("清空前请求头数量: " + headersBefore.size());
            assertTrue("应该包含自定义请求头", headersBefore.size() > 3); // 3个默认头
            
            // 清空请求头
            httpClient.clearHeaders();
            System.out.println("清空所有请求头（保留默认头）");
            
            Map<String, String> headersAfter = httpClient.getHeaders();
            System.out.println("清空后请求头数量: " + headersAfter.size());
            System.out.println("保留的默认头: " + headersAfter.keySet());
            
            // 验证结果
            assertFalse("不应该包含自定义请求头", headersAfter.containsKey("X-Custom-1"));
            assertFalse("不应该包含自定义请求头", headersAfter.containsKey("X-Custom-2"));
            // 应该保留默认头
            assertTrue("应该保留Accept-Encoding默认头", 
                    headersAfter.containsKey("Accept-Encoding"));
            assertTrue("应该保留User-Agent默认头", 
                    headersAfter.containsKey("User-Agent"));
            assertTrue("应该保留Accept-Language默认头", 
                    headersAfter.containsKey("Accept-Language"));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("清空请求头测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testGetHeaders() {
        System.out.println("\n[测试11] 获取所有请求头 - getHeaders方法");
        
        try {
            // 设置一些请求头
            httpClient.putHeader("X-Test-1", "value1");
            httpClient.putHeader("X-Test-2", "value2");
            
            Map<String, String> headers = httpClient.getHeaders();
            System.out.println("获取到的请求头数量: " + headers.size());
            System.out.println("请求头列表: " + headers);
            
            // 验证结果
            assertNotNull("请求头Map不能为null", headers);
            assertTrue("应该包含设置的请求头", headers.containsKey("X-Test-1"));
            assertTrue("应该包含设置的请求头", headers.containsKey("X-Test-2"));
            assertEquals("X-Test-1的值应该是value1", "value1", headers.get("X-Test-1"));
            assertEquals("X-Test-2的值应该是value2", "value2", headers.get("X-Test-2"));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("获取请求头测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testPutRequest() {
        System.out.println("\n[测试12] PUT请求 - put方法");
        
        try {
            String url = "https://httpbin.org/put";
            System.out.println("请求URL: " + url);
            
            Map<String, String> data = new HashMap<>();
            data.put("key1", "value1");
            data.put("key2", "value2");
            
            System.out.println("PUT数据: " + data);
            System.out.println("开始请求...");
            
            long startTime = System.currentTimeMillis();
            JsHttpClient.JsHttpResponse response = httpClient.put(url, data);
            long endTime = System.currentTimeMillis();
            
            System.out.println("请求完成，耗时: " + (endTime - startTime) + "ms");
            System.out.println("状态码: " + response.statusCode());
            
            String body = response.body();
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.statusCode());
            assertNotNull("响应体不能为null", body);
            assertTrue("响应体应该包含PUT的数据", 
                    body.contains("key1") || body.contains("value1"));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("PUT请求测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testDeleteRequest() {
        System.out.println("\n[测试13] DELETE请求 - delete方法");
        
        try {
            String url = "https://httpbin.org/delete";
            System.out.println("请求URL: " + url);
            System.out.println("开始请求...");
            
            long startTime = System.currentTimeMillis();
            JsHttpClient.JsHttpResponse response = httpClient.delete(url);
            long endTime = System.currentTimeMillis();
            
            System.out.println("请求完成，耗时: " + (endTime - startTime) + "ms");
            System.out.println("状态码: " + response.statusCode());
            
            String body = response.body();
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.statusCode());
            assertNotNull("响应体不能为null", body);
            assertTrue("响应体应该包含DELETE相关信息", 
                    body.contains("\"url\"") || body.contains("delete"));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("DELETE请求测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testPatchRequest() {
        System.out.println("\n[测试14] PATCH请求 - patch方法");
        
        try {
            String url = "https://httpbin.org/patch";
            System.out.println("请求URL: " + url);
            
            Map<String, String> data = new HashMap<>();
            data.put("field1", "newValue1");
            data.put("field2", "newValue2");
            
            System.out.println("PATCH数据: " + data);
            System.out.println("开始请求...");
            
            long startTime = System.currentTimeMillis();
            JsHttpClient.JsHttpResponse response = httpClient.patch(url, data);
            long endTime = System.currentTimeMillis();
            
            System.out.println("请求完成，耗时: " + (endTime - startTime) + "ms");
            System.out.println("状态码: " + response.statusCode());
            
            String body = response.body();
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.statusCode());
            assertNotNull("响应体不能为null", body);
            assertTrue("响应体应该包含PATCH的数据", 
                    body.contains("field1") || body.contains("newValue1"));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("PATCH请求测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testSetTimeout() {
        System.out.println("\n[测试15] 设置超时时间 - setTimeout方法");
        
        try {
            String url = "https://httpbin.org/delay/2";
            System.out.println("请求URL: " + url);
            
            // 设置超时时间为10秒
            httpClient.setTimeout(10);
            System.out.println("设置超时时间: 10秒");
            
            long startTime = System.currentTimeMillis();
            JsHttpClient.JsHttpResponse response = httpClient.get(url);
            long endTime = System.currentTimeMillis();
            
            long duration = endTime - startTime;
            System.out.println("请求完成，耗时: " + duration + "ms");
            System.out.println("状态码: " + response.statusCode());
            
            // 验证结果
            assertNotNull("响应不能为null", response);
            assertEquals("状态码应该是200", 200, response.statusCode());
            assertTrue("应该在合理时间内完成（2-5秒）", duration >= 2000 && duration < 5000);
            
            // 测试更短的超时时间（应该失败）
            httpClient.setTimeout(1);
            System.out.println("设置超时时间为1秒，请求延迟2秒的URL（应该超时）");
            
            try {
                httpClient.get("https://httpbin.org/delay/2");
                fail("应该抛出超时异常");
            } catch (Exception e) {
                System.out.println("✓ 正确抛出超时异常: " + e.getMessage());
                assertTrue("异常应该包含超时相关信息", 
                        e.getMessage().contains("超时") || 
                        e.getMessage().contains("timeout") ||
                        e.getMessage().contains("Timeout"));
            }
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("设置超时时间测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testUrlEncode() {
        System.out.println("\n[测试16] URL编码 - urlEncode静态方法");
        
        try {
            // 测试各种字符串
            String[] testStrings = {
                "hello world",
                "测试中文",
                "a+b=c&d=e",
                "特殊字符!@#$%^&*()",
                "123456"
            };
            
            for (String original : testStrings) {
                String encoded = JsHttpClient.urlEncode(original);
                System.out.println("原文: " + original);
                System.out.println("编码: " + encoded);
                
                // 验证结果
                assertNotNull("编码结果不能为null", encoded);
                assertNotEquals("编码后应该与原文不同（如果包含特殊字符）", original, encoded);
                
                // 验证编码后的字符串不包含空格（空格应该被编码为%20）
                if (original.contains(" ")) {
                    assertFalse("编码后的字符串不应该包含空格", encoded.contains(" "));
                }
            }
            
            // 测试null
            String nullEncoded = JsHttpClient.urlEncode(null);
            assertNull("null应该返回null", nullEncoded);
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("URL编码测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testUrlDecode() {
        System.out.println("\n[测试17] URL解码 - urlDecode静态方法");
        
        try {
            // 测试编码和解码的往返
            String[] testStrings = {
                "hello world",
                "测试中文",
                "a+b=c&d=e",
                "123456"
            };
            
            for (String original : testStrings) {
                String encoded = JsHttpClient.urlEncode(original);
                String decoded = JsHttpClient.urlDecode(encoded);
                
                System.out.println("原文: " + original);
                System.out.println("编码: " + encoded);
                System.out.println("解码: " + decoded);
                
                // 验证结果
                assertEquals("解码后应该与原文相同", original, decoded);
            }
            
            // 测试null
            String nullDecoded = JsHttpClient.urlDecode(null);
            assertNull("null应该返回null", nullDecoded);
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("URL解码测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testBodyBytes() {
        System.out.println("\n[测试18] 获取响应体字节数组 - bodyBytes方法");
        
        try {
            String url = "https://httpbin.org/get";
            System.out.println("请求URL: " + url);
            System.out.println("开始请求...");
            
            JsHttpClient.JsHttpResponse response = httpClient.get(url);
            System.out.println("状态码: " + response.statusCode());
            
            // 获取响应体字符串和字节数组
            String bodyString = response.body();
            byte[] bodyBytes = response.bodyBytes();
            
            System.out.println("响应体字符串长度: " + (bodyString != null ? bodyString.length() : 0));
            System.out.println("响应体字节数组长度: " + (bodyBytes != null ? bodyBytes.length : 0));
            
            // 验证结果
            assertNotNull("响应体字节数组不能为null", bodyBytes);
            assertTrue("字节数组长度应该大于0", bodyBytes.length > 0);
            assertTrue("字节数组长度应该与字符串长度相关", 
                    bodyBytes.length >= bodyString.length());
            
            // 验证字节数组可以转换为字符串
            String bytesAsString = new String(bodyBytes);
            assertTrue("字节数组转换的字符串应该包含关键内容", 
                    bytesAsString.contains("\"url\""));
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("获取响应体字节数组测试失败: " + e.getMessage());
        }
    }

    @Test
    public void testBodySize() {
        System.out.println("\n[测试19] 获取响应体大小 - bodySize方法");
        
        try {
            String url = "https://httpbin.org/get";
            System.out.println("请求URL: " + url);
            System.out.println("开始请求...");
            
            JsHttpClient.JsHttpResponse response = httpClient.get(url);
            System.out.println("状态码: " + response.statusCode());
            
            // 获取响应体大小和字符串
            long bodySize = response.bodySize();
            String bodyString = response.body();
            
            System.out.println("响应体大小: " + bodySize + " 字节");
            System.out.println("响应体字符串长度: " + (bodyString != null ? bodyString.length() : 0));
            
            // 验证结果
            assertTrue("响应体大小应该大于0", bodySize > 0);
            assertTrue("响应体大小应该与字符串长度相关", 
                    bodySize >= bodyString.length());
            
            // 验证bodySize与bodyBytes长度一致
            byte[] bodyBytes = response.bodyBytes();
            assertEquals("bodySize应该等于bodyBytes的长度", 
                    bodyBytes.length, bodySize);
            
            System.out.println("✓ 测试通过");
            
        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("获取响应体大小测试失败: " + e.getMessage());
        }
    }
}