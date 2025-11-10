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
 * Cloudreve 4.x API 版本解析器 <br>
 * 此解析器专门处理Cloudreve 4.x版本的API，使用新的下载流程
 */
public class Ce4Tool extends PanBase {

    // Cloudreve 4.x uses /api/v3/ prefix for most APIs
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
                            // 成功，获取文件信息和下载链接
                            JsonObject data = jsonObject.getJsonObject("data");
                            if (data != null) {
                                // 获取文件路径，如果没有则使用默认路径
                                String filePath = "/";
                                if (data.containsKey("path")) {
                                    filePath = data.getString("path");
                                }
                                // 对于4.x，需要通过 POST /api/v3/file/url 获取下载链接
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
