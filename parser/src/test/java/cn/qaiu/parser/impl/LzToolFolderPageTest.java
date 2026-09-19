package cn.qaiu.parser.impl;

import io.vertx.core.MultiMap;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 蓝奏目录首屏：桌面 UA 易返回 off0 空壳；抽出 filemoreajax 失败后再用移动 UA 重试。
 * 不访问真实蓝奏，只回归请求头选择与空壳/目录页识别。
 */
public class LzToolFolderPageTest {

    private static final String FOLDER_URL = "https://pan.lanzoux.com/b66477";
    private static final String FILE_URL = "https://wwww.lanzoux.com/iULV2n4361c";

    /** 桌面 UA 实测会落到的 off0 空壳（空 title，无 filemoreajax）。 */
    private static final String OFF0_STUB = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><title></title></head>
            <body>
            <div class="off">
            <div class="off0"></div>
            <div class="off1"></div>
            </div>
            </body>
            </html>
            """;

    /** 移动 UA 才能拿到的目录页片段。 */
    private static final String FOLDER_HTML = """
            <!DOCTYPE html>
            <html>
            <head><title>全网最全软件合集</title></head>
            <body>
            <div id="infos"></div>
            <script type="text/javascript">
            $.ajax({
                url : '/filemoreajax.php?file=66477',
                data : {
                    'lx':2,
                    'fid':66477,
                    'uid':'u1',
                    'pg':1,
                    'rep':'0',
                    't':t,
                    'k':k,
                    'up':1,
                    'pwd':pwd
                }
            });
            var t = 'TIME_TOKEN';
            var k = 'KEY_TOKEN';
            </script>
            </body>
            </html>
            """;

    private static final String FILE_HTML = """
            <title>demo.apk</title>
            <iframe src="/fn?abc"></iframe>
            <script>var wp_sign='SIGN';</script>
            """;

    @Test
    public void testFolderRetryHeadersUseMobileUaNotDesktop() {
        String desktopUa = LzTool.desktopPageUserAgent();
        String mobileUa = LzTool.folderMobileUserAgent();

        assertTrue("单文件页仍是桌面 Chrome", desktopUa.contains("Windows NT 10.0"));
        assertFalse("单文件页不能改成 Mobile", desktopUa.contains("Mobile"));
        assertTrue("目录重试 UA 是 Android Mobile", mobileUa.contains("Android"));
        assertTrue(mobileUa.contains("Mobile Safari"));
        assertNotEquals(desktopUa, mobileUa);

        MultiMap page = LzTool.folderPageHeaders(FOLDER_URL);
        MultiMap ajax = LzTool.folderListHeaders(FOLDER_URL);
        assertEquals(mobileUa, page.get("User-Agent"));
        assertEquals(mobileUa, ajax.get("User-Agent"));
        assertEquals("?1", page.get("sec-ch-ua-mobile"));
        assertEquals("Android", page.get("sec-ch-ua-platform"));
        assertEquals(FOLDER_URL, page.get("referer"));
    }

    @Test
    public void testOff0StubDetectedAndTriggersMobileRetry() {
        assertTrue(LzTool.isLzOfflineStub(OFF0_STUB));
        assertTrue(LzTool.isLzOfflineStub(""));
        assertTrue(LzTool.isLzOfflineStub(null));
        assertFalse(LzTool.isLzOfflineStub(FOLDER_HTML));
        assertFalse(LzTool.isLzFolderHtml(OFF0_STUB));
        assertNull(LzTool.extractFolderAjax(OFF0_STUB, null));

        assertTrue("off0 空壳应触发移动 UA 重试",
                LzTool.shouldRetryFolderPageWithMobileUa(OFF0_STUB, FOLDER_URL));
        assertTrue("文件链接到空壳也值得重试一次",
                LzTool.shouldRetryFolderPageWithMobileUa(OFF0_STUB, FILE_URL));
    }

    @Test
    public void testRealFolderHtmlExtractsFilemoreajax() {
        assertTrue(LzTool.isLzFolderHtml(FOLDER_HTML));
        assertFalse(LzTool.isLzOfflineStub(FOLDER_HTML));

        LzTool.AjaxCall call = LzTool.extractFolderAjax(FOLDER_HTML, "pwd1");
        assertNotNull(call);
        assertEquals("/filemoreajax.php?file=66477", call.path());
        assertEquals("66477", call.form().get("fid"));
        assertEquals("TIME_TOKEN", call.form().get("t"));
        assertEquals("KEY_TOKEN", call.form().get("k"));
        assertEquals("pwd1", call.form().get("pwd"));
    }

    @Test
    public void testRealFilePageDoesNotRetryMobileUa() {
        assertFalse(LzTool.isLzFolderUrl(FILE_URL));
        assertFalse(LzTool.isLzFolderHtml(FILE_HTML));
        assertFalse(LzTool.isLzOfflineStub(FILE_HTML));
        assertFalse("真实单文件页不要为了目录去换移动 UA",
                LzTool.shouldRetryFolderPageWithMobileUa(FILE_HTML, FILE_URL));
        assertTrue(LzTool.isLzFolderUrl(FOLDER_URL));
    }
}
