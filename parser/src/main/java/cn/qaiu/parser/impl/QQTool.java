package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo; 
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.StringUtils;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * <a href="https://wx.mail.qq.com/">QQ邮箱</a>
 */
public class QQTool extends PanBase {

    public static final String REDIRECT_URL_TEMP = "https://iwx.mail.qq.com/ftn/download?func=4&key={key}&code={code}";

    public QQTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        // QQ mail 直接替换为302链接 无需请求
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(shareLinkInfo.getShareUrl(), StandardCharsets.UTF_8);
        Map<String, List<String>> prms = queryStringDecoder.parameters();
        if (prms.containsKey("key") && prms.containsKey("code") && prms.containsKey("func")) {
            log.info(prms.get("func").get(0));
            promise.complete(REDIRECT_URL_TEMP.replace("{key}",
                    prms.get("key").get(0)).replace("{code}", prms.get("code").get(0)));
        } else {
            fail("key 不合法");
        }


        // 通过请求URL获取文件信息和直链 暂时不需要
        // getFileInfo(key);

        return promise.future();
    }

    private void getFileInfo(String key) {
        // 设置基础HTTP头部
        var userAgent2 = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, " +
            "like " +
            "Gecko) Chrome/111.0.0.0 Mobile Safari/537.36";

        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.set("User-Agent", userAgent2);
        headers.set("sec-ch-ua-platform", "Android");
        headers.set("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
        headers.set("sec-ch-ua-mobile", "sec-ch-ua-mobile");

        // 获取下载中转站页面
        client.getAbs(key).putHeaders(headers).send().onSuccess(res -> {
            if (res.statusCode() == 200) {
                String html = res.bodyAsString();

                // 匹配文件信息
                String filename = StringUtils.StringCutNot(html, "var filename = \"", "\"");
                String filesize = StringUtils.StringCutNot(html, "var filesize = ", "\n");
                String fileurl  = StringUtils.StringCutNot(html, "var url = \"", "\"");

                if (filename != null && filesize != null && fileurl != null) {
                    // 设置所需HTTP头部
                    headers.set("Referer", "https://" + StringUtils.StringCutNot(key, "https://", "/") + "/");
                    headers.set("Host", StringUtils.StringCutNot(fileurl, "https://", "/"));
                    res.headers().forEach((k, v) -> {
                        if (k.equalsIgnoreCase("set-cookie")) {
                            headers.set("Cookie", "mail5k=" + StringUtils.StringCutNot(v, "mail5k=", ";") + ";");
                        }
                    });

                    // 调试匹配的情况
                    System.out.println("文件名称: " + filename);
                    System.out.println("文件大小: " + filesize);
                    System.out.println("文件直链: " + fileurl);

                    // 提交
                    promise.complete(fileurl.replace("\\x26", "&"));
                } else {
                    this.fail("匹配失败，可能是分享链接的方式已更新");
                }
            } else {
                this.fail("HTTP状态不正确，可能是分享链接的方式已更新");
            }
        }).onFailure(this.handleFail(key));
    }

}
