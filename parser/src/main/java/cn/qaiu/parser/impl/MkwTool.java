package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.JsExecUtils;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.UriTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 酷我音乐分享
 * <a href="https://kuwo.cn/play_detail/395500809">分享示例</a>
 * <a href="https://m.kuwo.cn/newh5app/play_detail/318448522">分享示例</a>
 */
public class MkwTool extends PanBase {

    public static final String API_URL = "https://www.kuwo.cn/api/v1/www/music/playUrl?mid={mid}&type=music&httpsStatus=1&reqId=&plat=web_www&from=";


    public MkwTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        String shareUrl = shareLinkInfo.getStandardUrl();
        clientSession.getAbs(shareUrl).send().onSuccess(result -> {
            String cookie = result.headers().get("set-cookie");

            if (!cookie.isEmpty()) {

                String regex = "([A-Za-z0-9_]+)=([A-Za-z0-9]+)";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(cookie);
                if (matcher.find()) {
                    System.out.println(matcher.group(1));
                    System.out.println(matcher.group(2));

                    var key = matcher.group(1);
                    var token = matcher.group(2);
                    String sign = JsExecUtils.getKwSign(token, key);
                    System.out.println(sign);
                    clientSession.getAbs(UriTemplate.of(API_URL)).setTemplateParam("mid", shareLinkInfo.getShareKey())
                            .putHeader("Secret", sign).send().onSuccess(res -> {
                                JsonObject json = asJson(res);
                                log.debug(json.encodePrettily());
                                try {
                                    if (json.getInteger("code") == 200) {
                                        complete(json.getJsonObject("data").getString("url"));
                                    } else {
                                        fail("链接已失效/需要VIP");
                                    }

                                } catch (Exception e) {
                                    e.printStackTrace();
                                    fail("解析失败");
                                }
                            });
                }
            }

        });

        return promise.future();
    }
}
