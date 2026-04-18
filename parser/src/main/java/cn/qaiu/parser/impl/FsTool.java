package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.net.URL;

/**
 * <a href="https://www.feishu.cn/">飞书云盘(fs)</a>
 * 分享格式：
 * <ul>
 *   <li>文件: https://xxx.feishu.cn/file/TOKEN?from=from_copylink</li>
 *   <li>文件夹: https://xxx.feishu.cn/drive/folder/TOKEN?from=from_copylink</li>
 * </ul>
 * ?from=from_copylink 是可选参数，没有分享密码
 */
public class FsTool extends PanBase {

    private static final String DOWNLOAD_API_PATH = "/space/api/box/stream/download/all/";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public FsTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    @Override
    public Future<String> parse() {
        String shareUrl = shareLinkInfo.getShareUrl();
        try {
            // 去除查询参数以获取干净的URL
            String cleanUrl = shareUrl.contains("?") ? shareUrl.substring(0, shareUrl.indexOf("?")) : shareUrl;
            URL url = new URL(cleanUrl);
            String host = url.getHost();
            String path = url.getPath();
            String token = shareLinkInfo.getShareKey();

            if (path.contains("/file/")) {
                // 文件分享 - 获取下载直链
                getDownloadUrl(host, token);
            } else if (path.contains("/drive/folder/")) {
                fail("飞书文件夹分享暂不支持直接下载，请使用文件分享链接");
            } else {
                fail("不支持的飞书链接格式: {}", path);
            }
        } catch (Exception e) {
            fail(e, "解析飞书分享链接失败");
        }
        return promise.future();
    }

    /**
     * 通过飞书下载API获取文件直链
     *
     * @param host  飞书域名 (如 kcncuknojm60.feishu.cn)
     * @param token 文件token
     */
    private void getDownloadUrl(String host, String token) {
        String downloadApiUrl = "https://" + host + DOWNLOAD_API_PATH + token + "?mount_point=explorer";

        clientNoRedirects.getAbs(downloadApiUrl)
                .putHeader("User-Agent", UA)
                .putHeader("Referer", "https://" + host + "/")
                .send()
                .onSuccess(res -> {
                    int statusCode = res.statusCode();
                    if (statusCode == 302 || statusCode == 301) {
                        String location = res.getHeader("Location");
                        if (location != null && !location.isEmpty()) {
                            log.info("飞书文件解析成功: token={}", token);
                            complete(location);
                        } else {
                            fail("飞书下载API返回{}但没有Location头", statusCode);
                        }
                    } else if (statusCode == 200) {
                        // 部分情况下API返回JSON格式的下载链接
                        try {
                            JsonObject json = asJson(res);
                            if (json.containsKey("code") && json.getInteger("code") == 0) {
                                String downloadUrl = json.getString("url");
                                if (downloadUrl != null && !downloadUrl.isEmpty()) {
                                    log.info("飞书文件解析成功(JSON): token={}", token);
                                    complete(downloadUrl);
                                    return;
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        // 如果返回200且不是JSON，则API地址本身可能就是下载地址
                        complete(downloadApiUrl);
                    } else {
                        fail("飞书下载API返回非预期状态码: {}, body: {}", statusCode,
                                res.bodyAsString());
                    }
                })
                .onFailure(handleFail("请求飞书下载API"));
    }
}
