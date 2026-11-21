package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.impl.QuarkFamilyDownloadPolicy.Membership;
import cn.qaiu.parser.impl.QuarkFamilyDownloadPolicy.ProbeHit;
import cn.qaiu.util.QuarkAccountScope;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 夸克 / UC 大文件策略。会员、体积、文件名和响应分类不访问网盘。
 * 本地 HTTP 只验证探测不会收完整文件，以及 HTML / JSON 不会被当成下载。
 */
public class QuarkFamilyDownloadPolicyTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final String SECRET = "__pus=super-secret-cookie; __puus=another-secret";

    private static Vertx vertx;
    private static HttpServer server;
    private static WebClient client;
    private static int port;

    @BeforeClass
    public static void startServer() throws Exception {
        vertx = Vertx.vertx();
        client = WebClient.create(vertx);
        CountDownLatch latch = new CountDownLatch(1);
        server = vertx.createHttpServer().requestHandler(req -> {
            String path = req.path();
            if ("/member-valid".equals(path)) {
                req.response().putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("code", 0).put("data", new JsonObject()
                                .put("member_type", "SUPER_VIP").put("exp_at", 4_102_416_000_000L)).encode());
            } else if ("/member-auth".equals(path)) {
                req.response().putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("code", 31001).put("message", "未登录").encode());
            } else if ("/member-html".equals(path)) {
                req.response().putHeader("Content-Type", "text/html").end("<html>login</html>");
            } else if ("/preview-html".equals(path)) {
                req.response().putHeader("Content-Type", "text/html; charset=utf-8")
                        .end("<!DOCTYPE html><html><body>preview page</body></html>");
            } else if ("/preview-missing".equals(path)) {
                req.response().putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("code", 0).put("data", new JsonObject()).encode());
            } else if ("/preview-page".equals(path)) {
                req.response().putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("code", 0).put("data", new JsonObject()
                                .put("download_url", "https://vt.quark.cn/preview?foo=1")).encode());
            } else if ("/zip".equals(path)) {
                byte[] body = new byte[256 * 1024];
                body[0] = 'P';
                body[1] = 'K';
                req.response().putHeader("Content-Type", "application/zip")
                        .putHeader("Content-Length", Integer.toString(body.length))
                        .end(Buffer.buffer(body));
            } else if ("/html-file".equals(path)) {
                req.response().putHeader("Content-Type", "text/html")
                        .end("<html><body>not a file</body></html>");
            } else if ("/json-file".equals(path)) {
                req.response().putHeader("Content-Type", "application/json")
                        .end("{\"code\":412,\"message\":\"html-not-file\"}");
            } else if ("/empty".equals(path)) {
                req.response().putHeader("Content-Type", "text/plain").putHeader("Content-Length", "0").end();
            } else if ("/redirect".equals(path)) {
                req.response().setStatusCode(302).putHeader("Location", "https://pan.quark.cn/login").end();
            } else {
                req.response().setStatusCode(404).end();
            }
        });
        server.listen(0).onComplete(ar -> {
            if (ar.succeeded()) {
                port = ar.result().actualPort();
            }
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(port > 0);
    }

    @AfterClass
    public static void stopServer() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    public void thresholdKeepsEqualSizeOnNormalPath() {
        long limit = QuarkFamilyDownloadPolicy.NORMAL_PARSE_MAX_BYTES;
        assertEquals(10L * 1024 * 1024, limit);
        assertFalse(QuarkFamilyDownloadPolicy.knownLarge(0));
        assertFalse(QuarkFamilyDownloadPolicy.knownLarge(limit - 1));
        assertFalse(QuarkFamilyDownloadPolicy.knownLarge(limit));
        assertTrue(QuarkFamilyDownloadPolicy.knownLarge(limit + 1));
        assertFalse(QuarkFamilyDownloadPolicy.knownLarge(Long.MAX_VALUE));
        assertFalse(QuarkFamilyDownloadPolicy.knownLarge(-1));
        assertEquals(limit, QuarkFamilyDownloadPolicy.sizeOf(new JsonObject().put("size", limit)));
        assertEquals(Long.MAX_VALUE, QuarkFamilyDownloadPolicy.sizeOf(new JsonObject()));
    }

    @Test
    public void membershipRequiresTierAndFutureExpiry() {
        assertTrue(judge("SUPER_VIP", NOW + 1).validPaid);
        assertTrue(judge("VIP", NOW + 60_000).validPaid);
        assertTrue(judge("EXP_SVIP", NOW + 60_000).validPaid);
        assertTrue(judge("EXP_VIP", NOW + 60_000).validPaid);
        assertTrue(judge("Z_VIP", NOW + 60_000).validPaid);
        assertTrue(judge("vip", NOW + 1).validPaid);

        Membership expired = judge("SUPER_VIP", NOW);
        assertFalse(expired.validPaid);
        assertTrue(expired.reason.contains("过期"));

        Membership past = judge("VIP", NOW - 1);
        assertFalse(past.validPaid);

        Membership normal = judge("NORMAL", NOW + 60_000);
        assertFalse(normal.validPaid);
        assertTrue(normal.reason.contains("不是"));

        Membership unknown = judge("MINI", NOW + 60_000);
        assertFalse(unknown.validPaid);

        Membership noType = QuarkFamilyDownloadPolicy.judgeMembership(new JsonObject().put("exp_at", NOW + 1), NOW);
        assertFalse(noType.validPaid);
        assertTrue(noType.reason.contains("member_type"));

        Membership noExp = QuarkFamilyDownloadPolicy.judgeMembership(new JsonObject().put("member_type", "SVIP"), NOW);
        assertFalse(noExp.validPaid);

        Membership missingExp = QuarkFamilyDownloadPolicy.judgeMembership(
                new JsonObject().put("member_type", "SUPER_VIP"), NOW);
        assertFalse(missingExp.validPaid);
        assertTrue(missingExp.reason.contains("exp_at"));

        long seconds = (NOW + 5_000) / 1000;
        assertTrue(QuarkFamilyDownloadPolicy.judgeMembership(new JsonObject()
                .put("member_type", "VIP").put("exp_at", seconds), NOW).validPaid);
        assertNull(QuarkFamilyDownloadPolicy.judgeMembership(null, NOW).memberType);
    }

    @Test
    public void memberHttpAuthHtmlAndMissingCookieAreNotPaid() throws Exception {
        Membership valid = await(QuarkFamilyDownloadPolicy.fetchMembership(client, cookieHeaders(),
                url("/member-valid"), false));
        assertTrue(valid.validPaid);
        assertEquals("SUPER_VIP", valid.memberType);

        Membership auth = await(QuarkFamilyDownloadPolicy.fetchMembership(client, cookieHeaders(),
                url("/member-auth"), true));
        assertFalse(auth.validPaid);
        assertTrue(auth.reason.contains("31001"));

        Membership html = await(QuarkFamilyDownloadPolicy.fetchMembership(client, cookieHeaders(),
                url("/member-html"), true));
        assertFalse(html.validPaid);
        assertTrue(html.reason.contains("HTML"));

        Membership missing = await(QuarkFamilyDownloadPolicy.fetchMembership(client, MultiMap.caseInsensitiveMultiMap(),
                url("/member-valid"), false));
        assertFalse(missing.validPaid);
        assertTrue(missing.reason.contains("未配置"));
        assertFalse(missing.reason.contains("super-secret"));
    }

    @Test
    public void previewHtmlMissingAndWebpageAreNotDownloads() throws Exception {
        String html = awaitFailure(client.getAbs(url("/preview-html")).send()
                .compose(res -> QuarkFamilyDownloadPolicy.interpretPreview(res, "qk", false)));
        assertTrue(html.contains("HTML"));

        String missing = awaitFailure(client.getAbs(url("/preview-missing")).send()
                .compose(res -> QuarkFamilyDownloadPolicy.interpretPreview(res, "uc", false)));
        assertTrue(missing.contains("download_url"));

        String page = await(client.getAbs(url("/preview-page")).send()
                .compose(res -> QuarkFamilyDownloadPolicy.interpretPreview(res, "qk", false)));
        assertFalse(QuarkFamilyDownloadPolicy.isPublicFileUrl(page, "qk"));
        assertTrue(page.contains("vt.quark.cn"));
    }

    @Test
    public void publicCdnHostsAreSeparateForQuarkAndUc() {
        String quark = "https://dl-c-zb-u.drive.quark.cn/a/b?auth_key=1&sp=100&token=t&abt=9";
        String uc = "https://dl-c-zb.drive.uc.cn/a/b?auth_key=1&sp=100&token=t";
        assertTrue(QuarkFamilyDownloadPolicy.isPublicFileUrl(quark, "qk"));
        assertFalse(QuarkFamilyDownloadPolicy.isPublicFileUrl(quark, "uc"));
        assertTrue(QuarkFamilyDownloadPolicy.isPublicFileUrl(uc, "uc"));
        assertFalse(QuarkFamilyDownloadPolicy.isPublicFileUrl(uc, "qk"));
        assertFalse(QuarkFamilyDownloadPolicy.isPublicFileUrl("https://dl-c-sz.u-drive.quark.cn/a", "qk"));
        assertFalse(QuarkFamilyDownloadPolicy.isPublicFileUrl("https://dl-pc.sz.drive.quark.cn/a", "qk"));
        assertFalse(QuarkFamilyDownloadPolicy.isPublicFileUrl("https://dl-guest-1.drive.quark.cn/a", "qk"));
        assertFalse(QuarkFamilyDownloadPolicy.isPublicFileUrl("https://vt.quark.cn/preview", "qk"));
        assertFalse(QuarkFamilyDownloadPolicy.isPublicFileUrl("https://drive.uc.cn/s/abc", "uc"));
        assertFalse(QuarkFamilyDownloadPolicy.isPublicFileUrl("http://dl-c-zb.drive.uc.cn/a", "uc"));
    }

    @Test
    public void filenameUsesUtf8AndDropsTrackingParams() throws Exception {
        String raw = "https://dl-c-zb.drive.quark.cn/a/b?auth_key=9&abt=1&sp=100&token=abc&filename=old";
        String bare = QuarkFamilyDownloadPolicy.bareFileUrl(raw);
        assertFalse(bare.contains("abt="));
        assertFalse(bare.contains("filename="));
        String named = QuarkFamilyDownloadPolicy.withFilename(bare, "报告 (1).zip");
        assertTrue(named.contains("filename="));
        assertFalse(named.contains("报告"));
        String value = named.substring(named.indexOf("filename=") + "filename=".length());
        assertEquals("报告 (1).zip", URLDecoder.decode(value, StandardCharsets.UTF_8));
        assertFalse(value.contains(" "));
        assertFalse(value.contains("+"));
        String fromAccount = QuarkFamilyDownloadPolicy.presentPublicDownload(raw, "qk", "报告 (1).zip");
        assertTrue(fromAccount.contains("filename="));
        assertFalse(fromAccount.contains("abt="));
        assertEquals("https://dl-pc.sz.drive.quark.cn/a?auth_key=1",
                QuarkFamilyDownloadPolicy.presentPublicDownload(
                        "https://dl-pc.sz.drive.quark.cn/a?auth_key=1", "qk", "a.zip"));
    }

    @Test
    public void videoPickerKeepsProgressiveFileAndDropsPlaylist() {
        JsonObject data = new JsonObject().put("video_list", new JsonArray()
                .add(new JsonObject().put("video_info", new JsonObject()
                        .put("width", 1920)
                        .put("url", "https://dl-c-zb.drive.quark.cn/high.m3u8?auth_key=1&token=t")))
                .add(new JsonObject().put("video_info", new JsonObject()
                        .put("width", 1280)
                        .put("url", "https://dl-pc.sz.drive.quark.cn/login.mp4?auth_key=1")))
                .add(new JsonObject().put("video_info", new JsonObject()
                        .put("width", 640)
                        .put("url", "https://dl-c-sz.drive.quark.cn/low.mp4?auth_key=2&token=t&abt=1"))));
        assertEquals("https://dl-c-sz.drive.quark.cn/low.mp4?auth_key=2&token=t&abt=1",
                QuarkFamilyDownloadPolicy.pickProgressiveFileUrl(data, "qk"));
        assertEquals("", QuarkFamilyDownloadPolicy.pickProgressiveFileUrl(new JsonObject()
                .put("download_url", "https://pan.quark.cn/s/abc"), "qk"));
    }

    @Test
    public void probeRejectsHtmlJsonAndEmptyAndCapsZipPrefix() throws Exception {
        ProbeHit zip = await(QuarkFamilyDownloadPolicy.probeBody(vertx, url("/zip"), "qk", 1));
        assertTrue(zip.ok);
        assertTrue(zip.prefixLength > 0);
        assertTrue(zip.prefixLength <= 64);

        ProbeHit html = await(QuarkFamilyDownloadPolicy.probeBody(vertx, url("/html-file"), "qk", 1));
        assertFalse(html.ok);
        assertTrue(html.reason.contains("HTML"));

        ProbeHit json = await(QuarkFamilyDownloadPolicy.probeBody(vertx, url("/json-file"), "uc", 1));
        assertFalse(json.ok);
        assertTrue(json.reason.contains("JSON"));

        ProbeHit empty = await(QuarkFamilyDownloadPolicy.probeBody(vertx, url("/empty"), "qk", 1));
        assertFalse(empty.ok);

        ProbeHit redirect = await(QuarkFamilyDownloadPolicy.probeBody(vertx, url("/redirect"), "qk", 1));
        assertFalse(redirect.ok);
        assertTrue(redirect.reason.contains("不是公开文件"));

        ProbeHit rangedHtml = QuarkFamilyDownloadPolicy.judgeProbe(206, "text/html", "bytes 0-0/9", "1",
                Buffer.buffer("<html>"));
        assertFalse(rangedHtml.ok);
        ProbeHit rangedZip = QuarkFamilyDownloadPolicy.judgeProbe(206, "application/zip", "bytes 0-15/99", null,
                Buffer.buffer("PK"));
        assertTrue(rangedZip.ok);
        ProbeHit denied = QuarkFamilyDownloadPolicy.judgeProbe(412, "text/html", null, null, Buffer.buffer("<html>"));
        assertFalse(denied.ok);
        assertTrue(denied.reason.contains("412"));
    }

    @Test
    public void errorsStayActionableAndHideSecrets() {
        RuntimeException leaked = new RuntimeException("失败 cookie=" + SECRET + " <html>login</html>");
        String uc = QuarkFamilyDownloadPolicy.largeFileError("uc", leaked,
                new IllegalStateException("H5 预览没有 download_url"));
        String qk = QuarkFamilyDownloadPolicy.largeFileError("qk", null,
                new IllegalStateException("<!DOCTYPE html>"));
        assertTrue(uc.contains("不能免登录"));
        assertTrue(uc.contains("auths.uc.cookie"));
        assertTrue(uc.contains("10MiB"));
        assertFalse(uc.contains("super-secret"));
        assertFalse(uc.contains("<html"));
        assertTrue(qk.contains("auths.qk.cookie"));
        assertTrue(qk.contains("HTML"));
        assertFalse(qk.contains("免登录下载"));
    }

    @Test
    public void publicDirectDropsAccountHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("cookie", SECRET);
        headers.put("Referer", "https://pan.quark.cn/");
        assertFalse(QuarkFamilyDownloadPolicy.prepareDownloader(headers, true, true));
        assertTrue(headers.isEmpty());

        Map<String, String> account = new LinkedHashMap<>();
        account.put("cookie", "account-cookie");
        assertTrue(QuarkFamilyDownloadPolicy.prepareDownloader(account, true, false));
        assertEquals("account-cookie", account.get("cookie"));
    }

    @Test
    public void accountCacheScopeDoesNotReuseAnotherCookie() {
        Map<String, Object> guest = new HashMap<>();
        assertNull(QuarkAccountScope.suffix(guest));

        Map<String, Object> first = authParam(SECRET);
        Map<String, Object> second = authParam("__pus=other-account; __uid=2");
        Map<String, Object> same = authParam(SECRET);
        String a = QuarkAccountScope.suffix(first);
        String b = QuarkAccountScope.suffix(second);
        assertEquals(a, QuarkAccountScope.suffix(same));
        assertNotEquals(a, b);
        assertFalse(a.contains("super-secret"));
        assertFalse(b.contains("__pus"));

        ShareLinkInfo guestInfo = share("qk", guest);
        ShareLinkInfo accountInfo = share("qk", first);
        ShareLinkInfo otherInfo = share("UC", second);
        assertEquals("qk:key", guestInfo.getCacheKey());
        assertTrue(accountInfo.getCacheKey().startsWith("qk:key#"));
        assertNotEquals(accountInfo.getCacheKey(), otherInfo.getCacheKey().replace("UC:", "qk:"));
        assertFalse(accountInfo.getCacheKey().contains("super-secret"));
        assertFalse(otherInfo.getCacheKey().contains("other-account"));
    }

    private static Membership judge(String type, long expAt) {
        return QuarkFamilyDownloadPolicy.judgeMembership(new JsonObject()
                .put("member_type", type).put("exp_at", expAt), NOW);
    }

    private static MultiMap cookieHeaders() {
        return MultiMap.caseInsensitiveMultiMap().set("cookie", SECRET);
    }

    private static String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private static Map<String, Object> authParam(String cookie) {
        MultiMap auths = MultiMap.caseInsensitiveMultiMap().set("cookie", cookie);
        Map<String, Object> other = new HashMap<>();
        other.put("auths", auths);
        return other;
    }

    private static ShareLinkInfo share(String type, Map<String, Object> other) {
        ShareLinkInfo info = ShareLinkInfo.newBuilder().type(type).shareKey("key").build();
        info.setOtherParam(other);
        return info;
    }

    private static <T> T await(io.vertx.core.Future<T> future) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        future.onComplete(ar -> {
            if (ar.succeeded()) {
                value.set(ar.result());
            } else {
                error.set(ar.cause());
            }
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError(error.get().getMessage(), error.get());
        }
        return value.get();
    }

    private static String awaitFailure(io.vertx.core.Future<?> future) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        future.onComplete(ar -> {
            if (ar.failed()) {
                error.set(ar.cause());
            }
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(error.get() != null);
        return error.get().getMessage() == null ? "" : error.get().getMessage();
    }
}
