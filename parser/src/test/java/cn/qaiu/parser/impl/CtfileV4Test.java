package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.impl.CtfileV4Client.V4CallException;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CtfileV4Test {

    private static final String FILE_LINK = "xtc11-f22-ab-pw";
    private static final String DIR_LINK = "xtc11-d33-cd-pw";

    @Test
    public void webShareBecomesXtlink() {
        assertEquals(FILE_LINK, CtfileXtlink.fromShare("11-22-ab", "pw", false));
        assertEquals(DIR_LINK, CtfileXtlink.fromShare("11-33-cd", "pw", true));
        assertEquals("xturl44-f55", CtfileXtlink.fromShare("44-55", "", false));
        assertEquals("xturl11-d33-cd", CtfileXtlink.fromShare("11-33-cd", null, true));
        assertEquals("xtc1-d2-ab-pw", CtfileXtlink.fromShare("xtc1-d2-ab-pw", "other", true));
        assertNull(CtfileXtlink.fromShare("t-signed", "pw", true));
        assertEquals("f22", CtfileXtlink.prefixedId("22", false));
        assertEquals("22", CtfileXtlink.bareId("f22"));
    }

    @Test
    public void requestBodiesMatchTheClientProtocol() {
        JsonObject list = CtfileV4Client.listBody(DIR_LINK, "33", 1, 2);
        assertEquals(DIR_LINK, list.getString("xtlink"));
        assertEquals(33L, list.getLong("folder_id").longValue());
        assertEquals(1, list.getInteger("start").intValue());
        assertEquals(2, list.getInteger("pagesize").intValue());

        JsonObject auth = CtfileV4Client.authBody(FILE_LINK, "f22");
        assertEquals("f22", auth.getString("ids"));

        JsonObject fetch = CtfileV4Client.fetchBody(FILE_LINK, "22", "sig");
        assertEquals("22", fetch.getString("file_id"));
        assertEquals("sig", fetch.getString("download_sign"));
        assertEquals(0, fetch.getInteger("carrier").intValue());

        assertFalse(CtfileV4Client.refreshBody("rt", false).containsKey("rotate_token"));
        assertTrue(CtfileV4Client.refreshBody("rt", true).getBoolean("rotate_token"));
    }

    @Test
    public void errorCodesStaySpecific() {
        assertTrue(CtfileV4Client.describeFailure(401, "无效或过期的refresh token", "api.auth.invalid_refresh_token")
                .contains("refresh_token 无效或已过期"));
        assertTrue(CtfileV4Client.describeFailure(401, "无效的令牌或账户已锁定", "").contains("登录令牌无效或账号已锁定"));
        assertEquals("城通账号已锁定", CtfileV4Client.describeFailure(401, "账户已锁定", ""));
        assertEquals("访问密码不正确", CtfileV4Client.describeFailure(403, "访问密码不正确", "api.passcode"));
        assertTrue(CtfileV4Client.describeFailure(404, "分享不存在", "").contains("不存在或已过期"));
        assertTrue(CtfileV4Client.describeFailure(401, "请扫码完成登录保护", "qr_required").contains("扫码"));
        V4CallException invalid = new V4CallException(401, "无效的令牌或账户已锁定", "");
        assertTrue(invalid.authenticationFailure());
        assertFalse(new V4CallException(403, "访问密码不正确", "passcode").authenticationFailure());
    }

    @Test
    public void listRowBecomesDownloadParamWithXtlink() {
        ShareLinkInfo info = ShareLinkInfo.newBuilder()
                .type("ctd")
                .panName("城通网盘-目录")
                .shareKey("11-33-cd")
                .sharePassword("pw")
                .shareUrl("https://example.test/d/11-33-cd?p=pw")
                .build();
        info.getOtherParam().put("domainName", "https://nfd.example");
        FileInfo file = CtfileV4Client.toFileInfo(new JsonObject()
                .put("key", "f22")
                .put("name", "notes.pdf")
                .put("icon", "pdf")
                .put("size", 12)
                .put("date", "2024-01-02"), info, DIR_LINK);
        assertEquals("notes.pdf", file.getFileName());
        assertEquals(Long.valueOf(12L), file.getSize());
        assertTrue(file.getParserUrl().contains("/v2/redirectUrl/ctd/"));

        JsonObject param = new JsonObject(decode(file.getParserUrl().substring(file.getParserUrl().lastIndexOf('/') + 1)));
        assertEquals("f22", param.getString("id"));
        assertEquals(DIR_LINK, param.getString("xtlink"));
        assertEquals("pw", param.getString("pwd"));

        ShareLinkInfo download = ShareLinkInfo.newBuilder().type("ctd").sharePassword("").build();
        assertTrue(CtTool.applyFileParam(download, param));
        assertEquals("f22", download.getOtherParam().get("ctV4FileId"));
        assertEquals("11-33-cd", download.getShareKey());

        FileInfo folder = CtfileV4Client.toFileInfo(new JsonObject()
                .put("fid", "d33")
                .put("name", "2022")
                .put("is_folder", true), info, DIR_LINK);
        assertEquals("folder", folder.getFileType());
        assertTrue(folder.getParserUrl().contains("dirId=33"));
        assertTrue(folder.getParserUrl().contains("pwd=pw"));
    }

    @Test
    public void credentialsComeFromAuthsAndDoNotTreatSharePasswordAsLogin() {
        ShareLinkInfo info = ShareLinkInfo.newBuilder()
                .type("ct")
                .shareKey("11-22-ab")
                .sharePassword("pw")
                .build();
        assertFalse(CtfileV4Client.hasCredentials(info));
        MultiMap auths = MultiMap.caseInsensitiveMultiMap();
        auths.set("refresh_token", "refresh-value");
        auths.set("authorization", "Bearer access-value");
        auths.set("_configId", "home");
        info.getOtherParam().put("auths", auths);
        CtfileV4Client.Credentials credentials = CtfileV4Client.credentials(info);
        assertEquals("refresh-value", credentials.refreshToken);
        assertEquals("access-value", credentials.accessToken);
        assertTrue(credentials.usable());
        assertTrue(CtfileV4Client.missingAccountMessage().contains("auths.ct.refresh_token"));
    }

    private static String decode(String value) {
        String padded = value;
        int mod = padded.length() % 4;
        if (mod != 0) {
            padded += "=".repeat(4 - mod);
        }
        return new String(java.util.Base64.getUrlDecoder().decode(padded), java.nio.charset.StandardCharsets.UTF_8);
    }
}
