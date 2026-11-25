package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.FolderPaging;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.uritemplate.UriTemplate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** 小飞机 share/list：offset 是从 1 开始的页码，limit 为每页条数。 */
public class FjToolFolderPagingTest {

    private Vertx vertx;
    private WebClient local;
    private HttpServer server;
    private final List<String> queries = new CopyOnWriteArrayList<>();
    private volatile String override;

    @Before
    public void setup() throws Exception {
        vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        local = WebClient.create(vertx);
        server = vertx.createHttpServer().requestHandler(req -> {
            if (req.path().endsWith("/share/list")) {
                queries.add(req.query());
                if (override != null) {
                    req.response().end(override);
                    return;
                }
                int offset = Integer.parseInt(req.getParam("offset"));
                int limit = Integer.parseInt(req.getParam("limit"));
                int total = 125;
                int totalPage = (total + limit - 1) / limit;
                JsonArray list = new JsonArray();
                for (int i = (offset - 1) * limit; i < Math.min(total, offset * limit); i++) {
                    list.add(new JsonObject().put("fileType", 1).put("fileId", String.valueOf(1000 + i)).put("fileName", "f" + i + ".txt")
                            .put("fileSize", 1).put("fileIcon", "txt").put("userId", 9).put("fileDownloads", 0)
                            .put("updTime", "2024-01-01 00:00:00"));
                }
                req.response().end(new JsonObject().put("code", 200).put("list", list).put("total", total)
                        .put("offset", offset).put("limit", limit).put("totalPage", totalPage).encode());
            } else {
                req.response().setStatusCode(404).end();
            }
        }).listen(0, "127.0.0.1").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @After
    public void cleanup() throws Exception {
        local.close();
        server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test
    public void defaultRequestIsFirstPageWithLegacyLimit() throws Exception {
        ShareLinkInfo info = info(null);
        List<FileInfo> list = list(info);
        assertEquals(60, list.size());
        assertTrue(queries.get(0).contains("offset=1"));
        assertTrue(queries.get(0).contains("limit=60"));
        assertTrue(FolderPaging.pageInfo(info).getBoolean("hasMore"));
        assertEquals(Integer.valueOf(125), FolderPaging.pageInfo(info).getInteger("total"));
    }

    @Test
    public void pagesAreDistinctAndLastPageEnds() throws Exception {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int page = 1; page <= 3; page++) {
            ShareLinkInfo info = info(page);
            for (FileInfo f : list(info)) {
                assertTrue("重复 " + f.getFileId(), ids.add(f.getFileId()));
            }
            assertEquals(page < 3, FolderPaging.pageInfo(info).getBoolean("hasMore"));
            assertTrue(queries.get(queries.size() - 1).contains("offset=" + page));
        }
        assertEquals(125, ids.size());
        ShareLinkInfo beyond = info(4);
        assertTrue(list(beyond).isEmpty());
        assertFalse(FolderPaging.pageInfo(beyond).getBoolean("hasMore"));
    }

    @Test
    public void nonJsonListFailsInsteadOfHanging() throws Exception {
        override = "<html>busy</html>";
        try {
            list(info(null));
            fail();
        } catch (ExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("目录解析失败"));
        }
    }

    private List<FileInfo> list(ShareLinkInfo info) throws Exception {
        return tool(info).parseFileList().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private ShareLinkInfo info(Integer page) {
        ShareLinkInfo info = ShareLinkInfo.newBuilder().type("fj").shareKey("share")
                .shareUrl("https://share.feijipan.com/s/share").build();
        info.getOtherParam().put("dirId", "777");
        info.getOtherParam().put("uuid", "test-uuid");
        if (page != null) {
            info.getOtherParam().put(FolderPaging.PAGE, page);
        }
        return info;
    }

    private FjTool tool(ShareLinkInfo info) {
        WebClient routed = (WebClient) Proxy.newProxyInstance(WebClient.class.getClassLoader(),
                new Class<?>[]{WebClient.class}, (proxy, method, args) -> {
                    if (args != null && args.length > 0 && args[0] instanceof UriTemplate template) {
                        args[0] = Proxy.newProxyInstance(UriTemplate.class.getClassLoader(),
                                new Class<?>[]{UriTemplate.class}, (p, m, a) -> {
                                    Object expanded = m.invoke(template, a);
                                    return expanded instanceof String url
                                            ? url.replace("https://api.feijipan.com", "http://127.0.0.1:" + server.actualPort())
                                            : expanded;
                                });
                    }
                    return method.invoke(local, args);
                });
        return new FjTool(info) {{ client = routed; clientNoRedirects = routed; }};
    }
}
