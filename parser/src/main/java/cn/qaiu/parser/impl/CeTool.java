package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;

import java.net.URL;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

/**
 * <a href="https://github.com/cloudreve/Cloudreve">Cloudreve自建网盘解析</a> <br>
 * <a href="https://pan.xiaomuxi.cn">暮希云盘</a> <br>
 * <a href="https://pan.huang1111.cn">huang1111</a> <br>
 * <a href="https://pan.seeoss.com">看见存储</a> <br>
 * <a href="https://dav.yiandrive.com">亿安云盘</a> <br>
 * Cloudreve 3.x 解析器，会自动检测版本并在4.x时转发到Ce4Tool
 */
public class CeTool extends PanBase {

    private static final String DOWNLOAD_API_PATH = "/api/v3/share/download/";

    // api/v3/share/info/g31PcQ?password=qaiu
    private static final String SHARE_API_PATH = "/api/v3/share/info/";
    
    private static final String PING_API_V3_PATH = "/api/v3/site/ping";
    private static final String PING_API_V4_PATH = "/api/v4/site/ping";

    public CeTool(ShareLinkInfo shareLinkInfo) {
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
            
            // 先检测API版本
            detectVersionAndParse(baseUrl, key, pwd);
        } catch (Exception e) {
            fail(e, "URL解析错误");
        }
        return promise.future();
    }
    
    /**
     * 检测Cloudreve版本并选择合适的解析器
     * 检测策略：
     * 1. 优先检测 v4 ping，如果成功且返回有效JSON，使用Ce4Tool
     * 2. 如果 v4 ping 失败，检测 v3 ping
     * 3. 如果 v3 ping 成功，尝试调用 v3 share API 来确认是否为 v3
     * 4. 如果 v3 share API 成功，使用 v3 逻辑
     * 5. 否则尝试下一个解析器
     */
    private void detectVersionAndParse(String baseUrl, String key, String pwd) {
        // 优先检测 v4
        tryV4Ping(baseUrl, key, pwd);
    }
    
    /**
     * 尝试 v4 ping，如果成功则使用 Ce4Tool
     */
    private void tryV4Ping(String baseUrl, String key, String pwd) {
        String pingUrlV4 = baseUrl + PING_API_V4_PATH;
        
        clientSession.getAbs(pingUrlV4).send().onSuccess(res -> {
            if (res.statusCode() == 200) {
                try {
                    JsonObject json = asJson(res);
                    // v4 ping 成功且返回有效JSON，使用 Ce4Tool
                    if (json != null && !json.isEmpty()) {
                        log.debug("检测到Cloudreve 4.x (通过v4 ping)");
                        delegateToCe4Tool();
                        return;
                    }
                } catch (Exception e) {
                    // JSON解析失败，继续尝试 v3
                    log.debug("v4 ping返回非JSON响应，尝试v3");
                }
            }
            // v4 ping失败或返回非JSON，尝试 v3
            tryV3Ping(baseUrl, key, pwd);
        }).onFailure(t -> {
            // v4 ping 网络错误，尝试 v3
            log.debug("v4 ping请求失败，尝试v3: {}", t.getMessage());
            tryV3Ping(baseUrl, key, pwd);
        });
    }
    
    /**
     * 尝试 v3 ping，如果成功则验证是否为真正的 v3
     */
    private void tryV3Ping(String baseUrl, String key, String pwd) {
        String pingUrlV3 = baseUrl + PING_API_V3_PATH;
        
        clientSession.getAbs(pingUrlV3).send().onSuccess(res -> {
            if (res.statusCode() == 200) {
                try {
                    JsonObject json = asJson(res);
                    // v3 ping 成功且返回有效JSON，进一步验证是否为 v3
                    if (json != null && !json.isEmpty()) {
                        // 尝试调用 v3 share API 来确认
                        verifyV3AndParse(baseUrl, key, pwd);
                        return;
                    }
                } catch (Exception e) {
                    // JSON解析失败，不是Cloudreve盘
                    log.debug("v3 ping返回非JSON响应，不是Cloudreve盘");
                }
            }
            // v3 ping失败，不是Cloudreve盘
            log.debug("v3 ping失败，尝试下一个解析器");
            nextParser();
        }).onFailure(t -> {
            // v3 ping 网络错误，不是Cloudreve盘
            log.debug("v3 ping请求失败，尝试下一个解析器: {}", t.getMessage());
            nextParser();
        });
    }
    
    /**
     * 验证是否为 v3 版本并解析
     * 通过调用 v3 share API 来确认，如果成功则使用 v3 逻辑
     */
    private void verifyV3AndParse(String baseUrl, String key, String pwd) {
        String shareApiUrl = baseUrl + SHARE_API_PATH + key;
        HttpRequest<Buffer> httpRequest = clientSession.getAbs(shareApiUrl);
        if (pwd != null && !pwd.isEmpty()) {
            httpRequest.addQueryParam("password", pwd);
        }
        
        httpRequest.send().onSuccess(res -> {
            try {
                if (res.statusCode() == 200) {
                    JsonObject jsonObject = asJson(res);
                    // 检查响应格式是否符合 v3 API
                    if (jsonObject.containsKey("code") && jsonObject.getInteger("code") == 0) {
                        // v3 share API 成功，确认是 v3 版本
                        // 设置文件信息
                        setFileInfo(jsonObject);
                        log.debug("确认是Cloudreve 3.x，使用v3下载API");
                        String downloadApiUrl = baseUrl + DOWNLOAD_API_PATH + key + "?path=undefined/undefined;";
                        getDownURL(downloadApiUrl);
                        return;
                    }
                }
            } catch (Exception e) {
                log.debug("v3 share API解析失败: {}", e.getMessage());
            }
        }).onFailure(t -> {
            log.debug("v3 share API请求失败: {}", t.getMessage());
            // 请求失败，尝试 v4 或下一个解析器
            tryV4ShareApi(baseUrl, key, pwd);
        });
    }
    
    /**
     * 尝试 v4 share API，如果成功则使用 Ce4Tool
     */
    private void tryV4ShareApi(String baseUrl, String key, String pwd) {
        String shareApiUrl = baseUrl + "/api/v4/share/info/" + key;
        HttpRequest<Buffer> httpRequest = clientSession.getAbs(shareApiUrl);
        if (pwd != null && !pwd.isEmpty()) {
            httpRequest.addQueryParam("password", pwd);
        }
        
        httpRequest.send().onSuccess(res -> {
            try {
                if (res.statusCode() == 200) {
                    JsonObject jsonObject = asJson(res);
                    // 检查响应格式是否符合 v4 API
                    if (jsonObject.containsKey("code") && jsonObject.getInteger("code") == 0) {
                        // v4 share API 成功，使用 Ce4Tool
                        log.debug("确认是Cloudreve 4.x (通过v4 share API)");
                        delegateToCe4Tool();
                        return;
                    }
                }
            } catch (Exception e) {
                log.debug("v4 share API解析失败: {}", e.getMessage());
            }
            // v4 share API 也失败，不是Cloudreve盘
            log.debug("v4 share API验证失败，尝试下一个解析器");
            nextParser();
        }).onFailure(t -> {
            log.debug("v4 share API请求失败，尝试下一个解析器: {}", t.getMessage());
            nextParser();
        });
    }
    
    /**
     * 转发到Ce4Tool处理4.x版本
     */
    private void delegateToCe4Tool() {
        log.debug("检测到Cloudreve 4.x，转发到Ce4Tool处理");
        new Ce4Tool(shareLinkInfo).parse().onComplete(promise);
    }


    /**
     * 设置文件信息（Cloudreve 3.x）
     */
    private void setFileInfo(JsonObject jsonObject) {
        try {
            JsonObject data = jsonObject.getJsonObject("data");
            if (data == null) {
                return;
            }
            
            FileInfo fileInfo = new FileInfo();
            
            // 设置文件ID
            if (data.containsKey("key")) {
                fileInfo.setFileId(data.getString("key"));
            }
            
            // 设置文件名（从 source 对象中获取）
            if (data.containsKey("source")) {
                JsonObject source = data.getJsonObject("source");
                if (source != null) {
                    if (source.containsKey("name")) {
                        fileInfo.setFileName(source.getString("name"));
                    }
                    if (source.containsKey("size")) {
                        fileInfo.setSize(source.getLong("size"));
                    }
                }
            }
            
            // 设置下载次数
            if (data.containsKey("downloads")) {
                fileInfo.setDownloadCount(data.getInteger("downloads"));
            }
            
            // 设置创建者（从 creator 对象中获取）
            if (data.containsKey("creator")) {
                JsonObject creator = data.getJsonObject("creator");
                if (creator != null && creator.containsKey("nick")) {
                    fileInfo.setCreateBy(creator.getString("nick"));
                }
            }
            
            // 设置创建时间（格式化 ISO 8601 为 yyyy-MM-dd HH:mm:ss）
            if (data.containsKey("create_date")) {
                String createDate = data.getString("create_date");
                if (createDate != null && !createDate.isEmpty()) {
                    try {
                        String formattedTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .format(OffsetDateTime.parse(createDate).toLocalDateTime());
                        fileInfo.setCreateTime(formattedTime);
                    } catch (Exception e) {
                        log.warn("日期格式化失败: {}", createDate, e);
                        // 如果格式化失败，直接使用原始值
                        fileInfo.setCreateTime(createDate);
                    }
                }
            }
            
            // 设置访问次数（views）到扩展参数中
            if (data.containsKey("views")) {
                if (fileInfo.getExtParameters() == null) {
                    fileInfo.setExtParameters(new HashMap<>());
                }
                fileInfo.getExtParameters().put("views", data.getInteger("views"));
            }
            
            // 设置网盘类型
            fileInfo.setPanType(shareLinkInfo.getType());
            
            shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
        } catch (Exception e) {
            log.warn("设置文件信息失败", e);
        }
    }

    private void getDownURL(String shareApiUrl) {
        clientSession.putAbs(shareApiUrl)
                .putHeader("Referer", shareLinkInfo.getShareUrl())
                .send().onSuccess(res -> {
            JsonObject jsonObject = asJson(res);
            if (jsonObject.containsKey("code") && jsonObject.getInteger("code") == 0) {
                promise.complete(jsonObject.getString("data"));
            } else {
                fail("JSON解析失败: {}", jsonObject.encodePrettily());
            }
        }).onFailure(handleFail(shareApiUrl));
    }
}
