package cn.qaiu.parser.impl;

import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.AESUtils;
import cn.qaiu.util.CommonUtils;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.uritemplate.UriTemplate;

import java.util.UUID;

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

    private static final String FIRST_REQUEST_URL = API_URL_PREFIX + "recommend/list?devType=6&devModel=Chrome&extra" +
            "=2&shareId={shareId}&type=0&offset=1&limit=60";

    private static final String SECOND_REQUEST_URL = API_URL_PREFIX + "file/redirect?downloadId={fidEncode}&enable=1" +
            "&devType=6&uuid={uuid}&timestamp={ts}&auth={auth}";

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

        WebClient client = clientNoRedirects;
        String shareId = String.valueOf(AESUtils.idEncrypt(dataKey));

        // 第一次请求 获取文件信息
        // POST https://api.feijipan.com/ws/recommend/list?devType=6&devModel=Chrome&extra=2&shareId=146731&type=0&offset=1&limit=60
        client.postAbs(UriTemplate.of(FIRST_REQUEST_URL)).setTemplateParam("shareId", shareId).send().onSuccess(res -> {
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
            long nowTs = System.currentTimeMillis();
            String tsEncode = AESUtils.encrypt2Hex(Long.toString(nowTs));
            String uuid = UUID.randomUUID().toString();
            String fidEncode = AESUtils.encrypt2Hex(fileId + "|");
            String auth = AESUtils.encrypt2Hex(fileId + "|" + nowTs);

            MultiMap headers0 = MultiMap.caseInsensitiveMultiMap();
            headers0.set("referer", REFERER_URL);
            // 第二次请求
            client.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
                    .putHeaders(headers0)
                    .setTemplateParam("fidEncode", fidEncode)
                    .setTemplateParam("uuid", uuid)
                    .setTemplateParam("ts", tsEncode)
                    .setTemplateParam("auth", auth).send().onSuccess(res2 -> {
                        MultiMap headers = res2.headers();
                        if (!headers.contains("Location")) {
                            fail(SECOND_REQUEST_URL + " 未找到重定向URL: \n" + res.headers());
                            return;
                        }
                        promise.complete(headers.get("Location"));
                    }).onFailure(handleFail(SECOND_REQUEST_URL));
        }).onFailure(handleFail(FIRST_REQUEST_URL));

        return promise.future();
    }
}
