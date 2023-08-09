package cn.qaiu.lz.common.parser.impl;

import cn.qaiu.lz.common.parser.IPanTool;
import cn.qaiu.lz.common.util.PanExceptionUtils;
import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 蓝奏云解析工具
 *
 * @author QAIU
 * @version 1.0 update 2021/5/16 10:39
 */
public class LzTool implements IPanTool {

    public static final String SHARE_URL_PREFIX = "https://wwwa.lanzoui.com";

    public Future<String> parse(String data, String code) {
        Promise<String> promise = Promise.promise();
        String key = data.indexOf('/') > 0 ? data : SHARE_URL_PREFIX + "/" + data;

        WebClient client = WebClient.create(VertxHolder.getVertxInstance(),
                new WebClientOptions().setFollowRedirects(false));
        client.getAbs(key).send().onSuccess(res -> {
            String html = res.bodyAsString();
            // 匹配iframe
            Pattern compile = Pattern.compile("src=\"(/fn\\?[a-zA-Z\\d_+/=]{16,})\"");
            Matcher matcher = compile.matcher(html);
            if (!matcher.find()) {
                // 没有Iframe说明是加密分享, 匹配sign通过密码请求下载页面
                Pattern compile2 = Pattern.compile("sign=(\\w{16,})");
                Matcher matcher2 = compile2.matcher(html);
                if (!matcher2.find()) {
                    promise.fail(key + ": sign正则匹配失败, 可能分享已失效: " + html);
                    return;
                }
                String sign = matcher2.group(1);
                getDownURL(promise, code, key, client, sign);
                return;
            }
            String iframePath = matcher.group(1);
            client.getAbs(SHARE_URL_PREFIX + iframePath).send().onSuccess(res2 -> {
                String html2 = res2.bodyAsString();
                System.out.println(html);
                Matcher matcher2 = Pattern.compile("'sign'\s*:\s*'(\\w+)'").matcher(html2);
                if (!matcher2.find()) {
                    promise.fail(SHARE_URL_PREFIX + iframePath + " -> " + key + ": sign正则匹配失败, 可能分享已失效: " + html2);
                    return;
                }
                String sign = matcher2.group(1);
                getDownURL(promise, code, key, client, sign);
            }).onFailure(t -> promise.fail(PanExceptionUtils.fillRunTimeException("Lz", key, t)));
        }).onFailure(t -> promise.fail(PanExceptionUtils.fillRunTimeException("Lz", key, t)));
        return promise.future();
    }

    private void getDownURL(Promise<String> promise, String code, String key, WebClient client, String sign) {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        var userAgent2 = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, " +
                "like " +
                "Gecko) Chrome/111.0.0.0 Mobile Safari/537.36";
        headers.set("User-Agent", userAgent2);
        headers.set("referer", key);
        headers.set("sec-ch-ua-platform", "Android");
        headers.set("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
        headers.set("sec-ch-ua-mobile", "sec-ch-ua-mobile");

        client.postAbs(SHARE_URL_PREFIX + "/ajaxm.php").putHeaders(headers).sendForm(MultiMap
                .caseInsensitiveMultiMap()
                .set("action", "downprocess")
                .set("sign", sign).set("p", code)).onSuccess(res2 -> {
            JsonObject urlJson = res2.bodyAsJsonObject();
            if (urlJson.getInteger("zt") != 1) {
                promise.fail(urlJson.getString("inf"));
                return;
            }
            String downUrl = urlJson.getString("dom") + "/file/" + urlJson.getString("url");
            client.getAbs(downUrl).putHeaders(headers).send()
                    .onSuccess(res3 -> promise.complete(res3.headers().get("Location")))
                    .onFailure(t -> promise.fail(PanExceptionUtils.fillRunTimeException("Lz", key, t)));
        }).onFailure(t -> promise.fail(PanExceptionUtils.fillRunTimeException("Lz", key, t)));
    }
}
