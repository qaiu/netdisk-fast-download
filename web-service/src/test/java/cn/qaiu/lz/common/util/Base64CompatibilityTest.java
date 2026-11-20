package cn.qaiu.lz.common.util;

import org.junit.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Base64CompatibilityTest {
    @Test
    public void acceptsStoredPasswordHash() {
        String password = "密码-test";
        String hash = PasswordUtil.hashPassword(password);
        assertTrue(PasswordUtil.checkPassword(password, hash));
        assertFalse(PasswordUtil.checkPassword("wrong", hash));
    }

    @Test
    public void acceptsJwtWithUnpaddedUrlSafePayload() {
        String json = "{\"username\":\"下载用户\"}";
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        assertEquals("下载用户", JwtUtil.getUsernameFromToken("header." + payload + ".signature"));
    }

    @Test
    public void acceptsLegacyEncodedAuth() throws Exception {
        String key = "0123456789abcdef";
        String json = "{\"authType\":\"cookie\",\"token\":\"compatibility-test\"}";
        String base64 = cn.qaiu.util.AESUtils.encryptBase64ByAES(json, key);
        String urlEncoded = URLEncoder.encode(base64, StandardCharsets.UTF_8);
        assertEquals("cookie", AuthParamCodec.decode(urlEncoded, key).getAuthType());
        assertEquals("cookie", AuthParamCodec.decode(base64.replace('+', ' '), key).getAuthType());
        assertNull(AuthParamCodec.decode(base64 + "!", key));
    }
}
