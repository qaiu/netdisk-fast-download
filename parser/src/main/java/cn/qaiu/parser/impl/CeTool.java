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
        try {
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
     * 先调用 /api/v3/site/ping 判断哪个API 如果/v3 或者/v4 能查询到json响应，可以判断是哪个版本
     * 不然返回404说明不是ce盘直接nextParser
     */
    private void detectVersionAndParse(String baseUrl, String key, String pwd) {
        String pingUrlV3 = baseUrl + PING_API_V3_PATH;
        
        // 先尝试v3 ping
        clientSession.getAbs(pingUrlV3).send().onSuccess(res -> {
            if (res.statusCode() == 200) {
                try {
                    asJson(res);
                    // v3 ping成功，可能是3.x或4.x，尝试3.x的download API来判断
                    String shareApiUrl = baseUrl + SHARE_API_PATH + key;
                    String downloadApiUrl = baseUrl + DOWNLOAD_API_PATH + key + "?path=undefined/undefined;";
                    checkIfV3(shareApiUrl, downloadApiUrl, pwd);
                } catch (Exception e) {
                    // JSON解析失败，尝试v4 ping
                    tryV4Ping(baseUrl, key, pwd);
                }
            } else if (res.statusCode() == 404) {
                // v3 ping不存在，尝试v4
                tryV4Ping(baseUrl, key, pwd);
            } else {
                // 其他错误，不是Cloudreve盘
                nextParser();
            }
        }).onFailure(t -> {
            // 网络错误或不可达，尝试v4 ping
            tryV4Ping(baseUrl, key, pwd);
        });
    }
    
    private void tryV4Ping(String baseUrl, String key, String pwd) {
        String pingUrlV4 = baseUrl + PING_API_V4_PATH;
        
        clientSession.getAbs(pingUrlV4).send().onSuccess(res -> {
            if (res.statusCode() == 200) {
                try {
                    asJson(res);
                    // v4 ping成功，使用Ce4Tool
                    delegateToCe4Tool();
                } catch (Exception e) {
                    // JSON解析失败，不是Cloudreve盘
                    nextParser();
                }
            } else {
                // v4 ping失败，不是Cloudreve盘
                nextParser();
            }
        }).onFailure(t -> {
            // 网络错误，尝试下一个解析器
            nextParser();
        });
    }
    
    /**
     * 检查是否是3.x版本，通过尝试调用3.x的API
     */
    private void checkIfV3(String shareApiUrl, String downloadApiUrl, String pwd) {
        HttpRequest<Buffer> httpRequest = clientSession.getAbs(shareApiUrl);
        if (pwd != null) {
            httpRequest.addQueryParam("password", pwd);
        }
        
        httpRequest.send().onSuccess(res -> {
            try {
                if (res.statusCode() == 200 && res.bodyAsJsonObject().containsKey("code")) {
                    // share API成功，尝试download API
                    clientSession.putAbs(downloadApiUrl).send().onSuccess(res2 -> {
                        if (res2.statusCode() == 200 || res2.statusCode() == 400) {
                            // 3.x版本的download API存在
                            getDownURL(downloadApiUrl);
                        } else if (res2.statusCode() == 404 || res2.statusCode() == 405) {
                            // download API不存在，说明是4.x
                            delegateToCe4Tool();
                        } else {
                            // 其他错误，可能是4.x
                            delegateToCe4Tool();
                        }
                    }).onFailure(t -> {
                        // 请求失败，尝试4.x
                        delegateToCe4Tool();
                    });
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
