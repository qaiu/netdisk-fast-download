package cn.qaiu.lz.common.util;

import cn.qaiu.lz.web.model.AuthParam;
import cn.qaiu.util.AESUtils;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.*;

/**
 * 认证参数编解码测试
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * @date 2026/2/5
 */
public class AuthParamCodecTest {

    // 测试用的固定密钥
    private static final String TEST_KEY = "nfd_auth_key2026";

    @Test
    public void testAuthParamModel() {
        // 测试构建器
        AuthParam authParam = AuthParam.builder()
                .authType("accesstoken")
                .token("test_token_123")
                .build();

        assertEquals("accesstoken", authParam.getAuthType());
        assertEquals("test_token_123", authParam.getToken());
        assertTrue(authParam.hasValidAuth());
        assertEquals("test_token_123", authParam.getPrimaryCredential());
    }

    @Test
    public void testAuthParamFromJson() {
        JsonObject json = new JsonObject()
                .put("authType", "password")
                .put("username", "testuser")
                .put("password", "testpass");

        AuthParam authParam = new AuthParam(json);

        assertEquals("password", authParam.getAuthType());
        assertEquals("testuser", authParam.getUsername());
        assertEquals("testpass", authParam.getPassword());
        assertTrue(authParam.hasValidAuth());
    }

    @Test
    public void testAuthParamToJson() {
        AuthParam authParam = AuthParam.builder()
                .authType("cookie")
                .token("session=abc123")
                .ext1("key1:value1")
                .build();

        JsonObject json = authParam.toJsonObject();

        assertEquals("cookie", json.getString("authType"));
        assertEquals("session=abc123", json.getString("token"));
        assertEquals("key1:value1", json.getString("ext1"));
        assertNull(json.getString("username")); // 未设置的字段应为 null
    }

    @Test
    public void testManualEncodeDecodeToken() throws Exception {
        // 构建原始 JSON
        JsonObject original = new JsonObject()
                .put("authType", "accesstoken")
                .put("token", "my_access_token_12345");

        String jsonStr = original.encode();
        System.out.println("原始 JSON: " + jsonStr);

        // Step 1: AES 加密 + Base64 编码
        String base64Encoded = AESUtils.encryptBase64ByAES(jsonStr, TEST_KEY);
        System.out.println("AES+Base64 编码: " + base64Encoded);

        // Step 2: URL 编码
        String urlEncoded = URLEncoder.encode(base64Encoded, StandardCharsets.UTF_8);
        System.out.println("URL 编码: " + urlEncoded);

        // ===== 解码流程 =====

        // Step 1: URL 解码
        String urlDecoded = URLDecoder.decode(urlEncoded, StandardCharsets.UTF_8);
        assertEquals(base64Encoded, urlDecoded);

        // Step 2: Base64 解码
        byte[] base64Decoded = Base64.getDecoder().decode(urlDecoded);

        // Step 3: AES 解密
        String decryptedJson = AESUtils.decryptByAES(base64Decoded, TEST_KEY);
        System.out.println("解密后 JSON: " + decryptedJson);

        // Step 4: JSON 解析
        JsonObject decoded = new JsonObject(decryptedJson);
        assertEquals("accesstoken", decoded.getString("authType"));
        assertEquals("my_access_token_12345", decoded.getString("token"));
    }

    @Test
    public void testManualEncodeDecodePassword() throws Exception {
        JsonObject original = new JsonObject()
                .put("authType", "password")
                .put("username", "testuser")
                .put("password", "testpassword123");

        String jsonStr = original.encode();
        String base64Encoded = AESUtils.encryptBase64ByAES(jsonStr, TEST_KEY);
        String urlEncoded = URLEncoder.encode(base64Encoded, StandardCharsets.UTF_8);

        System.out.println("用户名密码认证 - 加密结果: " + urlEncoded);

        // 解码验证
        String urlDecoded = URLDecoder.decode(urlEncoded, StandardCharsets.UTF_8);
        byte[] base64Decoded = Base64.getDecoder().decode(urlDecoded);
        String decryptedJson = AESUtils.decryptByAES(base64Decoded, TEST_KEY);

        JsonObject decoded = new JsonObject(decryptedJson);
        assertEquals("password", decoded.getString("authType"));
        assertEquals("testuser", decoded.getString("username"));
        assertEquals("testpassword123", decoded.getString("password"));
    }

    @Test
    public void testManualEncodeDecodeCookie() throws Exception {
        JsonObject original = new JsonObject()
                .put("authType", "cookie")
                .put("token", "session_id=abc123xyz; user_token=def456");

        String jsonStr = original.encode();
        String base64Encoded = AESUtils.encryptBase64ByAES(jsonStr, TEST_KEY);
        String urlEncoded = URLEncoder.encode(base64Encoded, StandardCharsets.UTF_8);

        System.out.println("Cookie 认证 - 加密结果: " + urlEncoded);

        // 解码验证
        String urlDecoded = URLDecoder.decode(urlEncoded, StandardCharsets.UTF_8);
        byte[] base64Decoded = Base64.getDecoder().decode(urlDecoded);
        String decryptedJson = AESUtils.decryptByAES(base64Decoded, TEST_KEY);

        JsonObject decoded = new JsonObject(decryptedJson);
        assertEquals("cookie", decoded.getString("authType"));
        assertEquals("session_id=abc123xyz; user_token=def456", decoded.getString("token"));
    }

    @Test
    public void testManualEncodeDecodeCustom() throws Exception {
        JsonObject original = new JsonObject()
                .put("authType", "custom")
                .put("token", "main_token")
                .put("ext1", "refresh_token:rt_12345")
                .put("ext2", "device_id:device_abc")
                .put("ext3", "app_version:1.0.0");

        String jsonStr = original.encode();
        String base64Encoded = AESUtils.encryptBase64ByAES(jsonStr, TEST_KEY);
        String urlEncoded = URLEncoder.encode(base64Encoded, StandardCharsets.UTF_8);

        System.out.println("自定义认证 - 加密结果: " + urlEncoded);

        // 解码验证
        String urlDecoded = URLDecoder.decode(urlEncoded, StandardCharsets.UTF_8);
        byte[] base64Decoded = Base64.getDecoder().decode(urlDecoded);
        String decryptedJson = AESUtils.decryptByAES(base64Decoded, TEST_KEY);

        JsonObject decoded = new JsonObject(decryptedJson);
        assertEquals("custom", decoded.getString("authType"));
        assertEquals("main_token", decoded.getString("token"));
        assertEquals("refresh_token:rt_12345", decoded.getString("ext1"));
        assertEquals("device_id:device_abc", decoded.getString("ext2"));
        assertEquals("app_version:1.0.0", decoded.getString("ext3"));
    }

    @Test
    public void testPrimaryCredentialPriority() {
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
    }

    @Test
    public void testEmptyAuthParam() {
        AuthParam authParam = new AuthParam();
        assertFalse(authParam.hasValidAuth());
        assertNull(authParam.getPrimaryCredential());

        AuthParam authParam2 = new AuthParam(null);
        assertFalse(authParam2.hasValidAuth());
    }

    @Test
    public void testSpecialCharacters() throws Exception {
        JsonObject original = new JsonObject()
                .put("authType", "cookie")
                .put("token", "name=中文测试; value=!@#$%^&*()_+-={}|[]\\:\";'<>?,./");

        String jsonStr = original.encode();
        String base64Encoded = AESUtils.encryptBase64ByAES(jsonStr, TEST_KEY);
        String urlEncoded = URLEncoder.encode(base64Encoded, StandardCharsets.UTF_8);

        System.out.println("特殊字符测试 - 加密结果: " + urlEncoded);

        // 解码验证
        String urlDecoded = URLDecoder.decode(urlEncoded, StandardCharsets.UTF_8);
        byte[] base64Decoded = Base64.getDecoder().decode(urlDecoded);
        String decryptedJson = AESUtils.decryptByAES(base64Decoded, TEST_KEY);

        JsonObject decoded = new JsonObject(decryptedJson);
        assertEquals("cookie", decoded.getString("authType"));
        assertEquals("name=中文测试; value=!@#$%^&*()_+-={}|[]\\:\";'<>?,./", decoded.getString("token"));
    }

    @Test
    public void testGenerateAuthForApiCall() throws Exception {
        // 模拟实际使用场景
        JsonObject authJson = new JsonObject()
                .put("authType", "accesstoken")
                .put("token", "real_token_for_api_test");

        String jsonStr = authJson.encode();
        String base64Encoded = AESUtils.encryptBase64ByAES(jsonStr, TEST_KEY);
        String auth = URLEncoder.encode(base64Encoded, StandardCharsets.UTF_8);

        // 构建完整的 API URL
        String baseUrl = "http://127.0.0.1:6400/parser";
        String shareUrl = "https://www.lanzoux.com/test123";
        String pwd = "abcd";

        String fullUrl = String.format("%s?url=%s&pwd=%s&auth=%s",
                baseUrl,
                URLEncoder.encode(shareUrl, StandardCharsets.UTF_8),
                pwd,
                auth);

        System.out.println("=== 生成的完整 API 调用 URL ===");
        System.out.println(fullUrl);
        System.out.println("=== auth 参数值 ===");
        System.out.println(auth);

        // 验证 URL 格式正确
        assertTrue(fullUrl.contains("url="));
        assertTrue(fullUrl.contains("pwd="));
        assertTrue(fullUrl.contains("auth="));
    }

    @Test
    public void printEncryptionExamples() throws Exception {
        System.out.println("\n========== 认证参数加密示例 ==========\n");

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

        System.out.println("========== 示例结束 ==========\n");
    }
}
