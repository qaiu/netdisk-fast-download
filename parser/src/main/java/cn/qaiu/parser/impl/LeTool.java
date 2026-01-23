package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo; 
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.FileSizeConverter;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * <a href="https://lecloud.lenovo.com/">联想乐云</a>
 */
public class LeTool extends PanBase {
    private static final String API_URL_PREFIX = "https://lecloud.lenovo.com/mshare/api/clouddiskapi/share/public/v1/";
    private static final String DEFAULT_FILE_TYPE = "file";
    private static final int FILE_TYPE_DIRECTORY = 0; // 目录类型

    private static final MultiMap HEADERS;

    static {
        HEADERS = MultiMap.caseInsensitiveMultiMap();
        HEADERS.set("Accept", "application/json, text/plain, */*");
        HEADERS.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6");
        HEADERS.set("Cache-Control", "no-cache");
        HEADERS.set("Connection", "keep-alive");
        HEADERS.set("Content-Type", "application/json");
        HEADERS.set("DNT", "1");
        HEADERS.set("Origin", "https://lecloud.lenovo.com");
        HEADERS.set("Pragma", "no-cache");
        HEADERS.set("Sec-Fetch-Dest", "empty");
        HEADERS.set("Sec-Fetch-Mode", "cors");
        HEADERS.set("Sec-Fetch-Site", "same-origin");
        HEADERS.set("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1 Edg/143.0.0.0");
    }

    public LeTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    /**
     * 获取干净的 shareId（去掉可能的查询参数）
     * URL 如 https://lecloud.lenovo.com/share/5eoN3RA5PLhQcH4zE?path=... 会导致 shareKey 包含查询参数
     */
    private String getCleanShareId() {
        String shareKey = shareLinkInfo.getShareKey();
        if (shareKey != null && shareKey.contains("?")) {
            return shareKey.split("\\?")[0];
        }
        return shareKey;
    }

    public Future<String> parse() {
        final String dataKey = getCleanShareId();
        final String pwd = shareLinkInfo.getSharePassword();
        // {"shareId":"xxx","password":"xxx","directoryId":"-1"}
        String apiUrl1 = API_URL_PREFIX + "shareInfo";
        client.postAbs(apiUrl1)
            .putHeaders(HEADERS)
            .sendJsonObject(JsonObject.of("shareId", dataKey, "password", pwd, "directoryId", "-1"))
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
                                
                                // 判断是否为目录
                                Integer fileType = fileInfoJson.getInteger("fileType");
                                if (fileType != null && fileType == FILE_TYPE_DIRECTORY) {
                                    // 如果是目录，返回目录ID
                                    String fileId = fileInfoJson.getString("fileId");
                                    promise.complete(fileId);
                                    return;
                                }
                                
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

    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> listPromise = Promise.promise();

        String dataKey = getCleanShareId();
        
        // 如果参数里的目录ID不为空，则直接解析目录
        String dirId = (String) shareLinkInfo.getOtherParam().get("dirId");
        if (dirId == null || dirId.isEmpty()) {
            // 如果没有指定目录ID，使用根目录ID "-1"
            dirId = "-1";
        }
        
        // 直接请求shareInfo接口解析目录
        parseDirectory(dirId, dataKey, listPromise);
        return listPromise.future();
    }

    /**
     * 解析目录下的文件列表
     */
    private void parseDirectory(String directoryId, String shareId, Promise<List<FileInfo>> promise) {
        String pwd = shareLinkInfo.getSharePassword();
        if (pwd == null) {
            pwd = "";
        }
        String apiUrl = API_URL_PREFIX + "shareInfo";
        
        JsonObject requestBody = JsonObject.of("shareId", shareId, "password", pwd, "directoryId", directoryId);
        log.info("解析目录请求: url={}, body={}", apiUrl, requestBody.encode());
        
        client.postAbs(apiUrl)
            .putHeaders(HEADERS)
            .sendJsonObject(requestBody)
                .onSuccess(res -> {
                    JsonObject resJson = asJson(res);
                    
                    if (!resJson.containsKey("result") || !resJson.getBoolean("result")) {
                        promise.fail("解析目录失败: " + resJson.encode());
                        return;
                    }
                    
                    JsonObject dataJson = resJson.getJsonObject("data");
                    if (!dataJson.getBoolean("passwordVerified")) {
                        promise.fail("密码验证失败");
                        return;
                    }
                    
                    JsonArray files = dataJson.getJsonArray("files");
                    if (files == null || files.isEmpty()) {
                        promise.complete(new ArrayList<>());
                        return;
                    }
                    
                    List<FileInfo> fileList = new ArrayList<>();
                    for (int i = 0; i < files.size(); i++) {
                        JsonObject fileJson = files.getJsonObject(i);
                        FileInfo fileInfo = createFileInfoForList(fileJson, shareId);
                        fileList.add(fileInfo);
                    }
                    
                    promise.complete(fileList);
                })
                .onFailure(err -> {
                    log.error("解析目录请求失败: {}", err.getMessage());
                    promise.fail(err);
                });
    }

    /**
     * 为文件列表创建 FileInfo 对象
     */
    private FileInfo createFileInfoForList(JsonObject fileJson, String shareId) {
        FileInfo fileInfo = new FileInfo();
        
        try {
            String fileId = fileJson.getString("fileId");
            String fileName = fileJson.getString("fileName");
            Long fileSize = fileJson.getLong("fileSize");
            Integer fileType = fileJson.getInteger("fileType");
            
            fileInfo.setFileId(fileId);
            fileInfo.setFileName(fileName);
            fileInfo.setPanType(shareLinkInfo.getType());
            
            // 判断是否为目录
            if (fileType != null && fileType == FILE_TYPE_DIRECTORY) {
                // 目录类型
                fileInfo.setFileType("folder");
                fileInfo.setSize(0L);
                fileInfo.setSizeStr("0B");
                // 设置目录解析的URL - fileId 需要进行 URL 编码以保持特殊字符的编码状态
                try {
                    String encodedFileId = URLEncoder.encode(fileId, "UTF-8");
                    fileInfo.setParserUrl(String.format("%s/v2/getFileList?url=%s&dirId=%s", 
                            getDomainName(), 
                            shareLinkInfo.getShareUrl(), 
                            encodedFileId));
                } catch (UnsupportedEncodingException e) {
                    log.error("URL编码失败: {}", e.getMessage());
                    // 降级方案：直接使用原始 fileId
                    fileInfo.setParserUrl(String.format("%s/v2/getFileList?url=%s&dirId=%s", 
                            getDomainName(), 
                            shareLinkInfo.getShareUrl(), 
                            fileId));
                }
            } else {
                // 文件类型
                fileInfo.setFileType(fileType != null ? String.valueOf(fileType) : DEFAULT_FILE_TYPE);
                fileInfo.setSize(fileSize);
                fileInfo.setSizeStr(FileSizeConverter.convertToReadableSize(fileSize));
                
                // 创建参数JSON并编码为Base64
                JsonObject paramJson = JsonObject.of(
                        "shareId", shareId,
                        "fileId", fileId
                );
                String paramBase64 = Base64.getEncoder().encodeToString(paramJson.encode().getBytes());
                
                // 设置解析URL和预览URL
                fileInfo.setParserUrl(String.format("%s/v2/redirectUrl/%s/%s", 
                        getDomainName(), 
                        shareLinkInfo.getType(), 
                        paramBase64))
                        .setPreviewUrl(String.format("%s/v2/viewUrl/%s/%s", 
                                getDomainName(),
                                shareLinkInfo.getType(), 
                                paramBase64));
            }
            
        } catch (Exception e) {
            log.warn("创建文件信息失败: {}", e.getMessage());
        }
        
        return fileInfo;
    }

    @Override
    public Future<String> parseById() {
        Promise<String> parsePromise = Promise.promise();
        
        try {
            // 从参数中获取解析所需的信息
            JsonObject paramJson = (JsonObject) shareLinkInfo.getOtherParam().get("paramJson");
            String shareId = paramJson.getString("shareId");
            String fileId = paramJson.getString("fileId");
            
            // 调用获取下载链接
            getDownURLForById(shareId, fileId, parsePromise);
            
        } catch (Exception e) {
            parsePromise.fail("解析参数失败: " + e.getMessage());
        }
        
        return parsePromise.future();
    }

    /**
     * 根据文件ID获取下载URL (用于 parseById)
     */
    private void getDownURLForById(String shareId, String fileId, Promise<String> promise) {
        String uuid = UUID.randomUUID().toString();
        JsonArray fileIds = JsonArray.of(fileId);
        String apiUrl = API_URL_PREFIX + "packageDownloadWithFileIds";
        
        client.postAbs(apiUrl)
            .putHeaders(HEADERS)
            .sendJsonObject(JsonObject.of("fileIds", fileIds, "shareId", shareId, "browserId", uuid))
                .onSuccess(res -> {
                    JsonObject resJson = asJson(res);
                    if (resJson.containsKey("result")) {
                        if (resJson.getBoolean("result")) {
                            JsonObject dataJson = resJson.getJsonObject("data");
                            String downloadUrl = dataJson.getString("downloadUrl");
                            if (downloadUrl == null) {
                                promise.fail("Result JSON数据异常: downloadUrl不存在");
                                return;
                            }
                            // 获取重定向链接
                            clientNoRedirects.getAbs(downloadUrl).send()
                                    .onSuccess(res2 -> promise.complete(res2.headers().get("Location")))
                                    .onFailure(err -> promise.fail(err));
                        } else {
                            promise.fail(resJson.getString("errcode") + ": " + resJson.getString("errmsg"));
                        }
                    } else {
                        promise.fail("Result JSON数据异常: result字段不存在");
                    }
                }).onFailure(err -> promise.fail(err));
    }

    private void getDownURL(String key, String fileId) {
        String uuid = UUID.randomUUID().toString();
        JsonArray fileIds = JsonArray.of(fileId);
        String apiUrl2 = API_URL_PREFIX + "packageDownloadWithFileIds";
        // {"fileIds":[123],"shareId":"xxx","browserId":"uuid"}
        client.postAbs(apiUrl2)
            .putHeaders(HEADERS)
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
     * Uses exact field names from the API response without fallback checks
     */
    private FileInfo createFileInfo(JsonObject fileInfoJson) {
        FileInfo fileInfo = new FileInfo();
        
        try {
            // Set fileId
            String fileId = fileInfoJson.getString("fileId");
            if (fileId != null) {
                fileInfo.setFileId(fileId);
            }
            
            // Set fileName
            String fileName = fileInfoJson.getString("fileName");
            if (fileName != null) {
                fileInfo.setFileName(fileName);
            }
            
            // Set file size
            Long fileSize = fileInfoJson.getLong("fileSize");
            if (fileSize != null) {
                fileInfo.setSize(fileSize);
                // Convert to readable size string
                fileInfo.setSizeStr(FileSizeConverter.convertToReadableSize(fileSize));
            }
            
            // Set fileType (API returns it as an integer)
            Integer fileTypeInt = fileInfoJson.getInteger("fileType");
            if (fileTypeInt != null) {
                fileInfo.setFileType(String.valueOf(fileTypeInt));
            } else {
                // Default to generic file type if not available
                fileInfo.setFileType(DEFAULT_FILE_TYPE);
            }
            
            // Set panType
            fileInfo.setPanType(shareLinkInfo.getType());
            
        } catch (Exception e) {
            log.warn("Error extracting file info from JSON: {}", e.getMessage());
        }
        
        return fileInfo;
    }
}
