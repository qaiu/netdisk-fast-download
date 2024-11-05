package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.ProxyOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <a href="https://drive.google.com/">GoogleDrive</a>
 * Google Drive文件解析工具.
 */
public class PgdTool extends PanBase implements IPanTool {

    private static final String DOWN_URL_TEMPLATE =
            "https://drive.usercontent.google.com/download";

    private String downloadUrl;

    public PgdTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    @Override
    public Future<String> parse() {
        downloadUrl = DOWN_URL_TEMPLATE + "?id=" + shareLinkInfo.getShareKey() + "&export=download";

        if (shareLinkInfo.getOtherParam().containsKey("proxy")) {
//            if (shareLinkInfo.getOtherParam().containsKey("bypassCheck")
//                    && "true".equalsIgnoreCase(shareLinkInfo.getOtherParam().get("bypassCheck").toString())) {
            // 发起请求但不真正下载文件, 只检查响应头
            client.headAbs(downloadUrl).send()
                    .onSuccess(this::handleResponse)
                    .onFailure(handleFail("请求下载链接失败"));
            return future();
        }
        complete(downloadUrl);
        return future();
    }

    /**
     * 处理下载链接的响应.
     */
    private void handleResponse(HttpResponse<Buffer> response) {
        String contentType = response.getHeader("Content-Type");
        if (contentType != null && !contentType.contains("text/html")) {
            complete(downloadUrl);
        } else {
            // 如果不是文件流类型，从 HTML 中解析出真实下载链接
            client.getAbs(downloadUrl)
                    .send()
                    .onSuccess(res0 -> {
                        parseHtmlForRealLink(res0.bodyAsString());
                    })
                    .onFailure(handleFail("请求下载链接失败"));
        }
    }

    /**
     * 从HTML内容中解析真实下载链接.
     */
    private void parseHtmlForRealLink(String html) {
        // 使用正则表达式匹配 id、export、confirm、uuid、at 等参数
        String id = extractHiddenInputValue(html, "id");
        String confirm = extractHiddenInputValue(html, "confirm");
        String uuid = extractHiddenInputValue(html, "uuid");
        String at = extractHiddenInputValue(html, "at");

        if (id != null && confirm != null && uuid != null) {
            String realDownloadLink = DOWN_URL_TEMPLATE +
                    "?id=" + id +
                    "&export=download" +
                    "&confirm=" + confirm +
                    "&uuid=" + uuid;
            if (at != null) {
                realDownloadLink += ( "&at=" + at);
            }
            complete(realDownloadLink);
        } else {
            fail("无法找到完整的下载链接参数");
        }
    }

    /**
     * 辅助方法: 从HTML中提取指定name的input隐藏字段的value
     */
    private String extractHiddenInputValue(String html, String name) {
        Pattern pattern = Pattern.compile("<input[^>]*name=\"" + name + "\"[^>]*value=\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }
}
