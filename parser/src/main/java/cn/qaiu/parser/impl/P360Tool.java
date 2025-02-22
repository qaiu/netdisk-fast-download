package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <a href="https://yunpan.360.cn">360AI云盘</a>
 * 360AI云盘解析
 * 下载链接需要Referer: https://link.yunpan.com/
 */
public class P360Tool extends PanBase {
    public P360Tool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        // https://www.yunpan.com/surl_yD7jK9d4W6D 获取302跳转地址
        clientNoRedirects.getAbs(shareLinkInfo.getShareUrl()).send()
                .onSuccess(res -> {
                    String location = res.getHeader("Location");
                    if (location != null) {
                        down302(location);
                    } else {
                        fail();
                    }
                }).onFailure(handleFail(""));


        return future();
    }

    private void down302(String url) {
        // 获取URL前缀 https://214004.link.yunpan.com/lk/surl_yD7ZU4WreR8 -> https://214004.link.yunpan.com/
        String urlPrefix = url.substring(0, url.indexOf("/", 8));

        clientSession.getAbs(url)
                .send()
                .onSuccess(res -> {
                    // find  "nid": "17402043311959599"
                    Pattern compile = Pattern.compile("\"nid\": \"([^\"]+)\"");
                    Matcher matcher = compile.matcher(res.bodyAsString());
                    AtomicReference<String> nid = new AtomicReference<>();
                    if (matcher.find()) {
                        nid.set(matcher.group(1));
                    } else {
                        // 需要验证密码
                        /*
                         * POST https://4aec17.link.yunpan.com/share/verifyPassword
                         * Content-type: application/x-www-form-urlencoded UTF-8
                         * Referer: https://4aec17.link.yunpan.com/lk/surl_yD7jK9d4W6D
                         *
                         * shorturl=surl_yD7jK9d4W6D&linkpassword=d969
                         */
                        clientSession.postAbs(urlPrefix + "/share/verifyPassword")
                                .putHeader("Content-Type", "application/x-www-form-urlencoded")
                                .putHeader("Referer", urlPrefix)
                                .sendBuffer(Buffer.buffer("shorturl=" + shareLinkInfo.getShareKey() + "&linkpassword" +
                                        "=" + shareLinkInfo.getSharePassword()))
                                .onSuccess(res2 -> {
                                    JsonObject entries = asJson(res2);
                                    if (entries.getInteger("errno") == 0) {
                                        clientSession.getAbs(url)
                                                .send()
                                                .onSuccess(res3 -> {
                                                    Matcher matcher1 = compile.matcher(res3.bodyAsString());
                                                    if (matcher1.find()) {
                                                        nid.set(matcher1.group(1));
                                                    } else {
                                                        fail();
                                                        return;
                                                    }
                                                    down(urlPrefix, nid.get());
                                                }).onFailure(handleFail(""));
                                    } else {
                                        fail(entries.encode());
                                    }
                                }).onFailure(handleFail(""));
                        return;
                    }
                    down(urlPrefix, nid.get());
                }).onFailure(handleFail(""));
    }

    private void down(String urlPrefix, String nid) {
        clientSession.postAbs(urlPrefix + "/share/downloadfile")
                .putHeader("Content-Type", "application/x-www-form-urlencoded")
                .putHeader("Referer", urlPrefix)
                .sendBuffer(Buffer.buffer("shorturl=" + shareLinkInfo.getShareKey() + "&nid=" + nid))
                .onSuccess(res2 -> {
                    JsonObject entries = asJson(res2);
                    String downloadurl = entries.getJsonObject("data").getString("downloadurl");
                    complete(downloadurl);
                }).onFailure(handleFail(""));
    }

//    public static void main(String[] args) {
//        String s = new P360Tool(ShareLinkInfo.newBuilder().shareUrl("https://www.yunpan.com/surl_yD7ZU4WreR8")
//                .shareKey("surl_yD7ZU4WreR8")
//                .build()).parseSync();
//        System.out.println(s);
//    }
}
