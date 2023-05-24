package cn.qaiu.lz.common.util;

import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.uritemplate.UriTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * 移动云空间解析
 */
@Slf4j
public class UcTool {
    private static final String API_URL_PREFIX = "https://pc-api.uc.cn/1/clouddrive/";

    public static final String FULL_URL_PREFIX = "https://fast.uc.cn/s/";

    private static final String FIRST_REQUEST_URL = API_URL_PREFIX + "share/sharepage/token?entry=ft&fr=pc&pr" +
            "=UCBrowser";

    private static final String SECOND_REQUEST_URL = API_URL_PREFIX + "transfer_share/detail?pwd_id={pwd_id}&passcode" +
            "={passcode}&stoken={stoken}";

    private static final String THIRD_REQUEST_URL = API_URL_PREFIX + "file/download?entry=ft&fr=pc&pr=UCBrowser";

    public static Future<String> parse(String data, String code) {
        if (!data.startsWith(FULL_URL_PREFIX)) {
            data = FULL_URL_PREFIX + data;
        }
        var passcode =  (code == null) ? "" : code;
        var dataKey = data.substring(FULL_URL_PREFIX.length());
        Promise<String> promise = Promise.promise();
        var client = WebClient.create(VertxHolder.getVertxInstance());
        var jsonObject = JsonObject.of("share_for_transfer", true);
        jsonObject.put("pwd_id", dataKey);
        jsonObject.put("passcode", passcode);
        // 第一次请求 获取文件信息
        client.postAbs(FIRST_REQUEST_URL).sendJsonObject(jsonObject).onSuccess(res -> {
                    log.debug("第一阶段 {}", res.body());
                    var resJson = res.bodyAsJsonObject();
                    if (resJson.getInteger("code") != 0) {
                        promise.fail(FIRST_REQUEST_URL + " 返回异常: " + resJson);
                        return;
                    }
                    var stoken = resJson.getJsonObject("data").getString("stoken");
                    // 第二次请求
                    client.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
                            .setTemplateParam("pwd_id", dataKey)
                            .setTemplateParam("passcode", passcode)
                            .setTemplateParam("stoken", stoken)
                            .send().onSuccess(res2 -> {
                                log.debug("第二阶段 {}", res2.body());
                                JsonObject resJson2 = res2.bodyAsJsonObject();
                                if (resJson2.getInteger("code") != 0) {
                                    promise.fail(FIRST_REQUEST_URL + " 返回异常: " + resJson2);
                                    return;
                                }
                                // 文件信息
                                var info = resJson2.getJsonObject("data").getJsonArray("list").getJsonObject(0);
                                // 第二次请求
                                var bodyJson = JsonObject.of()
                                        .put("fids", JsonArray.of(info.getString("fid")))
                                        .put("pwd_id", dataKey)
                                        .put("stoken", stoken)
                                        .put("fids_token", JsonArray.of(info.getString("share_fid_token")));
                                client.postAbs(THIRD_REQUEST_URL).sendJsonObject(bodyJson)
                                        .onSuccess(res3 -> {
                                            log.debug("第三阶段 {}", res3.body());
                                            var resJson3 = res3.bodyAsJsonObject();
                                            if (resJson3.getInteger("code") != 0) {
                                                promise.fail(FIRST_REQUEST_URL + " 返回异常: " + resJson2);
                                                return;
                                            }
                                            promise.complete(resJson3.getJsonArray("data").getJsonObject(0).getString("download_url"));
                                        })
                                        .onFailure(t -> promise
                                                .fail(new RuntimeException("解析异常: ", t.fillInStackTrace())));

                            }).onFailure(t -> promise.fail(new RuntimeException("解析异常: ", t.fillInStackTrace())));
                }
        ).onFailure(t -> promise.fail(new RuntimeException("解析异常: key = " + dataKey, t.fillInStackTrace())));
        return promise.future();
    }
}
