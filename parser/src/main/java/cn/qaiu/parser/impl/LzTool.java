package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.*;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.WebClientSession;
import org.apache.commons.lang3.StringUtils;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.ScriptException;
import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * 蓝奏云解析工具
 *
 * @author QAIU
 */
public class LzTool extends PanBase {

    /** ESA 对 gzip 响应常见不带可识别的 Content-Encoding，需客户端自动解压。 */
    private final WebClient lzClient;
    private WebClientSession webClientSession;

    public static final String SHARE_URL_PREFIX = "https://w1.lanzn.com/";

    // 静态编译的正则表达式，避免每次调用都重新编译
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("padding: 56px 0px 20px 0px;\">(.*?)<|filenajax\">(.*?)<");
    private static final Pattern P_WP_SIGN = Pattern.compile("wp_sign\\s*=\\s*'([^']+)'");
    private static final Pattern P_AJAXDATA = Pattern.compile("ajaxdata\\s*=\\s*'([^']+)'");
    private static final Pattern P_WEBSIGN = Pattern.compile("'websign'\\s*:\\s*'([^']*)'");
    private static final Pattern P_AJAX_PATH = Pattern.compile("(?:['\"/]|^)(ajax(?:m|file)\\.php\\?file=\\d+)");
    private static final Pattern P_SIGN = Pattern.compile("'sign'\\s*:\\s*'([^']+)'");
    private static final Pattern P_ISNGIS = Pattern.compile("var\\s+isngis\\s*=\\s*'([^']+)'");
    private static final Pattern P_KDNS = Pattern.compile("var\\s+kdns\\s*=\\s*(\\d+)");
    private static final Pattern P_FILEMORE = Pattern.compile(
            "url\\s*:\\s*'(/filemoreajax\\.php\\?file=\\d+)'[\\s\\S]*?data\\s*:\\s*\\{([^}]+)\\}");
    private static final Pattern P_DATA_KV = Pattern.compile("'(\\w+)'\\s*:\\s*('(?:\\\\'|[^'])*'|\\d+|\\w+)");
    private static final Pattern P_INLINE_SCRIPT =
            Pattern.compile("(?is)<script(?![^>]*\\bsrc\\s*=)[^>]*>(.*?)</script>");
    private static final Pattern FILE_SIZE_PATTERN = Pattern.compile(">文件大小：</span>(.*?)<br>|\"n_filesize\">大小：(.*?)</div>");
    private static final Pattern SHARE_USER_PATTERN = Pattern.compile(">分享用户：</span><font>(.*?)</font>|获取<span>(.*?)</span>的文件|\"user-name\">(.*?)</");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?s)文件描述：</span><br>(.*?)</td>|class=\"n_box_des\">(.*?)</div>");
    private static final Pattern FILE_ID_PATTERN = Pattern.compile("\\?f=(.*?)&|fid = (.*?);");
    private static final Pattern CREATE_TIME_PATTERN = Pattern.compile(">上传时间：</span>(.*?)<");
    private static final Pattern URL_DATE_PATTERN = Pattern.compile("(\\d{4}/\\d{1,2}/\\d{1,2})");
    private static final Pattern ARG1_PATTERN = Pattern.compile("var arg1='([^']+)'");
    private static final Pattern IFRAME_SRC_PATTERN = Pattern.compile(
            "src\\s*=\\s*[\"'](/fn\\?[^\"'\\s>]+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATIVE_TIME_PATTERN = Pattern.compile("^(\\d+|几)\\s*(分钟|小时)前$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^(\\d{4})\\s*[-/年]\\s*(\\d{1,2})\\s*[-/月]\\s*(\\d{1,2})\\s*日?$");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("^(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日?$");
    MultiMap headers0 = HeaderUtils.parseHeaders("""
        Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7
        Accept-Encoding: identity
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
        this.lzClient = createLzClient(shareLinkInfo);
        this.webClientSession = WebClientSession.create(lzClient);
    }

    /**
     * ESA 对 gzip 响应常见不带可识别的 Content-Encoding，需客户端自动解压；
     * 代理模式必须带上 shareLinkInfo 里的 proxy，不能绕过全局代理。
     */
    private static WebClient createLzClient(ShareLinkInfo shareLinkInfo) {
        WebClientOptions opts = new WebClientOptions()
                .setFollowRedirects(false)
                .setDecompressionSupported(true)
                .setUserAgentEnabled(false)
                .setConnectTimeout(10000)
                .setIdleTimeout(12);
        if (shareLinkInfo != null && shareLinkInfo.getOtherParam().containsKey("proxy")) {
            JsonObject proxy = (JsonObject) shareLinkInfo.getOtherParam().get("proxy");
            if (proxy != null && proxy.getString("host") != null) {
                ProxyOptions proxyOptions = new ProxyOptions()
                        .setType(ProxyType.valueOf(proxy.getString("type", "http").toUpperCase()))
                        .setHost(proxy.getString("host"))
                        .setPort(proxy.getInteger("port", 0));
                if (StringUtils.isNotEmpty(proxy.getString("username"))) {
                    proxyOptions.setUsername(proxy.getString("username"));
                }
                if (StringUtils.isNotEmpty(proxy.getString("password"))) {
                    proxyOptions.setPassword(proxy.getString("password"));
                }
                opts.setProxyOptions(proxyOptions);
            }
        }
        return WebClient.create(WebClientVertxInit.get(), opts);
    }

    /**
     * Vert.x decompressionSupported 可能已经解压，但响应头仍带 gzip；
     * 再走 PanBase.asText 会二次解压得到乱码，iframe / down_p 都匹配不到。
     */
    @Override
    protected String asText(HttpResponse<?> res) {
        try {
            Object raw = res.body();
            if (raw instanceof Buffer body && body.length() > 0) {
                int i = 0;
                int len = body.length();
                while (i < len) {
                    byte c = body.getByte(i);
                    if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                        if (c == '<' || c == '{' || c == '[') {
                            return body.toString();
                        }
                        if ((c & 0xff) == 0x1f && i + 1 < len && (body.getByte(i + 1) & 0xff) == 0x8b) {
                            return gunzipUtf8(body);
                        }
                        break;
                    }
                    i++;
                }
            }
        } catch (Exception ignored) {
        }
        return super.asText(res);
    }

    private static String gunzipUtf8(Buffer body) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(body.getBytes()))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return body.toString();
        }
    }

    @Override
    protected JsonObject asJson(HttpResponse<?> res) {
        JsonObject parsed = parseLzJson(asText(res));
        if (parsed != null) {
            return parsed;
        }
        return super.asJson(res);
    }

    /** ajax 常被标成 gzip/text/json，body 可能已解压或为空，不能直接 bodyAsJsonObject。 */
    private static JsonObject parseLzJson(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        int start = t.indexOf('{');
        if (start < 0) {
            return null;
        }
        try {
            return new JsonObject(t.substring(start));
        } catch (Exception e) {
            return null;
        }
    }

    public Future<String> parse() {
        String sUrl = shareLinkInfo.getStandardUrl();
        String pwd = shareLinkInfo.getSharePassword();

        webClientSession.getAbs(sUrl)
                .putHeaders(headers0)
                .send().onSuccess(res -> {
                    try {
                        String html = asText(res);
                        if (hasAcwArg1(html)) {
                            webClientSession = WebClientSession.create(lzClient);
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
        // 检测是否为目录分享链接 (含 /s/、/b/ 路径段或 b0 开头的路径段)
        if (sUrl.matches(".*/(s|b)/[^/]+.*") || sUrl.matches(".*/b0[^/]+.*")) {
            fail("该链接为蓝奏云目录分享，请使用目录解析接口");
            return;
        }
        // 若仍是校验页 (parse()中cookie域名与实际URL不匹配时会出现), 重试一次
        if (hasAcwArg1(html)) {
            webClientSession = WebClientSession.create(lzClient);
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
            boolean pwdPage = html.contains("down_p") || html.contains("id=\"pwd\"") || html.contains("id='pwd'");
            if (pwdPage && (pwd == null || pwd.isBlank())) {
                fail("需要访问密码");
                return;
            }
            try {
                if (!postAjaxFromHtml(sUrl, html, pwd)) {
                    fail("未找到下载参数，可能密码错误或分享已失效 htmlLen=" + html.length()
                            + " hasFn=" + html.contains("/fn?")
                            + " hasIframe=" + html.contains("iframe"));
                }
            } catch (Exception e) {
                fail(e, "js引擎执行失败 htmlLen=" + html.length()
                        + " hasFn=" + html.contains("/fn?")
                        + " hasIframe=" + html.contains("iframe"));
            }
        } else {
            // 没有密码
            String iframePath = matcher.group(1);
            String absoluteURI = joinUrl(SHARE_URL_PREFIX, iframePath);
            // 创建局部副本，避免修改实例字段导致累积
            MultiMap headersCopy = MultiMap.caseInsensitiveMultiMap().addAll(headers0);
            headersCopy.add("Referer", absoluteURI);
            webClientSession.getAbs(absoluteURI).putHeaders(headersCopy).send().onSuccess(res2 -> {
                try {
                    String html2 = asText(res2);
                    handleIframeHtml(html2, sUrl, absoluteURI, iframePath, headersCopy);
                } catch (Exception e) {
                    fail("蓝奏云 iframe 响应处理异常: {}", e.getMessage());
                }
            }).onFailure(handleFail(SHARE_URL_PREFIX));
        }
    }

    private void handleIframeHtml(String html2, String sUrl, String absoluteURI, String iframePath, MultiMap headersCopy) {
        if (isShareCancelledPage(html2)) {
            fail("分享已失效或文件已取消分享");
            return;
        }
        if (hasAcwArg1(html2)) {
            if (!setCookie(html2, absoluteURI)) {
                fail("蓝奏云反爬 arg1 Cookie 解析失败，页面内容异常");
                return;
            }
            webClientSession.getAbs(absoluteURI).putHeaders(headersCopy).send().onSuccess(res3 -> {
                try {
                    String html3 = asText(res3);
                    if (isShareCancelledPage(html3)) {
                        fail("分享已失效或文件已取消分享");
                        return;
                    }
                    submitIframeAjax(html3, sUrl, absoluteURI, iframePath);
                } catch (Exception e) {
                    fail("蓝奏云 iframe 响应处理异常: {}", e.getMessage());
                }
            }).onFailure(handleFail(absoluteURI));
            return;
        }
        submitIframeAjax(html2, sUrl, absoluteURI, iframePath);
    }

    private void submitIframeAjax(String iframeHtml, String sUrl, String absoluteURI, String iframePath) {
        try {
            if (!postAjaxFromHtml(absoluteURI, iframeHtml, null)) {
                fail(SHARE_URL_PREFIX + iframePath + " -> " + sUrl + ": 获取失败0, 可能分享已失效");
            }
        } catch (ScriptException | NoSuchMethodException e) {
            fail(e, "js引擎执行失败");
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
            throw new RuntimeException("获取失败1, 可能分享已失效 htmlLen=" + (html == null ? 0 : html.length())
                    + " hasFn=" + (html != null && html.contains("/fn?"))
                    + " hasScript=" + (html != null && html.contains("<script")));
        }
        if (pwd != null) {
            String quoted = "\"" + pwd.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            jsText = jsText.replace("document.getElementById('pwd').value", quoted);
            jsText = jsText.replace("document.getElementById(\"pwd\").value", quoted);
            jsText = jsText.replace("document.querySelector('#pwd').value", quoted);
            jsText = jsText.replace("document.querySelector(\"#pwd\").value", quoted);
        }
        int i = jsText.indexOf(subText);
        if (i > 0) {
            jsText = jsText.substring(0, i);
        }
        return jsText;
    }

    private String getJsText(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        Matcher m = P_INLINE_SCRIPT.matcher(html);
        String lastAjax = null;
        while (m.find()) {
            String body = m.group(1).replaceAll("<!--.*?-->", "").trim();
            if (body.isEmpty() || body.contains("var arg1=") || body.contains("arg1='")) {
                continue;
            }
            if (body.contains("$.ajax") || body.contains("down_p") || body.contains("wp_sign")
                    || body.contains("ajaxdata") || body.contains("filemoreajax")) {
                lastAjax = body;
            }
        }
        if (lastAjax != null) {
            return lastAjax;
        }
        String jsTagStart = "<script type=\"text/javascript\">";
        int index = html.lastIndexOf(jsTagStart);
        if (index == -1) {
            return null;
        }
        int startPos = index + jsTagStart.length();
        int endPos = html.indexOf("</script>", startPos);
        if (endPos < 0) {
            return null;
        }
        String fallback = html.substring(startPos, endPos).replaceAll("<!--.*-->", "");
        if (fallback.contains("var arg1=") || fallback.contains("arg1='")) {
            return null;
        }
        if (fallback.contains("$.ajax") || fallback.contains("wp_sign") || fallback.contains("down_p")
                || fallback.contains("ajaxdata") || fallback.contains("filemoreajax")) {
            return fallback;
        }
        return null;
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

    private boolean postAjaxFromHtml(String referer, String html, String pwd)
            throws ScriptException, NoSuchMethodException {
        AjaxCall call = extractAjaxFromHtml(html, pwd);
        if (call != null) {
            getDownURL(referer, call);
            return true;
        }
        String jsText;
        try {
            jsText = (pwd != null && !pwd.isBlank())
                    ? getJsByPwd(pwd, html, "document.getElementById('rpt')")
                    : getJsText(html);
        } catch (RuntimeException e) {
            return false;
        }
        if (jsText == null) {
            return false;
        }
        String fun = (pwd != null && !pwd.isBlank() && jsText.contains("down_p")) ? "down_p" : null;
        ScriptObjectMirror mirror = JsExecUtils.executeDynamicJs(jsText, fun, pwd);
        if (mirror == null) {
            return false;
        }
        getDownURL(referer, mirror);
        return true;
    }

    /** 页面里提取出的 ajax 调用：相对路径 + 表单参数。 */
    record AjaxCall(String path, Map<String, String> form) {
        MultiMap toForm() {
            MultiMap m = MultiMap.caseInsensitiveMultiMap();
            form.forEach(m::set);
            return m;
        }
    }

    private void getDownURL(String referer, AjaxCall call) {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("url", call.path());
        obj.put("data", call.form());
        getDownURL(referer, obj);
    }

    static AjaxCall extractAjaxFromHtml(String html, String pwd) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        Matcher ajax = P_AJAX_PATH.matcher(html);
        if (!ajax.find()) {
            return null;
        }
        String ajaxPath = ajax.group(1);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("action", "downprocess");
        Matcher wp = P_WP_SIGN.matcher(html);
        Matcher ad = P_AJAXDATA.matcher(html);
        Matcher isngis = P_ISNGIS.matcher(html);
        String lastIsngis = null;
        while (isngis.find()) {
            if (!isngis.group(1).isEmpty()) {
                lastIsngis = isngis.group(1);
            }
        }
        String kd = "1";
        Matcher kdns = P_KDNS.matcher(html);
        if (kdns.find()) {
            kd = kdns.group(1);
        }
        if (wp.find()) {
            data.put("sign", wp.group(1));
            if (ad.find()) {
                data.put("websignkey", ad.group(1));
                data.put("signs", ad.group(1));
            }
            data.put("websign", "");
            Matcher ws = P_WEBSIGN.matcher(html);
            if (ws.find()) {
                data.put("websign", ws.group(1));
            }
            data.put("kd", kd);
            data.put("ves", "1");
            if (pwd != null && !pwd.isEmpty()) {
                data.put("p", pwd);
            }
        } else if (lastIsngis != null) {
            data.put("sign", lastIsngis);
            data.put("kd", kd);
            if (pwd != null && !pwd.isEmpty()) {
                data.put("p", pwd);
            }
        } else {
            List<String> signs = new ArrayList<>();
            Matcher sm = P_SIGN.matcher(html);
            while (sm.find()) {
                signs.add(sm.group(1));
            }
            if (signs.isEmpty()) {
                return null;
            }
            data.put("sign", signs.size() > 1 ? signs.get(1) : signs.get(0));
            if (pwd != null && !pwd.isEmpty()) {
                data.put("p", pwd);
            }
            data.put("kd", kd);
            Matcher ad2 = P_AJAXDATA.matcher(html);
            if (ad2.find()) {
                data.put("websignkey", ad2.group(1));
                data.put("signs", ad2.group(1));
            }
        }
        return new AjaxCall("/" + ajaxPath, data);
    }

    static AjaxCall extractFolderAjax(String html, String pwd) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        Matcher block = P_FILEMORE.matcher(html);
        if (!block.find()) {
            return null;
        }
        Map<String, String> data = new LinkedHashMap<>();
        Matcher kv = P_DATA_KV.matcher(block.group(2));
        while (kv.find()) {
            String key = kv.group(1);
            String raw = kv.group(2);
            if ("pwd".equals(key)) {
                data.put(key, pwd == null ? "" : pwd);
                continue;
            }
            if ("pg".equals(key) || "pgs".equals(key)) {
                data.put("pg", "1");
                continue;
            }
            data.put(key, resolveJsValue(html, raw));
        }
        if (!data.containsKey("fid") || !data.containsKey("t") || !data.containsKey("k")) {
            return null;
        }
        if (pwd != null && !pwd.isEmpty()) {
            data.put("pwd", pwd);
        }
        return new AjaxCall(block.group(1), data);
    }

    private static String resolveJsValue(String html, String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.length() >= 2 && raw.charAt(0) == '\'' && raw.charAt(raw.length() - 1) == '\'') {
            return raw.substring(1, raw.length() - 1);
        }
        if (raw.matches("\\d+")) {
            return raw;
        }
        Matcher m = Pattern.compile("var\\s+" + Pattern.quote(raw) + "\\s*=\\s*'([^']*)'").matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        if ("pgs".equals(raw)) {
            return "1";
        }
        return raw;
    }

    private static void parseFormString(MultiMap map, String form) {
        if (form == null || form.isBlank()) {
            return;
        }
        for (String part : form.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                map.add(part.substring(0, eq), part.substring(eq + 1));
            }
        }
    }

    private void getDownURL(String key, Map<String, ?> obj) {
        if (obj == null) {
            fail("需要访问密码");
            return;
        }
        Object dataObj = obj.get("data");
        if (dataObj == null) {
            fail("需要访问密码");
            return;
        }
        String url0 = String.valueOf(obj.get("url"));
        MultiMap map = MultiMap.caseInsensitiveMultiMap();
        if (dataObj instanceof CharSequence) {
            parseFormString(map, dataObj.toString());
        } else if (dataObj instanceof Map<?, ?> signMap) {
            signMap.forEach((k, v) -> {
                if (k != null) {
                    map.add(k.toString(), v == null ? "" : v.toString());
                }
            });
        } else {
            fail("需要访问密码");
            return;
        }
        MultiMap headers = HeaderUtils.parseHeaders("""
                Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7
                Accept-Encoding: identity
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
        String url = joinUrl(SHARE_URL_PREFIX, url0);
        webClientSession.postAbs(url).putHeaders(headers).sendForm(map).onSuccess(res2 -> {
            try {
                JsonObject urlJson = asJson(res2);
                Object infVal = urlJson.getValue("inf");
                String name = infVal instanceof CharSequence ? infVal.toString() : null;
                Integer zt = urlJson.getInteger("zt");
                if (zt == null || zt != 1) {
                    fail(name != null ? name : String.valueOf(infVal));
                    return;
                }
                // 文件名
                if (name != null) {
                    Object fi = shareLinkInfo.getOtherParam().get("fileInfo");
                    if (fi instanceof FileInfo fileInfo) {
                        fileInfo.setFileName(name);
                    }
                }

                String downUrl = urlJson.getString("dom") + "/file/" + urlJson.getString("url");
                followFileUrl(downUrl, headers);
            } catch (Exception e) {
                fail(e, "解析异常");
            }
        }).onFailure(handleFail(url));
    }

    /**
     * 下载域已不再走 arg1/acw_sc__v2 挑战页；带 down_ip=1 时通常直接 302。
     * 未跳转则走页面 down_r → POST /ajax.php 二次验证。
     */
    private void followFileUrl(String downUrl, MultiMap headers) {
        String origin = originOf(downUrl, "https://developer2.lanrar.com");
        putSessionCookie("down_ip", "1", downUrl, ".lanrar.com");
        headers.set("referer", origin);
        webClientSession.getAbs(downUrl).putHeaders(headers).send()
                .onSuccess(res3 -> {
                    try {
                        String location = res3.headers().get("Location");
                        if (location != null) {
                            setDateAndComplete(location);
                            return;
                        }
                        String text = asText(res3);
                        if (isShareCancelledPage(text)) {
                            fail(downUrl + " -> 分享已失效或文件已取消分享");
                            return;
                        }
                        if (text.contains("down_r") && text.contains("ajax.php")) {
                            verifyDownloadPage(origin, downUrl, text, headers);
                            return;
                        }
                        if (hasAcwArg1(text)) {
                            retryFileUrlWithAcw(downUrl, text, headers);
                            return;
                        }
                        fail(downUrl + " -> 直链获取失败2, 可能分享已失效");
                    } catch (Exception e) {
                        fail("蓝奏云直链响应处理异常: {}", e.getMessage());
                    }
                })
                .onFailure(handleFail(downUrl));
    }

    private void verifyDownloadPage(String origin, String downUrl, String html, MultiMap headers) {
        Matcher fileM = Pattern.compile("'file'\\s*:\\s*'([^']+)'").matcher(html);
        Matcher signM = Pattern.compile("'sign'\\s*:\\s*'([^']+)'").matcher(html);
        if (!fileM.find() || !signM.find()) {
            fail(downUrl + " -> 二次验证参数缺失");
            return;
        }
        String file = fileM.group(1);
        String sign = signM.group(1);
        WebClientVertxInit.get().setTimer(2000, id -> postVerifyAjax(origin, downUrl, file, sign, headers, false));
    }

    private void postVerifyAjax(String origin, String downUrl, String file, String sign, MultiMap headers, boolean filePath) {
        MultiMap map = MultiMap.caseInsensitiveMultiMap();
        map.add("file", file);
        map.add("el", "2");
        map.add("sign", sign);
        String ajaxUrl = origin + (filePath ? "/file/ajax.php" : "/ajax.php");
        headers.set("referer", origin);
        webClientSession.postAbs(ajaxUrl).putHeaders(headers).sendForm(map).onSuccess(res -> {
            try {
                JsonObject json = asJson(res);
                Integer zt = json.getInteger("zt");
                String u = json.getString("url");
                if (zt != null && zt == 1 && u != null && u.startsWith("http") && !u.contains("SignError")) {
                    setDateAndComplete(u);
                    return;
                }
                if (!filePath) {
                    postVerifyAjax(origin, downUrl, file, sign, headers, true);
                    return;
                }
                fail(downUrl + " -> 二次验证失败: " + (u != null ? u : String.valueOf(json.getValue("inf"))));
            } catch (Exception e) {
                fail("二次验证解析异常: {}", e.getMessage());
            }
        }).onFailure(handleFail(ajaxUrl));
    }

    private void retryFileUrlWithAcw(String downUrl, String text, MultiMap headers) {
        String arg1 = extractAcwArg1(text);
        if (arg1 == null) {
            fail(downUrl + " -> 直链获取失败2, 可能分享已失效");
            return;
        }
        String acw_sc__v2 = AcwScV2Generator.acwScV2Simple(arg1);
        putSessionCookie("acw_sc__v2", acw_sc__v2, downUrl, ".lanrar.com");
        headers.set("referer", originOf(downUrl, "https://developer2.lanrar.com"));
        webClientSession.getAbs(downUrl).putHeaders(headers).send()
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
    }

    private void putSessionCookie(String name, String value, String url, String fallbackDomain) {
        String domain = fallbackDomain;
        try {
            java.net.URL urlObj = new java.net.URL(url);
            String host = urlObj.getHost();
            int firstDot = host.indexOf('.');
            if (firstDot >= 0) {
                domain = host.substring(firstDot);
            }
        } catch (MalformedURLException ignored) {}
        DefaultCookie nettyCookie = new DefaultCookie(name, value);
        nettyCookie.setDomain(domain);
        nettyCookie.setPath("/");
        nettyCookie.setSecure(false);
        nettyCookie.setHttpOnly(false);
        webClientSession.cookieStore().put(nettyCookie);
    }

    private static String originOf(String url, String fallback) {
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getProtocol() + "://" + u.getHost();
        } catch (MalformedURLException e) {
            return fallback;
        }
    }

    private static String joinUrl(String base, String path) {
        if (path == null || path.isBlank()) {
            return base;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
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
                    webClientSession = WebClientSession.create(lzClient);
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
        // 检测是否为文件分享链接 (不含 /s/、/b/ 路径段且不含 b0 开头的路径段)
        if (!sUrl.matches(".*/(s|b)/[^/]+.*") && !sUrl.matches(".*/b0[^/]+.*")) {
            promise.tryFail(baseMsg() + "该链接为蓝奏云文件分享，请使用文件解析接口");
            return;
        }
        try {
            AjaxCall call = extractFolderAjax(html, pwd);
            if (call == null) {
                String jsText = getJsByPwd(pwd, html, "var urls =window.location.href");
                ScriptObjectMirror scriptObjectMirror = JsExecUtils.executeDynamicJs(jsText, "file", pwd);
                Map<String, Object> data = CastUtil.cast(scriptObjectMirror.get("data"));
                Map<String, String> form = new LinkedHashMap<>();
                data.forEach((k, v) -> form.put(k, String.valueOf(v)));
                call = new AjaxCall("/filemoreajax.php?file=" + form.get("fid"), form);
            }
            log.debug("解析参数: {}", call.form());
            MultiMap headers = getHeaders(sUrl);
            MultiMap map = call.toForm();

            String url = joinUrl(SHARE_URL_PREFIX, call.path());
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
            JsonObject fileListJson = parseLzJson(responseBody);
            if (fileListJson == null) {
                fileListJson = new JsonObject(responseBody);
            }
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
