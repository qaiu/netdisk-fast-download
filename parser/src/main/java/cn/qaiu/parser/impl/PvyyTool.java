package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.HeaderUtils;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.uritemplate.UriTemplate;

/**
 * 微雨云
 */
public class PvyyTool extends PanBase {
    private static final String API_URL_PREFIX1 = "https://www.vyuyun.com/apiv1/share/file/{key}?password={pwd}";
    private static final String API_URL_PREFIX2 = "https://www.vyuyun.com/apiv1/share/getShareDownUrl/{key}/{id}?password={pwd}";


    private static final MultiMap header = HeaderUtils.parseHeaders("""
            accept-language: zh-CN,zh;q=0.9,en;q=0.8
            cache-control: no-cache
            dnt: 1
            origin: https://www.vyuyun.com
            pragma: no-cache
            priority: u=1, i
            referer: https://www.vyuyun.com/
            sec-ch-ua: "Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"
            sec-ch-ua-mobile: ?0
            sec-ch-ua-platform: "Windows"
            sec-fetch-dest: empty
            sec-fetch-mode: cors
            sec-fetch-site: same-site
            user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36
            """);

    public PvyyTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        //
        client.getAbs(UriTemplate.of(API_URL_PREFIX1))
                .setTemplateParam("key", shareLinkInfo.getShareKey())
                .setTemplateParam("pwd", shareLinkInfo.getSharePassword())
                .putHeaders(header)
                .send().onSuccess(res -> {
                    try {
                        String id = asJson(res).getJsonObject("data").getJsonObject("data").getString("id");

                        client.getAbs(UriTemplate.of(API_URL_PREFIX2))
                                .setTemplateParam("key", shareLinkInfo.getShareKey())
                                .setTemplateParam("pwd", shareLinkInfo.getSharePassword())
                                .setTemplateParam("id", id)
                                .putHeaders(header).send().onSuccess(res2 -> {
                                    try {
                                        // data->downInfo->url
                                        String url =
                                                asJson(res2).getJsonObject("data").getJsonObject("downInfo").getString("url");
                                        complete(url);
                                    } catch (Exception ignored) {
                                        fail(asJson(res2).encodePrettily());
                                    }
                                });
                    } catch (Exception ignored) {
                        fail(asJson(res).encodePrettily());
                    }
                });

        return future();
    }
}
