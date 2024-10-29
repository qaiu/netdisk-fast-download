package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * t.cn 短链生成
 */
public class GenShortUrl extends PanBase {

    private static final Logger log = LoggerFactory.getLogger(GenShortUrl.class);

    private static final String COMMENT_URL = "https://www.weibo.com/aj/v6/comment/add";
    private static final String DELETE_COMMENT_URL = "https://www.weibo.com/aj/comment/del";
    private static final String MID = "5094736413852129";  // 微博的mid
    public GenShortUrl(ShareLinkInfo build) {
        super(build);
    }

    @Override
    public Future<String> parse() {
        String longUrl = shareLinkInfo.getStandardUrl();
        return addComment(longUrl)
                .compose(commentId -> deleteComment(commentId).map(v -> longUrl));
    }

    private Future<String> addComment(String longUrl) {
        Promise<String> promise = Promise.promise();

        String payload = "mid=" + MID + "&content=" + longUrl;

        clientSession.postAbs(COMMENT_URL)
                .putHeader("Content-Type", "application/x-www-form-urlencoded")
                .sendBuffer(Buffer.buffer(payload))
                .onSuccess(res -> {
                    String shortUrl = extractShortUrl(res);
                    if (shortUrl != null) {
                        log.info("生成的短链：{}", shortUrl);
                        String commentId = extractCommentId(res);
                        if (commentId != null) {
                            promise.complete(commentId);
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

    private Future<Void> deleteComment(String commentId) {
        Promise<Void> promise = Promise.promise();

        String payload = "mid=" + MID + "&cid=" + commentId;

        clientSession.postAbs(DELETE_COMMENT_URL)
                .putHeader("Content-Type", "application/x-www-form-urlencoded")
                .sendBuffer(Buffer.buffer(payload))
                .onSuccess(res -> {
                    JsonObject responseJson = res.bodyAsJsonObject();
                    if (responseJson.getString("code").equals("100000")) {
                        log.info("评论已删除: {}", commentId);
                        promise.complete();
                    } else {
                        promise.fail("删除评论失败，返回码：" + responseJson.getString("code"));
                    }
                })
                .onFailure(err -> {
                    log.error("删除评论失败", err);
                    promise.fail(err);
                });

        return promise.future();
    }

    private String extractShortUrl(HttpResponse<Buffer> response) {
        Pattern pattern = Pattern.compile("(https?)://t.cn/\\w+");
        Matcher matcher = pattern.matcher(response.bodyAsString());
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    private String extractCommentId(HttpResponse<Buffer> response) {
        Pattern pattern = Pattern.compile("comment_id=\"(\\d+)\"");
        Matcher matcher = pattern.matcher(response.bodyAsString());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static void main(String[] args) {
        new GenShortUrl(ShareLinkInfo.newBuilder().build());
    }

}
