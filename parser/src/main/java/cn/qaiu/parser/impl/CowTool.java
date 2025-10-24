package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo; 
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 奶牛快传解析工具
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2023/4/21 21:19
 */
public class CowTool extends PanBase {

    private static final String API_REQUEST_URL = "https://cowtransfer.com/core/api/transfer/share";

    public CowTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }


    public Future<String> parse() {
        final String key = shareLinkInfo.getShareKey();
        String url = API_REQUEST_URL + "?uniqueUrl=" + key;
        client.getAbs(url).send().onSuccess(res -> {
            JsonObject resJson = asJson(res);
            if ("success".equals(resJson.getString("message")) && resJson.containsKey("data")) {
                JsonObject dataJson = resJson.getJsonObject("data");
                String guid = dataJson.getString("guid");
                StringBuilder url2Build = new StringBuilder(API_REQUEST_URL + "/download?transferGuid=" + guid);
                if (dataJson.getBoolean("zipDownload")) {
                    // &title=xxx
                    JsonObject firstFolder = dataJson.getJsonObject("firstFolder");
                    url2Build.append("&title=").append(firstFolder.getString("title"));
                } else {
                    String fileId = dataJson.getJsonObject("firstFile").getString("id");
                    url2Build.append("&fileId=").append(fileId);
                }
                String url2 = url2Build.toString();
                client.getAbs(url2).send().onSuccess(res2 -> {
                    JsonObject res2Json = asJson(res2);
                    if ("success".equals(res2Json.getString("message")) && res2Json.containsKey("data")) {
                        JsonObject data2 = res2Json.getJsonObject("data");
                        String downloadUrl = data2.getString("downloadUrl");
                        if (StringUtils.isNotEmpty(downloadUrl)) {
                            log.info("cow parse success: {}", downloadUrl);
                            
                            // 存储下载元数据，包括必要的请求头
                            Map<String, String> headers = new HashMap<>();
                            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                            headers.put("Referer", shareLinkInfo.getShareUrl());
                            
                            // 使用新的 completeWithMeta 方法存储元数据
                            completeWithMeta(downloadUrl, headers);
                            return;
                        }
                        fail("cow parse fail: {}; downloadUrl is empty", url2);
                        return;
                    }
                    fail("cow parse fail: {}; json: {}", url2, res2Json);
                }).onFailure(handleFail(url2));
                return;
            }
            fail("cow parse fail: {}; json: {}", key, resJson);
        }).onFailure(handleFail(url));
        return promise.future();
    }

}
