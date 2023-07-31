package cn.qaiu.lz.common.parser.impl;

import cn.qaiu.lz.common.parser.IPanTool;
import cn.qaiu.lz.common.util.CommonUtils;
import cn.qaiu.lz.common.util.JsExecUtils;
import cn.qaiu.lz.common.util.PanExceptionUtils;
import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.uritemplate.UriTemplate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.ScriptException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 123网盘
 */
@Slf4j
public class YeTool implements IPanTool {
    public static final String SHARE_URL_PREFIX = "https://www.123pan.com/s/";
    public static final String FIRST_REQUEST_URL = SHARE_URL_PREFIX + "{key}.html";
/*
    private static final String GET_FILE_INFO_URL = "https://www.123pan.com/a/api/share/get?limit=100&next=1&orderBy" +
            "=file_name&orderDirection=asc&shareKey={shareKey}&SharePwd={pwd}&ParentFileId=0&Page=1&event" +
            "=homeListFile&operateType=1";
    private static final String GET_FILE_INFO_URL="https://www.123pan
    .com/b/api/share/get?limit=100&next=1&orderBy=file_name&orderDirection=asc" +
            "&shareKey={shareKey}&SharePwd={pwd}&ParentFileId=0&Page=1&event=homeListFile&operateType=1&auth-key
            ={authKey}";
*/

    private static final String GET_FILE_INFO_URL = "https://www.123pan.com/b/api/share/get?limit=100&next=1&orderBy" +
            "=file_name&orderDirection=asc" +
            "&shareKey={shareKey}&SharePwd={pwd}&ParentFileId=0&Page=1&event=homeListFile&operateType=1";
    private static final String DOWNLOAD_API_URL = "https://www.123pan.com/b/api/share/download/info?{authK}={authV}";

    public Future<String> parse(String data, String code) {

        String dataKey = CommonUtils.adaptShortPaths(SHARE_URL_PREFIX, data);
        Promise<String> promise = Promise.promise();
        WebClient client = WebClient.create(VertxHolder.getVertxInstance());

        client.getAbs(UriTemplate.of(FIRST_REQUEST_URL)).setTemplateParam("key", dataKey).send().onSuccess(res -> {

            String html = res.bodyAsString();
            Pattern compile = Pattern.compile("window.g_initialProps\\s*=\\s*(.*);");
            Matcher matcher = compile.matcher(html);

            if (!matcher.find()) {
                promise.fail(html + "\n Ye: " + dataKey + " 正则匹配失败");
                return;
            }
            String fileInfoString = matcher.group(1);
            JsonObject fileInfoJson = new JsonObject(fileInfoString);
            JsonObject resJson = fileInfoJson.getJsonObject("res");
            JsonObject resListJson = fileInfoJson.getJsonObject("reslist");

            if (resJson == null || resJson.getInteger("code") != 0) {
                promise.fail(dataKey + " 解析到异常JSON: " + resJson);
                return;
            }
            String shareKey = resJson.getJsonObject("data").getString("ShareKey");
            if (resListJson == null || resListJson.getInteger("code") != 0) {
                // 加密分享
                if (StringUtils.isNotEmpty(code)) {
                    client.getAbs(UriTemplate.of(GET_FILE_INFO_URL))
                            .setTemplateParam("shareKey", shareKey)
                            .setTemplateParam("pwd", code)
                            // .setTemplateParam("authKey", AESUtils.getAuthKey("/b/api/share/get"))
                            .putHeader("Platform", "web")
                            .putHeader("App-Version", "3")
                            .send().onSuccess(res2 -> {
                                JsonObject infoJson = res2.bodyAsJsonObject();
                                if (infoJson.getInteger("code") != 0) {
                                    promise.fail("Ye: " + dataKey + " 状态码异常" + infoJson);
                                    return;
                                }
                                JsonObject getFileInfoJson =
                                        infoJson.getJsonObject("data").getJsonArray("InfoList").getJsonObject(0);
                                getFileInfoJson.put("ShareKey", shareKey);
                                getDownUrl(promise, client, getFileInfoJson);
                            }).onFailure(t -> promise.fail(PanExceptionUtils.fillRunTimeException("Ye", dataKey, t)));
                } else {
                    promise.fail(dataKey + " 该分享需要密码");
                }
                return;
            }

            JsonObject reqBodyJson = resListJson.getJsonObject("data").getJsonArray("InfoList").getJsonObject(0);
            reqBodyJson.put("ShareKey", shareKey);
            getDownUrl(promise, client, reqBodyJson);
        }).onFailure(t -> promise.fail(PanExceptionUtils.fillRunTimeException("Ye", dataKey, t)));

        return promise.future();
    }

    private static void getDownUrl(Promise<String> promise, WebClient client, JsonObject reqBodyJson) {
        log.info(reqBodyJson.encodePrettily());
        JsonObject jsonObject = new JsonObject();
        // {"ShareKey":"iaKtVv-6OECd","FileID":2193732,"S3keyFlag":"1811834632-0","Size":4203111,
        // "Etag":"69c94adbc0b9190cf23c4e958d8c7c53"}
        jsonObject.put("ShareKey", reqBodyJson.getString("ShareKey"));
        jsonObject.put("FileID", reqBodyJson.getInteger("FileId"));
        jsonObject.put("S3keyFlag", reqBodyJson.getString("S3KeyFlag"));
        jsonObject.put("Size", reqBodyJson.getInteger("Size"));
        jsonObject.put("Etag", reqBodyJson.getString("Etag"));

        // 调用JS文件获取签名
        ScriptObjectMirror getSign;
        try {
            getSign = JsExecUtils.executeJs("getSign", "/b/api/share/download/info");
        } catch (ScriptException | IOException | NoSuchMethodException e) {
            promise.fail(e);
            return;
        }
        if (getSign == null) {
            promise.fail(ArrayUtils.toString(getSign));
            return;
        }
        log.info("ye getSign: {}={}", getSign.get("0").toString(), getSign.get("1").toString());

        client.postAbs(UriTemplate.of(DOWNLOAD_API_URL))
                .setTemplateParam("authK", getSign.get("0").toString())
                .setTemplateParam("authV", getSign.get("1").toString())
                .putHeader("Platform", "web")
                .putHeader("App-Version", "3")
                .sendJsonObject(jsonObject).onSuccess(res2 -> {
                    JsonObject downURLJson = res2.bodyAsJsonObject();

                    try {
                        if (downURLJson.getInteger("code") != 0) {
                            promise.fail("Ye: downURLJson返回值异常->" + downURLJson);
                            return;
                        }
                    } catch (Exception ignored) {
                        promise.fail("Ye: downURLJson格式异常->" + downURLJson);
                        return;
                    }
                    String downURL = downURLJson.getJsonObject("data").getString("DownloadURL");
                    try {
                        Map<String, String> urlParams = CommonUtils.getURLParams(downURL);
                        String params = urlParams.get("params");
                        byte[] decodeByte = Base64.getDecoder().decode(params);
                        String downUrl2 = new String(decodeByte);

                        // 获取直链
                        client.getAbs(downUrl2).send().onSuccess(res3 -> {
                            JsonObject res3Json = res3.bodyAsJsonObject();
                            try {
                                if (res3Json.getInteger("code") != 0) {
                                    promise.fail("Ye: downUrl2返回值异常->" + res3Json);
                                    return;
                                }
                            } catch (Exception ignored) {
                                promise.fail("Ye: downUrl2格式异常->" + downURLJson);
                                return;
                            }

                            promise.complete(res3Json.getJsonObject("data").getString("redirect_url"));

                        }).onFailure(t -> promise.fail(PanExceptionUtils.fillRunTimeException("Ye",
                                reqBodyJson.encodePrettily(), t)));

                    } catch (MalformedURLException e) {
                        promise.fail("urlParams解析异常" + e.getMessage());
                    }
                }).onFailure(t -> promise.fail(PanExceptionUtils.fillRunTimeException("Ye",
                        reqBodyJson.encodePrettily(), t)));
    }
}
