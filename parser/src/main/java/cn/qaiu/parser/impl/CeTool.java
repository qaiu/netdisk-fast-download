package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.parser.PanDomainTemplate;
import cn.qaiu.parser.ParserCreate;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;

import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;

/**
 * <a href="https://github.com/cloudreve/Cloudreve">Cloudreve自建网盘解析</a> <br>
 * <a href="https://pan.xiaomuxi.cn">暮希云盘</a> <br>
 * <a href="https://pan.huang1111.cn">huang1111</a> <br>
 * <a href="https://pan.seeoss.com">看见存储</a> <br>
 * <a href="https://dav.yiandrive.com">亿安云盘</a> <br>
 */
public class CeTool extends PanBase {

    private static final String DOWNLOAD_API_PATH = "/api/v3/share/download/";

    // api/v3/share/info/g31PcQ?password=qaiu
    private static final String SHARE_API_PATH = "/api/v3/share/info/";

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
            String downloadApiUrl = url.getProtocol() + "://" + url.getHost() + DOWNLOAD_API_PATH + key + "?path" +
                    "=undefined/undefined;";
            String shareApiUrl = url.getProtocol() + "://" + url.getHost() + SHARE_API_PATH + key;
            // 设置cookie
            HttpRequest<Buffer> httpRequest = clientSession.getAbs(shareApiUrl);
            if (pwd != null) {
                httpRequest.addQueryParam("password", pwd);
            }
            // 获取下载链接
            httpRequest.send().onSuccess(res -> {
                try {
                    if (res.statusCode() == 200 && res.bodyAsJsonObject().containsKey("code")) {
                        getDownURL(downloadApiUrl);
                    } else {
                        nextParser();
                    }
                } catch (Exception e) {
                    nextParser();
                }
            }).onFailure(handleFail(shareApiUrl));
        } catch (Exception e) {
            fail(e, "URL解析错误");
        }
        return promise.future();
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
