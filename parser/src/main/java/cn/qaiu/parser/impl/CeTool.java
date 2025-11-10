package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;

import java.net.URL;

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


    public Future<String> parse() {
        String key = shareLinkInfo.getShareKey();
        String pwd = shareLinkInfo.getSharePassword();
        // https://pan.huang1111.cn/s/wDz5TK
        // https://pan.huang1111.cn/s/y12bI6 -> https://pan.huang1111
        // .cn/api/v3/share/download/y12bI6?path=undefined%2Fundefined;
        // 类型解析 -> /ce/pan.huang1111.cn_s_wDz5TK
        // parser接口 -> /parser?url=https://pan.huang1111.cn/s/wDz5TK
        try {
//            // 处理URL
            URL url = new URL(shareLinkInfo.getShareUrl());
            String baseUrl = url.getProtocol() + "://" + url.getHost();
            
            // 先检测API版本
            detectVersionAndParse(baseUrl, key, pwd);
        } catch (Exception e) {
            fail(e, "URL解析错误");
        }
        return promise.future();
    }
    
    /**
     * 检测Cloudreve版本并选择合适的解析器
     * 先调用 /api/v3/site/ping 或 /api/v4/site/ping 判断是哪个版本
     * 如果都返回404说明不是Cloudreve盘，则调用nextParser
     */
    private void detectVersionAndParse(String baseUrl, String key, String pwd) {
        String pingUrlV3 = baseUrl + PING_API_V3_PATH;
        
        // 先尝试v3 ping
        clientSession.getAbs(pingUrlV3).send().onSuccess(res -> {
            if (res.statusCode() == 200) {
                try {
                    JsonObject pingResponse = asJson(res);
                    // 获取到JSON响应，检查是否是4.x版本
                    // 4.x的ping响应可能有不同的结构，我们通过share API来判断
                    checkVersionByShareApi(baseUrl, key, pwd);
                } catch (Exception e) {
                    // JSON解析失败，尝试v4 ping
                    tryV4PingAndParse(baseUrl, key, pwd);
                }
            } else if (res.statusCode() == 404) {
                // v3 ping不存在，尝试v4
                tryV4PingAndParse(baseUrl, key, pwd);
            } else {
                // 其他错误，不是Cloudreve盘
                nextParser();
            }
        }).onFailure(t -> {
            // 网络错误，尝试下一个解析器
            nextParser();
        });
    }
    
    private void tryV4PingAndParse(String baseUrl, String key, String pwd) {
        String pingUrlV4 = baseUrl + PING_API_V4_PATH;
        
        clientSession.getAbs(pingUrlV4).send().onSuccess(res -> {
            if (res.statusCode() == 200) {
                try {
                    JsonObject pingResponse = asJson(res);
                    // v4 ping成功，使用Ce4Tool
                    delegateToCe4Tool();
                } catch (Exception e) {
                    // 不是Cloudreve盘
                    nextParser();
                }
            } else {
                // 不是Cloudreve盘
                nextParser();
            }
        }).onFailure(t -> {
            // 网络错误，尝试下一个解析器
            nextParser();
        });
    }
    
    /**
     * 通过Share API的响应来判断版本
     * 3.x和4.x的share API响应格式可能不同
     */
    private void checkVersionByShareApi(String baseUrl, String key, String pwd) {
        String shareApiUrl = baseUrl + SHARE_API_PATH + key;
        HttpRequest<Buffer> httpRequest = clientSession.getAbs(shareApiUrl);
        if (pwd != null) {
            httpRequest.addQueryParam("password", pwd);
        }
        
        httpRequest.send().onSuccess(res -> {
            try {
                if (res.statusCode() == 200 && res.bodyAsJsonObject().containsKey("code")) {
                    JsonObject jsonObject = asJson(res);
                    // 检查响应结构来判断版本
                    // 如果share API成功，但download API返回404，说明是4.x
                    // 这里我们先尝试3.x的download API
                    String downloadApiUrl = baseUrl + DOWNLOAD_API_PATH + key + "?path=undefined/undefined;";
                    checkDownloadApi(downloadApiUrl, baseUrl, key, pwd);
                } else {
                    nextParser();
                }
            } catch (Exception e) {
                nextParser();
            }
        }).onFailure(t -> {
            nextParser();
        });
    }
    
    /**
     * 检查3.x的download API是否存在
     * 如果不存在，说明是4.x版本
     */
    private void checkDownloadApi(String downloadApiUrl, String baseUrl, String key, String pwd) {
        clientSession.putAbs(downloadApiUrl).send().onSuccess(res -> {
            if (res.statusCode() == 404 || res.statusCode() == 405) {
                // download API不存在或方法不允许，说明是4.x
                delegateToCe4Tool();
            } else if (res.statusCode() == 200) {
                // 3.x版本，继续使用当前逻辑
                getDownURL(downloadApiUrl);
            } else {
                // 其他错误
                fail("无法确定Cloudreve版本或接口调用失败");
            }
        }).onFailure(t -> {
            // 尝试使用4.x
            delegateToCe4Tool();
        });
    }
    
    /**
     * 转发到Ce4Tool处理4.x版本
     */
    private void delegateToCe4Tool() {
        log.debug("检测到Cloudreve 4.x，转发到Ce4Tool处理");
        new Ce4Tool(shareLinkInfo).parse().onComplete(promise);
    }


    private void getDownURL(String shareApiUrl) {
        clientSession.putAbs(shareApiUrl).send().onSuccess(res -> {
            JsonObject jsonObject = asJson(res);
            System.out.println(jsonObject.encodePrettily());
            if (jsonObject.containsKey("code") && jsonObject.getInteger("code") == 0) {
                promise.complete(jsonObject.getString("data"));
            } else {
                fail("JSON解析失败: {}", jsonObject.encodePrettily());
            }
        }).onFailure(handleFail(shareApiUrl));
    }
}
