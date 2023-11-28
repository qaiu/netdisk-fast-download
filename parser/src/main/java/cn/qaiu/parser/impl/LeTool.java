package cn.qaiu.parser.impl;

import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.CommonUtils;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

/**
 * <a href="https://lecloud.lenovo.com/">联想乐云</a>
 */
public class LeTool extends PanBase implements IPanTool {

    public static final String SHARE_URL_PREFIX = "https://lecloud.lenovo.com/share/";
    private static final String API_URL_PREFIX = "https://lecloud.lenovo.com/share/api/clouddiskapi/share/public/v1/";

    public LeTool(String key, String pwd) {
        super(key, pwd);
    }

    public Future<String> parse() {
        String dataKey = CommonUtils.adaptShortPaths(SHARE_URL_PREFIX, key);
        // {"shareId":"xxx","password":"xxx","directoryId":"-1"}
        String apiUrl1 = API_URL_PREFIX + "shareInfo";
        client.postAbs(apiUrl1)
                .sendJsonObject(JsonObject.of("shareId", dataKey, "password", pwd, "directoryId", -1))
                .onSuccess(res -> {
                    JsonObject resJson = res.bodyAsJsonObject();
                    if (resJson.containsKey("result")) {
                        if (resJson.getBoolean("result")) {
                            JsonObject dataJson = resJson.getJsonObject("data");
                            // 密码验证失败
                            if (!dataJson.getBoolean("passwordVerified")) {
                                fail("密码验证失败, 分享key: {}, 密码: {}", dataKey, pwd);
                                return;
                            }

                            // 获取文件信息
                            JsonArray files = dataJson.getJsonArray("files");
                            if (files == null || files.size() == 0) {
                                fail("Result JSON数据异常: files字段不存在或jsonArray长度为空");
                                return;
                            }
                            JsonObject fileInfoJson = files.getJsonObject(0);
                            if (fileInfoJson != null) {
                                // TODO 文件大小fileSize和文件名fileName
                                Long fileId = fileInfoJson.getLong("fileId");
                                // 根据文件ID获取跳转链接
                                getDownURL(dataKey, fileId);
                            }
                        } else {
                            fail("{}: {}", resJson.getString("errcode"), resJson.getString("errmsg"));
                        }
                    } else {
                        fail("Result JSON数据异常: result字段不存在");
                    }
                }).onFailure(handleFail(apiUrl1));
        return promise.future();
    }

    private void getDownURL(String key, Long fileId) {
        String uuid = UUID.randomUUID().toString();
        JsonArray fileIds = JsonArray.of(fileId);
        String apiUrl2 = API_URL_PREFIX + "packageDownloadWithFileIds";
        // {"fileIds":[123],"shareId":"xxx","browserId":"uuid"}
        client.postAbs(apiUrl2)
                .sendJsonObject(JsonObject.of("fileIds", fileIds, "shareId", key, "browserId", uuid))
                .onSuccess(res -> {
                    JsonObject resJson = res.bodyAsJsonObject();
                    if (resJson.containsKey("result")) {
                        if (resJson.getBoolean("result")) {
                            JsonObject dataJson = resJson.getJsonObject("data");
                            // 获取重定向链接跳转链接
                            String downloadUrl = dataJson.getString("downloadUrl");
                            if (downloadUrl == null) {
                                fail("Result JSON数据异常: downloadUrl不存在");
                                return;
                            }
                            // 获取重定向链接跳转链接
                            clientNoRedirects.getAbs(downloadUrl).send()
                                    .onSuccess(res2 -> promise.complete(res2.headers().get("Location")))
                                    .onFailure(handleFail(downloadUrl));
                        } else {
                            fail("{}: {}", resJson.getString("errcode"), resJson.getString("errmsg"));
                        }
                    } else {
                        fail("Result JSON数据异常: result字段不存在");
                    }
                }).onFailure(handleFail(apiUrl2));
    }
}
