package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo; 
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.FileSizeConverter;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

/**
 * <a href="https://lecloud.lenovo.com/">联想乐云</a>
 */
public class LeTool extends PanBase {
    private static final String API_URL_PREFIX = "https://lecloud.lenovo.com/share/api/clouddiskapi/share/public/v1/";

    public LeTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        final String dataKey = shareLinkInfo.getShareKey();
        final String pwd = shareLinkInfo.getSharePassword();
        // {"shareId":"xxx","password":"xxx","directoryId":"-1"}
        String apiUrl1 = API_URL_PREFIX + "shareInfo";
        client.postAbs(apiUrl1)
                .sendJsonObject(JsonObject.of("shareId", dataKey, "password", pwd, "directoryId", -1))
                .onSuccess(res -> {
                    JsonObject resJson = asJson(res);
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
                                // Extract and populate FileInfo
                                FileInfo fileInfo = createFileInfo(fileInfoJson);
                                shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
                                
                                String fileId = fileInfoJson.getString("fileId");
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

    private void getDownURL(String key, String fileId) {
        String uuid = UUID.randomUUID().toString();
        JsonArray fileIds = JsonArray.of(fileId);
        String apiUrl2 = API_URL_PREFIX + "packageDownloadWithFileIds";
        // {"fileIds":[123],"shareId":"xxx","browserId":"uuid"}
        client.postAbs(apiUrl2)
                .sendJsonObject(JsonObject.of("fileIds", fileIds, "shareId", key, "browserId", uuid))
                .onSuccess(res -> {
                    JsonObject resJson = asJson(res);
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

    /**
     * Create FileInfo object from JSON response
     * Handles null checks and missing fields gracefully
     */
    private FileInfo createFileInfo(JsonObject fileInfoJson) {
        FileInfo fileInfo = new FileInfo();
        
        try {
            // Set fileId (required field)
            String fileId = fileInfoJson.getString("fileId");
            if (fileId != null) {
                fileInfo.setFileId(fileId);
            }
            
            // Set fileName - try common field names
            String fileName = fileInfoJson.getString("fileName");
            if (fileName == null) {
                fileName = fileInfoJson.getString("name");
            }
            if (fileName != null) {
                fileInfo.setFileName(fileName);
            }
            
            // Set file size - try to parse from fileSize field
            Long fileSize = fileInfoJson.getLong("fileSize");
            if (fileSize == null) {
                fileSize = fileInfoJson.getLong("size");
            }
            if (fileSize != null) {
                fileInfo.setSize(fileSize);
                // Convert to readable size string
                fileInfo.setSizeStr(FileSizeConverter.convertToReadableSize(fileSize));
            } else {
                // Try to get size as string and convert
                String sizeStr = fileInfoJson.getString("sizeStr");
                if (sizeStr == null) {
                    sizeStr = fileInfoJson.getString("fileSizeStr");
                }
                if (sizeStr != null && !sizeStr.isEmpty()) {
                    try {
                        long bytes = FileSizeConverter.convertToBytes(sizeStr);
                        fileInfo.setSize(bytes);
                        fileInfo.setSizeStr(sizeStr);
                    } catch (Exception e) {
                        // If conversion fails, just set the string
                        fileInfo.setSizeStr(sizeStr);
                    }
                }
            }
            
            // Set fileType - try common field names
            String fileType = fileInfoJson.getString("fileType");
            if (fileType == null) {
                fileType = fileInfoJson.getString("type");
            }
            if (fileType == null) {
                fileType = fileInfoJson.getString("category");
            }
            if (fileType != null) {
                fileInfo.setFileType(fileType);
            } else {
                // Default to "file" if not available
                fileInfo.setFileType("file");
            }
            
            // Set panType
            fileInfo.setPanType(shareLinkInfo.getType());
            
            // Set createTime if available
            String createTime = fileInfoJson.getString("createTime");
            if (createTime == null) {
                createTime = fileInfoJson.getString("uploadTime");
            }
            if (createTime == null) {
                createTime = fileInfoJson.getString("modifyTime");
            }
            if (createTime != null) {
                fileInfo.setCreateTime(createTime);
            }
            
        } catch (Exception e) {
            log.warn("Error extracting file info from JSON: {}", e.getMessage());
        }
        
        return fileInfo;
    }
}
