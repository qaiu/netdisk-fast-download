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
 * 蓝奏云优享
 *
 */
public class IzTool extends PanBase implements IPanTool {

    public static final String SHARE_URL_PREFIX = "https://www.ilanzou.com/s/";
    private static final String API_URL_PREFIX = "https://api.ilanzou.com/unproved/";

    private static final String FIRST_REQUEST_URL = API_URL_PREFIX + "recommend/list?devType=6&devModel=Chrome&extra" +
            "=2&shareId={shareId}&type=0&offset=1&limit=60";

    private static final String SECOND_REQUEST_URL = API_URL_PREFIX + "file/redirect?downloadId={fidEncode}&enable=1" +
            "&devType=6&uuid={uuid}&timestamp={ts}&auth={auth}";

    public IzTool(String key, String pwd) {
        super(key, pwd);
    }

    public Future<String> parse() {
        String dataKey = CommonUtils.adaptShortPaths(SHARE_URL_PREFIX, key);

        WebClient client = clientNoRedirects;
        String shareId = String.valueOf(AESUtils.idEncryptIz(dataKey));

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
            String tsEncode = AESUtils.encrypt2HexIz(Long.toString(nowTs));
            String uuid = UUID.randomUUID().toString();
//            String fidEncode = AESUtils.encrypt2HexIz(fileId + "|");
            String fidEncode = AESUtils.encrypt2HexIz(fileId + "|" + userId);
            String auth = AESUtils.encrypt2HexIz(fileId + "|" + nowTs);
            // 第二次请求
            client.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
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
