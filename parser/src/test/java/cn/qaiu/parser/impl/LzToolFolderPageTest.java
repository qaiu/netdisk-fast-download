package cn.qaiu.parser.impl;

import io.vertx.core.MultiMap;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * 蓝奏 LzTool 对外请求一律移动 UA：桌面 Chrome 会触发 off0 下线空壳。
 * 不访问真实蓝奏，只回归请求头与空壳/目录页识别。
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

    @Test
    public void testAllOutboundHeadersUseAndroidMobileUa() {
        String mobileUa = LzTool.mobileUserAgent();
        assertTrue(mobileUa.contains("Android"));
        assertTrue(mobileUa.contains("Mobile Safari"));
        assertFalse("蓝奏路径不应再带桌面 Chrome UA", mobileUa.contains("Windows NT"));

        List<MultiMap> outbound = List.of(
                LzTool.sharePageHeaders(),
                LzTool.folderListHeaders(FOLDER_URL),
                LzTool.downAjaxHeaders(),
                LzTool.lanrarPageHeaders("https://developer2.lanrar.com"),
                LzTool.lanrarAjaxHeaders(FILE_URL)
        );
        for (MultiMap headers : outbound) {
            assertEquals(mobileUa, headers.get("User-Agent"));
            assertEquals("?1", headers.get("Sec-CH-UA-Mobile"));
            assertFalse(String.valueOf(headers.get("User-Agent")).contains("Windows NT"));
            assertFalse("?0".equals(headers.get("Sec-CH-UA-Mobile")));
        }
        assertEquals("\"Android\"", LzTool.sharePageHeaders().get("Sec-CH-UA-Platform"));
    }

    @Test
    public void testOff0StubDetectedWithoutFilemoreajax() {
        assertTrue(LzTool.isLzOfflineStub(OFF0_STUB));
        assertTrue(LzTool.isLzOfflineStub(""));
        assertTrue(LzTool.isLzOfflineStub(null));
        assertFalse(LzTool.isLzOfflineStub(FOLDER_HTML));
        assertFalse(LzTool.isLzFolderHtml(OFF0_STUB));
        assertNull(LzTool.extractFolderAjax(OFF0_STUB, null));
    }

    @Test
    public void testRealFolderHtmlExtractsFilemoreajax() {
        assertTrue(LzTool.isLzFolderHtml(FOLDER_HTML));
        assertFalse(LzTool.isLzOfflineStub(FOLDER_HTML));
        assertTrue(LzTool.isLzFolderUrl(FOLDER_URL));
        assertFalse(LzTool.isLzFolderUrl(FILE_URL));

        LzTool.AjaxCall call = LzTool.extractFolderAjax(FOLDER_HTML, "pwd1");
        assertNotNull(call);
        assertEquals("/filemoreajax.php?file=66477", call.path());
        assertEquals("66477", call.form().get("fid"));
        assertEquals("TIME_TOKEN", call.form().get("t"));
        assertEquals("KEY_TOKEN", call.form().get("k"));
        assertEquals("pwd1", call.form().get("pwd"));
    }
}
