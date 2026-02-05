package cn.qaiu.lz.common.util;

import cn.qaiu.lz.web.model.AuthParam;
import cn.qaiu.util.AESUtils;
import io.vertx.core.json.JsonObject;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 认证参数编解码测试 - 独立运行版本
 * 运行方法：在 IDE 中直接运行此类的 main 方法
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * @date 2026/2/5
 */
public class AuthParamCodecTestMain {

    private static final String TEST_KEY = "nfd_auth_key2026";
    private static int passCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) {
        System.out.println("========== 认证参数编解码测试 ==========\n");

        testAuthParamModel();
        testAuthParamFromJson();
        testAuthParamToJson();
        testManualEncodeDecodeToken();
        testManualEncodeDecodePassword();
        testManualEncodeDecodeCookie();
        testManualEncodeDecodeCustom();
        testPrimaryCredentialPriority();
        testEmptyAuthParam();
        testSpecialCharacters();
        testGenerateAuthForApiCall();
        printEncryptionExamples();

        System.out.println("\n========== 测试结果汇总 ==========");
        System.out.println("通过: " + passCount + ", 失败: " + failCount);
        System.out.println("========== 测试结束 ==========\n");
    }

    private static void testAuthParamModel() {
        System.out.println("测试: AuthParam 模型基本功能");
        try {
            AuthParam authParam = AuthParam.builder()
                    .authType("accesstoken")
                    .token("test_token_123")
                    .build();

            assertEquals("accesstoken", authParam.getAuthType());
            assertEquals("test_token_123", authParam.getToken());
            assertTrue(authParam.hasValidAuth());
            assertEquals("test_token_123", authParam.getPrimaryCredential());
            pass();
        } catch (AssertionError e) {
            fail(e.getMessage());
        }
    }

    private static void testAuthParamFromJson() {
        System.out.println("测试: AuthParam 从 JsonObject 构造");
        try {
            JsonObject json = new JsonObject()
                    .put("authType", "password")
                    .put("username", "testuser")
                    .put("password", "testpass");

            AuthParam authParam = new AuthParam(json);

            assertEquals("password", authParam.getAuthType());
            assertEquals("testuser", authParam.getUsername());
            assertEquals("testpass", authParam.getPassword());
            assertTrue(authParam.hasValidAuth());
            pass();
        } catch (AssertionError e) {
            fail(e.getMessage());
        }
    }

    private static void testAuthParamToJson() {
        System.out.println("测试: AuthParam 转换为 JsonObject");
        try {
            AuthParam authParam = AuthParam.builder()
                    .authType("cookie")
                    .token("session=abc123")
                    .ext1("key1:value1")
                    .build();

            JsonObject json = authParam.toJsonObject();

            assertEquals("cookie", json.getString("authType"));
            assertEquals("session=abc123", json.getString("token"));
            assertEquals("key1:value1", json.getString("ext1"));
            assertNull(json.getString("username"));
            pass();
        } catch (AssertionError e) {
            fail(e.getMessage());
        }
    }

    private static void testManualEncodeDecodeToken() {
        System.out.println("测试: 手动编解码流程 - Token 认证");
        try {
            JsonObject original = new JsonObject()
                    .put("authType", "accesstoken")
                    .put("token", "my_access_token_12345");

            String jsonStr = original.encode();
            String base64Encoded = AESUtils.encryptBase64ByAES(jsonStr, TEST_KEY);
            String urlEncoded = URLEncoder.encode(base64Encoded, StandardCharsets.UTF_8);

            // 解码验证
            String urlDecoded = URLDecoder.decode(urlEncoded, StandardCharsets.UTF_8);
            assertEquals(base64Encoded, urlDecoded);

            byte[] base64Decoded = Base64.getDecoder().decode(urlDecoded);
            String decryptedJson = AESUtils.decryptByAES(base64Decoded, TEST_KEY);

            JsonObject decoded = new JsonObject(decryptedJson);
            assertEquals("accesstoken", decoded.getString("authType"));
            assertEquals("my_access_token_12345", decoded.getString("token"));
            pass();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    private static void testManualEncodeDecodePassword() {
        System.out.println("测试: 手动编解码流程 - 用户名密码认证");
        try {
            JsonObject original = new JsonObject()
                    .put("authType", "password")
                    .put("username", "testuser")
                    .put("password", "testpassword123");

            String jsonStr = original.encode();
            String base64Encoded = AESUtils.encryptBase64ByAES(jsonStr, TEST_KEY);
            String urlEncoded = URLEncoder.encode(base64Encoded, StandardCharsets.UTF_8);

            String urlDecoded = URLDecoder.decode(urlEncoded, StandardCharsets.UTF_8);
            byte[] base64Decoded = Base64.getDecoder().decode(urlDecoded);
            String decryptedJson = AESUtils.decryptByAES(base64Decoded, TEST_KEY);

            JsonObject decoded = new JsonObject(decryptedJson);
            assertEquals("password", decoded.getString("authType"));
            assertEquals("testuser", decoded.getString("username"));
            assertEquals("testpassword123", decoded.getString("password"));
            pass();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    private static void testManualEncodeDecodeCookie() {
        System.out.println("测试: 手动编解码流程 - Cookie 认证");
        try {
            JsonObject original = new JsonObject()
                    .put("authType", "cookie")
                    .put("token", "session_id=abc123xyz; user_token=def456");

            String jsonStr = original.encode();
            String base64Encoded = AESUtils.encryptBase64ByAES(jsonStr, TEST_KEY);
            String urlEncoded = URLEncoder.encode(base64Encoded, StandardCharsets.UTF_8);

            String urlDecoded = URLDecoder.decode(urlEncoded, StandardCharsets.UTF_8);
            byte[] base64Decoded = Base64.getDecoder().decode(urlDecoded);
            String decryptedJson = AESUtils.decryptByAES(base64Decoded, TEST_KEY);

            JsonObject decoded = new JsonObject(decryptedJson);
            assertEquals("cookie", decoded.getString("authType"));
            assertEquals("session_id=abc123xyz; user_token=def456", decoded.getString("token"));
            pass();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    private static void testManualEncodeDecodeCustom() {
        System.out.println("测试: 手动编解码流程 - 自定义认证");
        try {
            JsonObject original = new JsonObject()
                    .put("authType", "custom")
                    .put("token", "main_token")
                    .put("ext1", "refresh_token:rt_12345")
                    .put("ext2", "device_id:device_abc")
                    .put("ext3", "app_version:1.0.0");

            String jsonStr = original.encode();
            String base64Encoded = AESUtils.encryptBase64ByAES(jsonStr, TEST_KEY);
            String urlEncoded = URLEncoder.encode(base64Encoded, StandardCharsets.UTF_8);

            String urlDecoded = URLDecoder.decode(urlEncoded, StandardCharsets.UTF_8);
            byte[] base64Decoded = Base64.getDecoder().decode(urlDecoded);
            String decryptedJson = AESUtils.decryptByAES(base64Decoded, TEST_KEY);

            JsonObject decoded = new JsonObject(decryptedJson);
            assertEquals("custom", decoded.getString("authType"));
            assertEquals("main_token", decoded.getString("token"));
            assertEquals("refresh_token:rt_12345", decoded.getString("ext1"));
            assertEquals("device_id:device_abc", decoded.getString("ext2"));
            assertEquals("app_version:1.0.0", decoded.getString("ext3"));
            pass();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    private static void testPrimaryCredentialPriority() {
        System.out.println("测试: 主要凭证优先级");
        try {
            // token 优先级最高
            AuthParam authParam1 = AuthParam.builder()
                    .token("token_value")
                    .cookie("cookie_value")
                    .auth("auth_value")
                    .username("user_value")
                    .build();
            assertEquals("token_value", authParam1.getPrimaryCredential());

            // 没有 token 时，cookie 优先
            AuthParam authParam2 = AuthParam.builder()
                    .cookie("cookie_value")
                    .auth("auth_value")
                    .username("user_value")
                    .build();
            assertEquals("cookie_value", authParam2.getPrimaryCredential());

            // 没有 token 和 cookie 时，auth 优先
            AuthParam authParam3 = AuthParam.builder()
                    .auth("auth_value")
                    .username("user_value")
                    .build();
            assertEquals("auth_value", authParam3.getPrimaryCredential());

            // 只有 username 时
            AuthParam authParam4 = AuthParam.builder()
                    .username("user_value")
                    .build();
            assertEquals("user_value", authParam4.getPrimaryCredential());
            pass();
        } catch (AssertionError e) {
            fail(e.getMessage());
        }
    }

    private static void testEmptyAuthParam() {
        System.out.println("测试: 空认证参数");
        try {
            AuthParam authParam = new AuthParam();
            assertFalse(authParam.hasValidAuth());
            assertNull(authParam.getPrimaryCredential());

            AuthParam authParam2 = new AuthParam(null);
            assertFalse(authParam2.hasValidAuth());
            pass();
        } catch (AssertionError e) {
            fail(e.getMessage());
        }
    }

    private static void testSpecialCharacters() {
        System.out.println("测试: 特殊字符处理");
        try {
            JsonObject original = new JsonObject()
                    .put("authType", "cookie")
                    .put("token", "name=中文测试; value=!@#$%^&*()_+-={}|[]\\:\";'<>?,./");

            String jsonStr = original.encode();
            String base64Encoded = AESUtils.encryptBase64ByAES(jsonStr, TEST_KEY);
            String urlEncoded = URLEncoder.encode(base64Encoded, StandardCharsets.UTF_8);

            String urlDecoded = URLDecoder.decode(urlEncoded, StandardCharsets.UTF_8);
            byte[] base64Decoded = Base64.getDecoder().decode(urlDecoded);
            String decryptedJson = AESUtils.decryptByAES(base64Decoded, TEST_KEY);

            JsonObject decoded = new JsonObject(decryptedJson);
            assertEquals("cookie", decoded.getString("authType"));
            assertEquals("name=中文测试; value=!@#$%^&*()_+-={}|[]\\:\";'<>?,./", decoded.getString("token"));
            pass();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    private static void testGenerateAuthForApiCall() {
        System.out.println("测试: 生成可用于接口调用的 auth 参数");
        try {
            JsonObject authJson = new JsonObject()
                    .put("authType", "accesstoken")
                    .put("token", "real_token_for_api_test");

            String jsonStr = authJson.encode();
            String base64Encoded = AESUtils.encryptBase64ByAES(jsonStr, TEST_KEY);
            String auth = URLEncoder.encode(base64Encoded, StandardCharsets.UTF_8);

            String baseUrl = "http://127.0.0.1:6400/parser";
            String shareUrl = "https://www.lanzoux.com/test123";
            String pwd = "abcd";

            String fullUrl = String.format("%s?url=%s&pwd=%s&auth=%s",
                    baseUrl,
                    URLEncoder.encode(shareUrl, StandardCharsets.UTF_8),
                    pwd,
                    auth);

            System.out.println("  生成的完整 API 调用 URL:");
            System.out.println("  " + fullUrl);

            assertTrue(fullUrl.contains("url="));
            assertTrue(fullUrl.contains("pwd="));
            assertTrue(fullUrl.contains("auth="));
            pass();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    private static void printEncryptionExamples() {
        System.out.println("\n========== 认证参数加密示例（供接口调用参考）==========\n");
        try {
            // 1. AccessToken
            JsonObject tokenAuth = new JsonObject()
                    .put("authType", "accesstoken")
                    .put("token", "example_access_token");
            String tokenEncrypted = URLEncoder.encode(
                    AESUtils.encryptBase64ByAES(tokenAuth.encode(), TEST_KEY),
                    StandardCharsets.UTF_8);
            System.out.println("1. AccessToken 认证:");
            System.out.println("   原始: " + tokenAuth.encode());
            System.out.println("   加密: " + tokenEncrypted);
            System.out.println();

            // 2. Cookie
            JsonObject cookieAuth = new JsonObject()
                    .put("authType", "cookie")
                    .put("token", "session=abc123");
            String cookieEncrypted = URLEncoder.encode(
                    AESUtils.encryptBase64ByAES(cookieAuth.encode(), TEST_KEY),
                    StandardCharsets.UTF_8);
            System.out.println("2. Cookie 认证:");
            System.out.println("   原始: " + cookieAuth.encode());
            System.out.println("   加密: " + cookieEncrypted);
            System.out.println();

            // 3. 用户名密码
            JsonObject passwordAuth = new JsonObject()
                    .put("authType", "password")
                    .put("username", "testuser")
                    .put("password", "testpass");
            String passwordEncrypted = URLEncoder.encode(
                    AESUtils.encryptBase64ByAES(passwordAuth.encode(), TEST_KEY),
                    StandardCharsets.UTF_8);
            System.out.println("3. 用户名密码认证:");
            System.out.println("   原始: " + passwordAuth.encode());
            System.out.println("   加密: " + passwordEncrypted);
            System.out.println();

            // 4. 自定义
            JsonObject customAuth = new JsonObject()
                    .put("authType", "custom")
                    .put("token", "main_token")
                    .put("ext1", "key1:value1");
            String customEncrypted = URLEncoder.encode(
                    AESUtils.encryptBase64ByAES(customAuth.encode(), TEST_KEY),
                    StandardCharsets.UTF_8);
            System.out.println("4. 自定义认证:");
            System.out.println("   原始: " + customAuth.encode());
            System.out.println("   加密: " + customEncrypted);
            System.out.println();

            pass();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    // 断言方法
    private static void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError("期望: " + expected + ", 实际: " + actual);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("期望为 true，实际为 false");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("期望为 false，实际为 true");
        }
    }

    private static void assertNull(Object obj) {
        if (obj != null) {
            throw new AssertionError("期望为 null，实际为: " + obj);
        }
    }

    private static void pass() {
        passCount++;
        System.out.println("  ✓ 通过\n");
    }

    private static void fail(String message) {
        failCount++;
        System.out.println("  ✗ 失败: " + message + "\n");
    }
}
