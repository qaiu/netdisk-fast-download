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
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.ScriptException;
import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    WebClientSession webClientSession = WebClientSession.create(clientNoRedirects);

    public static final String SHARE_URL_PREFIX = "https://w1.lanzn.com/";

    // 静态编译的正则表达式，避免每次调用都重新编译
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("padding: 56px 0px 20px 0px;\">(.*?)<|filenajax\">(.*?)<");
    private static final Pattern FILE_SIZE_PATTERN = Pattern.compile(">文件大小：</span>(.*?)<br>|\"n_filesize\">大小：(.*?)</div>");
    private static final Pattern SHARE_USER_PATTERN = Pattern.compile(">分享用户：</span><font>(.*?)</font>|获取<span>(.*?)</span>的文件|\"user-name\">(.*?)</");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?s)文件描述：</span><br>(.*?)</td>|class=\"n_box_des\">(.*?)</div>");
    private static final Pattern FILE_ID_PATTERN = Pattern.compile("\\?f=(.*?)&|fid = (.*?);");
    private static final Pattern CREATE_TIME_PATTERN = Pattern.compile(">上传时间：</span>(.*?)<");
    private static final Pattern URL_DATE_PATTERN = Pattern.compile("(\\d{4}/\\d{1,2}/\\d{1,2})");
    private static final Pattern ARG1_PATTERN = Pattern.compile("var arg1='([^']+)'");
    private static final Pattern IFRAME_SRC_PATTERN = Pattern.compile("src=\"(/fn\\?[a-zA-Z\\d_+/=]{16,})\"");
    private static final Pattern RELATIVE_TIME_PATTERN = Pattern.compile("^(\\d+|几)\\s*(分钟|小时)前$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^(\\d{4})\\s*[-/年]\\s*(\\d{1,2})\\s*[-/月]\\s*(\\d{1,2})\\s*日?$");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("^(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日?$");
    MultiMap headers0 = HeaderUtils.parseHeaders("""
        Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7
        Accept-Encoding: gzip, deflate
        Accept-Language: zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6
        Cache-Control: max-age=0
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
                    try {
                        String html = asText(res);
                        if (hasAcwArg1(html)) {
                            webClientSession = WebClientSession.create(clientNoRedirects);
                            if (!setCookie(html, sUrl)) {
                                fail("蓝奏云反爬 arg1 Cookie 解析失败，页面内容异常");
                                return;
                            }
                            webClientSession.getAbs(sUrl)
                                    .putHeaders(headers0)
                                    .send().onSuccess(res2 -> {
                                        try {
                                            String html2 = asText(res2);
                                            doParser(html2, pwd, sUrl);
                                        } catch (Exception e) {
                                            fail("蓝奏云页面响应处理异常: {}", e.getMessage());
                                        }
                                    }).onFailure(handleFail(sUrl));

                        } else {
                            doParser(html, pwd, sUrl);
                        }
                    } catch (Exception e) {
                        fail("蓝奏云页面响应处理异常: {}", e.getMessage());
                    }

                }).onFailure(handleFail(sUrl));
        return promise.future();
    }

    private void doParser(String html, String pwd, String sUrl) {
        if (html == null || html.isBlank()) {
            fail("蓝奏云页面响应为空");
            return;
        }
        if (isShareCancelledPage(html)) {
            fail("分享已失效或文件已取消分享");
            return;
        }
        // 检测是否为目录分享链接 (含 /s/、/b/ 路径段或 b 开头的路径段)
        if (sUrl.matches(".*/(s|b)/[^/]+.*") || sUrl.matches(".*/b[^/]+.*")) {
            fail("该链接为蓝奏云目录分享，请使用目录解析接口");
            return;
        }
        // 若仍是校验页 (parse()中cookie域名与实际URL不匹配时会出现), 重试一次
        if (hasAcwArg1(html)) {
            webClientSession = WebClientSession.create(clientNoRedirects);
            if (!setCookie(html, sUrl)) {
                fail("蓝奏云反爬 arg1 Cookie 解析失败，页面内容异常");
                return;
            }
            webClientSession.getAbs(sUrl).putHeaders(headers0).send().onSuccess(res -> {
                try {
                    String html2 = asText(res);
                    if (isShareCancelledPage(html2)) {
                        fail("分享已失效或文件已取消分享");
                        return;
                    }
                    if (hasAcwArg1(html2)) {
                        fail("蓝奏云反爬校验失败，请稍后重试");
                        return;
                    }
                    doParserInternal(html2, pwd, sUrl);
                } catch (Exception e) {
                    fail("蓝奏云页面响应处理异常: {}", e.getMessage());
                }
            }).onFailure(handleFail(sUrl));
            return;
        }
        doParserInternal(html, pwd, sUrl);
    }

    private void doParserInternal(String html, String pwd, String sUrl) {
        if (html == null || html.isBlank()) {
            fail("蓝奏云页面响应为空");
            return;
        }
        if (isShareCancelledPage(html)) {
            fail("分享已失效或文件已取消分享");
            return;
        }
        try {
            setFileInfo(html, shareLinkInfo);
        } catch (Exception e) {
            log.error("文件信息解析异常", e);
        }
        // 匹配iframe
        Matcher matcher = IFRAME_SRC_PATTERN.matcher(html);
        // 没有Iframe说明是加密分享, 匹配sign通过密码请求下载页面
        if (!matcher.find()) {
            try {
                String jsText = getJsByPwd(pwd, html, "document.getElementById('rpt')");
                ScriptObjectMirror scriptObjectMirror = JsExecUtils.executeDynamicJs(jsText, "down_p");
                getDownURL(sUrl, scriptObjectMirror);
            } catch (Exception e) {
                fail(e, "js引擎执行失败");
            }
        } else {
            // 没有密码
            String iframePath = matcher.group(1);
            String absoluteURI = SHARE_URL_PREFIX + iframePath;
            // 创建局部副本，避免修改实例字段导致累积
            MultiMap headersCopy = MultiMap.caseInsensitiveMultiMap().addAll(headers0);
            headersCopy.add("Referer", absoluteURI);
            webClientSession.getAbs(absoluteURI).putHeaders(headersCopy).send().onSuccess(res2 -> {
                try {
                    String html2 = asText(res2);
                    if (isShareCancelledPage(html2)) {
                        fail("分享已失效或文件已取消分享");
                        return;
                    }
                    String jsText = getJsText(html2);
                    if (jsText == null) {
                        if (!setCookie(html2, absoluteURI)) {
                            fail("蓝奏云反爬 arg1 Cookie 解析失败，页面内容异常");
                            return;
                        }
                        webClientSession.getAbs(absoluteURI).send().onSuccess(res3 -> {
                            try {
                                String html3 = asText(res3);
                                if (isShareCancelledPage(html3)) {
                                    fail("分享已失效或文件已取消分享");
                                    return;
                                }
                                String jsText3 = getJsText(html3);
                                if (jsText3 != null) {
                                    try {
                                        ScriptObjectMirror scriptObjectMirror = JsExecUtils.executeDynamicJs(jsText3, null);
                                        getDownURL(sUrl, scriptObjectMirror);
                                    } catch (ScriptException | NoSuchMethodException e) {
                                        fail(e, "引擎执行失败");
                                    }
                                } else {
                                    fail(SHARE_URL_PREFIX + iframePath + " -> " + sUrl + ": 获取失败0, 可能分享已失效");
                                }
                            } catch (Exception e) {
                                fail("蓝奏云 iframe 响应处理异常: {}", e.getMessage());
                            }
                        }).onFailure(handleFail(absoluteURI));
                    } else {
                        try {
                            ScriptObjectMirror scriptObjectMirror = JsExecUtils.executeDynamicJs(jsText, null);
                            getDownURL(sUrl, scriptObjectMirror);
                        } catch (ScriptException | NoSuchMethodException e) {
                            fail(e, "js引擎执行失败");
                        }
                    }
                } catch (Exception e) {
                    fail("蓝奏云 iframe 响应处理异常: {}", e.getMessage());
                }
            }).onFailure(handleFail(SHARE_URL_PREFIX));
        }
    }

    private boolean setCookie(String html, String url) {
        String arg1 = extractAcwArg1(html);
        if (arg1 == null) {
            return false;
        }
        String acw_sc__v2 = AcwScV2Generator.acwScV2Simple(arg1);
        // 从 URL 中动态提取域名（如 lanzoum.com, lanzoux.com 等）
        String domain = ".lanzn.com"; // 默认兜底
        try {
            java.net.URL urlObj = new java.net.URL(url);
            String host = urlObj.getHost(); // e.g. "dzvip.lanzoum.com"
            int firstDot = host.indexOf('.');
            if (firstDot >= 0) {
                domain = host.substring(firstDot); // e.g. ".lanzoum.com"
            }
        } catch (MalformedURLException ignored) {}
        // 创建一个 Cookie 并放入 CookieStore
        DefaultCookie nettyCookie = new DefaultCookie("acw_sc__v2", acw_sc__v2);
        nettyCookie.setDomain(domain);
        nettyCookie.setPath("/");
        nettyCookie.setSecure(false);
        nettyCookie.setHttpOnly(false);
        webClientSession.cookieStore().put(nettyCookie);
        return true;
    }

    private String getJsByPwd(String pwd, String html, String subText) {
        String jsText = getJsText(html);

        if (jsText == null) {
            throw new RuntimeException("获取失败1, 可能分享已失效");
        }
        jsText = jsText.replace("document.getElementById('pwd').value", "\"" + pwd + "\"");
        int i = jsText.indexOf(subText);
        if (i > 0) {
            jsText = jsText.substring(0, i);
        }
        return jsText;
    }

    private String getJsText(String html) {
        if (html == null) {
            return null;
        }
        String jsTagStart = "<script type=\"text/javascript\">";
        String jsTagEnd = "</script>";
        int index = html.lastIndexOf(jsTagStart);
        if (index == -1) {
            return null;
        }
        int startPos = index + jsTagStart.length();
        int endPos = html.indexOf(jsTagEnd, startPos);
        if (endPos <= startPos) {
            return null;
        }
        return html.substring(startPos, endPos).replaceAll("<!--.*-->", "");
    }

    static String extractAcwArg1(String html) {
        if (html == null) {
            return null;
        }
        int beginIndex = html.indexOf("arg1='");
        if (beginIndex < 0) {
            return null;
        }
        beginIndex += 6;
        int endIndex = html.indexOf("';", beginIndex);
        if (endIndex <= beginIndex) {
            return null;
        }
        return html.substring(beginIndex, endIndex);
    }

    static boolean isShareCancelledPage(String html) {
        return html != null
                && ((html.contains("来晚啦") && html.contains("取消分享"))
                || (html.contains("class=\"off\"") && html.contains("取消分享")));
    }

    private static boolean hasAcwArg1(String html) {
        return html != null && html.contains("var arg1='");
    }

    private void getDownURL(String key, Map<String, ?> obj) {
        if (obj == null) {
            fail("需要访问密码");
            return;
        }
        Map<?, ?> signMap = (Map<?, ?>)obj.get("data");
        String url0 = String.valueOf(obj.get("url"));
        MultiMap map = MultiMap.caseInsensitiveMultiMap();
        signMap.forEach((k, v) -> {
            map.add((String) k, v.toString());
        });
        MultiMap headers = HeaderUtils.parseHeaders("""
                Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7
                Accept-Encoding: gzip, deflate, br
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
        webClientSession.postAbs(url).putHeaders(headers).sendForm(map).onSuccess(res2 -> {
            try {
                JsonObject urlJson = asJson(res2);
                String name = urlJson.getString("inf");
                if (urlJson.getInteger("zt") != 1) {
                    fail(name);
                    return;
                }
                // 文件名
                if (urlJson.containsKey("inf") && urlJson.getMap().get("inf") instanceof CharSequence) {
                    ((FileInfo)shareLinkInfo.getOtherParam().get("fileInfo")).setFileName(name);
                }

                String downUrl = urlJson.getString("dom") + "/file/" + urlJson.getString("url");
                headers.remove("Referer");
                webClientSession.getAbs(downUrl).putHeaders(headers).send()
                        .onSuccess(res3 -> {
                            try {
                                String location = res3.headers().get("Location");
                                if (location == null) {
                                    String text = asText(res3);
                                    if (isShareCancelledPage(text)) {
                                        fail(downUrl + " -> 分享已失效或文件已取消分享");
                                        return;
                                    }
                                    // 使用cookie 再请求一次
                                    headers.add("Referer", downUrl);
                                    String arg1 = extractAcwArg1(text);
                                    if (arg1 == null) {
                                        fail(downUrl + " -> 蓝奏云反爬 arg1 Cookie 解析失败，可能分享已失效");
                                        return;
                                    }
                                    String acw_sc__v2 = AcwScV2Generator.acwScV2Simple(arg1);
                                    // 从 downUrl 中动态提取域名
                                    String downDomain = ".lanrar.com";
                                    try {
                                        java.net.URL du = new java.net.URL(downUrl);
                                        String h = du.getHost();
                                        int dot = h.indexOf('.');
                                        if (dot >= 0) downDomain = h.substring(dot);
                                    } catch (MalformedURLException ignored) {}
                                    // 创建一个 Cookie 并放入 CookieStore
                                    DefaultCookie nettyCookie = new DefaultCookie("acw_sc__v2", acw_sc__v2);
                                    nettyCookie.setDomain(downDomain);
                                    nettyCookie.setPath("/");
                                    nettyCookie.setSecure(false);
                                    nettyCookie.setHttpOnly(false);
                                    WebClientSession webClientSession2 = WebClientSession.create(clientNoRedirects);
                                    webClientSession2.cookieStore().put(nettyCookie);
                                    webClientSession2.getAbs(downUrl).putHeaders(headers).send()
                                            .onSuccess(res4 -> {
                                                try {
                                                    String location0 = res4.headers().get("Location");
                                                    if (location0 == null) {
                                                        fail(downUrl + " -> 直链获取失败2, 可能分享已失效");
                                                    } else {
                                                        setDateAndComplete(location0);
                                                    }
                                                } catch (Exception e) {
                                                    fail("蓝奏云直链二次响应处理异常: {}", e.getMessage());
                                                }
                                            }).onFailure(handleFail(downUrl));
                                    return;
                                }
                                setDateAndComplete(location);
                            } catch (Exception e) {
                                fail("蓝奏云直链响应处理异常: {}", e.getMessage());
                            }
                        })
                        .onFailure(handleFail(downUrl));
            } catch (Exception e) {
                fail("解析异常");
            }
        }).onFailure(handleFail(url));
    }

    private void setDateAndComplete(String location0) {
        // 分享时间 提取url中的时间戳格式：lanzoui.com/abc/abc/yyyy/mm/dd/
        Matcher matcher = URL_DATE_PATTERN.matcher(location0);
        if (matcher.find()) {
            String dateStr = parseLanzouFileTime(matcher.group());
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

        webClientSession.getAbs(sUrl).send().onSuccess(res -> {
            try {
                String html = asText(res);
                // 检查是否需要 cookie 验证
                if (hasAcwArg1(html)) {
                    webClientSession = WebClientSession.create(clientNoRedirects);
                    if (!setCookie(html, sUrl)) {
                        promise.tryFail(baseMsg() + "蓝奏云反爬 arg1 Cookie 解析失败，页面内容异常");
                        return;
                    }
                    // 重新请求
                    webClientSession.getAbs(sUrl).send().onSuccess(res2 -> {
                        try {
                            handleFileListParse(asText(res2), pwd, sUrl, promise);
                        } catch (Exception e) {
                            promise.tryFail(e);
                        }
                    }).onFailure(promise::tryFail);
                    return;
                }
                handleFileListParse(html, pwd, sUrl, promise);
            } catch (Exception e) {
                promise.tryFail(e);
            }
        }).onFailure(promise::tryFail);
        return promise.future();
    }

    private void handleFileListParse(String html, String pwd, String sUrl, Promise<List<FileInfo>> promise) {
        if (html == null || html.isBlank()) {
            promise.tryFail(baseMsg() + "蓝奏云页面响应为空");
            return;
        }
        if (isShareCancelledPage(html)) {
            promise.tryFail(baseMsg() + "分享已失效或文件已取消分享");
            return;
        }
        // 检测是否为文件分享链接 (不含 /s/、/b/ 路径段且不含 b 开头的路径段)
        if (!sUrl.matches(".*/(s|b)/[^/]+.*") && !sUrl.matches(".*/b[^/]+.*")) {
            promise.tryFail(baseMsg() + "该链接为蓝奏云文件分享，请使用文件解析接口");
            return;
        }
        try {
            String jsText = getJsByPwd(pwd, html, "var urls =window.location.href");
            ScriptObjectMirror scriptObjectMirror = JsExecUtils.executeDynamicJs(jsText, "file");
            Map<String, Object> data = CastUtil.cast(scriptObjectMirror.get("data"));
            MultiMap map = MultiMap.caseInsensitiveMultiMap();
            data.forEach((k, v) -> map.set(k, v.toString()));
            log.debug("解析参数: {}", map);
            MultiMap headers = getHeaders(sUrl);

            String url = SHARE_URL_PREFIX + "filemoreajax.php?file=" + data.get("fid");
            webClientSession.postAbs(url).putHeaders(headers).sendForm(map).onSuccess(res2 -> {
                try {
                    String resBody = asText(res2);
                    // 再次检查是否需要 cookie 验证
                    if (hasAcwArg1(resBody)) {
                        if (!setCookie(resBody, url)) {
                            promise.tryFail(baseMsg() + "蓝奏云反爬 arg1 Cookie 解析失败，页面内容异常");
                            return;
                        }
                        // 重新请求
                        webClientSession.postAbs(url).putHeaders(headers).sendForm(map).onSuccess(res3 -> {
                            try {
                                handleFileListResponse(asText(res3), promise);
                            } catch (Exception e) {
                                promise.tryFail(e);
                            }
                        }).onFailure(promise::tryFail);
                        return;
                    }
                    handleFileListResponse(resBody, promise);
                } catch (Exception e) {
                    promise.tryFail(e);
                }
            }).onFailure(promise::tryFail);
        } catch (ScriptException | NoSuchMethodException | RuntimeException e) {
            promise.tryFail(e);
        }
    }

    private void handleFileListResponse(String responseBody, Promise<List<FileInfo>> promise) {
        try {
            if (responseBody == null || responseBody.isBlank()) {
                promise.tryFail(baseMsg() + "蓝奏云文件列表响应为空");
                return;
            }
            JsonObject fileListJson = new JsonObject(responseBody);
            if (fileListJson.getInteger("zt") != 1) {
                promise.tryFail(baseMsg() + fileListJson.getString("info"));
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
                String fileName = fileJson.getString("name_all");
                // 构建 base64 参数，用于 /v2/redirectUrl 接口
                JsonObject paramJson = new JsonObject()
                        .put("id", id)
                        .put("fileName", fileName);
                String param = CommonUtils.urlBase64Encode(paramJson.encode());
                fileInfo.setFileName(fileName)
                        .setFileId(id)
                        .setCreateTime(parseLanzouFileTime(fileJson.getString("time")))
                        .setFileType(fileJson.getString("icon"))
                        .setSizeStr(fileJson.getString("size"))
                        .setSize(sizeNum)
                        .setPanType(panType)
                        .setParserUrl(String.format("%s/v2/redirectUrl/%s/%s", getDomainName(), panType, param))
                        .setPreviewUrl(String.format("%s/v2/view/%s/%s", getDomainName(),
                                shareLinkInfo.getType(), id));
                log.debug("文件信息: {}", fileInfo);
                list.add(fileInfo);
            });
            promise.complete(list);
        } catch (Exception e) {
            promise.tryFail(e);
        }
    }

    private static String parseLanzouFileTime(String timeText) {
        if (timeText == null || timeText.isBlank()) {
            return timeText;
        }
        String normalized = timeText.trim().replaceAll("\\s+", " ");
        Matcher matcher = RELATIVE_TIME_PATTERN.matcher(normalized);
        if (matcher.matches()) {
            int amount = "几".equals(matcher.group(1)) ? 1 : Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            LocalDateTime time = LocalDateTime.now();
            if ("小时".equals(unit)) {
                time = time.minusHours(amount);
            } else {
                time = time.minusMinutes(amount);
            }
            return time.toLocalDate().toString();
        }
        matcher = DATE_PATTERN.matcher(normalized);
        if (matcher.matches()) {
            return LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            ).toString();
        }
        matcher = MONTH_DAY_PATTERN.matcher(normalized);
        if (matcher.matches()) {
            return LocalDate.of(
                    LocalDate.now().getYear(),
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))
            ).toString();
        }
        return normalized;
    }

    @Override
    public Future<String> parseById() {
        JsonObject paramJson = (JsonObject) shareLinkInfo.getOtherParam().get("paramJson");
        String id = paramJson.getString("id");
        // 以文件ID重新构造标准访问URL，复用 parse() 流程
        shareLinkInfo.setStandardUrl(SHARE_URL_PREFIX + id);
        return parse();
    }

    void setFileInfo(String html, ShareLinkInfo shareLinkInfo) {
        // 写入 fileInfo
        FileInfo fileInfo = new FileInfo();
        shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
        try {
            // 提取文件名
            String fileName = CommonUtils.extract(html, FILE_NAME_PATTERN);
            String sizeStr  = CommonUtils.extract(html, FILE_SIZE_PATTERN);
            String createBy = CommonUtils.extract(html, SHARE_USER_PATTERN);
            String description = CommonUtils.extract(html, DESCRIPTION_PATTERN);
            // String icon = CommonUtils.extract(html, Pattern.compile("class=\"n_file_icon\" src=\"(.*?)\""));
            String fileId = CommonUtils.extract(html, FILE_ID_PATTERN);
            String createTime = CommonUtils.extract(html, CREATE_TIME_PATTERN);
            try {
                fileInfo.setFileName(fileName)
                        .setCreateBy(createBy)
                        .setPanType(shareLinkInfo.getType())
                        .setDescription(description)
                        .setFileType("file")
                        .setFileId(fileId)
                        .setCreateTime(parseLanzouFileTime(createTime));
                if (sizeStr != null && !sizeStr.isBlank()) {
                    long bytes = FileSizeConverter.convertToBytes(sizeStr);
                    fileInfo.setSize(bytes).setSizeStr(FileSizeConverter.convertToReadableSize(bytes));
                }
            } catch (Exception e) {
                log.warn("文件信息解析异常", e);
            }
        } catch (Exception e) {
            log.warn("文件信息匹配异常", e);
        }
    }
}
