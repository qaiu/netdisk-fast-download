package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.uritemplate.UriTemplate;

/**
 * 微雨云
 */
public class PvyyTool extends PanBase {
    private static final String API_URL_PREFIX1 = "https://www.vyuyun.com/apiv1/share/file/{key}?password={pwd}";
    private static final String API_URL_PREFIX2 = "https://www.vyuyun.com/apiv1/share/getShareDownUrl/{key}/{id}?password={pwd}";

    public PvyyTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        //
        client.getAbs(UriTemplate.of(API_URL_PREFIX1))
                .setTemplateParam("key", shareLinkInfo.getShareKey())
                .setTemplateParam("pwd", shareLinkInfo.getSharePassword())
                .putHeader("referer", "https://www.vyuyun.com")
                .send().onSuccess(res -> {
                    try {
                        String id = asJson(res).getJsonObject("data").getJsonObject("data").getString("id");

                        client.getAbs(UriTemplate.of(API_URL_PREFIX2))
                                .setTemplateParam("key", shareLinkInfo.getShareKey())
                                .setTemplateParam("pwd", shareLinkInfo.getSharePassword())
                                .setTemplateParam("id", id)
                                .putHeader("referer", "https://www.vyuyun.com")
                                .send().onSuccess(res2 -> {
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
