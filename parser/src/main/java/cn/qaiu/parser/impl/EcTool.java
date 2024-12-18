package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.UriTemplate;

/**
 * 移动云空间解析
 */
public class EcTool extends PanBase {
    // https://www.ecpan.cn/web/#/yunpanProxy?path=%2F%23%2Fdrive%2Foutside&data=4b3d786755688b85c6eb0c04b9124f4dalzdaJpXHx&isShare=1
    private static final String FIRST_REQUEST_URL = "https://www.ecpan.cn/drive/fileextoverrid" +
            ".do?extractionCode={extractionCode}&chainUrlTemplate=https:%2F%2Fwww.ecpan" +
            ".cn%2Fweb%2F%23%2FyunpanProxy%3Fpath%3D%252F%2523%252Fdrive%252Foutside&parentId=-1&data={dataKey}";

    private static final String DOWNLOAD_REQUEST_URL = "https://www.ecpan.cn/drive/sharedownload.do";

    public EcTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }


    public Future<String> parse() {
        final String dataKey = shareLinkInfo.getShareKey();
        final String pwd = shareLinkInfo.getSharePassword();

        // 第一次请求 获取文件信息
        client.getAbs(UriTemplate.of(FIRST_REQUEST_URL))
                .setTemplateParam("dataKey", dataKey)
                .setTemplateParam("extractionCode", pwd == null ? "" : pwd)
                .send()
                .onSuccess(res -> {
                    JsonObject jsonObject = asJson(res);
                    log.debug("ecPan get file info -> {}", jsonObject);
                    JsonObject fileInfo = jsonObject
                            .getJsonObject("var")
                            .getJsonObject("chainFileInfo");
                    if (fileInfo.containsKey("errMesg")) {
                        fail("{} 解析失败:{} key = {}", FIRST_REQUEST_URL, fileInfo.getString("errMesg"), dataKey);
                        return;
                    }
                    if (!fileInfo.containsKey("cloudpFile")) {
                        fail("{} 解析失败:cloudpFile不存在 key = {}", FIRST_REQUEST_URL, dataKey);
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
                        JsonObject jsonRes = asJson(res2);
                        log.debug("ecPan get download url -> {}", res2.body().toString());
                        promise.complete(jsonRes.getJsonObject("var").getString("downloadUrl"));
                    }).onFailure(handleFail(""));
                }
        ).onFailure(handleFail(FIRST_REQUEST_URL));
        return promise.future();
    }
}
