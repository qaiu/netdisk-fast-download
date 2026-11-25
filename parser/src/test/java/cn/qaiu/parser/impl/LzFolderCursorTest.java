package cn.qaiu.parser.impl;

import cn.qaiu.util.CommonUtils;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class LzFolderCursorTest {

    private static final String PATH = "/filemoreajax.php?file=66477";

    private static Map<String, String> form() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("lx", "2");
        form.put("fid", "66477");
        form.put("uid", "234978");
        form.put("pg", "1");
        form.put("t", "1795564485");
        form.put("k", "abc");
        form.put("pwd", "secret");
        return form;
    }

    @Test
    public void roundTripDropsPasswordAndUsesRequestedPage() {
        String raw = LzFolderCursor.encode("b66477", PATH, form(), 2, "wp1");
        assertFalse(CommonUtils.urlBase64Decode(raw).contains("secret"));
        LzFolderCursor cursor = LzFolderCursor.decode(raw, "b66477", 2);
        assertNotNull(cursor);
        assertEquals(PATH, cursor.path);
        assertEquals("wp1", cursor.webpage);
        Map<String, String> next = cursor.formFor("newpwd", 2);
        assertEquals("2", next.get("pg"));
        assertEquals("newpwd", next.get("pwd"));
        assertEquals("1795564485", next.get("t"));
    }

    @Test
    public void mismatchedShareOrPageIsIgnored() {
        String raw = LzFolderCursor.encode("b66477", PATH, form(), 2, null);
        assertNull(LzFolderCursor.decode(raw, "b00000", 2));
        assertNull(LzFolderCursor.decode(raw, "b66477", 3));
        assertNull(LzFolderCursor.decode("not-base64!", "b66477", 2));
        assertNull(LzFolderCursor.decode(null, "b66477", 2));
    }

    @Test
    public void tamperedPathOrFieldsAreIgnored() {
        assertNull(LzFolderCursor.decode(LzFolderCursor.encode("b1", "http://evil/x", form(), 2, null), "b1", 2));
        assertNull(LzFolderCursor.decode(LzFolderCursor.encode("b1", "/filemoreajax.php?file=1", form(), 2, null), "b1", 2));
        Map<String, String> bad = form();
        bad.put("x\ny", "1");
        assertNull(LzFolderCursor.decode(LzFolderCursor.encode("b1", PATH, bad, 2, null), "b1", 2));
        Map<String, String> noToken = form();
        noToken.remove("t");
        assertNull(LzFolderCursor.decode(LzFolderCursor.encode("b1", PATH, noToken, 2, null), "b1", 2));
        JsonObject json = new JsonObject(CommonUtils.urlBase64Decode(LzFolderCursor.encode("b1", PATH, form(), 2, null)));
        json.put("v", 99);
        assertNull(LzFolderCursor.decode(CommonUtils.urlBase64Encode(json.encode()), "b1", 2));
    }

    @Test
    public void unsafeWebpageIsDropped() {
        String raw = LzFolderCursor.encode("b1", PATH, form(), 2, "a/../../x?y");
        LzFolderCursor cursor = LzFolderCursor.decode(raw, "b1", 2);
        assertNotNull(cursor);
        assertNull(cursor.webpage);
    }
}
