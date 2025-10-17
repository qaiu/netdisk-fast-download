package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.*;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientSession;
import org.apache.commons.lang3.RegExUtils;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 蓝奏云解析工具
 *
 * @author QAIU
 */
public class LzTool extends PanBase {

    public static final String SHARE_URL_PREFIX = "https://wwww.lanzoum.com";
    MultiMap headers0 = HeaderUtils.parseHeaders("""
        Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7
        Accept-Encoding: gzip, deflate
        Accept-Language: zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6
        Cache-Control: max-age=0
        Cookie: codelen=1; pc_ad1=1
        DNT: 1
        Priority: u=0, i
        Sec-CH-UA: "Chromium";v="140", "Not=A?Brand";v="24", "Microsoft Edge";v="140"
        Sec-CH-UA-Mobile: ?0
        Sec-CH-UA-Platform: "macOS"
        Sec-Fetch-Dest: document
        Sec-Fetch-Mode: navigate
        Sec-Fetch-Site: cross-site
        Sec-Fetch-User: ?1
        Upgrade-Insecure-Requests: 1
        User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0
        """);


    public LzTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        String sUrl = shareLinkInfo.getStandardUrl();
        String pwd = shareLinkInfo.getSharePassword();

        WebClient client = clientNoRedirects;
        client.getAbs(sUrl)
                .putHeaders(headers0)
                .send().onSuccess(res -> {
                    String html = asText(res);
                    try {
                        setFileInfo(html, shareLinkInfo);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // 匹配iframe
                    Pattern compile = Pattern.compile("src=\"(/fn\\?[a-zA-Z\\d_+/=]{16,})\"");
                    Matcher matcher = compile.matcher(html);
                    // 没有Iframe说明是加密分享, 匹配sign通过密码请求下载页面
                    if (!matcher.find()) {
                        try {
                            String jsText = getJsByPwd(pwd, html, "document.getElementById('rpt')");
                            ScriptObjectMirror scriptObjectMirror = JsExecUtils.executeDynamicJs(jsText, "down_p");
                            getDownURL(sUrl, client, scriptObjectMirror);
                        } catch (Exception e) {
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
                                getDownURL(sUrl, client, scriptObjectMirror);
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

    private void getDownURL(String key, WebClient client, Map<String, ?> obj) {
        if (obj == null) {
            fail("需要访问密码");
            return;
        }
        Map<?, ?> signMap = (Map<?, ?>)obj.get("data");
        String url0 = obj.get("url").toString();
        MultiMap map = MultiMap.caseInsensitiveMultiMap();
        signMap.forEach((k, v) -> {
            map.add((String) k, v.toString());
        });
        MultiMap headers = HeaderUtils.parseHeaders("""
                Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7
                Accept-Encoding: gzip, deflate, br, zstd
                Accept-Language: zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6
                Cache-Control: no-cache
                Connection: keep-alive
                Content-Type: application/x-www-form-urlencoded
                Pragma: no-cache
                Sec-Fetch-Dest: empty
                Sec-Fetch-Mode: cors
                Sec-Fetch-Site: same-origin
                User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.0.0
                X-Requested-With: XMLHttpRequest
                sec-ch-ua: "Chromium";v="134", "Not:A-Brand";v="24", "Microsoft Edge";v="134"
                sec-ch-ua-mobile: ?0
                sec-ch-ua-platform: "Windows"
                """);

        headers.set("referer", key);
        // action=downprocess&signs=%3Fctdf&websignkey=I5gl&sign=BWMGOF1sBTRWXwI9BjZdYVA7BDhfNAIyUG9UawJtUGMIPlAhACkCa1UyUTAAYFxvUj5XY1E7UGFXaFVq&websign=&kd=1&ves=1
        String url = SHARE_URL_PREFIX + url0;
        client.postAbs(url).putHeaders(headers).sendForm(map).onSuccess(res2 -> {
            try {
                JsonObject urlJson = asJson(res2);
                String name = urlJson.getString("inf");
                if (urlJson.getInteger("zt") != 1) {
                    fail(name);
                    return;
                }
                // 文件名
                if (urlJson.containsKey("inf") && urlJson.getMap().get("inf") instanceof Character) {
                    ((FileInfo)shareLinkInfo.getOtherParam().get("fileInfo")).setFileName(name);
                }

                String downUrl = urlJson.getString("dom") + "/file/" + urlJson.getString("url");
                headers.remove("Referer");
                WebClientSession webClientSession = WebClientSession.create(client);
                webClientSession.getAbs(downUrl).putHeaders(headers).send()
                        .onSuccess(res3 -> {
                            String location = res3.headers().get("Location");
                            if (location == null) {
                                String text = asText(res3);
                                // 使用cookie 再请求一次
                                headers.add("Referer", downUrl);
                                int beginIndex = text.indexOf("arg1='") + 6;
                                String arg1 = text.substring(beginIndex, text.indexOf("';", beginIndex));
                                String acw_sc__v2 = AcwScV2Generator.acwScV2Simple(arg1);
                                // 创建一个 Cookie 并放入 CookieStore
                                DefaultCookie nettyCookie = new DefaultCookie("acw_sc__v2", acw_sc__v2);
                                nettyCookie.setDomain(".lanrar.com"); // 设置域名
                                nettyCookie.setPath("/");             // 设置路径
                                nettyCookie.setSecure(false);
                                nettyCookie.setHttpOnly(false);
                                webClientSession.cookieStore().put(nettyCookie);
                                webClientSession.getAbs(downUrl).putHeaders(headers).send()
                                        .onSuccess(res4 -> {
                                            String location0 = res4.headers().get("Location");
                                            if (location0 == null) {
                                                fail(downUrl + " -> 直链获取失败, 可能分享已失效");
                                            } else {
                                                setDateAndComplate(location0);
                                            }
                                        }).onFailure(handleFail(downUrl));
                                return;
                            }
                            setDateAndComplate(location);
                        })
                        .onFailure(handleFail(downUrl));
            } catch (Exception e) {
                fail("解析异常");
            }
        }).onFailure(handleFail(url));
    }

    private void setDateAndComplate(String location0) {
        // 分享时间 提取url中的时间戳格式：lanzoui.com/abc/abc/yyyy/mm/dd/
        String regex = "(\\d{4}/\\d{1,2}/\\d{1,2})";
        Matcher matcher = Pattern.compile(regex).matcher(location0);
        if (matcher.find()) {
            String dateStr = matcher.group().replace("/", "-");
            ((FileInfo)shareLinkInfo.getOtherParam().get("fileInfo")).setCreateTime(dateStr);
        }
        promise.complete(location0);
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
                MultiMap map = MultiMap.caseInsensitiveMultiMap();
                data.forEach((k, v) -> map.set(k, v.toString()));
                log.debug("解析参数: {}", map);
                MultiMap headers = getHeaders(sUrl);

                String url = SHARE_URL_PREFIX + "/filemoreajax.php?file=" + data.get("fid");
                client.postAbs(url).putHeaders(headers).sendForm(map).onSuccess(res2 -> {
                    JsonObject fileListJson = asJson(res2);
                    if (fileListJson.getInteger("zt") != 1) {
                        promise.fail(baseMsg() + fileListJson.getString("info"));
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
                                .setParserUrl(getDomainName() + "/d/" + panType + "/" + id)
                                .setPreviewUrl(String.format("%s/v2/view/%s/%s", getDomainName(),
                                        shareLinkInfo.getType(), id));
                        log.debug("文件信息: {}", fileInfo);
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

    void setFileInfo(String html, ShareLinkInfo shareLinkInfo) {
        // 写入 fileInfo
        FileInfo fileInfo = new FileInfo();
        shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
        try {
            // 提取文件名
            String fileName = CommonUtils.extract(html, Pattern.compile("padding: 56px 0px 20px 0px;\">(.*?)<|filenajax\">(.*?)<"));
            String sizeStr  = CommonUtils.extract(html, Pattern.compile(">文件大小：</span>(.*?)<br>|\"n_filesize\">大小：(.*?)</div>"));
            String createBy = CommonUtils.extract(html, Pattern.compile(">分享用户：</span><font>(.*?)</font>|获取<span>(.*?)</span>的文件|\"user-name\">(.*?)</"));
            String description = CommonUtils.extract(html, Pattern.compile("(?s)文件描述：</span><br>(.*?)</td>|class=\"n_box_des\">(.*?)</div>"));
            // String icon = CommonUtils.extract(html, Pattern.compile("class=\"n_file_icon\" src=\"(.*?)\""));
            String fileId = CommonUtils.extract(html, Pattern.compile("\\?f=(.*?)&|fid = (.*?);"));
            String createTime = CommonUtils.extract(html, Pattern.compile(">上传时间：</span>(.*?)<"));
            try {
                long bytes = FileSizeConverter.convertToBytes(sizeStr);
                fileInfo.setFileName(fileName)
                        .setSize(bytes)
                        .setSizeStr(FileSizeConverter.convertToReadableSize(bytes))
                        .setCreateBy(createBy)
                        .setPanType(shareLinkInfo.getType())
                        .setDescription(description)
                        .setFileType("file")
                        .setFileId(fileId)
                        .setCreateTime(createTime);
            } catch (Exception e) {
                log.warn("文件信息解析异常", e);
            }
        } catch (Exception e) {
            log.warn("文件信息匹配异常", e);
        }
    }
}
