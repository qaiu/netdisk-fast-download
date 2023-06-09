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
public class EcTool {
    private static final String SHARE_URL_PREFIX = "https://www.ecpan.cn/drive/fileextoverrid" +
            ".do?chainUrlTemplate=https:%2F%2Fwww.ecpan" +
            ".cn%2Fweb%2F%23%2FyunpanProxy%3Fpath%3D%252F%2523%252Fdrive%252Foutside&parentId=-1&data={dataKey}";

    private static final String DOWNLOAD_REQUEST_URL = "https://www.ecpan.cn/drive/sharedownload.do";

    public static final String EC_HOST = "www.ecpan.cn";

    public static Future<String> parse(String dataKey) {
        Promise<String> promise = Promise.promise();
        WebClient client = WebClient.create(VertxHolder.getVertxInstance());
        // 第一次请求 获取文件信息
        client.getAbs(UriTemplate.of(SHARE_URL_PREFIX)).setTemplateParam("dataKey", dataKey).send().onSuccess(res -> {
                    JsonObject jsonObject = res.bodyAsJsonObject();
                    log.debug("ecPan get file info -> {}", jsonObject);
                    JsonObject fileInfo = jsonObject
                            .getJsonObject("var")
                            .getJsonObject("chainFileInfo");
                    if (!fileInfo.containsKey("errMesg")) {
                        JsonObject cloudpFile = fileInfo.getJsonObject("cloudpFile");
                        JsonArray fileIdList = JsonArray.of(cloudpFile);
                        // 构造请求JSON {"extCodeFlag":0,"isIp":0}
                        JsonObject requestBodyJson = JsonObject.of("extCodeFlag", 0, "isIp", 0);
                        requestBodyJson.put("shareId", Integer.parseInt(fileInfo.getString("shareId"))); // 注意shareId
                        // 数据类型
                        requestBodyJson.put("groupId", cloudpFile.getString("groupId"));
                        requestBodyJson.put("fileIdList", fileInfo.getJsonArray("cloudpFileList"));

                        // 第二次请求 获取下载链接
                        client.postAbs(DOWNLOAD_REQUEST_URL)
                                .sendJsonObject(requestBodyJson).onSuccess(res2 -> {
                                    JsonObject jsonRes = res2.bodyAsJsonObject();
                                    log.debug("ecPan get download url -> {}", res2.body().toString());
                                    promise.complete(jsonRes.getJsonObject("var").getString("downloadUrl"));
                                }).onFailure(t -> {
                                    promise.fail(new RuntimeException("解析异常: key = " + dataKey, t.fillInStackTrace()));
                                });

                    } else {
                        promise.fail(new RuntimeException(DOWNLOAD_REQUEST_URL + " 解析失败: "
                                + fileInfo.getString("errMesg")) + " key = " + dataKey);
                    }
                }
        ).onFailure(t -> {
            promise.fail(new RuntimeException("解析异常: key = " + dataKey, t.fillInStackTrace()));
        });
        return promise.future();
    }
}
