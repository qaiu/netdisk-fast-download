package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.impl.CtfileSession.Decision;
import cn.qaiu.parser.impl.CtfileSession.Kind;
import cn.qaiu.parser.impl.CtfileSession.ShareTarget;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CtfileResponseTest {

    @Test
    public void classifiesNotFoundInvalidPasswordAndCaptcha() {
        Decision missing = CtfileSession.classify(json(404, null, "File was not found."), 404, "");
        assertEquals(Kind.NOT_FOUND, missing.kind);
        assertTrue(missing.message.contains("不存在或已过期"));
        assertFalse(missing.message.contains("密码"));

        Decision unsigned = CtfileSession.classify(json(403, null, "Unsigned file link is not accepted."), 403, "");
        assertEquals(Kind.INVALID, unsigned.kind);

        Decision needPassword = CtfileSession.classify(new JsonObject(
                "{\"code\":423,\"data\":{\"captcha_type\":\"turnstile\",\"capabilities\":{\"passcodeRequired\":true}},"
                        + "\"msg\":\"Share passcode is required.\"}"), 423, "");
        assertEquals(Kind.NEED_PASSWORD, needPassword.kind);
        assertTrue(needPassword.message.contains("需要访问密码"));

        Decision wrong = CtfileSession.classify(new JsonObject(
                "{\"code\":423,\"data\":{\"error_type\":\"passcode_incorrect\",\"captcha_type\":\"turnstile\","
                        + "\"capabilities\":{\"passcodeRequired\":true}},\"msg\":\"incorrect\"}"), 423, "7609");
        assertEquals(Kind.BAD_PASSWORD, wrong.kind);

        Decision turnstile = CtfileSession.classify(new JsonObject(
                "{\"code\":423,\"data\":{\"error_type\":\"captcha_required\",\"captcha_type\":\"turnstile\","
                        + "\"capabilities\":{\"passcodeRequired\":true,\"ownerId\":64115194}},"
                        + "\"msg\":\"Share passcode needs a new human check.\"}"), 423, "7609");
        assertEquals(Kind.TURNSTILE, turnstile.kind);
        assertEquals("64115194", turnstile.ownerId);
        assertTrue(turnstile.message.contains("Turnstile"));
        assertFalse(turnstile.message.contains("密码"));

        Decision altcha = CtfileSession.classify(new JsonObject(
                "{\"code\":423,\"data\":{\"error_type\":\"captcha_required\",\"captcha_type\":\"altcha\","
                        + "\"capabilities\":{\"passcodeRequired\":false,\"ownerId\":4015376}},"
                        + "\"msg\":\"Share access needs a human check.\"}"), 423, "");
        assertEquals(Kind.ALTCHA, altcha.kind);
        assertEquals("4015376", altcha.ownerId);

        Decision ok = CtfileSession.classify(new JsonObject(
                "{\"code\":200,\"data\":{\"access_pass\":\"v1.aaa.bbb\",\"capabilities\":{\"ownerId\":4015376}}}"), 200, "");
        assertEquals(Kind.OK, ok.kind);
        assertEquals("v1.aaa.bbb", ok.accessPass);

        Decision labeledTurnstile = CtfileSession.classify(new JsonObject(
                "{\"code\":423,\"data\":{\"error_type\":\"captcha_required\",\"captcha_type\":\"turnstile\","
                        + "\"capabilities\":{\"passcodeRequired\":true}},\"msg\":\"Share passcode needs a new human check.\"}"),
                423, "pw");
        assertTrue(CtfileSession.shouldSolveAltcha(labeledTurnstile));
        assertFalse(CtfileSession.shouldSolveAltcha(needPassword));
        Decision wrongAfterAltcha = CtfileSession.classify(new JsonObject(
                "{\"code\":423,\"data\":{\"error_type\":\"passcode_incorrect\",\"captcha_type\":\"turnstile\","
                        + "\"capabilities\":{\"passcodeRequired\":true}},\"msg\":\"Share passcode is incorrect.\"}"),
                423, "0000");
        assertEquals(Kind.BAD_PASSWORD, wrongAfterAltcha.kind);
        assertFalse(CtfileSession.shouldSolveAltcha(wrongAfterAltcha));

        ShareLinkInfo passwordShare = ShareLinkInfo.newBuilder()
                .type("ct")
                .shareKey("11-22-ab")
                .sharePassword("pw")
                .shareUrl("https://example.test/f/11-22-ab?p=pw")
                .build();
        assertFalse(CtTool.requiresV4(passwordShare));
        passwordShare.getOtherParam().put("ctV4FileId", "f22");
        assertTrue(CtTool.requiresV4(passwordShare));
    }

    @Test
    public void resourceQueryKeepsTokensAndEncodesThem() {
        Map<String, String> query = CtfileSession.resourceQuery("file", "4015376-131945810",
                new ShareTarget("0", "", "0"), "7609", "a+b/c=", "turn stile", "v1.aaa.bbb");
        String form = CtfileSession.form(query);
        assertTrue(form.contains("kind=file"));
        assertTrue(form.contains("passcode=7609"));
        assertTrue(form.contains("altcha_token=a%2Bb%2Fc%3D"));
        assertTrue(form.contains("turnstile_token=turn+stile") || form.contains("turnstile_token=turn%20stile"));
        assertTrue(form.contains("access_pass=v1.aaa.bbb"));
    }

    @Test
    public void listingRowBecomesFileAndFolderLinks() {
        ShareLinkInfo info = ShareLinkInfo.newBuilder()
                .type("ctd")
                .panName("城通网盘-目录")
                .shareKey("64115194-164803691-48508c")
                .sharePassword("7609")
                .shareUrl("https://url94.ctfile.com/d/64115194-164803691-48508c?p=7609")
                .build();
        info.getOtherParam().put("domainName", "https://nfd.example");

        FileInfo folder = CtTool.toFileInfo(new JsonObject()
                .put("kind", "folder")
                .put("name", "2022")
                .put("folderId", 54597713)
                .put("folderKey", "bf0603")
                .put("date", "2023-02-22"), info);
        assertEquals("folder", folder.getFileType());
        assertTrue(folder.getParserUrl().contains("dirId="));
        assertTrue(folder.getParserUrl().contains("54597713"));
        assertTrue(folder.getParserUrl().contains("pwd=7609"));

        FileInfo file = CtTool.toFileInfo(new JsonObject()
                .put("kind", "file")
                .put("name", "notes.pdf")
                .put("href", "#/f/t-token")
                .put("fileId", 415670738)
                .put("extension", "pdf")
                .put("size", "1 MB")
                .put("size_bytes", 1048576)
                .put("date", "2019-12-31"), info);
        assertEquals("t-token", fileId(file));
        assertEquals(Long.valueOf(1048576L), file.getSize());
        assertEquals("pdf", file.getFileType());
        assertTrue(file.getParserUrl().startsWith("https://nfd.example/v2/redirectUrl/ctd/"));
    }

    private static JsonObject json(int code, JsonObject data, String message) {
        JsonObject body = new JsonObject().put("code", code).put("msg", message);
        if (data != null) {
            body.put("data", data);
        }
        return body;
    }

    private static String fileId(FileInfo file) {
        String url = file.getParserUrl();
        String encoded = url.substring(url.lastIndexOf('/') + 1);
        return new JsonObject(decodeUrlBase64(encoded)).getString("id");
    }

    private static String decodeUrlBase64(String value) {
        String padded = value;
        int mod = padded.length() % 4;
        if (mod != 0) {
            padded += "=".repeat(4 - mod);
        }
        byte[] bytes = java.util.Base64.getUrlDecoder().decode(padded);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
