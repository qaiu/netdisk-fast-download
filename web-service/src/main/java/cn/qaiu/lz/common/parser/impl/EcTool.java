package cn.qaiu.lz.common.parser.impl;

import cn.qaiu.lz.common.parser.IPanTool;
import cn.qaiu.lz.common.parser.PanBase;
import cn.qaiu.lz.common.util.CommonUtils;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.UriTemplate;

/**
 * 移动云空间解析
 */
public class EcTool extends PanBase implements IPanTool {
    private static final String FIRST_REQUEST_URL = "https://www.ecpan.cn/drive/fileextoverrid" +
            ".do?chainUrlTemplate=https:%2F%2Fwww.ecpan" +
            ".cn%2Fweb%2F%23%2FyunpanProxy%3Fpath%3D%252F%2523%252Fdrive%252Foutside&parentId=-1&data={dataKey}";

    private static final String DOWNLOAD_REQUEST_URL = "https://www.ecpan.cn/drive/sharedownload.do";

    public static final String SHARE_URL_PREFIX = "www.ecpan.cn/";

    public EcTool(String key, String pwd) {
        super(key, pwd);
    }

    public Future<String> parse() {
        String dataKey = CommonUtils.adaptShortPaths(SHARE_URL_PREFIX, key);
        // 第一次请求 获取文件信息
        client.getAbs(UriTemplate.of(FIRST_REQUEST_URL)).setTemplateParam("dataKey", dataKey).send().onSuccess(res -> {
                    JsonObject jsonObject = res.bodyAsJsonObject();
                    log.debug("ecPan get file info -> {}", jsonObject);
                    JsonObject fileInfo = jsonObject
                            .getJsonObject("var")
                            .getJsonObject("chainFileInfo");
                    if (fileInfo.containsKey("errMesg")) {
                        fail("{} 解析失败:{} key = {}", FIRST_REQUEST_URL, fileInfo.getString("errMesg"), dataKey);
                        return;
                    }
                    JsonObject cloudpFile = fileInfo.getJsonObject("cloudpFile");
                    JsonArray fileIdList = JsonArray.of(cloudpFile);
                    // 构造请求JSON {"extCodeFlag":0,"isIp":0}
                    JsonObject requestBodyJson = JsonObject.of("extCodeFlag", 0, "isIp", 0);
                    requestBodyJson.put("shareId", Integer.parseInt(fileInfo.getString("shareId"))); // 注意shareId
                    // 数据类型
                    requestBodyJson.put("groupId", cloudpFile.getString("groupId"));
                    requestBodyJson.put("fileIdList", fileInfo.getJsonArray("cloudpFileList"));

                    // 第二次请求 获取下载链接
                    client.postAbs(DOWNLOAD_REQUEST_URL).sendJsonObject(requestBodyJson).onSuccess(res2 -> {
                        JsonObject jsonRes = res2.bodyAsJsonObject();
                        log.debug("ecPan get download url -> {}", res2.body().toString());
                        promise.complete(jsonRes.getJsonObject("var").getString("downloadUrl"));
                    }).onFailure(handleFail(""));
                }
        ).onFailure(handleFail(FIRST_REQUEST_URL));
        return promise.future();
    }
}
