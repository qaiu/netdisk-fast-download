package cn.qaiu.parser.impl;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 个性域名 a.lanzouw.com 不能被改写到 wwww.lanzoux.com（四个 w）。
 * 部分出口连不上四 w，页面请求会超时；ajax 只把四 w 当作最后兜底。
 */
public class LzToolShareUrlTest {

    @Test
    public void personalDomainPageStaysOffWwww() {
        String resolved = LzTool.resolveSharePageUrl(
                "https://a.lanzouw.com/xxx",
                "https://w1.lanzn.com/xxx",
                "xxx");
        assertEquals("https://a.lanzouw.com/xxx", resolved);
        assertFalse(resolved.contains("wwww.lanzoux.com"));
    }

    @Test
    public void folderPageKeepsOriginalHost() {
        String resolved = LzTool.resolveSharePageUrl(
                "https://a.lanzouw.com/b710887",
                "https://w1.lanzn.com/b710887",
                "b710887");
        assertEquals("https://a.lanzouw.com/b710887", resolved);
    }

    @Test
    public void nodeShareIsNotRewrittenToWwww() {
        String resolved = LzTool.resolveSharePageUrl(
                "https://wwn.lanzouy.com/ihLkw1gezutg",
                "https://w1.lanzn.com/ihLkw1gezutg",
                "ihLkw1gezutg");
        assertEquals("https://wwn.lanzouy.com/ihLkw1gezutg", resolved);
    }

    @Test
    public void webpageQueryStaysOnOriginalHost() {
        String resolved = LzTool.resolveSharePageUrl(
                "https://a.lanzouw.com/iabc?webpage=wp1&pwd=secret",
                "https://w1.lanzn.com/iabc",
                "iabc");
        assertEquals("https://a.lanzouw.com/iabc?webpage=wp1", resolved);
    }

    @Test
    public void placeholderUsesStandardHostNotWwww() {
        assertEquals("https://w1.lanzn.com/", LzTool.SHARE_URL_PREFIX);
        assertFalse(LzTool.SHARE_URL_PREFIX.contains("wwww"));
        String resolved = LzTool.resolveSharePageUrl(
                "https://w1.lanzn.com/-",
                "https://w1.lanzn.com/-",
                "iFileId");
        assertEquals("https://w1.lanzn.com/iFileId", resolved);
        assertFalse(resolved.contains("wwww.lanzoux.com"));
    }

    @Test
    public void missingShareUrlKeepsRealStandardHost() {
        String resolved = LzTool.resolveSharePageUrl(
                null,
                "https://www.lanzoux.com/iabc",
                "iabc");
        assertEquals("https://www.lanzoux.com/iabc", resolved);
    }

    @Test
    public void ajaxFallbackOrderPutsWwwwLast() {
        List<String> fallback = LzTool.AJAX_FALLBACK_ORIGINS;
        assertEquals("https://w1.lanzn.com", fallback.get(0));
        assertEquals("https://www.lanzoux.com", fallback.get(1));
        assertEquals("https://wwww.lanzoux.com", fallback.get(fallback.size() - 1));

        List<String> origins = LzTool.ajaxOrigins("https://a.lanzouw.com/xxx");
        assertEquals("https://a.lanzouw.com", origins.get(0));
        assertEquals("https://w1.lanzn.com", origins.get(1));
        assertEquals("https://www.lanzoux.com", origins.get(2));
        assertEquals("https://wwww.lanzoux.com", origins.get(origins.size() - 1));
        assertTrue(origins.indexOf("https://wwww.lanzoux.com")
                > origins.indexOf("https://w1.lanzn.com"));
    }

    @Test
    public void ajaxOriginsDoNotDuplicateKnownHost() {
        List<String> origins = LzTool.ajaxOrigins("https://www.lanzoux.com/iabc");
        assertEquals(3, origins.size());
        assertEquals("https://www.lanzoux.com", origins.get(0));
        assertEquals("https://w1.lanzn.com", origins.get(1));
        assertEquals("https://wwww.lanzoux.com", origins.get(2));
    }

    @Test
    public void timeoutAjaxFallsBackButBusinessErrorDoesNot() {
        assertTrue(LzTool.ajaxResponseShouldFallback("{\"zt\":0,\"inf\":\"已超时\"}"));
        assertTrue(LzTool.ajaxResponseShouldFallback("{\"info\":\"请求超时\"}"));
        assertTrue(LzTool.ajaxResponseShouldFallback(""));
        assertTrue(LzTool.ajaxResponseShouldFallback("<html>blocked</html>"));
        assertFalse(LzTool.ajaxResponseShouldFallback("{\"zt\":0,\"inf\":\"密码错误\"}"));
        assertFalse(LzTool.ajaxResponseShouldFallback(
                "{\"zt\":1,\"inf\":\"name.zip\",\"dom\":\"https://x\",\"url\":\"abc\"}"));
    }

    @Test
    public void filePagePrefersExistingHostOverWwww() {
        assertEquals("https://a.lanzouw.com/",
                LzTool.sharePageHostBase("https://a.lanzouw.com/b123", "https://w1.lanzn.com/b123"));
        assertEquals("https://w1.lanzn.com/",
                LzTool.sharePageHostBase("https://w1.lanzn.com/-", "https://w1.lanzn.com/-"));
        assertFalse(LzTool.sharePageHostBase("https://a.lanzouw.com/b123", null).contains("wwww"));
    }

    @Test
    public void absoluteAjaxUrlIsKeptAndTriedFirst() {
        String html = """
                url : 'https://apifile.lanzouw.com/ajaxfile.php?file=320665771',
                data : { 'action':'downprocess','sign':isngis,'kd':kdns,'p':pwd, },
                """;
        String target = LzTool.fileAjaxTarget(html);
        assertEquals("https://apifile.lanzouw.com/ajaxfile.php?file=320665771", target);

        List<String> origins = LzTool.fileAjaxOrigins(
                "https://wwaqg.lanzouu.com/iqouM49vy9ib", target);
        assertEquals("https://apifile.lanzouw.com", origins.get(0));
        assertTrue(origins.contains("https://wwaqg.lanzouu.com"));
        assertEquals("https://wwww.lanzoux.com", origins.get(origins.size() - 1));
        assertTrue(origins.indexOf("https://apifile.lanzouw.com")
                < origins.indexOf("https://wwaqg.lanzouu.com"));
        assertTrue(origins.indexOf("https://wwww.lanzoux.com")
                > origins.indexOf("https://w1.lanzn.com"));
        assertEquals(1, origins.stream().filter("https://apifile.lanzouw.com"::equals).count());
    }

    @Test
    public void protocolRelativeAjaxBecomesHttps() {
        String html = "url : '//apifile.lanzouw.com/ajaxfile.php?file=9',";
        assertEquals("https://apifile.lanzouw.com/ajaxfile.php?file=9", LzTool.fileAjaxTarget(html));
    }

    @Test
    public void absoluteAjaxWinsOverRelativePathInSamePage() {
        String html = """
                //data : { url : '/ajaxfile.php?file=1' }
                url : "https://apifile.lanzouw.com/ajaxm.php?file=42",
                """;
        assertEquals("https://apifile.lanzouw.com/ajaxm.php?file=42", LzTool.fileAjaxTarget(html));
    }

    @Test
    public void relativeFileAjaxFallsThroughToApifileBeforeWwww() {
        String html = "url : '/ajaxfile.php?file=150233466',";
        assertEquals("/ajaxfile.php?file=150233466", LzTool.fileAjaxTarget(html));

        List<String> origins = LzTool.fileAjaxOrigins("https://a.lanzouw.com/xxx", LzTool.fileAjaxTarget(html));
        assertEquals("https://a.lanzouw.com", origins.get(0));
        assertEquals("https://apifile.lanzouw.com", origins.get(1));
        assertEquals("https://wwww.lanzoux.com", origins.get(origins.size() - 1));
        assertTrue(origins.indexOf("https://apifile.lanzouw.com")
                < origins.indexOf("https://wwww.lanzoux.com"));
        assertFalse(LzTool.ajaxOrigins("https://a.lanzouw.com/xxx").contains("https://apifile.lanzouw.com"));
    }

    @Test
    public void missingAjaxTargetStillPutsApifileBeforeWwww() {
        List<String> origins = LzTool.fileAjaxOrigins("https://www.lanzoux.com/ihLkw1gezutg", null);
        assertEquals("https://www.lanzoux.com", origins.get(0));
        assertEquals("https://apifile.lanzouw.com", origins.get(1));
        assertEquals("https://wwww.lanzoux.com", origins.get(origins.size() - 1));
        assertFalse(origins.get(0).contains("wwww"));
    }
}
