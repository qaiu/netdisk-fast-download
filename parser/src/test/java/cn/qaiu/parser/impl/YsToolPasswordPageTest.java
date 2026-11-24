package cn.qaiu.parser.impl;

import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 永硕入口页解析与重定向的离线测试，不访问网络。
 */
public class YsToolPasswordPageTest {

    private static final String NEW_GATE = ""
            + "<div id=\"entryConfig\" style=\"display:none\" data-dlmc=\"qaiu\"\n"
            + "     data-verification-token=\"new-token_value.1\"></div>\n"
            + "<form id=\"loginForm\"></form>\n";

    private static final String OLD_GATE = ""
            + "<script>var cfg = { RequestVerificationToken': 'legacy-token' };</script>\n"
            + "<div id=\"loginPopup\"></div>\n"
            + "<form action=\"/?handler=VerifyPassword\"></form>\n";

    private static final String OPEN_SPACE = ""
            + "<script>window.htxx = {\"dlmc\":\"fufu1\",\"qqdz\":\"https://c6.ysepan.com/api/\","
            + "\"yh\":{\"isgly\":false},\"ym\":\"ysepan.com\"};</script>";

    @Test
    public void testNewPasswordPageTokenAndDlmc() {
        assertTrue(YsTool.isPasswordPage(NEW_GATE));
        assertEquals("new-token_value.1", YsTool.extractAntiforgery(NEW_GATE));
        assertEquals("qaiu", YsTool.extractDlmc(NEW_GATE));
        assertNull(YsTool.extractHtxx(NEW_GATE));
    }

    @Test
    public void testLegacyPasswordPageStillParsed() {
        assertTrue(YsTool.isPasswordPage(OLD_GATE));
        assertEquals("legacy-token", YsTool.extractAntiforgery(OLD_GATE));
        assertNull(YsTool.extractDlmc(OLD_GATE));
    }

    @Test
    public void testNewTokenWinsWhenBothFormatsExist() {
        String mixed = NEW_GATE + OLD_GATE;
        assertEquals("new-token_value.1", YsTool.extractAntiforgery(mixed));
    }

    @Test
    public void testSingleQuotedAttributes() {
        String html = "<div id='entryConfig' data-dlmc='sohehe4' "
                + "data-verification-token='quoted-token'></div>";
        assertTrue(YsTool.isPasswordPage(html));
        assertEquals("quoted-token", YsTool.extractAntiforgery(html));
        assertEquals("sohehe4", YsTool.extractDlmc(html));
    }

    @Test
    public void testOpenSpaceIsNotPasswordPageAndKeepsNestedHtxx() {
        assertFalse(YsTool.isPasswordPage(OPEN_SPACE));
        JsonObject htxx = YsTool.extractHtxx(OPEN_SPACE);
        assertEquals("fufu1", htxx.getString("dlmc"));
        assertEquals("https://c6.ysepan.com/api/", htxx.getString("qqdz"));
        assertFalse(htxx.getJsonObject("yh").getBoolean("isgly"));
    }

    @Test
    public void testYs168RedirectTargetsYsepanPasswordEndpoint() {
        String next = YsTool.resolveRedirect("https://qaiu.ys168.com/", "https://qaiu.ysepan.com");
        assertEquals("https://qaiu.ysepan.com", next);
        assertEquals("https://qaiu.ysepan.com", YsTool.originOf(next));
        assertEquals("https://qaiu.ysepan.com/?handler=VerifyPassword",
                YsTool.verifyPasswordEndpoint(YsTool.originOf(next)));
        assertFalse(YsTool.verifyPasswordEndpoint(YsTool.originOf(next)).contains("ys168.com"));
    }

    @Test
    public void testProtocolRelativeAndRelativeRedirects() {
        String protocolRelative = YsTool.resolveRedirect(
                "https://qaiu.ys168.com/", "//qaiu.ysepan.com/");
        assertEquals("https://qaiu.ysepan.com/", protocolRelative);
        assertEquals("https://qaiu.ysepan.com", YsTool.originOf(protocolRelative));

        String relative = YsTool.resolveRedirect(
                "https://qaiu.ysupan.com/", "/login.aspx?d=qaiu");
        assertEquals("https://qaiu.ysupan.com/login.aspx?d=qaiu", relative);
        assertEquals("https://qaiu.ysupan.com", YsTool.originOf(relative));
    }

    @Test
    public void testNonDefaultPortKeptInOrigin() {
        assertEquals("https://y.ys168.com:8100", YsTool.originOf("https://y.ys168.com:8100/"));
        assertEquals("https://qaiu.ysepan.com", YsTool.originOf("https://qaiu.ysepan.com:443/"));
    }

    @Test
    public void testWrongPasswordUsesUpstreamMessageWithoutSecrets() {
        String body = "{\"success\":false,\"code\":\"invalid_password\",\"message\":\"密码错误\"}";
        assertEquals("密码错误", YsTool.passwordFailureMessage(400, body));

        String leaked = "{\"success\":false,\"code\":\"invalid_password\",\"message\":\"eyJhbGciOiJIUzI1NiJ9.secret\"}";
        assertEquals("密码错误", YsTool.passwordFailureMessage(400, leaked));

        String other = "{\"success\":false,\"message\":\"空间已关闭\"}";
        assertEquals("空间已关闭", YsTool.passwordFailureMessage(400, other));

        assertEquals("密码验证失败 HTTP 500", YsTool.passwordFailureMessage(500, "<html>CfDJ8secret</html>"));
        assertEquals("密码验证失败 HTTP 400", YsTool.passwordFailureMessage(400, ""));
    }

    @Test
    public void testBlankRedirectIsRejected() {
        assertNull(YsTool.resolveRedirect("https://qaiu.ys168.com/", " "));
        assertNull(YsTool.resolveRedirect("https://qaiu.ys168.com/", null));
        assertNull(YsTool.originOf("not a url"));
        assertFalse(YsTool.isPasswordPage(null));
        assertNull(YsTool.extractAntiforgery(null));
    }
}
