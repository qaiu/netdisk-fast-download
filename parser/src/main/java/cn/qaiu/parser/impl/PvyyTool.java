package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.FileSizeConverter;
import cn.qaiu.util.HeaderUtils;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.uritemplate.UriTemplate;

import java.util.List;

/**
 * 微雨云
 */
public class PvyyTool extends PanBase {
    private static final String API_URL_PREFIX1 = "https://www.vyuyun.com/apiv1/share/file/{key}?password={pwd}";
    private static final String API_URL_PREFIX2 = "https://www.vyuyun.com/apiv1/share/getShareDownUrl/{key}/{id}?password={pwd}";

    byte[] hexArray = {
            0x68, 0x74, 0x74, 0x70, 0x3a, 0x2f, 0x2f, 0x31, 0x31, 0x36, 0x2e, 0x32, 0x30, 0x35, 0x2e,
            0x39, 0x36, 0x2e, 0x31, 0x39, 0x38, 0x3a, 0x33, 0x30, 0x30, 0x30, 0x2f, 0x63, 0x6f, 0x64, 0x65, 0x2f
    };

    private static final MultiMap header = HeaderUtils.parseHeaders("""
            accept-language: zh-CN,zh;q=0.9,en;q=0.8
            cache-control: no-cache
            dnt: 1
            origin: https://www.vyuyun.com
            pragma: no-cache
            priority: u=1, i
            referer: https://www.vyuyun.com/
            sec-ch-ua: "Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"
            sec-ch-ua-mobile: ?0
            sec-ch-ua-platform: "Windows"
            sec-fetch-dest: empty
            sec-fetch-mode: cors
            sec-fetch-site: same-site
            user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36
            """);

    private String api;
    public PvyyTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
        api = new String(hexArray);
        System.out.println(api);
    }

    @Override
    public Future<String> parse() {
        // 请求downcode
        WebClient.create(WebClientVertxInit.get())
                .getAbs(api + shareLinkInfo.getShareKey())
                .send()
                .onSuccess(res -> {
                    if (res.statusCode() == 200) {
                        String code = res.bodyAsString();
                        log.info("vyy url:{}, code:{}", shareLinkInfo.getStandardUrl(), code);
                        String downApi = API_URL_PREFIX2 + "&downcode=" + code;
                        getDownUrl(downApi);
                    } else {
                        fail("code获取失败");
                    }
                }).onFailure(handleFail("code服务异常"));
        return future();
    }

    private void getDownUrl(String apiUrl) {
        client.getAbs(UriTemplate.of(API_URL_PREFIX1))
                .setTemplateParam("key", shareLinkInfo.getShareKey())
                .setTemplateParam("pwd", shareLinkInfo.getSharePassword())
                .putHeaders(header)
                .send().onSuccess(res -> {
                    try {
                        JsonObject resJson = asJson(res);
                        if (!resJson.containsKey("code") || resJson.getInteger("code") != 0) {
                            fail("获取文件信息失败: " + resJson.getString("message"));
                            return;
                        }
                        JsonObject fileData = resJson.getJsonObject("data").getJsonObject("data");
                        if (fileData == null) {
                            fail("文件数据为空");
                            return;
                        }
                        setFileInfo(fileData);
                        String id = fileData.getString("id");

                        client.getAbs(UriTemplate.of(apiUrl))
                                .setTemplateParam("key", shareLinkInfo.getShareKey())
                                .setTemplateParam("pwd", shareLinkInfo.getSharePassword())
                                .setTemplateParam("id", id)
                                .putHeaders(header).send().onSuccess(res2 -> {
                                    try {
                                        // data->downInfo->url
                                        String url =
                                                asJson(res2).getJsonObject("data").getJsonObject("downInfo").getString("url");
                                        complete(url);
                                    } catch (Exception ignored) {
                                        fail(asJson(res2).encodePrettily());
                                    }
                                });
                    } catch (Exception ignored) {
                        fail();
                    }
                });
    }

    private void setFileInfo(JsonObject fileData) {
        JsonObject attributes = fileData.getJsonObject("attributes");
        JsonObject user = (JsonObject)(JsonPointer.from("/relationships/user/data").queryJson(fileData));
        int downCount = (Integer)(JsonPointer.from("/relationships/shared/data/attributes/down").queryJson(fileData));
        String filesize = attributes.getString("filesize");
        FileInfo fileInfo = new FileInfo()
                .setFileId(fileData.getString("id"))
                .setFileName(attributes.getString("basename"))
                .setFileType(attributes.getString("mimetype"))
                .setPanType(shareLinkInfo.getType())
                .setCreateBy(user.getString("email"))
                .setDownloadCount(downCount)
                .setSize(FileSizeConverter.convertToBytes(filesize))
                .setSizeStr(filesize);
        shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
    }

    private static final String DIR_API = "https://www.vyuyun.com/apiv1/share/folders/809Pt6/bMjnUg?sort=created_at&direction=DESC&password={pwd}";
    private static final String SHARE_TYPE_API = "https://www.vyuyun.com/apiv1/share/info/{key}?password={pwd}";
//
//    @Override
//    public Future<List<FileInfo>> parseFileList() {
//        Promise<List<FileInfo>> promise = Promise.promise();
//        client.getAbs(UriTemplate.of(SHARE_TYPE_API))
//                .setTemplateParam("key", shareLinkInfo.getShareKey())
//                .setTemplateParam("pwd", shareLinkInfo.getSharePassword()).send().onSuccess(res -> {
//                    // "data" -> "attributes"->type
//                    String type = asJson(res).getJsonObject("data").getJsonObject("attributes").getString("type");
//                    if ("folder".equals(type)) {
//                        // 文件夹
//                        client.getAbs(UriTemplate.of(DIR_API))
//                                .setTemplateParam("key", shareLinkInfo.getShareKey())
//                                .setTemplateParam("pwd", shareLinkInfo.getSharePassword())
//                                .send().onSuccess(res2 -> {                                    try {
//
//                                    try {
//                                        // 新的解析逻辑
//                                        var arr = asJson(res2).getJsonObject("data").getJsonArray("data");
//                                        List<FileInfo> list = arr.stream().map(o -> {
//                                            FileInfo fileInfo = new FileInfo();
//                                            var jo = ((io.vertx.core.json.JsonObject) o).getJsonObject("data");
//                                            String fileType = jo.getString("type");
//                                            fileInfo.setFileId(jo.getString("id"));
//                                            fileInfo.setFileName(jo.getJsonObject("attributes").getString("name"));
//                                            // 文件大小可能为null或字符串
//                                            Object sizeObj = jo.getJsonObject("attributes").getValue("filesize");
//                                            if (sizeObj instanceof Number) {
//                                                fileInfo.setSize(((Number) sizeObj).longValue());
//                                            } else if (sizeObj instanceof String sizeStr) {
//                                                try {
//                                                    getSize(fileInfo, sizeStr);
//                                                } catch (Exception e) {
//                                                    fileInfo.setSize(0L);
//                                                }
//                                            } else {
//                                                fileInfo.setSize(0L);
//                                            }
//                                            fileInfo.setFileType("folder".equals(fileType) ? "folder" : "file");
//                                            return fileInfo;
//                                        }).toList();
//                                        promise.complete(list);
//                                    } catch (Exception ignored) {
//                                        promise.fail(asJson(res2).encodePrettily());
//                                    }
//                                }).onFailure(t->{
//                                    promise.fail("获取文件夹内容失败: " + t.getMessage());
//                                });
//                    } else if ("file".equals(type)) {
//                        // 单文件
//                        FileInfo fileInfo = new FileInfo();
//                        var jo = asJson(res).getJsonObject("data").getJsonObject("attributes");
//                        fileInfo.setFileId(asJson(res).getJsonObject("data").getString("id"));
//                        fileInfo.setFileName(jo.getString("name"));
//                        Object sizeObj = jo.getValue("filesize");
//                        if (sizeObj instanceof Number) {
//                            fileInfo.setSize(((Number) sizeObj).longValue());
//                        } else if (sizeObj instanceof String sizeStr) {
//                            try {
//                                getSize(fileInfo, sizeStr);
//                            } catch (Exception e) {
//                                fileInfo.setSize(0L);
//                            }
//                        } else {
//                            fileInfo.setSize(0L);
//                        }
//                        fileInfo.setFileType("file");
//                        promise.complete(List.of(fileInfo));
//                    } else {
//                        promise.fail("未知的分享类型");
//                    }
//                });
//        return promise.future();
//    }
//
//    private void getSize(FileInfo fileInfo, String sizeStr) {
//        if (sizeStr.endsWith("KB")) {
//            fileInfo.setSize(Long.parseLong(sizeStr.replace("KB", "").trim()) * 1024);
//        } else if (sizeStr.endsWith("MB")) {
//            fileInfo.setSize(Long.parseLong(sizeStr.replace("MB", "").trim()) * 1024 * 1024);
//        } else {
//            fileInfo.setSize(Long.parseLong(sizeStr));
//        }
//    }
//
//    @Override
//    public Future<String> parseById() {
//        return super.parseById();
//    }
}
