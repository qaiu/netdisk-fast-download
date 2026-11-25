package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.FolderPaging;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 蓝奏目录分页离线回归：本地模拟分享页与 filemoreajax.php。
 * 上游真实行为（b66477 实测）：每页 50 条，不足 50 即末页；zt=2「没有了」；
 * 同一组 t/k 请求过快返回 {@code {"zt":4,"info":"请刷新，重试0","text":0}}，隔约 2 秒同页可正常取到。
 */
public class LzToolFolderPagingTest {

    private static final String SHARE_KEY = "b66477";
    private static final String TOKEN_T = "1700000000";
    private static final String TOKEN_K = "test-k-token";
    private static final String RATE_LIMITED = "{\"zt\":4,\"info\":\"请刷新，重试0\",\"text\":0}";

    private Vertx vertx;
    private HttpServer server;
    private final AtomicInteger pageGets = new AtomicInteger();
    private final List<MultiMap> ajaxForms = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> ajaxCountByPg = new ConcurrentHashMap<>();
    /** (pg, 该页第几次请求 从 1 开始) -> 响应体；返回 null 用默认三页数据 */
    private volatile BiFunction<Integer, Integer, String> ajax = (pg, n) -> null;

    @Before
    public void setup() throws Exception {
        vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        server = vertx.createHttpServer().requestHandler(req -> {
            if ("GET".equals(req.method().name()) && req.path().equals("/" + SHARE_KEY)) {
                pageGets.incrementAndGet();
                req.response().putHeader("Content-Type", "text/html; charset=utf-8").end(folderHtml());
            } else if (req.path().equals("/filemoreajax.php")) {
                req.setExpectMultipart(true);
                req.endHandler(v -> {
                    MultiMap form = MultiMap.caseInsensitiveMultiMap().addAll(req.formAttributes());
                    ajaxForms.add(form);
                    int pg = Integer.parseInt(form.get("pg"));
                    int n = ajaxCountByPg.computeIfAbsent(String.valueOf(pg), k -> new AtomicInteger()).incrementAndGet();
                    String body = ajax.apply(pg, n);
                    req.response().putHeader("Content-Type", "text/json; charset=utf-8")
                            .end(body != null ? body : defaultPage(pg));
                });
            } else {
                req.response().setStatusCode(404).end();
            }
        }).listen(0, "127.0.0.1").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @After
    public void cleanup() throws Exception {
        server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test
    public void defaultRequestStillReturnsFirstPageWithoutPlaceholder() throws Exception {
        ShareLinkInfo info = info(null, null);
        List<FileInfo> list = list(info);
        assertEquals(49, list.size());
        assertTrue(list.stream().noneMatch(f -> "-1".equals(f.getFileId())));
        assertEquals("p1-0", list.get(0).getFileId());
        assertTrue(list.get(0).getParserUrl().startsWith("http://nfd.test/v2/redirectUrl/lz/"));
        JsonObject pageInfo = FolderPaging.pageInfo(info);
        assertTrue(pageInfo.getBoolean("hasMore"));
        assertEquals(2, (int) pageInfo.getInteger("nextPage"));
        assertNotNull(pageInfo.getString("nextCursor"));
        assertEquals(1, pageGets.get());
        assertEquals("1", ajaxForms.get(0).get("pg"));
        assertEquals("secret", ajaxForms.get(0).get("pwd"));
    }

    @Test
    public void rateLimitedNextPageIsRetriedNotTreatedAsLastPage() throws Exception {
        ajax = (pg, n) -> pg == 2 && n == 1 ? RATE_LIMITED : null;
        ShareLinkInfo info = info(2, null);
        List<FileInfo> list = list(info);
        assertEquals(50, list.size());
        assertEquals("p2-0", list.get(0).getFileId());
        JsonObject pageInfo = FolderPaging.pageInfo(info);
        assertTrue("zt=4 不能被当作末页", pageInfo.getBoolean("hasMore"));
        assertEquals(2, ajaxCountByPg.get("2").get());
    }

    @Test
    public void persistentRateLimitFailsWithRetryableErrorInsteadOfEmptyEnd() throws Exception {
        ajax = (pg, n) -> pg == 2 ? RATE_LIMITED : null;
        ShareLinkInfo info = info(2, null);
        try {
            list(info);
            fail("zt=4 一直不恢复时必须报错，不能返回空列表或 hasMore=false");
        } catch (ExecutionException e) {
            String msg = e.getCause().getMessage();
            assertTrue(msg, msg.contains("请刷新，重试0"));
            assertTrue(msg, msg.contains("不完整"));
            assertTrue(msg, msg.contains("稍后重试"));
        }
        assertNull(FolderPaging.pageInfo(info));
        assertEquals(1 + LzTool.LZ_REFRESH_RETRIES, ajaxCountByPg.get("2").get());
    }

    @Test
    public void cursorContinuesWithSameTokenWithoutReloadingPage() throws Exception {
        ShareLinkInfo first = info(1, null);
        list(first);
        String cursor = FolderPaging.pageInfo(first).getString("nextCursor");

        ShareLinkInfo second = info(2, cursor);
        List<FileInfo> page2 = list(second);
        assertEquals(50, page2.size());
        assertEquals(1, pageGets.get());
        MultiMap last = ajaxForms.get(ajaxForms.size() - 1);
        assertEquals("2", last.get("pg"));
        assertEquals(TOKEN_T, last.get("t"));
        assertEquals(TOKEN_K, last.get("k"));
        assertEquals("secret", last.get("pwd"));

        ShareLinkInfo third = info(3, FolderPaging.pageInfo(second).getString("nextCursor"));
        List<FileInfo> page3 = list(third);
        assertEquals(9, page3.size());
        JsonObject end = FolderPaging.pageInfo(third);
        assertFalse(end.getBoolean("hasMore"));
        assertNull(end.getString("nextCursor"));
        assertNull(end.getValue("nextPage"));

        Set<String> ids = new HashSet<>();
        for (List<FileInfo> page : List.of(list(info(1, null)), page2, page3)) {
            page.forEach(f -> assertTrue("跨页重复 " + f.getFileId(), ids.add(f.getFileId())));
        }
        assertEquals(49 + 50 + 9, ids.size());
    }

    @Test
    public void rateLimitedCursorRequestRetriesSamePage() throws Exception {
        ShareLinkInfo first = info(1, null);
        list(first);
        ajax = (pg, n) -> pg == 2 && n == 1 ? RATE_LIMITED : null;
        ShareLinkInfo second = info(2, FolderPaging.pageInfo(first).getString("nextCursor"));
        assertEquals(50, list(second).size());
        assertTrue(FolderPaging.pageInfo(second).getBoolean("hasMore"));
        assertEquals(1, pageGets.get());
    }

    @Test
    public void pastLastPageIsEmptyAndEnds() throws Exception {
        ajax = (pg, n) -> pg == 3 ? page(pg, 50) : pg == 4 ? "{\"zt\":2,\"info\":\"没有了\",\"text\":\"no file\"}" : null;
        ShareLinkInfo third = info(3, null);
        list(third);
        ShareLinkInfo fourth = info(4, FolderPaging.pageInfo(third).getString("nextCursor"));
        List<FileInfo> list = list(fourth);
        assertTrue(list.isEmpty());
        assertFalse(FolderPaging.pageInfo(fourth).getBoolean("hasMore"));
    }

    @Test
    public void walkStopsAtShortPageWhenTargetIsBeyondEnd() throws Exception {
        ShareLinkInfo info = info(5, null);
        assertTrue(list(info).isEmpty());
        assertFalse(FolderPaging.pageInfo(info).getBoolean("hasMore"));
        assertEquals(null, ajaxCountByPg.get("4"));
    }

    @Test
    public void deepPageWithoutCursorFailsFastWithHint() throws Exception {
        ShareLinkInfo info = info(LzTool.LZ_MAX_WALK_PAGES + 1, null);
        try {
            list(info);
            fail();
        } catch (ExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("nextCursor"));
        }
        assertEquals(0, pageGets.get());
        assertTrue(ajaxForms.isEmpty());
    }

    @Test
    public void foreignCursorIsIgnored() throws Exception {
        String foreign = LzFolderCursor.encode("bOTHER", "/filemoreajax.php?file=66477",
                Map.of("fid", "66477", "t", "x", "k", "y"), 2, null);
        ShareLinkInfo info = info(2, foreign);
        assertEquals(50, list(info).size());
        assertEquals(1, pageGets.get());
        assertTrue(ajaxForms.stream().noneMatch(f -> "x".equals(f.get("t"))));
    }

    @Test
    public void emptyFolderKeepsLegacyFailure() throws Exception {
        ajax = (pg, n) -> "{\"zt\":2,\"info\":\"没有文件\",\"text\":\"no file\"}";
        try {
            list(info(null, null));
            fail();
        } catch (ExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("没有文件"));
        }
    }

    private List<FileInfo> list(ShareLinkInfo info) throws Exception {
        return new LzTool(info).parseFileList().toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    private ShareLinkInfo info(Integer page, String cursor) {
        String url = "http://127.0.0.1:" + server.actualPort() + "/" + SHARE_KEY;
        ShareLinkInfo info = ShareLinkInfo.newBuilder().type("lz").panName("蓝奏云").shareKey(SHARE_KEY)
                .shareUrl(url).standardUrl(url).sharePassword("secret").build();
        info.getOtherParam().put("domainName", "http://nfd.test");
        if (page != null) {
            info.getOtherParam().put(FolderPaging.PAGE, page);
        }
        if (cursor != null) {
            info.getOtherParam().put(FolderPaging.CURSOR, cursor);
        }
        return info;
    }

    private static String folderHtml() {
        return """
                <html><body><div id="infos"></div>
                <script type="text/javascript">
                	var pwd;
                	var pgs;
                	var ibb6kj = '%s';
                	var _h9imc = '%s';
                	pgs =1;
                	function file(){
                		$.ajax({
                			type : 'post',
                			url : '/filemoreajax.php?file=66477',
                			data : {
                			'lx':2,
                			'fid':66477,
                			'uid':'234978',
                			'pg':pgs,
                			'rep':'0',
                			't':ibb6kj,
                			'k':_h9imc,
                			'up':1,
                			'ls':1,
                			'pwd':pwd			},
                			dataType : 'json'
                		});
                	}
                </script></body></html>
                """.formatted(TOKEN_T, TOKEN_K);
    }

    /** 默认三页：50（含一个 -1 占位）、50、9。 */
    private static String defaultPage(int pg) {
        return switch (pg) {
            case 1, 2 -> page(pg, 50);
            case 3 -> page(pg, 9);
            default -> "{\"zt\":2,\"info\":\"没有了\",\"text\":\"no file\"}";
        };
    }

    private static String page(int pg, int count) {
        JsonArray text = new JsonArray();
        for (int i = 0; i < count; i++) {
            String id = pg == 1 && i == 10 ? "-1" : "p" + pg + "-" + i;
            text.add(new JsonObject().put("icon", "apk").put("t", 0).put("id", id)
                    .put("name_all", id + ".apk").put("size", "1.0 M").put("time", "2023-03-08"));
        }
        return new JsonObject().put("zt", 1).put("info", "sucess").put("text", text).encode();
    }
}
