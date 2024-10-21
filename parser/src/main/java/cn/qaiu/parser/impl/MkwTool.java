package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.JsExecUtils;
import io.vertx.core.Future;
import io.vertx.uritemplate.UriTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 酷我音乐分享
 * <a href="https://kuwo.cn/play_detail/395500809">分享示例</a>
 */
public class MkwTool extends PanBase {

    public static final String API_URL = "https://www.kuwo.cn/api/v1/www/music/playUrl?mid={mid}&type=music&httpsStatus=1&reqId=&plat=web_www&from=";


    public MkwTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
//        String shareUrl = shareLinkInfo.getStandardUrl();
        String shareUrl = "https://kuwo.cn/play_detail/395500809";
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
                    clientSession.getAbs(UriTemplate.of(API_URL)).setQueryParam("mid", "395500809")
                            .putHeader("Secret", sign).putHeader("Cookie", key + "=" + token).send().onSuccess(res -> {
                                System.out.println(res.bodyAsString());
                            });
                }
            }

        });

        return promise.future();
    }

    MkwTool() {
    }

    public static void main(String[] args) {
        new MkwTool().parse();
    }
}
