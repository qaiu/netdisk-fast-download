package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class QQwTool extends QQTool {

    public QQwTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    @Override
    public Future<String> parse() {
        String k = shareLinkInfo.getShareKey();
        String postBody = "f=json&k=" + URLEncoder.encode(k, StandardCharsets.UTF_8);

        client.request(HttpMethod.POST, 443, "wx.mail.qq.com", "/s")
                .putHeader("Content-Type", "application/x-www-form-urlencoded")
                .putHeader("Accept", "application/json, text/plain, */*")
                .putHeader("Referer", shareLinkInfo.getShareUrl())
                .putHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.0.0")
                .sendBuffer(io.vertx.core.buffer.Buffer.buffer(postBody))
                .onSuccess(res -> {
                    try {
                        JsonObject data = asJson(res);
                        JsonObject head = data.getJsonObject("head");
                        if (head == null || head.getInteger("ret", -1) != 0) {
                            String msg = head != null ? head.getString("msg", "未知错误") : "未知错误";
                            fail("API错误: " + msg);
                            return;
                        }

                        JsonObject body = data.getJsonObject("body");
                        if (body == null) {
                            fail("文件信息为空");
                            return;
                        }

                        String url = body.getString("url");
                        String fn = body.getString("name", "");
                        long size = body.getLong("size", 0L);

                        if (url == null || url.isEmpty()) {
                            fail("分享链接解析失败, 可能是链接失效");
                            return;
                        }

                        FileInfo fileInfo = new FileInfo().setFileName(fn).setSize(size);
                        shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);

                        String url302 = url.replace("\\x26", "&");
                        complete(url302);
                    } catch (Exception e) {
                        fail(e, "解析响应失败");
                    }
                }).onFailure(handleFail());

        return promise.future();
    }
}
