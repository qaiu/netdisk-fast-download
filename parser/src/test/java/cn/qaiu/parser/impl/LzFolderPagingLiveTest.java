package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.parser.FolderPaging;
import cn.qaiu.parser.ParserCreate;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 蓝奏目录分页真实联调（b66477 约 93 页，每页 50 条）。默认不跑：
 * mvn -pl parser -DskipTests=false -Dlz.live=true -Dtest=LzFolderPagingLiveTest test
 * 加 -Dlz.live.full=true 会顺序翻完整个目录（约 2 分钟）。
 */
public class LzFolderPagingLiveTest {

    private static final String OUI = "https://pan.lanzoui.com/b66477";
    private static final String OUG = "https://pan.lanzoug.com/b66477";

    private static Vertx vertx;

    @AfterClass
    public static void close() {
        if (vertx != null) {
            vertx.close();
        }
    }

    record Page(List<FileInfo> list, JsonObject info) {
        String cursor() {
            return info.getString("nextCursor");
        }
    }

    @Test
    public void pacedPagesOneToThreeAreFullAndDistinct() throws Exception {
        liveOnly();
        Set<String> ids = new HashSet<>();
        Page page = page(OUI, null, 1, null);
        for (int n = 1; n <= 3; n++) {
            if (n > 1) {
                Thread.sleep(5000);
                page = page(OUI, null, n, page.cursor());
            }
            // 上游每页 50 条原始项，其中 id=-1 的占位项（如 ***.apk）已被过滤
            assertTrue("第 " + n + " 页 " + page.list().size(), page.list().size() >= 45 && page.list().size() <= 50);
            assertEquals(Integer.valueOf(50), page.info().getInteger("pageSize"));
            assertTrue(page.info().getBoolean("hasMore"));
            assertNotNull(page.cursor());
            page.list().forEach(f -> assertTrue("重复 " + f.getFileId(), ids.add(f.getFileId())));
        }
    }

    /** 上游对紧跟的下一页常回 zt=4「请刷新，重试0」：必须重试成功或明确报错，不能当末页。 */
    @Test
    public void immediateNextPageIsNeverReportedAsEnd() throws Exception {
        liveOnly();
        Page first = page(OUG, null, 1, null);
        try {
            Page second = page(OUG, null, 2, first.cursor());
            assertFalse(second.list().isEmpty());
            assertTrue(second.info().getBoolean("hasMore"));
        } catch (ExecutionException e) {
            String msg = e.getCause().getMessage();
            assertTrue(msg, msg.contains("稍后重试") || msg.contains("重新加载"));
        }
        Page again = page(OUG, null, 2, null);
        assertEquals(50, again.list().size());
        assertTrue(again.info().getBoolean("hasMore"));
    }

    @Test
    public void passwordFolderIsSinglePageAndPageTwoIsEmpty() throws Exception {
        liveOnly();
        Page first = page("https://moteam.lanzoum.com/b00yaejvte", "1234", 1, null);
        assertFalse(first.list().isEmpty());
        assertFalse(first.info().getBoolean("hasMore"));
        assertNull(first.cursor());
        Thread.sleep(2000);
        Page second = page("https://moteam.lanzoum.com/b00yaejvte", "1234", 2, null);
        assertTrue(second.list().isEmpty());
        assertFalse(second.info().getBoolean("hasMore"));
    }

    @Test
    public void fileFromSecondPageDownloads() throws Exception {
        liveOnly();
        Page first = page(OUI, null, 1, null);
        Thread.sleep(2000);
        Page second = page(OUI, null, 2, first.cursor());
        FileInfo small = second.list().get(0);
        for (FileInfo f : second.list()) {
            if (f.getSize() != null && small.getSize() != null && f.getSize() < small.getSize()) {
                small = f;
            }
        }
        String url = ParserCreate.fromShareUrl("https://pan.lanzoui.com/" + small.getFileId())
                .createTool().parse().toCompletionStage().toCompletableFuture().get(90, TimeUnit.SECONDS);
        HttpResponse<byte[]> response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build()
                .send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(120)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().length > 0);
    }

    @Test
    public void wholeFolderWithCursorHasNoDuplicatesAndEnds() throws Exception {
        liveOnly();
        Assume.assumeTrue(Boolean.getBoolean("lz.live.full"));
        Set<String> ids = new HashSet<>();
        String cursor = null;
        int pages = 0;
        int lastSize = -1;
        for (int n = 1; n <= FolderPaging.MAX_PAGE; n++) {
            Page page = page(OUI, null, n, cursor);
            pages = n;
            lastSize = page.list().size();
            page.list().forEach(f -> assertTrue("重复 " + f.getFileId(), ids.add(f.getFileId())));
            if (!page.info().getBoolean("hasMore")) {
                break;
            }
            cursor = page.cursor();
            assertNotNull(cursor);
            Thread.sleep(800);
        }
        assertTrue("应有多页", pages > 2);
        assertTrue("末页应不足 50 条", lastSize < 50);
        assertTrue(ids.size() > (pages - 1) * 49);
    }

    private static Page page(String url, String pwd, int page, String cursor) throws Exception {
        ParserCreate create = ParserCreate.fromShareUrl(url);
        if (pwd != null) {
            create.setShareLinkInfoPwd(pwd);
        }
        create.getShareLinkInfo().getOtherParam().put(FolderPaging.PAGE, page);
        if (cursor != null) {
            create.getShareLinkInfo().getOtherParam().put(FolderPaging.CURSOR, cursor);
        }
        List<FileInfo> list = create.createTool().parseFileList()
                .toCompletionStage().toCompletableFuture().get(90, TimeUnit.SECONDS);
        return new Page(list, FolderPaging.pageInfo(create.getShareLinkInfo()));
    }

    private static void liveOnly() {
        Assume.assumeTrue(Boolean.getBoolean("lz.live"));
        if (vertx == null) {
            vertx = Vertx.vertx();
            WebClientVertxInit.init(vertx);
        }
    }
}
