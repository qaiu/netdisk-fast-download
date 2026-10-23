package cn.qaiu.parser.impl;

import cn.qaiu.util.AESUtils;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class FjDownloadParamsTest {
    @Test
    public void anonymousIdentityIsEmptyRatherThanNull() throws Exception {
        JsonObject params = FjDownloadParams.create("123", null, "uuid", "share", 1000L);
        assertEquals("123|", decrypt(params.getString("fidEncode")));
        assertEquals("1000", decrypt(params.getString("ts")));
        assertEquals("123|1000", decrypt(params.getString("auth")));
    }

    @Test
    public void authenticatedIdentityAndTimestampAreRegenerated() throws Exception {
        JsonObject params = FjDownloadParams.create("123", "456", "uuid", "share", 2000L);
        assertEquals("123|456", decrypt(params.getString("fidEncode")));
        assertEquals("123|2000", decrypt(params.getString("auth")));
    }

    @Test
    public void legacyLinksRecoverOnlyFileId() throws Exception {
        JsonObject old = JsonObject.of("fidEncode", AESUtils.encrypt2Hex("123|null"), "ts", "expired");
        assertEquals("123", FjDownloadParams.fileId(old));
        assertEquals("789", FjDownloadParams.fileId(JsonObject.of("fileId", "789")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidLegacyLinkFailsExplicitly() throws Exception {
        FjDownloadParams.fileId(JsonObject.of("fidEncode", AESUtils.encrypt2Hex("invalid")));
    }

    @Test
    public void upstreamErrorPreservesBusinessCodeAndMessage() {
        String message = FjDownloadParams.missingLocation(200, "{\"code\":401,\"msg\":\"请登录后下载\"}");
        assertTrue(message.contains("HTTP 200"));
        assertTrue(message.contains("code=401"));
        assertTrue(message.contains("请登录后下载"));
        assertTrue(FjDownloadParams.missingLocation(502, "").contains("响应体为空"));
        assertTrue(FjDownloadParams.missingLocation(502, "x".repeat(1000)).length() < 600);
    }

    @Test
    public void ownerIdentityComesFromDirectoryDataOrLegacySignature() throws Exception {
        assertEquals("11069975", FjDownloadParams.ownerId(JsonObject.of("userId", 11069975L)));
        assertEquals("11069975", FjDownloadParams.ownerId(
                JsonObject.of("fidEncode", AESUtils.encrypt2Hex("61731757356|11069975"))));
        assertNull(FjDownloadParams.ownerId(JsonObject.of("fidEncode", AESUtils.encrypt2Hex("123|null"))));
        assertNull(FjDownloadParams.ownerId(JsonObject.of("fidEncode", AESUtils.encrypt2Hex("123|"))));
    }

    private String decrypt(String value) throws Exception {
        return AESUtils.decryptByHexAES(value, AESUtils.CIPHER_AES0);
    }
}
