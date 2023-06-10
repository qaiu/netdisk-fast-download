package cn.qaiu.lz.common.util;

import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.uritemplate.UriTemplate;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 123网盘
 */
public class YeTool {
    public static final String SHARE_URL_PREFIX = "https://www.123pan.com/s/";
    public static final String FIRST_REQUEST_URL = SHARE_URL_PREFIX + "{key}.html";
    private static final String GET_FILE_INFO_URL = "https://www.123pan.com/a/api/share/get?limit=100&next=1&orderBy" +
            "=file_name&orderDirection=asc&shareKey={shareKey}&SharePwd={pwd}&ParentFileId=0&Page=1&event" +
            "=homeListFile&operateType=1";

    public static Future<String> parse(String data, String code) {

        String dataKey = CommonUtils.adaptShortPaths(SHARE_URL_PREFIX, data);
        Promise<String> promise = Promise.promise();
        WebClient client = WebClient.create(VertxHolder.getVertxInstance());

        client.getAbs(UriTemplate.of(FIRST_REQUEST_URL)).setTemplateParam("key", dataKey).send().onSuccess(res -> {

            String html = res.bodyAsString();
            Pattern compile = Pattern.compile("window.g_initialProps\\s*=\\s*(.*);");
            Matcher matcher = compile.matcher(html);

            if (!matcher.find()) {
                System.out.println("err");
                return;
            }
            String fileInfoString = matcher.group(1);
            JsonObject fileInfoJson = new JsonObject(fileInfoString);
            JsonObject resJson = fileInfoJson.getJsonObject("res");
            JsonObject resListJson = fileInfoJson.getJsonObject("reslist");

            if (resJson == null || resJson.getInteger("code") != 0) {
                promise.fail(dataKey + " 解析到异常JSON: "+resJson);
                return;
            }
            String shareKey = resJson.getJsonObject("data").getString("ShareKey");
            if (resListJson == null || resListJson.getInteger("code") != 0) {
                // 加密分享
                if (StringUtils.isNotEmpty(code)) {
                    client.getAbs(UriTemplate.of(GET_FILE_INFO_URL))
                            .setTemplateParam("shareKey", shareKey)
                            .setTemplateParam("pwd", code)
                            .send().onSuccess(res2 -> {
                                JsonObject infoJson = res2.bodyAsJsonObject();
                                if (infoJson.getInteger("code") != 0) {
                                    return;
                                }
                                JsonObject getFileInfoJson =
                                        infoJson.getJsonObject("data").getJsonArray("InfoList").getJsonObject(0);
                                getFileInfoJson.put("ShareKey", shareKey);
                                getDownUrl(promise, client, getFileInfoJson);
                            });
                } else {
                    promise.fail(dataKey + " 该分享需要密码");
                }
                return;
            }

            JsonObject reqBodyJson = resListJson.getJsonObject("data").getJsonArray("InfoList").getJsonObject(0);
            reqBodyJson.put("ShareKey", shareKey);
            getDownUrl(promise, client, reqBodyJson);
        });

        return promise.future();
    }

    private static void getDownUrl(Promise<String> promise, WebClient client, JsonObject reqBodyJson) {
        System.out.println(reqBodyJson);
        client.postAbs("https://www.123pan.com/a/api/share/download/info").sendJsonObject(reqBodyJson).onSuccess(res2 -> {
            JsonObject downURLJson = res2.bodyAsJsonObject();
            System.out.println(downURLJson);
            if (downURLJson.getInteger("code") != 0) {
                return;
            }
            String downURL = downURLJson.getJsonObject("data").getString("DownloadURL");
            try {
                Map<String, String> urlParams = CommonUtils.getURLParams(downURL);
                String params = urlParams.get("params");
                byte[] decodeByte = Base64.getDecoder().decode(params);
                promise.complete(new String(decodeByte));
            } catch (MalformedURLException e) {
                promise.fail("urlParams解析异常" + e.getMessage());
            }
        });
    }
}
