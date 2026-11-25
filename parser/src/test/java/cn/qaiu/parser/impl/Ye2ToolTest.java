package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 123 分享单文件下载规则。不访问网络。
 * mvn -pl parser -DskipTests=false -Dtest=Ye2ToolTest,Ye2LoginHintTest test
 */
public class Ye2ToolTest {

    @Test
    public void downloadUsesShareKeyEncodedInDirectoryFileParameter() {
        JsonObject directoryFileParameter = new JsonObject()
                .put("shareKey", "iaKtVv-6OECd.html?from=dir")
                .put("fileId", 123);

        assertEquals("iaKtVv-6OECd",
                Ye2Tool.resolveDownloadShareKey(directoryFileParameter, "-"));
    }

    @Test
    public void downloadFallsBackToParserShareKeyWhenParameterHasNone() {
        assertEquals("iaKtVv-6OECd",
                Ye2Tool.resolveDownloadShareKey(new JsonObject(), "iaKtVv-6OECd.html"));
    }

    @Test
    public void downloadAcceptsCapitalShareKeyFromParameter() {
        JsonObject directoryFileParameter = new JsonObject()
                .put("ShareKey", "iaKtVv-r4aCd");

        assertEquals("iaKtVv-r4aCd",
                Ye2Tool.resolveDownloadShareKey(directoryFileParameter, "-"));
    }

    @Test
    public void anonymous123RequestsDoNotSendFakeBearerTokens() {
        assertNull(Ye2Tool.bearerAuthorization(null));
        assertNull(Ye2Tool.bearerAuthorization(""));
        assertNull(Ye2Tool.bearerAuthorization("nologin"));
    }

    @Test
    public void configured123TokenKeepsExistingBearerAuthentication() {
        assertEquals("Bearer configured-token",
                Ye2Tool.bearerAuthorization("configured-token"));
    }

    @Test
    public void shareDownloadUsesCurrentWebAppVersion() {
        assertEquals("149", Ye2Tool.SHARE_WEB_APP_VERSION);
        assertTrue(Ye2Tool.usableShareKey("iaKtVv-r4aCd"));
        assertFalse(Ye2Tool.usableShareKey("-"));
        assertFalse(Ye2Tool.usableShareKey(""));
        assertFalse(Ye2Tool.usableShareKey(null));
    }

    @Test
    public void placeholderShareUrlIsReplacedWithShareKeyFromFileParameter() {
        ShareLinkInfo info = ShareLinkInfo.newBuilder()
                .type("ye")
                .shareKey("-")
                .shareUrl("https://www.123pan.com/s/-")
                .standardUrl("https://www.123pan.com/s/-")
                .build();

        Ye2Tool.applyResolvedShareKey(info, "iaKtVv-r4aCd");

        assertEquals("iaKtVv-r4aCd", info.getShareKey());
        assertEquals("https://www.123pan.com/s/iaKtVv-r4aCd", info.getShareUrl());
        assertEquals("https://www.123pan.com/s/iaKtVv-r4aCd", info.getStandardUrl());
    }

    @Test
    public void realShareUrlIsKeptWhenResolvingDirectoryFile() {
        String original = "https://1815268665.mshare.123pan.cn/123pan/iaKtVv-r4aCd";
        ShareLinkInfo info = ShareLinkInfo.newBuilder()
                .type("ye")
                .shareKey("iaKtVv-r4aCd")
                .shareUrl(original)
                .standardUrl("https://www.123pan.com/s/iaKtVv-r4aCd")
                .build();

        Ye2Tool.applyResolvedShareKey(info, "iaKtVv-r4aCd");

        assertEquals(original, info.getShareUrl());
        assertEquals("https://www.123pan.com/s/iaKtVv-r4aCd", info.getStandardUrl());
    }

    @Test
    public void placeholderStandardUrlDoesNotReplaceOriginalShareUrl() {
        String original = "https://1815268665.mshare.123pan.cn/123pan/iaKtVv-r4aCd";
        ShareLinkInfo info = ShareLinkInfo.newBuilder()
                .type("ye")
                .shareKey("-")
                .shareUrl(original)
                .standardUrl("https://www.123pan.com/s/-")
                .build();

        Ye2Tool.applyResolvedShareKey(info, "iaKtVv-r4aCd");

        assertEquals(original, info.getShareUrl());
        assertEquals("https://www.123pan.com/s/iaKtVv-r4aCd", info.getStandardUrl());
    }

    @Test
    public void anonymousV2LoginCodeUsesExistingLoginHint() {
        JsonObject json = new JsonObject()
                .put("code", 5112)
                .put("message", "您需要注册登录或付费后下载");

        String msg = Ye2Tool.shareV2FailureMessage(json, "nologin");

        assertTrue(msg.contains("auths.ye.authorization"));
        assertTrue(msg.contains("上游信息: 您需要注册登录或付费后下载"));
        assertFalse(msg.contains("downURLJson"));
        assertFalse(msg.contains("download_info"));
    }

    @Test
    public void loggedInV2TrafficShortageStaysOnV2Error() {
        JsonObject json = new JsonObject()
                .put("code", 5113)
                .put("message", "分享方提取流量包不足");

        String msg = Ye2Tool.shareV2FailureMessage(json, "configured-token");

        assertTrue(msg.startsWith("v2接口返回异常:"));
        assertTrue(msg.contains("5113"));
        assertTrue(msg.contains("分享方提取流量包不足"));
        assertFalse(msg.contains("downURLJson"));
        assertFalse(msg.contains("download_info"));
        assertFalse(msg.contains("auths.ye.authorization"));
    }

    @Test
    public void oldClientErrorIsNotRewrittenAsPersonalFileApiFailure() {
        JsonObject json = new JsonObject()
                .put("code", 5066)
                .put("message", "该文件暂不支持在当前版本下载");

        String msg = Ye2Tool.shareV2FailureMessage(json, "configured-token");

        assertTrue(msg.startsWith("v2接口返回异常:"));
        assertTrue(msg.contains("5066"));
        assertFalse(msg.contains("downURLJson"));
        assertFalse(Ye2Tool.isYeLoginRequired(5066, "该文件暂不支持在当前版本下载"));
        assertFalse(Ye2Tool.isYeLoginRequired(5113, "分享方提取流量包不足"));
    }

    @Test
    public void signatureUsesActualSharePathPlatformAndVersion() {
        long time = 1_700_000_000_000L;
        long randomA = 42L;
        String traffic = Ye2Tool.signMaterial(
                Ye2Tool.SHARE_TRAFFIC_CHECK_PATH, Ye2Tool.SHARE_PLATFORM, Ye2Tool.SHARE_WEB_APP_VERSION, time, randomA);
        String download = Ye2Tool.signMaterial(
                Ye2Tool.SHARE_DOWNLOAD_INFO_PATH, Ye2Tool.SHARE_PLATFORM, Ye2Tool.SHARE_WEB_APP_VERSION, time, randomA);

        assertTrue(traffic.contains("|/api/share/download/traffic/check|web|149|"));
        assertTrue(download.contains("|/api/v2/share/download/info|web|149|"));
        assertFalse(traffic.contains("/b/api/"));
        assertFalse(download.contains("/b/api/"));
        assertEquals(Ye2Tool.signQuery(
                        Ye2Tool.SHARE_DOWNLOAD_INFO_PATH, "web", "149", time, randomA),
                Ye2Tool.signQuery(Ye2Tool.SHARE_DOWNLOAD_INFO_PATH, Ye2Tool.SHARE_PLATFORM,
                        Ye2Tool.SHARE_WEB_APP_VERSION, time, randomA));
    }

    @Test
    public void mshareAndPlainSharePageUseDecodedShareHost() {
        String host = "https://1815268665.share.123pan.cn";
        assertEquals(host, Ye2Tool.shareApiOrigin(
                "https://1815268665.mshare.123pan.cn/123pan/iaKtVv-r4aCd",
                "https://www.123pan.com/s/iaKtVv-r4aCd",
                "iaKtVv-r4aCd"));
        assertEquals(host, Ye2Tool.shareApiOrigin(
                "https://1815268665.share.123pan.cn/123pan/iaKtVv-r4aCd?notoken=1",
                null,
                "iaKtVv-r4aCd"));
        assertEquals(host, Ye2Tool.shareApiOrigin(
                "https://www.123pan.com/s/iaKtVv-r4aCd",
                "https://yun.123pan.com/s/iaKtVv-r4aCd",
                "iaKtVv-r4aCd"));
        assertFalse(host.contains("mshare"));
    }

    @Test
    public void maliciousHostIsNotCalled() {
        AtomicInteger calls = new AtomicInteger();
        Future<String> blocked = Ye2Tool.executeShareFileDownload(call -> {
            calls.incrementAndGet();
            return Future.succeededFuture(new JsonObject().put("code", 0));
        }, sampleRequest("http://127.0.0.1/latest/meta-data", "iaKtVv-r4aCd", "configured-token"));
        Future<String> disguised = Ye2Tool.executeShareFileDownload(call -> {
            calls.incrementAndGet();
            return Future.succeededFuture(new JsonObject().put("code", 0));
        }, sampleRequest("https://1815268665.share.123pan.cn.evil.com/123pan/iaKtVv-r4aCd",
                "iaKtVv-r4aCd", "configured-token"));
        Future<String> unknownKey = Ye2Tool.executeShareFileDownload(call -> {
            calls.incrementAndGet();
            return Future.succeededFuture(new JsonObject().put("code", 0));
        }, sampleRequest("https://evil.example/123pan/not-a-real-key", "not-a-real-key", "configured-token"));

        assertEquals(0, calls.get());
        assertTrue(blocked.failed());
        assertTrue(disguised.failed());
        assertTrue(unknownKey.failed());
        assertTrue(blocked.cause().getMessage().contains("分享域名"));
    }

    @Test
    public void successChecksTrafficThenDownloadsOnShareHost() {
        List<Ye2Tool.ShareHttpCall> seen = new ArrayList<>();
        Future<String> result = Ye2Tool.executeShareFileDownload(call -> {
            seen.add(call);
            if (seen.size() == 1) {
                return Future.succeededFuture(new JsonObject()
                        .put("code", 0)
                        .put("data", new JsonObject()
                                .put("isBlocked", false)
                                .put("isTrafficExceeded", false)
                                .put("isPay", false)));
            }
            return Future.succeededFuture(new JsonObject()
                    .put("code", 0)
                    .put("data", new JsonObject()
                            .put("downloadPath", "/123/file.zip")
                            .put("dispatchList", new JsonArray()
                                    .add(new JsonObject().put("prefix", "https://cdn-a.example/"))
                                    .add(new JsonObject().put("prefix", "https://cdn-b.example/")))));
        }, sampleRequest("https://1815268665.mshare.123pan.cn/123pan/iaKtVv-r4aCd",
                "iaKtVv-r4aCd", "configured-token"));

        assertTrue(result.succeeded());
        assertEquals("https://cdn-a.example/123/file.zip", result.result());
        assertEquals(2, seen.size());

        Ye2Tool.ShareHttpCall traffic = seen.get(0);
        Ye2Tool.ShareHttpCall download = seen.get(1);
        assertEquals("https://1815268665.share.123pan.cn", traffic.origin);
        assertEquals(Ye2Tool.SHARE_TRAFFIC_CHECK_PATH, traffic.path);
        assertEquals(Ye2Tool.SHARE_DOWNLOAD_INFO_PATH, download.path);
        assertEquals(traffic.origin, download.origin);
        assertFalse(traffic.url().contains("api.123278.com"));
        assertFalse(traffic.url().contains("/b/api/"));
        assertEquals(Ye2Tool.signQuery(traffic.path, "web", "149", 1_700_000_000_000L, 42L), traffic.query);
        assertEquals(Ye2Tool.signQuery(download.path, "web", "149", 1_700_000_000_000L, 42L), download.query);

        assertEquals("149", traffic.headers.get("App-Version"));
        assertEquals("web", traffic.headers.get("platform"));
        assertEquals("Bearer configured-token", traffic.headers.get("Authorization"));
        assertEquals("application/json;charset=UTF-8", traffic.headers.get("Content-Type"));
        assertEquals("https://1815268665.share.123pan.cn", traffic.headers.get("Origin"));
        assertEquals("https://1815268665.share.123pan.cn/123pan/iaKtVv-r4aCd", traffic.headers.get("Referer"));
        assertEquals("iaKtVv-r4aCd", traffic.body.getString("shareKey"));
        assertEquals(Long.valueOf(2468L), traffic.body.getJsonArray("fileIds").getLong(0));
        assertFalse(traffic.body.containsKey("payType"));

        assertEquals("iaKtVv-r4aCd", download.body.getString("ShareKey"));
        assertEquals(Long.valueOf(2468L), download.body.getLong("FileID"));
        assertEquals("etag-flag", download.body.getString("S3keyFlag"));
        assertEquals(Long.valueOf(6915371L), download.body.getLong("Size"));
        assertEquals("abcetag", download.body.getString("Etag"));
        assertFalse(download.body.containsKey("OrderId"));
        assertFalse(download.body.containsKey("payType"));
    }

    @Test
    public void directoryDownloadUsesShareKeyHostNotPlaceholderPage() {
        List<Ye2Tool.ShareHttpCall> seen = new ArrayList<>();
        Ye2Tool.ShareFileRequest request = sampleRequest(
                "https://www.123pan.com/s/iaKtVv-r4aCd", "iaKtVv-r4aCd", "configured-token");
        Future<String> result = Ye2Tool.executeShareFileDownload(call -> {
            seen.add(call);
            if (seen.size() == 1) {
                return Future.succeededFuture(openTraffic());
            }
            return Future.succeededFuture(new JsonObject()
                    .put("code", 0)
                    .put("data", new JsonObject()
                            .put("downloadPath", "folder/file.bin")
                            .put("dispatchList", new JsonArray().add(
                                    new JsonObject().put("prefix", "https://cdn.example/root")))));
        }, request);

        assertTrue(result.succeeded());
        assertEquals("https://cdn.example/root/folder/file.bin", result.result());
        assertEquals("https://1815268665.share.123pan.cn", seen.get(0).origin);
        assertFalse(seen.get(0).url().contains("www.123pan.com"));
    }

    @Test
    public void absoluteDownloadPathIsKept() {
        Future<String> result = Ye2Tool.executeShareFileDownload(call -> {
            if (call.path.equals(Ye2Tool.SHARE_TRAFFIC_CHECK_PATH)) {
                return Future.succeededFuture(openTraffic());
            }
            return Future.succeededFuture(new JsonObject()
                    .put("code", 0)
                    .put("data", new JsonObject()
                            .put("downloadPath", "https://cdn.example/already.zip")
                            .put("dispatchList", new JsonArray().add(
                                    new JsonObject().put("prefix", "https://ignored.example/")))));
        }, sampleRequest("https://www.123pan.com/s/iaKtVv-r4aCd", "iaKtVv-r4aCd", "configured-token"));

        assertEquals("https://cdn.example/already.zip", result.result());
    }

    @Test
    public void blockedPayAndMissingLinkDoNotContinue() {
        Future<String> blocked = downloadAfterTraffic(new JsonObject()
                .put("code", 0)
                .put("data", new JsonObject().put("isBlocked", true).put("isPay", false)));
        Future<String> pay = downloadAfterTraffic(new JsonObject()
                .put("code", 0)
                .put("data", new JsonObject().put("isPay", true)));
        Future<String> missing = Ye2Tool.executeShareFileDownload(call -> {
            if (call.path.equals(Ye2Tool.SHARE_TRAFFIC_CHECK_PATH)) {
                return Future.succeededFuture(openTraffic());
            }
            return Future.succeededFuture(new JsonObject().put("code", 0).put("data", new JsonObject()));
        }, sampleRequest("https://www.123pan.com/s/iaKtVv-r4aCd", "iaKtVv-r4aCd", "configured-token"));

        Future<String> exceeded = downloadAfterTraffic(new JsonObject()
                .put("code", 0)
                .put("data", new JsonObject().put("isTrafficExceeded", true)));

        assertTrue(blocked.cause().getMessage().contains("不会自动下单"));
        assertTrue(pay.cause().getMessage().contains("不会自动下单"));
        assertTrue(exceeded.cause().getMessage().contains("不会自动购买"));
        assertTrue(missing.cause().getMessage().contains("未返回下载链接"));
        assertFalse(blocked.cause().getMessage().contains("OrderId"));
    }

    @Test
    public void loginAndTrafficErrorsStayOnTheStepThatReturnedThem() {
        AtomicInteger calls = new AtomicInteger();
        Future<String> login = Ye2Tool.executeShareFileDownload(call -> {
            calls.incrementAndGet();
            return Future.succeededFuture(new JsonObject()
                    .put("code", 5112)
                    .put("message", "您需要注册登录或付费后下载"));
        }, sampleRequest("https://www.123pan.com/s/iaKtVv-r4aCd", "iaKtVv-r4aCd", "nologin"));
        Future<String> traffic = Ye2Tool.executeShareFileDownload(call -> {
            calls.incrementAndGet();
            if (call.path.equals(Ye2Tool.SHARE_TRAFFIC_CHECK_PATH)) {
                assertNull(call.headers.get("Authorization"));
                return Future.succeededFuture(openTraffic());
            }
            return Future.succeededFuture(new JsonObject()
                    .put("code", 5113)
                    .put("message", "分享方提取流量包不足"));
        }, sampleRequest("https://www.123pan.com/s/iaKtVv-r4aCd", "iaKtVv-r4aCd", "nologin"));

        assertEquals(3, calls.get());
        assertTrue(login.cause().getMessage().contains("auths.ye.authorization"));
        assertTrue(traffic.cause().getMessage().contains("5113"));
        assertFalse(traffic.cause().getMessage().contains("downURLJson"));
        assertFalse(traffic.cause().getMessage().contains("download_info"));
    }

    private static Future<String> downloadAfterTraffic(JsonObject trafficJson) {
        AtomicInteger calls = new AtomicInteger();
        Future<String> result = Ye2Tool.executeShareFileDownload(call -> {
            calls.incrementAndGet();
            return Future.succeededFuture(trafficJson);
        }, sampleRequest("https://www.123pan.com/s/iaKtVv-r4aCd", "iaKtVv-r4aCd", "configured-token"));
        assertEquals(1, calls.get());
        assertTrue(result.failed());
        return result;
    }

    private static JsonObject openTraffic() {
        return new JsonObject()
                .put("code", 0)
                .put("data", new JsonObject()
                        .put("isBlocked", false)
                        .put("isTrafficExceeded", false)
                        .put("isPay", false));
    }

    private static Ye2Tool.ShareFileRequest sampleRequest(String shareUrl, String shareKey, String token) {
        return new Ye2Tool.ShareFileRequest(
                shareUrl,
                "https://www.123pan.com/s/" + shareKey,
                shareKey,
                "",
                2468L,
                "etag-flag",
                6915371L,
                "abcetag",
                token,
                1_700_000_000_000L,
                42L);
    }
}
