package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * t.cn 短链生成 //
 */
public class GenShortUrl extends PanBase {

    private static final Logger log = LoggerFactory.getLogger(GenShortUrl.class);

    private static final String COMMENT_URL = "https://www.weibo.com/aj/v6/comment/add";
    private static final String DELETE_COMMENT_URL = "https://www.weibo.com/aj/comment/del";

    private static final String WRAPPER_URL = "https://www.so.com/link?m=ewgUSYiFWXIoTybC3fJH8YoJy8y10iRquo6cazgINwWjTn3HvVJ92TrCJu0PmMUR0RMDfOAucP3wa4G8j64SrhNH9Z0Cr0PEyn9ASuvpkUGmAjjUEGJkO5%2BIDGWVrEkPHsL7UsoKO6%2BlT%2BD6r&ccc=";
    private static final String MID = "5095144728824883";  // 微博的mid

    private static final MultiMap HEADER = HeadersMultiMap.headers()
            .add("Content-Type", "application/x-www-form-urlencoded")
            .add("Referer", "https://www.weibo.com")
            .add("Content-Type", "application/x-www-form-urlencoded")
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36");

    Cookie cookie = new DefaultCookie("SUB", "_2A25KJE5vDeRhGeRJ6lsR9SjJzDuIHXVpWM-nrDV8PUJbkNAbLVPlkW1NUmJm3GjYtRHBsHdMUKafkdTL_YheMEmu");
    public GenShortUrl(ShareLinkInfo build) {
        super(build);
    }

    @Override
    public Future<String> parse() {
        String longUrl = shareLinkInfo.getShareUrl();

        String url = WRAPPER_URL + Base64.getEncoder().encodeToString(longUrl.getBytes());

        String payload = "mid=" + MID + "&content=" + URLEncoder.encode(url, StandardCharsets.UTF_8);
        clientSession.cookieStore().put(cookie);

        clientSession.postAbs(COMMENT_URL)
                .putHeaders(HEADER)
                .sendBuffer(Buffer.buffer(payload))
                .onSuccess(res -> {
                    JsonObject data = asJson(res).getJsonObject("data");
                    if (data.isEmpty()) {
                        fail(asJson(res).getString("msg"));
                        return;
                    }
                    String comment = data.getString("comment");
                    String shortUrl = extractShortUrl(comment);
                    if (shortUrl != null) {
                        log.info("生成的短链：{}", shortUrl);
                        String commentId = extractCommentId(comment);
                        if (commentId != null) {
                            deleteComment(commentId);
                        } else {
                            promise.fail("未能提取评论ID");
                        }
                    } else {
                        promise.fail("未能生成短链");
                    }
                })
                .onFailure(err -> {
                    log.error("添加评论失败", err);
                    promise.fail(err);
                });

        return promise.future();
    }


    private void deleteComment(String commentId) {
        String payload = "mid=" + MID + "&cid=" + commentId;

        clientSession.postAbs(DELETE_COMMENT_URL)
                .putHeaders(HEADER)
                .sendBuffer(Buffer.buffer(payload))
                .onSuccess(res -> {
                    JsonObject responseJson = res.bodyAsJsonObject();
                    if (responseJson.getString("code").equals("100000")) {
                        log.info("评论已删除: {}", commentId);
                    } else {
                        log.error("删除评论失败: {}", responseJson.encode());
                    }
                })
                .onFailure(err -> {
                    log.error("删除评论失败", err);
                });
    }

    private String extractShortUrl(String comment) {
        Pattern pattern = Pattern.compile("(https?)://t.cn/\\w+");
        Matcher matcher = pattern.matcher(comment);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    private String extractCommentId(String comment) {
        Pattern pattern = Pattern.compile("comment_id=\"(\\d+)\"");
        Matcher matcher = pattern.matcher(comment);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static void main(String[] args) {
        // http://t.cn/A6nfZn86
        // http://t.cn/A6nfZn86

        new GenShortUrl(ShareLinkInfo.newBuilder().shareUrl("https://qaiu.top/sdfsdf").build()).parse().onSuccess(
                System.out::println
        );
    }

}
