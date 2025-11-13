package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * <a href="https://github.com/cloudreve/Cloudreve">Cloudreve 4.x 自建网盘解析</a> <br>
 * Cloudreve 4.x API 版本解析器 <br>
 * 此解析器专门处理Cloudreve 4.x版本的API，使用新的下载流程
 */
public class Ce4Tool extends PanBase {

    // Cloudreve 4.x uses /api/v3/ prefix for most APIs
    private static final String FILE_URL_API_PATH = "/api/v4/file/url";
    private static final String SHARE_API_PATH = "/api/v4/share/info/";

    public Ce4Tool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    @Override
    public Future<String> parse() {
        String key = shareLinkInfo.getShareKey();
        String pwd = shareLinkInfo.getSharePassword();
        
        try {
            URL url = new URL(shareLinkInfo.getShareUrl());
            String baseUrl = url.getProtocol() + "://" + url.getHost();
            // 如果有端口，拼接上端口
            if (url.getPort() != -1) {
                baseUrl += ":" + url.getPort();
            }
            
            // 获取分享信息
            getShareInfo(baseUrl, key, pwd);
        } catch (Exception e) {
            fail(e, "URL解析错误");
        }
        return promise.future();
    }

    /**
     * 获取Cloudreve 4.x分享信息
     */
    private void getShareInfo(String baseUrl, String key, String pwd) {
        // 第一步：请求分享URL，获取302跳转地址
        String shareUrl = shareLinkInfo.getShareUrl();
        clientNoRedirects.getAbs(shareUrl).send().onSuccess(res -> {
            try {
                if (res.statusCode() == 302 || res.statusCode() == 301) {
                    String location = res.headers().get("Location");
                    if (location == null || location.isEmpty()) {
                        fail("获取重定向地址失败: Location头为空");
                        return;
                    }
                    
                    // 从Location URL中提取path参数
                    String path = extractPathFromUrl(location);
                    if (path == null || path.isEmpty()) {
                        fail("从重定向URL中提取path参数失败: {}", location);
                        return;
                    }
                    
                    // 解码URI
                    String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
                    
                    // 第二步：请求分享详情接口，获取文件名
                    requestShareDetail(baseUrl, key, pwd, decodedPath);
                } else {
                    fail("分享URL请求失败: 期望302/301重定向，实际状态码 {}", res.statusCode());
                }
            } catch (Exception e) {
                fail(e, "解析重定向响应失败");
            }
        }).onFailure(handleFail(shareUrl));
    }
    
    /**
     * 从URL中提取path参数
     */
    private String extractPathFromUrl(String url) {
        try {
            // 解析查询参数
            String[] keyValue = url.split("=", 2);
            if (keyValue.length == 2 && keyValue[0].contains("path")) {
                return keyValue[1];
            }
            return null;
        } catch (Exception e) {
            log.error("解析URL失败: {}", url, e);
            return null;
        }
    }
    
    /**
     * 请求分享详情接口，获取文件名
     */
    private void requestShareDetail(String baseUrl, String key, String pwd, String path) {
        String shareApiUrl = baseUrl + SHARE_API_PATH + key;
        
        HttpRequest<Buffer> httpRequest = clientSession.getAbs(shareApiUrl);
        if (pwd != null && !pwd.isEmpty()) {
            httpRequest.addQueryParam("password", pwd);
        }
        
        httpRequest.send().onSuccess(res -> {
            try {
                if (res.statusCode() == 200) {
                    JsonObject jsonObject = asJson(res);
                    setFileInfo(jsonObject);
                    if (jsonObject.containsKey("code")) {
                        int code = jsonObject.getInteger("code");
                        if (code == 0) {
                            // 成功，获取文件信息和下载链接
                            JsonObject data = jsonObject.getJsonObject("data");
                            if (data != null) {
                                // 获取文件名
                                String fileName = data.getString("name");
                                if (fileName == null || fileName.isEmpty()) {
                                    fail("分享信息中缺少name字段");
                                    return;
                                }
                                
                                // 拼接path和文件名
                                String filePath = path + "/" + fileName;
                                
                                // 对于4.x，需要通过 POST /api/v4/file/url 获取下载链接
                                getDownloadUrl(baseUrl, filePath);
                            } else {
                                fail("分享信息获取失败: data字段为空");
                            }
                        } else {
                            // 错误码，可能是密码错误或分享失效
                            String msg = jsonObject.getString("msg", "未知错误");
                            fail("分享验证失败: {}", msg);
                        }
                    } else {
                        // 响应格式不符合预期
                        fail("响应格式不符合Cloudreve 4.x规范");
                    }
                } else {
                    // HTTP错误
                    fail("获取分享信息失败: HTTP {}", res.statusCode());
                }
            } catch (Exception e) {
                fail(e, "解析分享信息响应失败");
            }
        }).onFailure(handleFail(shareApiUrl));
    }

    private void setFileInfo(JsonObject jsonObject) {
        try {
            JsonObject data = jsonObject.getJsonObject("data");
            if (data == null) {
                return;
            }
            
            FileInfo fileInfo = new FileInfo();
            
            // 设置文件ID
            if (data.containsKey("id")) {
                fileInfo.setFileId(data.getString("id"));
            }
            
            // 设置文件名
            if (data.containsKey("name")) {
                fileInfo.setFileName(data.getString("name"));
            }
            
            // 设置下载次数
            if (data.containsKey("downloaded")) {
                fileInfo.setDownloadCount(data.getInteger("downloaded"));
            }
            
            // 设置访问次数（visited）
            // 注意：FileInfo 没有 visited 字段，可以放在 extParameters 中
            
            // 设置创建者（从 owner 对象中获取）
            if (data.containsKey("owner")) {
                JsonObject owner = data.getJsonObject("owner");
                if (owner != null && owner.containsKey("nickname")) {
                    fileInfo.setCreateBy(owner.getString("nickname"));
                }
            }
            
            // 设置创建时间（格式化 ISO 8601 为 yyyy-MM-dd HH:mm:ss）
            if (data.containsKey("created_at")) {
                String createdAt = data.getString("created_at");
                if (createdAt != null && !createdAt.isEmpty()) {
                    try {
                        String formattedTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .format(OffsetDateTime.parse(createdAt).toLocalDateTime());
                        fileInfo.setCreateTime(formattedTime);
                    } catch (Exception e) {
                        log.warn("日期格式化失败: {}", createdAt, e);
                        // 如果格式化失败，直接使用原始值
                        fileInfo.setCreateTime(createdAt);
                    }
                }
            }
            
            // 设置网盘类型
            fileInfo.setPanType(shareLinkInfo.getType());
            
            shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
        } catch (Exception e) {
            log.warn("设置文件信息失败", e);
        }
    }

    /**
     * 通过 POST /api/v3/file/url 获取下载链接 (Cloudreve 4.x API)
     */
    private void getDownloadUrl(String baseUrl, String filePath) {
        String fileUrlApi = baseUrl + FILE_URL_API_PATH;
        
        // 准备Cloudreve 4.x的请求体
        JsonObject requestBody = new JsonObject()
                .put("uris", new JsonArray().add(filePath))
                .put("download", true);
        
        clientSession.postAbs(fileUrlApi)
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(requestBody)
                .onSuccess(res -> {
                    try {
                        if (res.statusCode() == 200) {
                            JsonObject jsonObject = asJson(res);
                            if (jsonObject.containsKey("data") && jsonObject.getJsonObject("data").containsKey("urls")) {
                                JsonArray urls = jsonObject.getJsonObject("data").getJsonArray("urls");
                                if (urls != null && !urls.isEmpty()) {
                                    JsonObject urlObj = urls.getJsonObject(0);
                                    String downloadUrl = urlObj.getString("url");
                                    if (downloadUrl != null && !downloadUrl.isEmpty()) {
                                        promise.complete(downloadUrl);
                                    } else {
                                        fail("下载链接为空");
                                    }
                                } else {
                                    fail("下载链接列表为空");
                                }
                            } else {
                                fail("响应中不包含urls字段: {}", jsonObject.encodePrettily());
                            }
                        } else {
                            fail("获取下载链接失败: HTTP {}", res.statusCode());
                        }
                    } catch (Exception e) {
                        fail(e, "解析下载链接响应失败");
                    }
                }).onFailure(handleFail(fileUrlApi));
    }
}
