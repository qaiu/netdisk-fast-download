package cn.qaiu.lz.common.parser.impl;

import cn.qaiu.lz.common.parser.IPanTool;
import cn.qaiu.lz.common.util.CommonUtils;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 奶牛快传解析工具
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * @date 2023/4/21 21:19
 */
@Slf4j
public class CowTool implements IPanTool {

    private static final String API_REQUEST_URL = "https://cowtransfer.com/core/api/transfer/share";
    public static final String SHARE_URL_PREFIX = "https://cowtransfer.com/s/";

    public Future<String> parse(String data, String code) {
        Promise<String> promise = Promise.promise();
        WebClient client = WebClient.create(Vertx.vertx());
        String key = CommonUtils.adaptShortPaths(SHARE_URL_PREFIX, data);
        client.getAbs(API_REQUEST_URL + "?uniqueUrl=" + key).send().onSuccess(res -> {
            JsonObject resJson = res.bodyAsJsonObject();
            if ("success".equals(resJson.getString("message")) && resJson.containsKey("data")) {
                JsonObject dataJson = resJson.getJsonObject("data");
                String guid = dataJson.getString("guid");
                String fileId = dataJson.getJsonObject("firstFile").getString("id");
                String url2 = API_REQUEST_URL + "/download?transferGuid=" + guid + "&fileId=" + fileId;
                client.getAbs(url2).send().onSuccess(res2 -> {
                    JsonObject res2Json = res2.bodyAsJsonObject();
                    if ("success".equals(res2Json.getString("message")) && res2Json.containsKey("data")) {
                        JsonObject data2 = res2Json.getJsonObject("data");
                        String downloadUrl = data2.getString("downloadUrl");
                        if (StringUtils.isNotEmpty(downloadUrl)) {
                            log.info("cow parse success: {}", downloadUrl);
                            promise.complete(downloadUrl);
                        }
                    } else {
                        log.error("cow parse fail: {}; json: {}", url2, res2Json);
                        promise.fail("cow parse fail: " + url2 + "; json:" + res2Json);
                    }
                });
            } else {
                log.error("cow parse fail: {}; json: {}", key, resJson);
                promise.fail("cow parse fail: " + key + "; json:" + resJson);
            }
        });
        return promise.future();
    }

}
