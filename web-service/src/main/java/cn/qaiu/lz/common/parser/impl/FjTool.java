package cn.qaiu.lz.common.parser.impl;

import cn.qaiu.lz.common.parser.IPanTool;
import cn.qaiu.lz.common.util.AESUtils;
import cn.qaiu.lz.common.util.CommonUtils;
import cn.qaiu.lz.common.util.PanExceptionUtils;
import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.uritemplate.UriTemplate;

import java.util.UUID;

/**
 * 小飞机网盘
 *
 * @version V016_230609
 */
public class FjTool implements IPanTool {

    public static final String SHARE_URL_PREFIX = "https://www.feijix.com/s/";
    private static final String API_URL_PREFIX = "https://api.feijipan.com/ws/";

    private static final String FIRST_REQUEST_URL = API_URL_PREFIX + "recommend/list?devType=6&devModel=Chrome&extra" +
            "=2&shareId={shareId}&type=0&offset=1&limit=60";

    private static final String SECOND_REQUEST_URL = API_URL_PREFIX + "file/redirect?downloadId={fidEncode}&enable=1" +
            "&devType=6&uuid={uuid}&timestamp={ts}&auth={auth}";

    public Future<String> parse(String data, String code) {
        String dataKey = CommonUtils.adaptShortPaths(SHARE_URL_PREFIX, data);

        Promise<String> promise = Promise.promise();
        WebClient client = WebClient.create(VertxHolder.getVertxInstance(),
                new WebClientOptions().setFollowRedirects(false));
        String shareId = String.valueOf(AESUtils.idEncrypt(dataKey));

        // 第一次请求 获取文件信息
        // POST https://api.feijipan.com/ws/recommend/list?devType=6&devModel=Chrome&extra=2&shareId=146731&type=0&offset=1&limit=60
        client.postAbs(UriTemplate.of(FIRST_REQUEST_URL)).setTemplateParam("shareId", shareId).send().onSuccess(res -> {
            JsonObject resJson = res.bodyAsJsonObject();
            if (resJson.getInteger("code") != 200) {
                promise.fail(FIRST_REQUEST_URL + " 返回异常: " + resJson);
                return;
            }
            if (resJson.getJsonArray("list").size() == 0) {
                promise.fail(FIRST_REQUEST_URL + " 解析文件列表为空: " + resJson);
                return;
            }
            // 文件Id
            String fileId = resJson.getJsonArray("list").getJsonObject(0).getString("fileIds");
            // 其他参数
            long nowTs = System.currentTimeMillis();
            String tsEncode = AESUtils.encrypt2Hex(Long.toString(nowTs));
            String uuid = UUID.randomUUID().toString();
            String fidEncode = AESUtils.encrypt2Hex(fileId + "|");
            String auth = AESUtils.encrypt2Hex(fileId + "|" + nowTs);
            // 第二次请求
            client.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
                    .setTemplateParam("fidEncode", fidEncode)
                    .setTemplateParam("uuid", uuid)
                    .setTemplateParam("ts", tsEncode)
                    .setTemplateParam("auth", auth).send().onSuccess(res2 -> {
                        MultiMap headers = res2.headers();
                        if (!headers.contains("Location")) {
                            promise.fail(SECOND_REQUEST_URL + " 未找到重定向URL: \n" + res.headers());
                            return;
                        }
                        promise.complete(headers.get("Location"));
                    }).onFailure(t -> promise.fail(PanExceptionUtils.fillRunTimeException("Fj", dataKey, t)));
        }).onFailure(t -> promise.fail(PanExceptionUtils.fillRunTimeException("Fj", dataKey, t)));

        return promise.future();
    }
}
