package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.URLUtil;
import io.vertx.core.Future;
import io.vertx.uritemplate.UriTemplate;

/**
 * 网易云音乐, 单歌曲直链解析
 * <a href="http://163cn.tv/ykLZJJT">示例分享1</a>
 * <a href="https://music.163.com/#/song?id=472194327">示例分享2</a>
 */
public class MnesTool extends PanBase {


    public static final String API_URL = "https://music.163.com/song/media/outer/url?id={id}";


    public MnesTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        String shareUrl = shareLinkInfo.getStandardUrl();
        clientNoRedirects.getAbs(shareUrl).send().onSuccess(res -> {
            String locationURL = res.headers().get("Location");
            downUrl(locationURL);
        }).onFailure(handleFail(shareUrl));
        return promise.future();
    }

    protected void downUrl(String locationURL) {
        String id = URLUtil.from(locationURL).getParam("id");
        clientNoRedirects.getAbs(UriTemplate.of(API_URL)).setTemplateParam("id", id).send()
                .onSuccess(res2 -> {
                    String location = res2.headers().get("Location");
                    if (location.endsWith("/404")) {
                        fail("链接已失效: id={}", id);
                    } else {
                        promise.complete(location);
                    }
                }).onFailure(handleFail(API_URL.replace("{id}", id)));
    }


    public static class MneTool extends MnesTool{

        public MneTool(ShareLinkInfo shareLinkInfo) {
            super(shareLinkInfo);
        }

        @Override
        public Future<String> parse() {
            downUrl(shareLinkInfo.getStandardUrl());
            return promise.future();
        }
    }
}
