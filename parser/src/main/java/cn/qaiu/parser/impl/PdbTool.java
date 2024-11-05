package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.URLUtil;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.ProxyOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <a href="https://www.dropbox.com/">dropbox</a>
 * Dropbox网盘--不支持大陆地区
 */
public class PdbTool extends PanBase implements IPanTool {

    private static final String API_URL =
            "https://www.dropbox.com/sharing/fetch_user_content_link";
    static final String COOKIE_KEY = "__Host-js_csrf=";

    public PdbTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    @Override
    public Future<String> parse() {
        // https://www.dropbox.com/scl/fi/cwnbms1yn8u6rcatzyta7/emqx-5.0.26-el7-amd64.tar.gz?rlkey=3uoi4bxz5mv93jmlaws0nlol1&e=8&st=fe0lclc2&dl=0
        // https://www.dropbox.com/scl/fi/()/Get-Started-with-Dropbox.pdf?rlkey=yrddd6s9gxsq967pmbgtzvfl3&st=2trcc1f3&dl=0
        //
        clientSession.getAbs(shareLinkInfo.getShareUrl())
                .send()
                .onSuccess(res->{
                    List<String> collect =
                            res.cookies().stream().filter(key -> key.contains(COOKIE_KEY)).toList();
                    if (collect.isEmpty()) {
                        fail("cookie未找到");
                        return;
                    }
                    Matcher matcher = Pattern.compile(COOKIE_KEY + "([\\w-]+);").matcher(collect.get(0));
                    String _t;
                    if (matcher.find()) {
                        _t = matcher.group(1);
                    } else {
                        fail("cookie未找到");
                        return;
                    }
                    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
                    headers.set("accept", "*/*");
                    headers.set("accept-language", "zh-CN,zh;q=0.9");
                    headers.set("cache-control", "no-cache");
                    headers.set("dnt", "1");
                    headers.set("origin", "https://www.dropbox.com");
                    headers.set("pragma", "no-cache");
                    headers.set("priority", "u=1, i");
                    headers.set("referer", shareLinkInfo.getShareUrl());
                    headers.set("sec-ch-ua", "\"Chromium\";v=\"130\", \"Microsoft Edge\";v=\"130\", \"Not?A_Brand\";v=\"99\"");
                    headers.set("sec-ch-ua-mobile", "?0");
                    headers.set("sec-ch-ua-platform", "\"Windows\"");
                    headers.set("sec-fetch-dest", "empty");
                    headers.set("sec-fetch-mode", "cors");
                    headers.set("sec-fetch-site", "same-origin");
                    headers.set("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0");
                    headers.set("x-dropbox-client-yaps-attribution", "edison_atlasservlet.file_viewer-edison:prod");
                    headers.set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    try {
                        URL url = new URL(shareLinkInfo.getShareUrl());
                        // https://www.dropbox.com/scl/fi/cwnbms1yn8u6rcatzyta7/xxx?rlkey=xx&dl=0
                        String u0 = URLEncoder.encode((url.getProtocol() + "://" + url.getHost() + url.getPath() + "?rlkey=%s&dl=0")
                                .formatted(URLUtil.from(shareLinkInfo.getShareUrl()).getParam("rlkey")), StandardCharsets.UTF_8);
                        clientSession.postAbs(API_URL)
                                .sendBuffer(Buffer.buffer("is_xhr=true&t=%s&url=%s&origin=PREVIEW_PAGE".formatted(_t, u0)))
                                .onSuccess(res2 -> {
                                    complete(res2.bodyAsString());
                                })
                                .onFailure(handleFail());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    })
                    .onFailure(handleFail("请求下载链接失败"));
            return future();
    }
}
