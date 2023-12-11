package cn.qaiu.parser.impl;

import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.client.WebClient;

public class QQTool extends PanBase implements IPanTool {

    public static final String SHARE_URL_PREFIX = "wx.mail.qq.com/ftn/download?";

    static String test = "";

    public QQTool(String key, String pwd) {
        super(key, pwd);
    }

    @SuppressWarnings("unchecked")
    public Future<String> parse() {

        WebClient httpClient = this.client;

        // 补全链接
        if (!this.key.startsWith("https://" + SHARE_URL_PREFIX))
        {
            if (this.key.startsWith(SHARE_URL_PREFIX))
            {
                this.key = "https://" + this.key;
            }
            else if (this.key.startsWith("func="))
            {
                this.key = "https://" + SHARE_URL_PREFIX + this.key;
            }
            else
            {
                // fail("未知分享链接: " + this.key);
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
            if (res.statusCode() == 200)
            {
                String html = res.bodyAsString();

                // 匹配文件信息
                String filename = StringCutNot(html, "var filename = \"", "\"");
                String filesize = StringCutNot(html, "var filesize = ", "\n");
                String url      = StringCutNot(html, "var url = \"", "\"");

                if (filename != null && filesize != null && url != null)
                {
                    // 设置所需HTTP头部
                    headers.set("Referer", "https://" + StringCutNot(this.key, "https://", "/") + "/");
                    headers.set("Host", StringCutNot(url, "https://", "/"));
                    res.headers().forEach((k, v) -> {
                        if (k.toLowerCase().equals("set-cookie"))
                        {
                            test = StringCutNot(v, "mail5k=", ";");
                            headers.set("Cookie", "mail5k=" + StringCutNot(v, "mail5k=", ";") + ";");
                        }
                    });

                    // 调试匹配的情况
                    System.out.println("文件名称: " + filename);
                    System.out.println("文件大小: " + filesize);
                    System.out.println("文件直链: " + url);
                    System.out.println("mail5k= "  + test);

                    // 访问直链
                    httpClient.getAbs(url).putHeaders(headers).send().onSuccess(res2 -> {
                            // 调试获取文件内容
                            System.out.println("文件内容: " + res2.bodyAsString());

                            // 提交
                            promise.complete(url);
                        }).onFailure(this.handleFail(this.key));

                }
                else
                {
                    this.fail("匹配失败，可能是分享链接的方式已更新");
                }
            }
            else
            {
                this.fail("HTTP状态不正确，可能是分享链接的方式已更新");
            }
        }).onFailure(this.handleFail(this.key));

        return promise.future();
    }

    // 非贪婪截断匹配
    private String StringCutNot(final String strtarget, final String strstart, final String strend)
    {
        char[] target = strtarget.toCharArray();
        char[] start  = strstart.toCharArray();
        char[] end    = strend.toCharArray();

        int startIdx  = -1;
        int endIdx    = -1;
        int targetLen = target.length;
        int startLen  = start.length;
        int endLen    = end.length;

        for (int i = 0; i <= targetLen - startLen; i++)
        {
            boolean match = true;
            for (int j = 0; j < startLen; j++)
            {
                if (target[i + j] != start[j])
                {
                    match = false;
                    break;
                }
            }
            if (match)
            {
                startIdx = i;
                break;
            }
        }

        if (startIdx == -1)
        {
            return null;
        }

        for (int i = startIdx + startLen; i <= targetLen - endLen; i++)
        {
            boolean match = true;
            for (int j = 0; j < endLen; j++)
            {
                if (target[i + j] != end[j])
                {
                    match = false;
                    break;
                }
            }
            if (match)
            {
                endIdx = i;
                break;
            }
        }

        if (endIdx == -1)
        {
            return null;
        }

        if (startIdx + startLen < endIdx)
        {
            StringBuilder strbuilder = new StringBuilder();

            for (int i = startIdx + startLen; i < endIdx; i++)
            {
                strbuilder.append(target[i]);
            }

            return strbuilder.toString();
        }

        return null;
    }

}
