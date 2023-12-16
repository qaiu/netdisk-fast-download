package cn.qaiu.parser.impl;

import cn.qaiu.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.client.WebClient;

/**
 * <a href="https://wx.mail.qq.com/">QQ邮箱</a>
 */
public class QQTool extends PanBase implements IPanTool {

    public static final String SHARE_URL_PREFIX = "wx.mail.qq.com/ftn/download?";

    public QQTool(String key, String pwd) {
        super(key, pwd);
    }

    @SuppressWarnings("unchecked")
    public Future<String> parse() {

        WebClient httpClient = this.client;

        // 补全链接
        if (!this.key.startsWith("https://" + SHARE_URL_PREFIX)) {
            if (this.key.startsWith(SHARE_URL_PREFIX)) {
                this.key = "https://" + this.key;
            } else if (this.key.startsWith("func=")) {
                this.key = "https://" + SHARE_URL_PREFIX + this.key;
            } else {
                throw new UnsupportedOperationException("未知分享类型");
            }
        }

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
        httpClient.getAbs(this.key).putHeaders(headers).send().onSuccess(res -> {
            if (res.statusCode() == 200) {
                String html = res.bodyAsString();

                // 匹配文件信息
                String filename = StringUtils.StringCutNot(html, "var filename = \"", "\"");
                String filesize = StringUtils.StringCutNot(html, "var filesize = ", "\n");
                String fileurl  = StringUtils.StringCutNot(html, "var url = \"", "\"");

                if (filename != null && filesize != null && fileurl != null) {
                    // 设置所需HTTP头部
                    headers.set("Referer", "https://" + StringUtils.StringCutNot(this.key, "https://", "/") + "/");
                    headers.set("Host", StringUtils.StringCutNot(fileurl, "https://", "/"));
                    res.headers().forEach((k, v) -> {
                        if (k.toLowerCase().equals("set-cookie")) {
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
        }).onFailure(this.handleFail(this.key));

        return promise.future();
    }

}
