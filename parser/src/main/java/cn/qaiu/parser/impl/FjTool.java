package cn.qaiu.parser.impl;

import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.AESUtils;
import cn.qaiu.util.CommonUtils;
import cn.qaiu.util.UUIDUtil;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.uritemplate.UriTemplate;

/**
 * 小飞机网盘
 *
 * @version V016_230609
 */
public class FjTool extends PanBase implements IPanTool {

    public static final String SHARE_URL_PREFIX = "https://www.feijix.com/s/";
    public static final String REFERER_URL = "https://share.feijipan.com/";
    public static final String SHARE_URL_PREFIX2 = REFERER_URL + "s/";
    private static final String API_URL_PREFIX = "https://api.feijipan.com/ws/";

    private static final String FIRST_REQUEST_URL = API_URL_PREFIX + "recommend/list?devType=6&devModel=Chrome" +
            "&uuid={uuid}&extra=2&timestamp={ts}&shareId={shareId}&type=0&offset=1&limit=60";
    /// recommend/list?devType=6&devModel=Chrome&uuid={uuid}&extra=2&timestamp={ts}&shareId={shareId}&type=0&offset=1
    // &limit=60

    private static final String SECOND_REQUEST_URL = API_URL_PREFIX + "file/redirect?downloadId={fidEncode}&enable=1" +
            "&devType=6&uuid={uuid}&timestamp={ts}&auth={auth}&shareId={dataKey}";
    // https://api.feijipan.com/ws/file/redirect?downloadId={fidEncode}&enable=1&devType=6&uuid={uuid}&timestamp={ts}&auth={auth}&shareId={dataKey}


    private static final String VIP_REQUEST_URL = API_URL_PREFIX + "/buy/vip/list?devType=6&devModel=Chrome&uuid" +
            "={uuid}&extra=2&timestamp={ts}";
    // https://api.feijipan.com/ws/buy/vip/list?devType=6&devModel=Chrome&uuid=WQAl5yBy1naGudJEILBvE&extra=2&timestamp=E2C53155F6D09417A27981561134CB73

    public FjTool(String key, String pwd) {
        super(key, pwd);
    }

    public Future<String> parse() {
        String dataKey;
        if (key.startsWith(SHARE_URL_PREFIX2)) {
            dataKey = CommonUtils.adaptShortPaths(SHARE_URL_PREFIX2, key);
        } else {
            dataKey = CommonUtils.adaptShortPaths(SHARE_URL_PREFIX, key);
        }

        String shareId = String.valueOf(AESUtils.idEncrypt(dataKey));
        long nowTs = System.currentTimeMillis();
        String tsEncode = AESUtils.encrypt2Hex(Long.toString(nowTs));
        String uuid = UUIDUtil.fjUuid(); // 也可以使用 UUID.randomUUID().toString()

        // 24.5.12 飞机盘 规则修改 需要固定UUID先请求会员接口, 再请求后续接口
        client.postAbs(UriTemplate.of(VIP_REQUEST_URL))
                .setTemplateParam("uuid", uuid)
                .setTemplateParam("ts", tsEncode)
                .send().onSuccess(r0 -> { // 忽略res

                    long nowTs0 = System.currentTimeMillis();
                    String tsEncode0 = AESUtils.encrypt2Hex(Long.toString(nowTs));
                    // 第一次请求 获取文件信息
                    // POST https://api.feijipan.com/ws/recommend/list?devType=6&devModel=Chrome&extra=2&shareId=146731&type=0&offset=1&limit=60
                    client.postAbs(UriTemplate.of(FIRST_REQUEST_URL))
                            .setTemplateParam("shareId", shareId)
                            .setTemplateParam("uuid", uuid)
                            .setTemplateParam("ts", tsEncode0)
                            .send().onSuccess(res -> {
                                JsonObject resJson = res.bodyAsJsonObject();
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
                                long nowTs2 = System.currentTimeMillis();
                                String tsEncode2 = AESUtils.encrypt2Hex(Long.toString(nowTs2));
                                String fidEncode = AESUtils.encrypt2Hex(fileId + "|" + userId);
                                String auth = AESUtils.encrypt2Hex(fileId + "|" + nowTs2);

                                MultiMap headers0 = MultiMap.caseInsensitiveMultiMap();
                                headers0.set("referer", REFERER_URL);
                                // 第二次请求
                                HttpRequest<Buffer> httpRequest =
                                        clientNoRedirects.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
                                        .putHeaders(headers0)
                                        .setTemplateParam("fidEncode", fidEncode)
                                        .setTemplateParam("uuid", uuid)
                                        .setTemplateParam("ts", tsEncode2)
                                        .setTemplateParam("auth", auth)
                                        .setTemplateParam("dataKey", dataKey);
                                System.out.println(httpRequest.toString());
                                httpRequest.send().onSuccess(res2 -> {
                                    MultiMap headers = res2.headers();
                                    if (!headers.contains("Location")) {
                                        fail(SECOND_REQUEST_URL + " 未找到重定向URL: \n" + res.headers());
                                        return;
                                    }
                                    promise.complete(headers.get("Location"));
                                }).onFailure(handleFail(SECOND_REQUEST_URL));
                            }).onFailure(handleFail(FIRST_REQUEST_URL));

                }).onFailure(handleFail(FIRST_REQUEST_URL));

        return promise.future();
    }
}
