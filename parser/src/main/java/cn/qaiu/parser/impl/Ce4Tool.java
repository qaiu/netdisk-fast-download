package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;

import java.net.URL;

/**
 * <a href="https://github.com/cloudreve/Cloudreve">Cloudreve 4.x 自建网盘解析</a> <br>
 * Cloudreve 4.x API 版本解析器
 */
public class Ce4Tool extends PanBase {

    // Cloudreve 4.x uses /api/v3/ prefix for most APIs
    private static final String PING_API_V3_PATH = "/api/v3/site/ping";
    private static final String PING_API_V4_PATH = "/api/v4/site/ping";
    private static final String FILE_URL_API_PATH = "/api/v3/file/url";
    private static final String SHARE_API_PATH = "/api/v3/share/info/";

    public Ce4Tool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        String key = shareLinkInfo.getShareKey();
        String pwd = shareLinkInfo.getSharePassword();
        
        try {
            URL url = new URL(shareLinkInfo.getShareUrl());
            String baseUrl = url.getProtocol() + "://" + url.getHost();
            
            // First, detect API version by pinging
            detectVersion(baseUrl, key, pwd);
        } catch (Exception e) {
            fail(e, "URL解析错误");
        }
        return promise.future();
    }

    /**
     * Detect Cloudreve version by pinging /api/v3/site/ping or /api/v4/site/ping
     */
    private void detectVersion(String baseUrl, String key, String pwd) {
        String pingUrlV3 = baseUrl + PING_API_V3_PATH;
        
        // Try v3 ping first (which also works for 4.x)
        clientSession.getAbs(pingUrlV3).send().onSuccess(res -> {
            if (res.statusCode() == 200) {
                try {
                    JsonObject pingResponse = asJson(res);
                    // If we get a valid JSON response, this is a Cloudreve instance
                    // Check if it's 4.x by trying the share API
                    getShareInfo(baseUrl, key, pwd);
                } catch (Exception e) {
                    // Not a valid JSON response, try v4 ping
                    tryV4Ping(baseUrl, key, pwd);
                }
            } else if (res.statusCode() == 404) {
                // Try v4 ping
                tryV4Ping(baseUrl, key, pwd);
            } else {
                // Not a Cloudreve instance, try next parser
                nextParser();
            }
        }).onFailure(t -> {
            // Network error or not accessible, try next parser
            nextParser();
        });
    }

    private void tryV4Ping(String baseUrl, String key, String pwd) {
        String pingUrlV4 = baseUrl + PING_API_V4_PATH;
        
        clientSession.getAbs(pingUrlV4).send().onSuccess(res -> {
            if (res.statusCode() == 200) {
                try {
                    JsonObject pingResponse = asJson(res);
                    // Valid v4 response
                    getShareInfo(baseUrl, key, pwd);
                } catch (Exception e) {
                    // Not a Cloudreve instance
                    nextParser();
                }
            } else {
                // Not a Cloudreve instance
                nextParser();
            }
        }).onFailure(t -> {
            // Not accessible, try next parser
            nextParser();
        });
    }

    /**
     * Get share information from Cloudreve 4.x
     */
    private void getShareInfo(String baseUrl, String key, String pwd) {
        String shareApiUrl = baseUrl + SHARE_API_PATH + key;
        
        HttpRequest<Buffer> httpRequest = clientSession.getAbs(shareApiUrl);
        if (pwd != null && !pwd.isEmpty()) {
            httpRequest.addQueryParam("password", pwd);
        }
        
        httpRequest.send().onSuccess(res -> {
            try {
                if (res.statusCode() == 200) {
                    JsonObject jsonObject = asJson(res);
                    if (jsonObject.containsKey("code")) {
                        int code = jsonObject.getInteger("code");
                        if (code == 0) {
                            // Success, get file info and download URL
                            JsonObject data = jsonObject.getJsonObject("data");
                            if (data != null) {
                                // Get file path or use default
                                String filePath = "/";
                                if (data.containsKey("path")) {
                                    filePath = data.getString("path");
                                }
                                // For 4.x, we need to get the download URL via POST /api/v3/file/url
                                getDownloadUrl(baseUrl, key, filePath);
                            } else {
                                fail("分享信息获取失败: data字段为空");
                            }
                        } else {
                            // Error code, might be wrong password or invalid share
                            String msg = jsonObject.getString("msg", "未知错误");
                            fail("分享验证失败: {}", msg);
                        }
                    } else {
                        // Not a Cloudreve 4.x response, try next parser
                        nextParser();
                    }
                } else {
                    // HTTP error, not a valid Cloudreve instance
                    nextParser();
                }
            } catch (Exception e) {
                // JSON parsing error, not a Cloudreve instance
                nextParser();
            }
        }).onFailure(t -> {
            // Network error, try next parser
            nextParser();
        });
    }

    /**
     * Get download URL via POST /api/v3/file/url (Cloudreve 4.x API)
     */
    private void getDownloadUrl(String baseUrl, String key, String filePath) {
        String fileUrlApi = baseUrl + FILE_URL_API_PATH;
        
        // Prepare request body for Cloudreve 4.x
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
                            if (jsonObject.containsKey("urls")) {
                                JsonArray urls = jsonObject.getJsonArray("urls");
                                if (urls != null && urls.size() > 0) {
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
                                fail("响应中不包含urls字段");
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
