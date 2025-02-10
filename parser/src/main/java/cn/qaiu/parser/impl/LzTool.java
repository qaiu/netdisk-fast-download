package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.CastUtil;
import cn.qaiu.util.FileSizeConverter;
import cn.qaiu.util.JsExecUtils;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 蓝奏云解析工具
 *
 * @author QAIU
 */
public class LzTool extends PanBase {

    public static final String SHARE_URL_PREFIX = "https://wwwa.lanzoux.com";


    public LzTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        String sUrl = shareLinkInfo.getStandardUrl();
        String pwd = shareLinkInfo.getSharePassword();

        WebClient client = clientNoRedirects;
        client.getAbs(sUrl).send().onSuccess(res -> {
            String html = res.bodyAsString();
            // 匹配iframe
            Pattern compile = Pattern.compile("src=\"(/fn\\?[a-zA-Z\\d_+/=]{16,})\"");
            Matcher matcher = compile.matcher(html);
            // 没有Iframe说明是加密分享, 匹配sign通过密码请求下载页面
            if (!matcher.find()) {
                try {
                    String jsText = getJsByPwd(pwd, html, "document.getElementById('rpt')");
                    ScriptObjectMirror scriptObjectMirror = JsExecUtils.executeDynamicJs(jsText, "down_p");
                    getDownURL(sUrl, client, CastUtil.cast(scriptObjectMirror.get("data")));
                } catch (ScriptException | NoSuchMethodException e) {
                    fail(e, "js引擎执行失败");
                }
            } else {
                // 没有密码
                String iframePath = matcher.group(1);
                client.getAbs(SHARE_URL_PREFIX + iframePath).send().onSuccess(res2 -> {
                    String html2 = res2.bodyAsString();

                    // 去TMD正则
                    // Matcher matcher2 = Pattern.compile("'sign'\s*:\s*'(\\w+)'").matcher(html2);
                    String jsText = getJsText(html2);
                    if (jsText == null) {
                        fail(SHARE_URL_PREFIX + iframePath + " -> " + sUrl + ": js脚本匹配失败, 可能分享已失效");
                        return;
                    }
                    try {
                        ScriptObjectMirror scriptObjectMirror = JsExecUtils.executeDynamicJs(jsText, null);
                        getDownURL(sUrl, client, CastUtil.cast(scriptObjectMirror.get("data")));
                    } catch (ScriptException | NoSuchMethodException e) {
                        fail(e, "js引擎执行失败");
                    }
                }).onFailure(handleFail(SHARE_URL_PREFIX));
            }
        }).onFailure(handleFail(sUrl));
        return promise.future();
    }

    private String getJsByPwd(String pwd, String html, String subText) {
        String jsText = getJsText(html);

        if (jsText == null) {
            throw new RuntimeException("js脚本匹配失败, 可能分享已失效");
        }
        jsText = jsText.replace("document.getElementById('pwd').value", "\"" + pwd + "\"");
        int i = jsText.indexOf(subText);
        if (i > 0) {
            jsText = jsText.substring(0, i);
        }
        return jsText;
    }

    private String getJsText(String html) {
        String jsTagStart = "<script type=\"text/javascript\">";
        String jsTagEnd = "</script>";
        int index = html.lastIndexOf(jsTagStart);
        if (index == -1) {
            return null;
        }
        int startPos = index + jsTagStart.length();
        int endPos = html.indexOf(jsTagEnd, startPos);
        return html.substring(startPos, endPos).replaceAll("<!--.*-->", "");
    }

    private void getDownURL(String key, WebClient client, Map<String, Object> signMap) {
        MultiMap map = MultiMap.caseInsensitiveMultiMap();
        signMap.forEach((k, v) -> map.set(k, v.toString()));
        MultiMap headers = getHeaders(key);

        String url = SHARE_URL_PREFIX + "/ajaxm.php";
        client.postAbs(url).putHeaders(headers).sendForm(map).onSuccess(res2 -> {
            JsonObject urlJson = asJson(res2);
            if (urlJson.getInteger("zt") != 1) {
                fail(urlJson.getString("inf"));
                return;
            }
            String downUrl = urlJson.getString("dom") + "/file/" + urlJson.getString("url");
            client.getAbs(downUrl).putHeaders(headers).send()
                    .onSuccess(res3 -> promise.complete(res3.headers().get("Location")))
                    .onFailure(handleFail(downUrl));
        }).onFailure(handleFail(url));
    }

    private static MultiMap getHeaders(String key) {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        var userAgent2 = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, " +
                "like " +
                "Gecko) Chrome/111.0.0.0 Mobile Safari/537.36";
        headers.set("User-Agent", userAgent2);
        headers.set("referer", key);
        headers.set("sec-ch-ua-platform", "Android");
        headers.set("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
        headers.set("sec-ch-ua-mobile", "sec-ch-ua-mobile");
        return headers;
    }


    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> promise = Promise.promise();

        String sUrl = shareLinkInfo.getShareUrl();
        String pwd = shareLinkInfo.getSharePassword();

        WebClient client = clientNoRedirects;
        client.getAbs(sUrl).send().onSuccess(res -> {
            String html = res.bodyAsString();
            try {
                String jsText = getJsByPwd(pwd, html, "var urls =window.location.href");
                ScriptObjectMirror scriptObjectMirror = JsExecUtils.executeDynamicJs(jsText, "file");
                Map<String, Object> data = CastUtil.cast(scriptObjectMirror.get("data"));
                System.out.println(data);
                MultiMap map = MultiMap.caseInsensitiveMultiMap();
                data.forEach((k, v) -> map.set(k, v.toString()));
                MultiMap headers = getHeaders(sUrl);

                String url = SHARE_URL_PREFIX + "/filemoreajax.php?file=" + data.get("fid");
                client.postAbs(url).putHeaders(headers).sendForm(map).onSuccess(res2 -> {
                    JsonObject fileListJson = asJson(res2);
                    if (fileListJson.getInteger("zt") != 1) {
                        fail(fileListJson.getString("inf"));
                        return;
                    }
                    List<FileInfo> list = new ArrayList<>();
                    fileListJson.getJsonArray("text").forEach(item -> {
                        /*
                        {
                          "icon": "apk",
                          "t": 0,
                          "id": "iULV2n4361c",
                          "name_all": "xx.apk",
                          "size": "49.8 M",
                          "time": "2021-03-19",
                          "duan": "in4361",
                          "p_ico": 0
                        }
                         */
                        JsonObject fileJson = (JsonObject) item;
                        FileInfo fileInfo = new FileInfo();
                        String size = fileJson.getString("size");
                        Long sizeNum = FileSizeConverter.convertToBytes(size);
                        String panType = shareLinkInfo.getType();
                        String id = fileJson.getString("id");
                        fileInfo.setFileName(fileJson.getString("name_all"))
                                .setFileId(id)
                                .setCreateTime(fileJson.getString("time"))
                                .setFileType(fileJson.getString("icon"))
                                .setSizeStr(fileJson.getString("size"))
                                .setSize(sizeNum)
                                .setPanType(panType)
                                .setParserUrl(getDomainName() + "/d/" + panType + "/" + id);
                        System.out.println(fileInfo);
                        list.add(fileInfo);
                    });
                    promise.complete(list);
                });
            } catch (ScriptException | NoSuchMethodException e) {
                promise.fail(e);
            }
        });
        return promise.future();
    }
}
