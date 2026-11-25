package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

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
}
