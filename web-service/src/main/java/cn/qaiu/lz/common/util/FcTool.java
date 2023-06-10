package cn.qaiu.lz.common.util;

import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.WebClientSession;
import io.vertx.uritemplate.UriTemplate;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 亿方云
 */
public class FcTool {

    public static final String SHARE_URL_PREFIX = "https://v2.fangcloud.com/sharing/";
    public static final String SHARE_URL_PREFIX2 = "https://v2.fangcloud.cn/sharing/";
    private static final String DOWN_REQUEST_URL = "https://v2.fangcloud.cn/apps/files/download?file_id={fid}" +
            "&scenario=share&unique_name={uname}";

    public static Future<String> parse(String data, String code) {
        String dataKey = CommonUtils.adaptShortPaths(SHARE_URL_PREFIX, data);

        Promise<String> promise = Promise.promise();

        Vertx vertx = VertxHolder.getVertxInstance();
        WebClient client = WebClient.create(vertx);
        WebClientSession sClient = WebClientSession.create(client);
        // 第一次请求 自动重定向
        sClient.getAbs(SHARE_URL_PREFIX + dataKey).send().onSuccess(res -> {

            // 判断是否是加密分享
            if (StringUtils.isNotEmpty(code)) {
                // 获取requesttoken
                String html = res.bodyAsString();
                Pattern compile = Pattern.compile("name=\"requesttoken\"\\s+value=\"([a-zA-Z0-9_+=]+)\"");
                Matcher matcher = compile.matcher(html);
                if (!matcher.find()) {
                    promise.fail(SHARE_URL_PREFIX + " 未匹配到加密分享的密码输入页面的requesttoken: \n" + html);
                    return;
                }
                String token = matcher.group(1);

                sClient.postAbs(SHARE_URL_PREFIX2 + dataKey).sendForm(MultiMap.caseInsensitiveMultiMap()
                        .set("requesttoken", token)
                        .set("password", code)).onSuccess(res2 -> {
                    if (res2.statusCode() == 302) {
                        sClient.getAbs(res2.getHeader("Location")).send().onSuccess(res3 -> {
                            getDownURL(dataKey, promise, res3, sClient);
                        });
                    } else {
                        promise.fail(SHARE_URL_PREFIX + " 密码跳转后获取重定向失败 \n" + html);
                    }
                });
                return;
            }
            getDownURL(dataKey, promise, res, sClient);
        });
        return promise.future();
    }

    private static void getDownURL(String dataKey, Promise<String> promise, HttpResponse<Buffer> res,
                                   WebClientSession sClient) {
        // 从HTML中找到文件id
        String html = res.bodyAsString();
        Pattern compile = Pattern.compile("id=\"typed_id\"\\s+value=\"file_(\\d+)\"");
        Matcher matcher = compile.matcher(html);
        if (!matcher.find()) {
            promise.fail(SHARE_URL_PREFIX + " 未匹配到文件id(typed_id): \n" + html);
            return;
        }
        String fid = matcher.group(1);

        // 创建一个不自动重定向的WebClientSession
        WebClient clientNoRedirects = WebClient.create(VertxHolder.getVertxInstance(),
                new WebClientOptions().setFollowRedirects(false));
        WebClientSession sClientNoRedirects = WebClientSession.create(clientNoRedirects, sClient.cookieStore());
        // 第二次请求
        sClientNoRedirects.getAbs(UriTemplate.of(DOWN_REQUEST_URL))
                .setTemplateParam("fid", fid)
                .setTemplateParam("unique_name", dataKey).send().onSuccess(res2 -> {
                    JsonObject resJson;
                    try {
                        resJson = res2.bodyAsJsonObject();
                    } catch (Exception e) {
                        promise.fail(DOWN_REQUEST_URL + " 第二次请求没有返回JSON, 可能下载受限: " + res2.bodyAsString());
                        return;
                    }
                    if (!resJson.getBoolean("success")) {
                        promise.fail(DOWN_REQUEST_URL + " 第二次请求未得到正确相应: " + resJson);
                        return;
                    }
                    promise.complete(resJson.getString("download_url"));
                });
    }
}
