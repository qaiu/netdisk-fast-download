package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.UriTemplate;

/**
 * 115网盘
 *
 * 需要请求API的UA和请求下载链接的UA保持一致，安卓Chrome需要访问电脑版才能下载
 */
public class P115Tool extends PanBase {
    private static final String API_URL_PREFIX = "https://115cdn.com/webapi/";

    private static final String FIRST_REQUEST_URL = API_URL_PREFIX + "share/snap?share_code={dataKey}&offset=0" +
            "&limit=20&receive_code={dataPwd}&cid=";

    private static final String SECOND_REQUEST_URL = API_URL_PREFIX + "share/skip_login_downurl";


    private static final MultiMap header;

    static {
        header = MultiMap.caseInsensitiveMultiMap();
        header.set("Accept", "application/json, text/plain, */*");
        header.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        header.set("Cache-Control", "no-cache");
        header.set("Connection", "keep-alive");
        header.set("Content-Length", "0");
        header.set("DNT", "1");
        header.set("Host", "115cdn.com");
        header.set("Origin", "https://115cdn.com");
        header.set("Pragma", "no-cache");
        header.set("Referer", "https://115cdn.com");
        header.set("Sec-Fetch-Dest", "empty");
        header.set("Sec-Fetch-Mode", "cors");
        header.set("Sec-Fetch-Site", "cross-site");
        header.set("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"");
        header.set("sec-ch-ua-mobile", "?0");
        header.set("sec-ch-ua-platform", "\"Windows\"");
    }

    public P115Tool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        // 第一次请求 获取文件信息
        client.getAbs(UriTemplate.of(FIRST_REQUEST_URL))
                .putHeaders(header)
                .putHeader("User-Agent", shareLinkInfo.getOtherParam().get("UA").toString())
                .setTemplateParam("dataKey", shareLinkInfo.getShareKey())
                .setTemplateParam("dataPwd", shareLinkInfo.getSharePassword())
                .send().onSuccess(res -> {
                    JsonObject resJson = asJson(res);
                    if (!resJson.getBoolean("state")) {
                        fail(FIRST_REQUEST_URL + " 解析错误: " + resJson);
                        return;
                    }
                    // 文件Id: data.list[0].fid
                    JsonObject fileInfo = resJson.getJsonObject("data").getJsonArray("list").getJsonObject(0);
                    String fileId = fileInfo.getString("fid");

                    // 第二次请求
                    // share_code={dataKey}&receive_code={dataPwd}&file_id={file_id}
                    client.postAbs(SECOND_REQUEST_URL)
                            .putHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                            .putHeader("User-Agent", shareLinkInfo.getOtherParam().get("UA").toString())
                            .sendForm(MultiMap.caseInsensitiveMultiMap()
                                    .set("share_code", shareLinkInfo.getShareKey())
                                    .set("receive_code", shareLinkInfo.getSharePassword())
                                    .set("file_id", fileId))
                            .onSuccess(res2 -> {
                                JsonObject resJson2 = asJson(res2);
                                if (!resJson2.getBoolean("state")) {
                                    fail(FIRST_REQUEST_URL + " 解析错误: " + resJson2);
                                    return;
                                }
                                // data.url.url
                                promise.complete(resJson2.getJsonObject("data").getJsonObject("url").getString("url"));
                    }).onFailure(handleFail(SECOND_REQUEST_URL));
                }).onFailure(handleFail(FIRST_REQUEST_URL));


        return promise.future();
    }
}
