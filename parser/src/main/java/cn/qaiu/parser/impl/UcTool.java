package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.UriTemplate;

/**
 * UC网盘解析
 */
public class UcTool extends PanBase {
    private static final String API_URL_PREFIX = "https://pc-api.uc.cn/1/clouddrive/";

    public static final String SHARE_URL_PREFIX = "https://fast.uc.cn/s/";

    private static final String FIRST_REQUEST_URL = API_URL_PREFIX + "share/sharepage/token?entry=ft&fr=pc&pr" +
            "=UCBrowser";

    private static final String SECOND_REQUEST_URL = API_URL_PREFIX + "transfer_share/detail?pwd_id={pwd_id}&passcode" +
            "={passcode}&stoken={stoken}";

    private static final String THIRD_REQUEST_URL = API_URL_PREFIX + "file/download?entry=ft&fr=pc&pr=UCBrowser";

    public UcTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        var dataKey = shareLinkInfo.getShareKey();
        var passcode =  shareLinkInfo.getSharePassword();

        var jsonObject = JsonObject.of("share_for_transfer", true);
        jsonObject.put("pwd_id", dataKey);
        jsonObject.put("passcode", passcode);
        // 第一次请求 获取文件信息
        client.postAbs(FIRST_REQUEST_URL).sendJsonObject(jsonObject).onSuccess(res -> {
                    log.debug("第一阶段 {}", res.body());
                    var resJson = res.bodyAsJsonObject();
                    if (resJson.getInteger("code") != 0) {
                        fail(FIRST_REQUEST_URL + " 返回异常: " + resJson);
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
                                    fail(FIRST_REQUEST_URL + " 返回异常: " + resJson2);
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
                                                fail(FIRST_REQUEST_URL + " 返回异常: " + resJson2);
                                                return;
                                            }
                                            promise.complete(resJson3.getJsonArray("data").getJsonObject(0).getString("download_url"));
                                        }).onFailure(handleFail(THIRD_REQUEST_URL));

                            }).onFailure(handleFail(SECOND_REQUEST_URL));
                }
        ).onFailure(handleFail(FIRST_REQUEST_URL));
        return promise.future();
    }

    public static void main(String[] args) {

        // https://dl-uf-zb.pds.uc.cn/l3PNAKfz/64623447/
        // 646b0de6e9f13000c9b14ba182b805312795a82a/
        // 646b0de6717e1bfa5bb44dd2a456f103c5177850?
        // Expires=1737784900&OSSAccessKeyId=LTAI5tJJpWQEfrcKHnd1LqsZ&
        // Signature=oBVV3anhv3tBKanHUcEIsktkB%2BM%3D&x-oss-traffic-limit=503316480
        // &response-content-disposition=attachment%3B%20filename%3DC%2523%2520Shell%2520%2528C%2523%2520Offline%2520Compiler%2529_2.5.16.apks
        // %3Bfilename%2A%3Dutf-8%27%27C%2523%2520Shell%2520%2528C%2523%2520Offline%2520Compiler%2529_2.5.16.apks

        //eyJ4OmF1IjoiLSIsIng6dWQiOiI0LU4tNS0wLTYtTi0zLWZ0LTAtMi1OLU4iLCJ4OnNwIjoiMTAwIiwieDp0b2tlbiI6IjQtZjY0ZmMxMDFjZmQxZGVkNTRkMGM0NmMzYzliMzkyOWYtNS03LTE1MzYxMS1kYWNiMzY2NWJiYWE0ZjVlOWQzNzgwMGVjNjQwMzE2MC0wLTAtMC0wLTQ5YzUzNTE3OGIxOTY0YzhjYzUwYzRlMDk5MTZmYWRhIiwieDp0dGwiOiIxMDgwMCJ9
        //eyJjYWxsYmFja0JvZHlUeXBlIjoiYXBwbGljYXRpb24vanNvbiIsImNhbGxiYWNrU3RhZ2UiOiJiZWZvcmUtZXhlY3V0ZSIsImNhbGxiYWNrRmFpbHVyZUFjdGlvbiI6Imlnbm9yZSIsImNhbGxiYWNrVXJsIjoiaHR0cHM6Ly9hdXRoLWNkbi51Yy5jbi9vdXRlci9vc3MvY2hlY2twbGF5IiwiY2FsbGJhY2tCb2R5Ijoie1wiaG9zdFwiOiR7aHR0cEhlYWRlci5ob3N0fSxcInNpemVcIjoke3NpemV9LFwicmFuZ2VcIjoke2h0dHBIZWFkZXIucmFuZ2V9LFwicmVmZXJlclwiOiR7aHR0cEhlYWRlci5yZWZlcmVyfSxcImNvb2tpZVwiOiR7aHR0cEhlYWRlci5jb29raWV9LFwibWV0aG9kXCI6JHtodHRwSGVhZGVyLm1ldGhvZH0sXCJpcFwiOiR7Y2xpZW50SXB9LFwicG9ydFwiOiR7Y2xpZW50UG9ydH0sXCJvYmplY3RcIjoke29iamVjdH0sXCJzcFwiOiR7eDpzcH0sXCJ1ZFwiOiR7eDp1ZH0sXCJ0b2tlblwiOiR7eDp0b2tlbn0sXCJhdVwiOiR7eDphdX0sXCJ0dGxcIjoke3g6dHRsfSxcImR0X3NwXCI6JHt4OmR0X3NwfSxcImhzcFwiOiR7eDpoc3B9LFwiY2xpZW50X3Rva2VuXCI6JHtxdWVyeVN0cmluZy5jbGllbnRfdG9rZW59fSJ9
        //callback-var {"x:au":"-","x:ud":"4-N-5-0-6-N-3-ft-0-2-N-N","x:sp":"100","x:token":"4-f64fc101cfd1ded54d0c46c3c9b3929f-5-7-153611-dacb3665bbaa4f5e9d37800ec6403160-0-0-0-0-49c535178b1964c8cc50c4e09916fada","x:ttl":"10800"}
        //callback {"callbackBodyType":"application/json","callbackStage":"before-execute","callbackFailureAction":"ignore","callbackUrl":"https://auth-cdn.uc.cn/outer/oss/checkplay","callbackBody":"{\"host\":${httpHeader.host},\"size\":${size},\"range\":${httpHeader.range},\"referer\":${httpHeader.referer},\"cookie\":${httpHeader.cookie},\"method\":${httpHeader.method},\"ip\":${clientIp},\"port\":${clientPort},\"object\":${object},\"sp\":${x:sp},\"ud\":${x:ud},\"token\":${x:token},\"au\":${x:au},\"ttl\":${x:ttl},\"dt_sp\":${x:dt_sp},\"hsp\":${x:hsp},\"client_token\":${queryString.client_token}}"}

        /*
        // callback-var
{
  "x:au": "-",
  "x:ud": "4-N-5-0-6-N-3-ft-0-2-N-N",
  "x:sp": "100",
  "x:token": "4-f64fc101cfd1ded54d0c46c3c9b3929f-5-7-153611-dacb3665bbaa4f5e9d37800ec6403160-0-0-0-0-49c535178b1964c8cc50c4e09916fada",
  "x:ttl": "10800"
}

// callback
{
  "callbackBodyType": "application/json",
  "callbackStage": "before-execute",
  "callbackFailureAction": "ignore",
  "callbackUrl": "https://auth-cdn.uc.cn/outer/oss/checkplay",
  "callbackBody": "{\"host\":${httpHeader.host},\"size\":${size},\"range\":${httpHeader.range},\"referer\":${httpHeader.referer},\"cookie\":${httpHeader.cookie},\"method\":${httpHeader.method},\"ip\":${clientIp},\"port\":${clientPort},\"object\":${object},\"sp\":${x:sp},\"ud\":${x:ud},\"token\":${x:token},\"au\":${x:au},\"ttl\":${x:ttl},\"dt_sp\":${x:dt_sp},\"hsp\":${x:hsp},\"client_token\":${queryString.client_token}}"
}
         */

        new UcTool(ShareLinkInfo.newBuilder().shareUrl("https://fast.uc.cn/s/33197dd53ace4").shareKey("33197dd53ace4").build()).parse().onSuccess(
                System.out::println
        );
    }
}
