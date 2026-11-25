package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * 蓝奏文件页离线回归：2026-11 起文件页 iframe 的下载 ajax 改名为 ajaxfiles.php，
 * 其余字段（wp_sign / ajaxdata / kd / ves）不变。本地模拟 分享页 → /fn iframe → ajaxfiles.php → 下载域 302。
 */
public class LzToolAjaxfilesTest {

    private static final String SHARE_KEY = "winx123";
    private static final String FILE_ID = "333017612";

    private Vertx vertx;
    private HttpServer server;
    private final List<String> ajaxPaths = new CopyOnWriteArrayList<>();
    private final List<MultiMap> ajaxForms = new CopyOnWriteArrayList<>();

    @Before
    public void setup() throws Exception {
        vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        server = vertx.createHttpServer().requestHandler(req -> {
            String path = req.path();
            if ("GET".equals(req.method().name()) && path.equals("/" + SHARE_KEY)) {
                html(req.response(), sharePage());
            } else if ("GET".equals(req.method().name()) && path.equals("/fn")) {
                html(req.response(), iframePage());
            } else if ("POST".equals(req.method().name()) && path.startsWith("/ajax")) {
                req.setExpectMultipart(true);
                req.endHandler(v -> {
                    ajaxPaths.add(req.uri());
                    ajaxForms.add(MultiMap.caseInsensitiveMultiMap().addAll(req.formAttributes()));
                    String dom = "http://127.0.0.1:" + server.actualPort();
                    req.response().putHeader("Content-Type", "application/json")
                            .end("{\"zt\":1,\"dom\":\"" + dom + "\",\"url\":\"?mock-token\",\"inf\":0}");
                });
            } else if (path.equals("/file/")) {
                req.response().setStatusCode(302)
                        .putHeader("Location", "http://cdn.test/2026/11/25/steam.zip")
                        .end();
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
    public void filePageWithAjaxfilesIframeResolvesDirectLink() throws Exception {
        String url = "http://127.0.0.1:" + server.actualPort() + "/" + SHARE_KEY;
        ShareLinkInfo info = ShareLinkInfo.newBuilder().type("lz").panName("蓝奏云").shareKey(SHARE_KEY)
                .shareUrl(url).standardUrl(url).build();

        String link = new LzTool(info).parse().toCompletionStage().toCompletableFuture()
                .get(30, TimeUnit.SECONDS);

        assertEquals("http://cdn.test/2026/11/25/steam.zip", link);
        assertEquals(List.of("/ajaxfiles.php?file=" + FILE_ID), ajaxPaths);
        MultiMap form = ajaxForms.get(0);
        assertEquals("downprocess", form.get("action"));
        assertEquals("test-wp-sign", form.get("sign"));
        assertEquals("test-ajaxdata", form.get("websignkey"));
        assertEquals("test-ajaxdata", form.get("signs"));
        assertEquals("1", form.get("ves"));
    }

    private static void html(io.vertx.core.http.HttpServerResponse res, String body) {
        res.putHeader("Content-Type", "text/html; charset=utf-8").end(body);
    }

    private static String sharePage() {
        return """
                <!DOCTYPE html><html><head><title>steam.zip - 蓝奏云</title>
                <meta name="description" content="文件大小：1.1 M" /></head><body>
                <div class="n_box_3fn" id="filenajax">steam.zip</div>
                <div class="n_file"><div class="n_filesize">大小：1.1 M</div>
                <iframe class="n_downlink" name="1795620352" src="/fn?mock-fn-query" frameborder="0" scrolling="no"></iframe></div>
                </body></html>
                """;
    }

    private static String iframePage() {
        return """
                <html><body><div id="tourl"></div>
                <script type="text/javascript">
                		var wsk_sign = 'c20230908';
                		var wp_sign = 'test-wp-sign';
                		var ajaxdata = 'test-ajaxdata';
                		var kdns =1;
                		$.ajax({
                			type : 'post',
                			url : '/ajaxfiles.php?file=%s',
                		data : { 'action':'downprocess','websignkey':ajaxdata,'signs':ajaxdata,'sign':wp_sign,'websign':'','kd':kdns,'ves':1 },
                			dataType : 'json'
                		});
                </script></body></html>
                """.formatted(FILE_ID);
    }
}
