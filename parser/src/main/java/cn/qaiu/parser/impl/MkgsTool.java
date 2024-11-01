package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.UriTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 酷狗音乐分享
 * <a href="https://t1.kugou.com/song.html?id=2bi8Fe9CSV3">分享链接1</a>
 * <a href="https://www.kugou.com/share/2bi8Fe9CSV3.html?id=2bi8Fe9CSV3#6ed9gna4">分享链接2</a>
 * <a href="https://www.kugou.com/share/2bi8Fe9CSV3.html">分享链接3</a>
 * <a href="https://www.kugou.com/mixsong/8odv4ef8.html">歌曲链接1</a>
 */
public class MkgsTool extends PanBase {

    public static final String API_URL = "https://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash={hash}";

    private static final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    static {
        // 设置 User-Agent
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0");

        // 设置 Accept
        headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");

        // 设置 If-Modified-Since
        headers.set("If-Modified-Since", "Mon, 21 Oct 2024 08:45:50 GMT");

        // 设置 Priority
        headers.set("Priority", "u=0, i");

        // 设置 Sec-CH-UA
        headers.set("Sec-CH-UA", "\"Microsoft Edge\";v=\"129\", \"Not=A?Brand\";v=\"8\", \"Chromium\";v=\"129\"");

        // 设置 Sec-CH-UA-Mobile
        headers.set("Sec-CH-UA-Mobile", "?0");

        // 设置 Sec-CH-UA-Platform
        headers.set("Sec-CH-UA-Platform", "\"Windows\"");

        // 设置 Sec-Fetch-Dest
        headers.set("Sec-Fetch-Dest", "document");

        // 设置 Sec-Fetch-Mode
        headers.set("Sec-Fetch-Mode", "navigate");

        // 设置 Sec-Fetch-Site
        headers.set("Sec-Fetch-Site", "none");

        // 设置 Sec-Fetch-User
        headers.set("Sec-Fetch-User", "?1");

        // 设置 Upgrade-Insecure-Requests
        headers.set("Upgrade-Insecure-Requests", "1");
    };

    public MkgsTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        String shareUrl = shareLinkInfo.getStandardUrl();
        // String shareUrl = "https://t1.kugou.com/song.html?id=2bi8Fe9CSV3";
        clientNoRedirects.getAbs(shareUrl).send().onSuccess(res -> {
            String locationURL = res.headers().get("Location");
            downUrl(locationURL);
        }).onFailure(handleFail(shareUrl));

        return promise.future();
    }

    protected void downUrl(String locationURL) {
        client.getAbs(locationURL).putHeaders(headers).send().onSuccess(res2->{
            String body = res2.bodyAsString();
            // 正则表达式匹配 hash 字段
            String regex = "\"hash\"\s*:\s*\"([A-F0-9]+)\"";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(body);

            // 查找并输出 hash 字段的值
            if (matcher.find()) {
                String hashValue = matcher.group(1);  // 获取第一个捕获组
                System.out.println(hashValue);
                client.getAbs(UriTemplate.of(API_URL)).setTemplateParam("hash", hashValue).send().onSuccess(res3 -> {
                    JsonObject jsonObject = asJson(res3);
                    System.out.println(jsonObject.encodePrettily());
                    if (jsonObject.containsKey("url")) {
                        promise.complete(jsonObject.getString("url"));
                    } else {
                        fail("下载链接不存在");
                    }
                }).onFailure(handleFail(API_URL.replace("{hash}", hashValue)));
            } else {
                fail("歌曲hash匹配失败, 可能分享已失效");
            }
        }).onFailure(handleFail(locationURL));
    }

    public static class MkgTool extends MkgsTool {

        public MkgTool(ShareLinkInfo shareLinkInfo) {
            super(shareLinkInfo);
        }

        @Override
        public Future<String> parse() {
            downUrl(shareLinkInfo.getStandardUrl());
            return promise.future();
        }

        ;
    }

    public static class Mkgs2Tool extends MkgTool {
        public Mkgs2Tool(ShareLinkInfo shareLinkInfo) {
            super(shareLinkInfo);
        }
    }
}
