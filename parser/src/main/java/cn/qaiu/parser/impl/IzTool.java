package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.AESUtils;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.UriTemplate;

import java.util.UUID;

/**
 * 蓝奏云优享
 *
 */
public class IzTool extends PanBase {

    private static final String API_URL_PREFIX = "https://api.ilanzou.com/unproved/";

    private static final String FIRST_REQUEST_URL = API_URL_PREFIX + "recommend/list?devType=6&devModel=Chrome" +
            "&uuid={uuid}&extra=2&timestamp={ts}&shareId={shareId}&type=0&offset=1&limit=60";

    private static final String SECOND_REQUEST_URL = API_URL_PREFIX + "file/redirect?downloadId={fidEncode}&enable=1" +
            "&devType=6&uuid={uuid}&timestamp={ts}&auth={auth}&shareId={dataKey}";
    // downloadId=x&enable=1&devType=6&uuid=x&timestamp=x&auth=x&shareId=lGFndCM

    private static final String VIP_REQUEST_URL = API_URL_PREFIX + "/buy/vip/list?devType=6&devModel=Chrome&uuid" +
            "={uuid}&extra=2&timestamp={ts}";
    private static final MultiMap header;

    static {
        header = MultiMap.caseInsensitiveMultiMap();
        header.set("Accept", "application/json, text/plain, */*");
        header.set("Accept-Encoding", "gzip, deflate, br, zstd");
        header.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        header.set("Cache-Control", "no-cache");
        header.set("Connection", "keep-alive");
        header.set("Content-Length", "0");
        header.set("DNT", "1");
        header.set("Host", "api.ilanzou.com");
        header.set("Origin", "https://www.ilanzou.com/");
        header.set("Pragma", "no-cache");
        header.set("Referer", "https://www.ilanzou.com/");
        header.set("Sec-Fetch-Dest", "empty");
        header.set("Sec-Fetch-Mode", "cors");
        header.set("Sec-Fetch-Site", "cross-site");
        header.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        header.set("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"");
        header.set("sec-ch-ua-mobile", "?0");
        header.set("sec-ch-ua-platform", "\"Windows\"");
    }

    public IzTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        String shareId = shareLinkInfo.getShareKey();
        long nowTs = System.currentTimeMillis();
        String tsEncode = AESUtils.encrypt2HexIz(Long.toString(nowTs));
        String uuid = UUID.randomUUID().toString();

        // 24.5.12 ilanzou改规则无需计算shareId
        // String shareId = String.valueOf(AESUtils.idEncryptIz(dataKey));

        // 第一次请求 获取文件信息
        // POST https://api.ilanzou.com/ws/recommend/list?devType=6&devModel=Chrome&extra=2&shareId=146731&type=0&offset=1&limit=60

        client.postAbs(UriTemplate.of(VIP_REQUEST_URL))
                .setTemplateParam("uuid", uuid)
                .setTemplateParam("ts", tsEncode)
                .send().onSuccess(r0 -> { // 忽略res
                    // 第一次请求 获取文件信息
                    // POST https://api.feijipan.com/ws/recommend/list?devType=6&devModel=Chrome&extra=2&shareId=146731&type=0&offset=1&limit=60
                    client.postAbs(UriTemplate.of(FIRST_REQUEST_URL))
                            .putHeaders(header)
                            .setTemplateParam("shareId", shareId)
                            .setTemplateParam("uuid", uuid)
                            .setTemplateParam("ts", tsEncode)
                            .send().onSuccess(res -> {
                                JsonObject resJson = asJson(res);
                                if (resJson.getInteger("code") != 200) {
                                    fail(FIRST_REQUEST_URL + " 返回异常: " + resJson);
                                    return;
                                }
                                if (resJson.getJsonArray("list").size() == 0) {
                                    fail(FIRST_REQUEST_URL + " 解析文件列表为空: " + resJson);
                                    return;
                                }
                                // 文件Id
                                JsonObject fileInfo = resJson.getJsonArray("list").getJsonObject(0);
                                String fileId = fileInfo.getString("fileIds");
                                String userId = fileInfo.getString("userId");
                                // 其他参数
                                // String fidEncode = AESUtils.encrypt2HexIz(fileId + "|");
                                String fidEncode = AESUtils.encrypt2HexIz(fileId + "|" + userId);
                                String auth = AESUtils.encrypt2HexIz(fileId + "|" + nowTs);
                                // 第二次请求
                                clientNoRedirects.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
                                        .setTemplateParam("fidEncode", fidEncode)
                                        .setTemplateParam("uuid", uuid)
                                        .setTemplateParam("ts", tsEncode)
                                        .setTemplateParam("auth", auth)
                                        .setTemplateParam("shareId", shareId)
                                        .putHeaders(header).send().onSuccess(res2 -> {
                                            MultiMap headers = res2.headers();
                                            if (!headers.contains("Location")) {
                                                fail(SECOND_REQUEST_URL + " 未找到重定向URL: \n" + res.headers());
                                                return;
                                            }
                                            promise.complete(headers.get("Location"));
                                        }).onFailure(handleFail(SECOND_REQUEST_URL));
                            }).onFailure(handleFail(FIRST_REQUEST_URL));
                });
        return promise.future();
    }
}
