package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * <a href="https://www.kdocs.cn/">WPS云文档</a>
 * 分享格式：https://www.kdocs.cn/l/ck0azivLlDi3
 * API格式：https://www.kdocs.cn/api/office/file/{shareKey}/download
 * 响应：{download_url: "https://hwc-bj.ag.kdocs.cn/api/xx",url: "",fize: 0,fver: 0,store: ""}
 */
public class PwpsTool extends PanBase {
    private static final String API_URL_TEMPLATE = "https://www.kdocs.cn/api/office/file/%s/download";

    public PwpsTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    @Override
    public Future<String> parse() {
        final String shareKey = shareLinkInfo.getShareKey();
        
        // 构建API URL
        String apiUrl = String.format(API_URL_TEMPLATE, shareKey);
        
        // 发送GET请求到WPS API
        client.getAbs(apiUrl)
                .send()
                .onSuccess(res -> {
                    try {
                        JsonObject resJson = asJson(res);
                        
                        // 检查响应是否包含download_url字段
                        if (resJson.containsKey("download_url")) {
                            String downloadUrl = resJson.getString("download_url");
                            
                            if (downloadUrl != null && !downloadUrl.isEmpty()) {
                                log.info("WPS云文档解析成功: shareKey={}, downloadUrl={}", shareKey, downloadUrl);
                                
                                // 存储下载元数据，包括必要的请求头
                                Map<String, String> headers = new HashMap<>();
                                headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                                headers.put("Referer", shareLinkInfo.getShareUrl());
                                
                                // 使用新的 completeWithMeta 方法存储元数据
                                completeWithMeta(downloadUrl, headers);
                                return;
                            } else {
                                fail("download_url字段为空");
                            }
                        } else {
                            // 检查是否有错误信息
                            if (resJson.containsKey("error") || resJson.containsKey("msg")) {
                                String errorMsg = resJson.getString("error", resJson.getString("msg", "未知错误"));
                                fail("API返回错误: {}", errorMsg);
                            } else {
                                fail("响应中未找到download_url字段, 响应内容: {}", resJson.encodePrettily());
                            }
                        }
                    } catch (Exception e) {
                        fail(e, "解析响应JSON失败");
                    }
                })
                .onFailure(handleFail(apiUrl));
        
        return promise.future();
    }
}

