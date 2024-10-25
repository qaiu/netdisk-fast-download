package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.URLUtil;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.UriTemplate;


/**
 *
 * QQ音乐分享解析
 * <a href="https://c6.y.qq.com/base/fcgi-bin/u?__=w3lqEpOHACLO">分享示例</a>
 * <a href="https://y.qq.com/n/ryqq/songDetail/000XjcLg0fbRjv?songtype=0">详情页</a>
 */
public class MqqsTool extends PanBase {

    public static final String API_URL = "https://u.y.qq.com/cgi-bin/musicu" +
            ".fcg?-=getplaysongvkey2682247447678878&g_tk=5381&loginUin=956581739&hostUin=0&format=json&inCharset=utf8" +
            "&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0&data=%7B%22req_0%22%3A%7B%22module%22%3A" +
            "%22vkey.GetVkeyServer%22%2C%22method%22%3A%22CgiGetVkey%22%2C%22param%22%3A%7B%22guid%22%3A%222796982635" +
            "%22%2C%22songmid%22%3A%5B%22{songmid}%22%5D%2C%22songtype%22%3A%5B1%5D%2C%22uin%22%3A%22956581739%22" +
            "%2C%22loginflag%22%3A1%2C%22platform%22%3A%2220%22%7D%7D%2C%22comm%22%3A%7B%22uin%22%3A956581739%2C" +
            "%22format%22%3A%22json%22%2C%22ct%22%3A24%2C%22cv%22%3A0%7D%7D";

    public MqqsTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        String shareUrl = shareLinkInfo.getStandardUrl();
        // https://c6.y.qq.com/base/fcgi-bin/u?__=uXXtsB
        // String shareUrl = "https://c6.y.qq.com/base/fcgi-bin/u?__=k8gafY6HAQ5Y";

        clientNoRedirects.getAbs(shareUrl).send().onSuccess(res -> {
            String locationURL = res.headers().get("Location");
            String id = URLUtil.from(locationURL).getParam("songmid");
            downUrl(id);
        }).onFailure(handleFail(shareUrl));

        return promise.future();
    }

    protected void downUrl(String id) {
        clientNoRedirects.getAbs(UriTemplate.of(API_URL)).setTemplateParam("songmid", id).send().onSuccess(res2 -> {
            JsonObject jsonObject = asJson(res2);
            log.debug(jsonObject.encodePrettily());
            try {
                JsonObject data = jsonObject.getJsonObject("req_0").getJsonObject("data");
                String path = data.getJsonArray("midurlinfo").getJsonObject(0).getString("purl");
                if (path.isEmpty()) {
                    fail("暂不支持VIP音乐");
                    return;
                }
                String downURL = data.getJsonArray("sip").getString(0)
                        .replace("http://", "https://") + path;
                promise.complete(downURL);
            } catch (Exception e) {
                fail("获取失败");
            }
        }).onFailure(handleFail(API_URL.replace("{id}", id)));
    }


    public static class MqqTool extends MqqsTool{

        public MqqTool(ShareLinkInfo shareLinkInfo) {
            super(shareLinkInfo);
        }

        @Override
        public Future<String> parse() {
            downUrl(shareLinkInfo.getShareKey());
            return promise.future();
        }
    }
}
