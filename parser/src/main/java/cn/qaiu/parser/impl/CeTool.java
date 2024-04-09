package cn.qaiu.parser.impl;

import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * <a href="https://github.com/cloudreve/Cloudreve">Cloudreve网盘解析</a> <br>
 * <a href="https://pan.xiaomuxi.cn">暮希云盘</a> <br>
 * <a href="https://pan.huang1111.cn">huang1111</a> <br>
 */
public class CeTool extends PanBase implements IPanTool {

    private static final String DOWNLOAD_API_PATH = "/api/v3/share/download/";

    // api/v3/share/info/g31PcQ?password=qaiu
    private static final String SHARE_API_PATH = "/api/v3/share/info/";

    public CeTool(String key, String pwd) {
        super(key, pwd);
    }

    public Future<String> parse() {
        // https://pan.huang1111.cn/s/wDz5TK
        // https://pan.huang1111.cn/s/y12bI6 -> https://pan.huang1111
        // .cn/api/v3/share/download/y12bI6?path=undefined%2Fundefined;
        // 类型解析 -> /ce/https_pan.huang1111.cn_s_wDz5TK
        // parser接口 -> /parser?url=https://pan.huang1111.cn/s/wDz5TK
        try {
            if (key.startsWith("https_") || key.startsWith("http_")) {
                key = key.replace("https_", "https://")
                        .replace("http_", "http://")
                        .replace("_", "/");
            }
            // 处理URL
            URL url = new URL(key);
            String path = url.getPath();
            String shareKey = path.substring(3);
            String downloadApiUrl = url.getProtocol() + "://" + url.getHost() + DOWNLOAD_API_PATH + shareKey + "?path" +
                    "=undefined/undefined;";
            String shareApiUrl = url.getProtocol() + "://" + url.getHost() + SHARE_API_PATH + shareKey;

            // 设置cookie
            HttpRequest<Buffer> httpRequest = clientSession.getAbs(shareApiUrl);
            if (pwd != null) {
                httpRequest.addQueryParam("password", pwd);
            }
            // 获取下载链接
            httpRequest.send().onSuccess(res -> getDownURL(downloadApiUrl)).onFailure(handleFail(shareApiUrl));
        } catch (MalformedURLException e) {
            fail(e, "URL解析错误");
        }
        return promise.future();
    }

    private void getDownURL(String apiUrl) {
        clientSession.putAbs(apiUrl).send().onSuccess(res -> {
            JsonObject jsonObject = res.bodyAsJsonObject();
            System.out.println(jsonObject.encodePrettily());
            if (jsonObject.containsKey("code") && jsonObject.getInteger("code") == 0) {
                promise.complete(jsonObject.getString("data"));
            } else {
                fail("JSON解析失败: {}", jsonObject.encodePrettily());
            }
        }).onFailure(handleFail(apiUrl));
    }
}
