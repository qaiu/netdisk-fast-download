package cn.qaiu.parser.impl;

import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 夸克目录项分类与访客选文件。不访问网络。
 * 父 POM 的 surefire 默认 skipTests=true，本地验证用：
 * mvn -pl parser -DskipTests=false -Dtest=QkToolShareListTest test
 */
public class QkToolShareListTest {

    @Test
    public void folderIsNotADownloadableFile() {
        JsonObject folder = new JsonObject()
                .put("file", false)
                .put("dir", true)
                .put("file_type", 0)
                .put("file_name", "BiliTools最新版");
        assertTrue(QkTool.isQuarkFolder(folder));
        assertFalse(QkTool.isQuarkFile(folder));
    }

    @Test
    public void archiveAndInstallerCountAsFiles() {
        JsonObject rar = new JsonObject()
                .put("file", true)
                .put("dir", false)
                .put("file_type", 1)
                .put("obj_category", "archive")
                .put("size", 8228);
        JsonObject exe = new JsonObject()
                .put("file", true)
                .put("dir", false)
                .put("file_type", 1)
                .put("obj_category", "default")
                .put("size", 11919276);
        assertTrue(QkTool.isQuarkFile(rar));
        assertTrue(QkTool.isQuarkFile(exe));
        assertFalse(QkTool.isQuarkFolder(rar));
    }

    @Test
    public void directoryFlagWinsOverCategory() {
        JsonObject odd = new JsonObject()
                .put("file", false)
                .put("dir", true)
                .put("file_type", 0)
                .put("obj_category", "default");
        assertTrue(QkTool.isQuarkFolder(odd));
        assertFalse(QkTool.isQuarkFile(odd));
    }

    @Test
    public void guestOrderKeepsFirstFileThenSortsTheRestBySize() {
        JsonObject large = file("large", 90_000_000L);
        JsonObject mid = file("mid", 20_000_000L);
        JsonObject small = file("small", 8_000L);
        List<JsonObject> ordered = QkTool.orderGuestCandidates(List.of(large, mid, small));
        assertEquals("large", ordered.get(0).getString("file_name"));
        assertEquals("small", ordered.get(1).getString("file_name"));
        assertEquals("mid", ordered.get(2).getString("file_name"));
    }

    @Test
    public void sizeLimitIsTransferFallbackAndLoginIsNot() {
        assertTrue(QkTool.needsTransferFallback(new RuntimeException(
                "分享直链获取失败(code=23018): download file size limit")));
        assertFalse(QkTool.needsTransferFallback(new RuntimeException("未登录或 Cookie 已失效")));
        assertTrue(QkTool.isGuestAuthError(new RuntimeException("code=31001 未登录")));
        assertFalse(QkTool.isGuestAuthError(new RuntimeException("code=23018 size limit")));
    }

    @Test
    public void violationCodeIsExplainedWithoutFolderWording() {
        JsonObject json = new JsonObject().put("code", 41010).put("message", "文件涉及违规内容");
        String msg = QkTool.quarkApiFailure("https://drive-pc.quark.cn/token", json);
        assertTrue(msg.contains("违规"));
        assertTrue(msg.contains("41010"));
        assertFalse(msg.contains("都是文件夹"));
    }

    @Test
    public void zeroBytePlaceholderIsDroppedWhenARealFileExists() {
        JsonObject placeholder = file("等待更新", 0L);
        JsonObject real = file("notes.rar", 8228L);
        List<JsonObject> kept = QkTool.preferSizedFiles(List.of(placeholder, real));
        assertEquals(1, kept.size());
        assertEquals("notes.rar", kept.get(0).getString("file_name"));
        assertTrue(QkTool.hasPositiveSize(List.of(real)));
        assertFalse(QkTool.hasPositiveSize(List.of(placeholder)));
        assertEquals(1, QkTool.preferSizedFiles(List.of(placeholder)).size());
    }

    @Test
    public void oversizeHintPointsAtCookie() {
        assertTrue(QkTool.GUEST_OVERSIZE_MSG.contains("auths.qk.cookie"));
        assertTrue(QkTool.GUEST_OVERSIZE_MSG.contains("23018"));
        assertFalse(QkTool.GUEST_OVERSIZE_MSG.contains("都是文件夹"));
        assertTrue(QkTool.GUEST_LOGIN_MSG.contains("auths.qk.cookie"));
    }

    private static JsonObject file(String name, long size) {
        return new JsonObject().put("file_name", name).put("size", size).put("file", true);
    }
}
