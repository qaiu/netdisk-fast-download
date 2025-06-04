package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.CommonUtils;
import cn.qaiu.util.JsExecUtils;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.uritemplate.UriTemplate;
import org.apache.commons.lang3.StringUtils;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import java.net.MalformedURLException;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.qaiu.util.RandomStringGenerator.gen36String;

/**
 * 123网盘
 */
public class YeTool extends PanBase {

    public static final String SHARE_URL_PREFIX = "https://www.123pan.com/s/";
    public static final String FIRST_REQUEST_URL = SHARE_URL_PREFIX + "{key}.html";

    private static final String GET_FILE_INFO_URL = "https://www.123pan.com/a/api/share/get?limit=100&next=1&orderBy" +
            "=file_name&orderDirection=asc" +
            "&shareKey={shareKey}&SharePwd={pwd}&ParentFileId=0&Page=1&event=homeListFile&operateType=1";
    private static final String DOWNLOAD_API_URL = "https://www.123pan.com/a/api/share/download/info?{authK}={authV}";

    private static final String BATCH_DOWNLOAD_API_URL = "https://www.123pan.com/b/api/file/batch_download_share_info?{authK}={authV}";
    private final MultiMap header = MultiMap.caseInsensitiveMultiMap();

    public YeTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
        header.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6");
        header.set("App-Version", "3");
        header.set("Cache-Control", "no-cache");
        header.set("Connection", "keep-alive");
        //header.set("DNT", "1");
        //header.set("Host", "www.123pan.com");
        header.set("LoginUuid", gen36String());
        header.set("Pragma", "no-cache");
        header.set("Referer", shareLinkInfo.getStandardUrl());
        header.set("Sec-Fetch-Dest", "empty");
        header.set("Sec-Fetch-Mode", "cors");
        header.set("Sec-Fetch-Site", "same-origin");
        header.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36 Edg/127.0.0.0");
        header.set("platform", "web");
        header.set("sec-ch-ua", "\"Not)A;Brand\";v=\"99\", \"Microsoft Edge\";v=\"127\", \"Chromium\";v=\"127\"");
        header.set("sec-ch-ua-mobile", "?0");
        header.set("sec-ch-ua-platform", "Windows");
    }

    public Future<String> parse() {

        final String dataKey = shareLinkInfo.getShareKey();
        final String pwd = shareLinkInfo.getSharePassword();

        client.getAbs(UriTemplate.of(FIRST_REQUEST_URL)).setTemplateParam("key", dataKey).send().onSuccess(res -> {

            String html = res.bodyAsString();
            // 判断分享是否已经失效
            if (html.contains("分享链接已失效")) {
                fail("该分享已失效({})已失效", shareLinkInfo.getShareUrl());
                return;
            }

            Pattern compile = Pattern.compile("window.g_initialProps\\s*=\\s*(.*);");
            Matcher matcher = compile.matcher(html);

            if (!matcher.find()) {
                fail("该分享({})文件信息找不到, 可能分享已失效", shareLinkInfo.getShareUrl());
                return;
            }
            String fileInfoString = matcher.group(1);
            JsonObject fileInfoJson = new JsonObject(fileInfoString);
            JsonObject resJson = fileInfoJson.getJsonObject("res");
            JsonObject resListJson = fileInfoJson.getJsonObject("reslist");

            if (resJson == null || resJson.getInteger("code") != 0) {
                fail(dataKey + " 解析到异常JSON: " + resJson);
                return;
            }
            String shareKey = resJson.getJsonObject("data").getString("ShareKey");

            if (resListJson == null || resListJson.getInteger("code") != 0) {
                // 加密分享
                if (StringUtils.isNotEmpty(pwd)) {
                    client.getAbs(UriTemplate.of(GET_FILE_INFO_URL))
                            .setTemplateParam("shareKey", shareKey)
                            .setTemplateParam("pwd", pwd)
                            // .setTemplateParam("authKey", AESUtils.getAuthKey("/a/api/share/get"))
                            .putHeader("Platform", "web")
                            .putHeader("App-Version", "3")
                            .send().onSuccess(res2 -> {
                                JsonObject infoJson = asJson(res2);
                                if (infoJson.getInteger("code") != 0) {
                                    fail("{} 状态码异常 {}", dataKey, infoJson);
                                    return;
                                }

                                JsonObject getFileInfoJson =
                                        infoJson.getJsonObject("data").getJsonArray("InfoList").getJsonObject(0);
                                getFileInfoJson.put("ShareKey", shareKey);

                                // 判断是否为文件夹: data->InfoList->0->Type: 1为文件夹, 0为文件
                                try {
                                    int type = (Integer)JsonPointer.from("/data/InfoList/0/Type").queryJson(infoJson);
                                    if (type == 1) {
                                        getZipDownUrl(client, getFileInfoJson);
                                        return;
                                    }
                                } catch (Exception exception) {
                                    fail("该分享[{}]解析异常: {}", dataKey, exception.getMessage());
                                    return;
                                }

                                getDownUrl(client, getFileInfoJson);
                            }).onFailure(this.handleFail(GET_FILE_INFO_URL));
                } else {
                    fail("该分享[{}]需要密码",dataKey);
                }
                return;
            }

            JsonObject reqBodyJson = resListJson.getJsonObject("data").getJsonArray("InfoList").getJsonObject(0);
            reqBodyJson.put("ShareKey", shareKey);
            if (reqBodyJson.getInteger("Type") == 1) {
                // 文件夹
                getZipDownUrl(client, reqBodyJson);
                return;
            }
            getDownUrl(client, reqBodyJson);
        }).onFailure(this.handleFail(FIRST_REQUEST_URL));

        return promise.future();
    }

    private void getDownUrl(WebClient client, JsonObject reqBodyJson) {
        log.info(reqBodyJson.encodePrettily());
        JsonObject jsonObject = new JsonObject();
        // {"ShareKey":"iaKtVv-6OECd","FileID":2193732,"S3keyFlag":"1811834632-0","Size":4203111,
        // "Etag":"69c94adbc0b9190cf23c4e958d8c7c53"}
        jsonObject.put("ShareKey", reqBodyJson.getString("ShareKey"));
        jsonObject.put("FileID", reqBodyJson.getInteger("FileId"));
        jsonObject.put("S3keyFlag", reqBodyJson.getString("S3KeyFlag"));
        jsonObject.put("Size", reqBodyJson.getLong("Size"));
        jsonObject.put("Etag", reqBodyJson.getString("Etag"));

        // 调用JS文件获取签名
        down(client, jsonObject, DOWNLOAD_API_URL);
    }


    private void getZipDownUrl(WebClient client, JsonObject reqBodyJson) {
        log.info(reqBodyJson.encodePrettily());
        JsonObject jsonObject = new JsonObject();
        // {"ShareKey":"LH3rTd-1ENed","fileIdList":[{"fileId":17525952}]}
        jsonObject.put("ShareKey", reqBodyJson.getString("ShareKey"));
        jsonObject.put("fileIdList", new JsonArray().add(JsonObject.of("fileId", reqBodyJson.getInteger("FileId"))));
        // 调用JS文件获取签名
        down(client, jsonObject, BATCH_DOWNLOAD_API_URL);
    }

    private void down(WebClient client, JsonObject jsonObject, String api) {
        ScriptObjectMirror getSign;
        try {
            getSign = JsExecUtils.executeJs("getSign", "/a/api/share/download/info");
        } catch (Exception e) {
            fail(e, "JS函数执行异常");
            return;
        }
        log.info("ye getSign: {}={}", getSign.get("0").toString(), getSign.get("1").toString());

        client.postAbs(UriTemplate.of(api))
                .setTemplateParam("authK", getSign.get("0").toString())
                .setTemplateParam("authV", getSign.get("1").toString())
                .putHeader("Platform", "web")
                .putHeader("App-Version", "3")
                .sendJsonObject(jsonObject).onSuccess(res2 -> {
                    JsonObject downURLJson = asJson(res2);

                    try {
                        if (downURLJson.getInteger("code") != 0) {
                            fail("Ye: downURLJson返回值异常->" + downURLJson);
                            return;
                        }
                    } catch (Exception ignored) {
                        fail("Ye: downURLJson格式异常->" + downURLJson);
                        return;
                    }
                    String downURL = downURLJson.getJsonObject("data")
                            .getString(api.contains("batch_download_share_info")? "DownloadUrl" : "DownloadURL");
                    try {
                        Map<String, String> urlParams = CommonUtils.getURLParams(downURL);
                        String params = urlParams.get("params");
                        byte[] decodeByte = Base64.getDecoder().decode(params);
                        String downUrl2 = new String(decodeByte);

                        // 获取直链
                        client.getAbs(downUrl2).send().onSuccess(res3 -> {
                            JsonObject res3Json = asJson(res3);
                            try {
                                if (res3Json.getInteger("code") != 0) {
                                    fail("Ye: downUrl2返回值异常->" + res3Json);
                                    return;
                                }
                            } catch (Exception ignored) {
                                fail("Ye: downUrl2格式异常->" + downURLJson);
                                return;
                            }

                            promise.complete(res3Json.getJsonObject("data").getString("redirect_url"));

                        }).onFailure(this.handleFail("获取直链失败"));

                    } catch (MalformedURLException e) {
                        fail("urlParams解析异常" + e.getMessage());
                    }
                }).onFailure(this.handleFail(DOWNLOAD_API_URL));
    }
}
