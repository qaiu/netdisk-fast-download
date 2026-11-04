package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.util.AESUtils;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.uritemplate.UriTemplate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class FjToolDownloadTest {
    private Vertx vertx;
    private WebClient local;
    private HttpServer server;
    private final AtomicInteger logins = new AtomicInteger();
    private final AtomicInteger downloads = new AtomicInteger();
    private String identity;
    private String businessError;

    @Before
    public void setup() throws Exception {
        FjTool.resetToken();
        vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        local = WebClient.create(vertx, new WebClientOptions().setFollowRedirects(false));
        server = vertx.createHttpServer().requestHandler(req -> {
            if (req.path().equals("/ws/login")) {
                logins.incrementAndGet();
                req.response().setStatusCode(500).end("Share downloads must not require login");
            } else if (req.path().equals("/ws/file/redirect")) {
                downloads.incrementAndGet();
                try {
                    identity = AESUtils.decryptByHexAES(req.getParam("downloadId"), AESUtils.CIPHER_AES0);
                    long timestamp = Long.parseLong(AESUtils.decryptByHexAES(req.getParam("timestamp"), AESUtils.CIPHER_AES0));
                    assertTrue(Math.abs(System.currentTimeMillis() - timestamp) < 10000);
                    assertEquals(AESUtils.encrypt2Hex("123|" + timestamp), req.getParam("auth"));
                    assertEquals("share", req.getParam("shareId"));
                    if (businessError != null) {
                        req.response().end(businessError);
                    } else {
                        req.response().setStatusCode(302).putHeader("Location", "https://download.example/file").end();
                    }
                } catch (Throwable e) {
                    req.response().setStatusCode(500).end(e.toString());
                }
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
        FjTool.resetToken();
    }

    @Test
    public void directoryLinkUsesOwnerEvenWithConfiguredCredentials() throws Exception {
        ShareLinkInfo info = info();
        info.getOtherParam().put("auths", MultiMap.caseInsensitiveMultiMap()
                .set("username", "test-user").set("password", "test-password"));
        assertEquals("https://download.example/file", tool(info).parseById()
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS));
        assertEquals(0, logins.get());
        assertEquals(1, downloads.get());
        assertEquals("123|11069975", identity);
    }

    @Test
    public void shareRequestUsesOwnerNotGlobalUserIdentity() throws Exception {
        FjTool.userId = "other-user";
        assertEquals("https://download.example/file", tool(info()).parseById()
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS));
        assertEquals("123|11069975", identity);
        assertEquals(0, logins.get());
        FjTool.userId = null;
    }

    @Test
    public void upstreamBusinessErrorCompletesFutureWithReason() throws Exception {
        businessError = "{\"code\":403,\"msg\":\"需要会员\"}";
        try {
            tool(info()).parseById().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            fail("Expected upstream error");
        } catch (ExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("需要会员"));
            assertTrue(e.getCause().getMessage().contains("code=403"));
        }
    }

    @Test
    public void missingOwnerFailsBeforeDownload() throws Exception {
        ShareLinkInfo info = info();
        info.getOtherParam().put("paramJson", JsonObject.of("fileId", "123", "shareId", "share"));
        try {
            tool(info).parseById().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            fail("Expected missing owner error");
        } catch (ExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("缺少分享者 userId"));
        }
        assertEquals(0, downloads.get());
    }

    private ShareLinkInfo info() {
        ShareLinkInfo info = ShareLinkInfo.newBuilder().type("fj").shareKey("share")
                .shareUrl("https://share.feijipan.com/s/share").build();
        info.getOtherParam().put("paramJson", JsonObject.of("fidEncode", AESUtils.encrypt2Hex("123|11069975"),
                "uuid", "test-uuid", "ts", "expired", "auth", "expired", "shareId", "share"));
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
