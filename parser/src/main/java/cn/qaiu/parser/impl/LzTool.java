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
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.ScriptException;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * 蓝奏云解析工具
 *
 * @author QAIU
 */
public class LzTool extends PanBase {

    private final WebClientSession webClientSession;

    /** 实测服务器出口 IP 上 wwww.lanzoux.com 可用，且个性域名目录内文件 ID 也能打开。 */
    public static final String SHARE_URL_PREFIX = "https://wwww.lanzoux.com/";
    private static final String SHARE_ORIGIN = "https://wwww.lanzoux.com";
    /** 分享页 / ajax 请求超时 */
    private static final long REQUEST_TIMEOUT = 8000;
    /** 下载域跳转与二次验证超时，链路更长 */
    private static final long DOWNLOAD_TIMEOUT = 10000;
    private static final Pattern IFRAME_FN = Pattern.compile(
            "src\\s*=\\s*[\"'](/fn\\?[^\"'\\s>]+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_WP_SIGN = Pattern.compile("wp_sign\\s*=\\s*'([^']+)'");
    private static final Pattern P_AJAXDATA = Pattern.compile("ajaxdata\\s*=\\s*'([^']+)'");
    private static final Pattern P_WEBSIGN = Pattern.compile("'websign'\\s*:\\s*'([^']*)'");
    private static final Pattern P_WEBPAGE = Pattern.compile("[?&]webpage=([^&\"'\\s#]+)");
    private static final Pattern P_AJAX_PATH = Pattern.compile("(?:['\"/]|^)(ajax(?:m|file)\\.php\\?file=\\d+)");
    private static final Pattern P_SIGN = Pattern.compile("'sign'\\s*:\\s*'([^']+)'");
    private static final Pattern P_ISNGIS = Pattern.compile("var\\s+isngis\\s*=\\s*'([^']+)'");
    private static final Pattern P_VERIFY_FILE = Pattern.compile("'file'\\s*:\\s*'([^']+)'");
    /** 蓝奏「取消分享 / 地址错误」提示页 */
    private static final Pattern P_OFF_MSG = Pattern.compile("class=\"off1\"></div>\\s*</div>\\s*([^<]{2,80})<");
    /** ESA 反爬挑战页特征 */
    private static final String ARG1_MARK = "var arg1='";
    private static final Pattern P_FILEMORE = Pattern.compile(
            "url\\s*:\\s*'(/filemoreajax\\.php\\?file=\\d+)'[\\s\\S]*?data\\s*:\\s*\\{([^}]+)\\}");
    private static final Pattern P_DATA_KV = Pattern.compile("'(\\w+)'\\s*:\\s*('(?:\\\\'|[^'])*'|\\d+|\\w+)");
    /** CDN 直链里的 yyyy/mm/dd 分享日期 */
    private static final Pattern P_URL_DATE = Pattern.compile("\\d{4}/\\d{1,2}/\\d{1,2}");
    private static final Pattern P_INLINE_SCRIPT =
            Pattern.compile("(?is)<script(?![^>]*\\bsrc\\s*=)[^>]*>(.*?)</script>");
    private static final Pattern P_FI_NAME =
            Pattern.compile("padding: 56px 0px 20px 0px;\">(.*?)<|filenajax\">(.*?)<"
                    + "|class=\"b\"><span>(.*?)</span>");
    private static final Pattern P_FI_SIZE =
            Pattern.compile(">文件大小：</span>(.*?)<br>|\"n_filesize\">大小：(.*?)</div>"
                    + "|文件大小：</div><div class=\"fileinforight\">(.*?)</div>");
    private static final Pattern P_FI_AUTHOR =
            Pattern.compile(">分享用户：</span><font>(.*?)</font>|获取<span>(.*?)</span>的文件|\"user-name\">(.*?)</"
                    + "|分享用户：</div>[\\s\\S]{0,400}?<font[^>]*>(.*?)</font>");
    private static final Pattern P_FI_DESC =
            Pattern.compile("(?s)文件描述：</span><br>(.*?)</td>|class=\"n_box_des\">(.*?)</div>");
    private static final Pattern P_FI_ID = Pattern.compile("\\?f=(.*?)&|fid = (.*?);");
    private static final Pattern P_FI_TIME = Pattern.compile(">上传时间：</span>(.*?)<");
    /** 分享页导航头，只读共享；需要改写时先 addAll 到新 MultiMap。 */
    private static final MultiMap PAGE_HEADERS = HeaderUtils.parseHeaders("""
        Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7
        Accept-Encoding: identity
        Accept-Language: zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6
        Cache-Control: max-age=0
        DNT: 1
        Priority: u=0, i
        Sec-CH-UA: "Chromium";v="134", "Not:A-Brand";v="24", "Google Chrome";v="134"
        Sec-CH-UA-Mobile: ?0
        Sec-CH-UA-Platform: "Windows"
        Sec-Fetch-Dest: document
        Sec-Fetch-Mode: navigate
        Sec-Fetch-Site: cross-site
        Sec-Fetch-User: ?1
        Upgrade-Insecure-Requests: 1
        User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36
        """);

    private static final String DOWN_AJAX_HEADERS = """
            Accept: application/json, text/javascript, */*; q=0.01
            Accept-Encoding: identity
            Accept-Language: zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6
            Cache-Control: no-cache
            Content-Type: application/x-www-form-urlencoded
            Pragma: no-cache
            Sec-Fetch-Dest: empty
            Sec-Fetch-Mode: cors
            Sec-Fetch-Site: same-origin
            User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36
            X-Requested-With: XMLHttpRequest
            sec-ch-ua: "Chromium";v="134", "Not:A-Brand";v="24", "Google Chrome";v="134"
            sec-ch-ua-mobile: ?0
            sec-ch-ua-platform: "Windows"
            """;

    private static final String VERIFY_AJAX_HEADERS = """
            Accept: application/json, text/javascript, */*; q=0.01
            Accept-Encoding: identity
            Accept-Language: zh-CN,zh;q=0.9
            Content-Type: application/x-www-form-urlencoded
            Sec-Fetch-Dest: empty
            Sec-Fetch-Mode: cors
            Sec-Fetch-Site: same-origin
            X-Requested-With: XMLHttpRequest
            """;

    public LzTool() {
        super();
        this.webClientSession = WebClientSession.create(createLzClient(null));
    }

    public LzTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
        this.webClientSession = WebClientSession.create(createLzClient(shareLinkInfo));
    }

    /**
     * ESA 对 gzip 响应常见不带可识别的 Content-Encoding，需客户端自动解压；
     * 开源版没有 createBaseClientOptions，这里按 PanBase 同样规则带上 proxy，避免绕过全局代理。
     */
    private static WebClient createLzClient(ShareLinkInfo shareLinkInfo) {
        WebClientOptions opts = new WebClientOptions()
                .setConnectTimeout(10000)
                .setFollowRedirects(false)
                .setDecompressionSupported(true)
                .setUserAgentEnabled(false)
                .setIdleTimeout(12);
        if (shareLinkInfo != null && shareLinkInfo.getOtherParam().containsKey("proxy")) {
            JsonObject proxy = (JsonObject) shareLinkInfo.getOtherParam().get("proxy");
            if (proxy != null && proxy.getString("host") != null && proxy.getInteger("port") != null) {
                ProxyOptions proxyOptions = new ProxyOptions()
                        .setType(ProxyType.valueOf(proxy.getString("type", "http").toUpperCase()))
                        .setHost(proxy.getString("host"))
                        .setPort(proxy.getInteger("port"));
                String username = proxy.getString("username");
                if (username != null && !username.isEmpty()) {
                    proxyOptions.setUsername(username);
                }
                String password = proxy.getString("password");
                if (password != null && !password.isEmpty()) {
                    proxyOptions.setPassword(password);
                }
                opts.setProxyOptions(proxyOptions);
            }
        }
        if (opts.getConnectTimeout() <= 0) {
            opts.setConnectTimeout(10000);
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

    private static String jsonString(JsonObject json, String key) {
        Object v = json.getValue(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "0".equals(s) || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return s;
    }

    /** 目录文件下载接口会先用 shareKey="-" 占位，不能当成真实分享路径。 */
    private static boolean isPlaceholderShare(String u) {
        if (u == null || u.isBlank()) {
            return true;
        }
        String path = lzSharePath(u);
        return path.isEmpty() || "-".equals(path);
    }

    /** 统一改写到 {@link #SHARE_URL_PREFIX}，只保留分享路径。 */
    private String resolveShareUrl() {
        String u = shareLinkInfo.getShareUrl();
        if (isPlaceholderShare(u)) {
            u = shareLinkInfo.getStandardUrl();
        }
        if (isPlaceholderShare(u)) {
            String key = shareLinkInfo.getShareKey();
            if (key == null || key.isBlank() || "-".equals(key)) {
                return SHARE_URL_PREFIX;
            }
            return SHARE_URL_PREFIX + key;
        }
        int q = u.indexOf('?');
        String query = null;
        if (q > 0) {
            query = u.substring(q + 1);
            u = u.substring(0, q);
        }
        int schemeEnd = u.indexOf("://");
        if (schemeEnd < 0) {
            return appendWebpage(u, query);
        }
        int pathStart = u.indexOf('/', schemeEnd + 3);
        if (pathStart < 0) {
            String key = shareLinkInfo.getShareKey();
            return appendWebpage(SHARE_URL_PREFIX + (key == null ? "" : key), query);
        }
        return appendWebpage(SHARE_ORIGIN + u.substring(pathStart), query);
    }

    /** 目录文件 ID 可能带 webpage=，必须保留；pwd 走独立字段，不拼进 URL。 */
    private static String appendWebpage(String url, String query) {
        if (query == null || query.isBlank()) {
            return url;
        }
        for (String part : query.split("&")) {
            if (part.startsWith("webpage=")) {
                return url + "?" + part;
            }
        }
        return url;
    }

    public Future<String> parse() {
        String sUrl = resolveShareUrl();
        String rawPwd = shareLinkInfo.getSharePassword();
        final String pwd = "-".equals(rawPwd) ? null : rawPwd;

        getWithArg1Retry(sUrl, PAGE_HEADERS)
                .onSuccess(html -> doParser(html, pwd, sUrl))
                .onFailure(handleFail(sUrl));
        return promise.future();
    }

    private void doParser(String html, String pwd, String sUrl) {
        if (isLzFolderShare(sUrl, html)) {
            fail("该链接为蓝奏云目录分享，请使用目录解析接口");
            return;
        }
        setFileInfo(html, shareLinkInfo);
        Matcher matcher = IFRAME_FN.matcher(html);
        if (!matcher.find()) {
            if (html.contains("down_p") || html.contains("id=\"pwd\"") || html.contains("id='pwd'")) {
                if (pwd == null || pwd.isBlank()) {
                    fail("需要访问密码");
                    return;
                }
                if (!postAjaxFromHtml(sUrl, html, pwd)) {
                    fail("密码页未找到下载参数，可能密码错误或分享已失效");
                }
            } else {
                Matcher offMatcher = P_OFF_MSG.matcher(html);
                if (offMatcher.find()) {
                    fail(offMatcher.group(1).trim());
                } else {
                    fail("未找到下载入口，可能被风控拦截或分享已失效");
                }
            }
            return;
        }
        String iframePath = matcher.group(1);
        String absoluteURI = joinUrl(originOf(sUrl, SHARE_ORIGIN) + "/", iframePath);
        getWithArg1Retry(absoluteURI, PAGE_HEADERS)
                .onSuccess(iframeHtml -> {
                    if (!postAjaxFromHtml(absoluteURI, iframeHtml, null)) {
                        fail(absoluteURI + " -> " + sUrl + ": 获取失败0, 可能分享已失效");
                    }
                })
                .onFailure(handleFail(absoluteURI));
    }

    private Future<String> getWithArg1Retry(String url, MultiMap headers) {
        return sendWithArg1Retry(url,
                () -> webClientSession.getAbs(url).timeout(REQUEST_TIMEOUT).putHeaders(headers).send());
    }

    private Future<String> postFormWithArg1Retry(String url, MultiMap headers, MultiMap form) {
        return sendWithArg1Retry(url,
                () -> webClientSession.postAbs(url).timeout(REQUEST_TIMEOUT).putHeaders(headers).sendForm(form));
    }

    /** ESA 挑战页只需算出 acw_sc__v2 后原样重放一次，各解析步骤共用。 */
    private Future<String> sendWithArg1Retry(String url, Supplier<Future<HttpResponse<Buffer>>> sender) {
        return sender.get().map(this::asText).compose(html -> {
            if (html == null || !html.contains(ARG1_MARK)) {
                return Future.succeededFuture(html);
            }
            if (!applyArg1Cookie(html, url)) {
                return Future.failedFuture("蓝奏云反爬 arg1 Cookie 解析失败，页面内容异常");
            }
            return sender.get().map(this::asText).compose(retried ->
                    retried != null && retried.contains(ARG1_MARK)
                            ? Future.failedFuture("蓝奏云反爬校验失败，请稍后重试")
                            : Future.succeededFuture(retried));
        });
    }

    private boolean applyArg1Cookie(String html, String url) {
        int beginIndex = html.indexOf("arg1='") + 6;
        int endIndex = html.indexOf("';", beginIndex);
        if (beginIndex < 6 || endIndex == -1 || endIndex <= beginIndex) {
            return false;
        }
        String arg1 = html.substring(beginIndex, endIndex);
        putSessionCookie("acw_sc__v2", AcwScV2Generator.acwScV2Simple(arg1), url, null);
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

    private boolean postAjaxFromHtml(String referer, String html, String pwd) {
        AjaxCall call = extractAjaxFromHtml(html, pwd);
        if (call == null) {
            return false;
        }
        getDownURL(referer, call);
        return true;
    }

    /** 页面里提取出的 ajax 调用：相对路径 + 表单参数。 */
    private record AjaxCall(String path, Map<String, String> form) {
        MultiMap toForm() {
            MultiMap m = MultiMap.caseInsensitiveMultiMap();
            form.forEach(m::set);
            return m;
        }
    }

    private static AjaxCall extractAjaxFromHtml(String html, String pwd) {
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
            data.put("kd", "1");
            data.put("ves", "1");
            if (pwd != null && !pwd.isEmpty()) {
                data.put("p", pwd);
            }
        } else if (lastIsngis != null) {
            data.put("sign", lastIsngis);
            data.put("kd", "1");
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
            data.put("kd", "1");
            Matcher ad2 = P_AJAXDATA.matcher(html);
            if (ad2.find()) {
                data.put("websignkey", ad2.group(1));
                data.put("signs", ad2.group(1));
            }
        }
        return new AjaxCall("/" + ajaxPath, data);
    }

    private static AjaxCall extractFolderAjax(String html, String pwd) {
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

    private String getJsText(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        Matcher m = P_INLINE_SCRIPT.matcher(html);
        String lastAjax = null;
        while (m.find()) {
            String body = m.group(1).replaceAll("<!--.*?-->", "").trim();
            if (body.isEmpty()) {
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
        if (fallback.contains("$.ajax") || fallback.contains("wp_sign") || fallback.contains("down_p")
                || fallback.contains("ajaxdata") || fallback.contains("filemoreajax")) {
            return fallback;
        }
        return null;
    }

    private void getDownURL(String referer, AjaxCall call) {
        MultiMap headers = HeaderUtils.parseHeaders(DOWN_AJAX_HEADERS);
        // 个性域名 ajaxfile.php 会立刻 inf=已超时；POST 必须打 wwww。iframe 请求 Referer 用 iframe 地址。
        headers.set("referer", referer != null && !referer.isBlank() ? referer : resolveShareUrl());
        String url = joinUrl(SHARE_ORIGIN + "/", call.path());
        postFormWithArg1Retry(url, headers, call.toForm())
                .onSuccess(this::handleAjaxDownResponse)
                .onFailure(handleFail(url));
    }

    private void handleAjaxDownResponse(String text) {
        JsonObject urlJson = parseLzJson(text);
        if (urlJson == null) {
            String preview = text == null ? "空响应" : text.substring(0, Math.min(200, text.length()));
            fail("ajax 返回非JSON: " + preview);
            return;
        }
        Object infVal = urlJson.getValue("inf");
        String name = infVal instanceof CharSequence ? infVal.toString() : null;
        Integer zt = urlJson.getInteger("zt");
        if (zt == null || zt != 1) {
            fail(name != null ? name : String.valueOf(infVal));
            return;
        }
        if (name != null) {
            Object fi = shareLinkInfo.getOtherParam().get("fileInfo");
            if (fi instanceof FileInfo fileInfo) {
                fileInfo.setFileName(name);
            }
        }
        String dom = jsonString(urlJson, "dom");
        String token = jsonString(urlJson, "url");
        if (dom == null || token == null) {
            fail("ajax 未返回下载地址");
            return;
        }
        String downUrl = dom + "/file/" + token;
        followFileUrl(downUrl, false);
    }

    /**
     * 中间链：部分出口仍 302；多数（含机房 IP）固定出「验证并下载」页。
     * dmpdmp 等下载域仍会出 arg1 挑战（Windows UA 更常见），必须算 acw_sc__v2 再请求。
     * 必须等约 2s 后 POST /file/ajax.php el=2 拿 CDN 直链；立刻 POST 会 ?SignError。
     * 中间页不能当成功结果返回——用户侧裸打开永远是验证 HTML。
     */
    private void followFileUrl(String downUrl, boolean arg1Retried) {
        String host = hostOf(downUrl);
        String parent = registrableDomain(host);
        putSessionCookie("down_ip", "1", downUrl, parent != null ? parent : "lanrar.com");
        if (parent != null) {
            DefaultCookie parentCookie = new DefaultCookie("down_ip", "1");
            parentCookie.setDomain(parent);
            parentCookie.setPath("/");
            webClientSession.cookieStore().put(parentCookie);
        }

        String origin = originOf(downUrl, "https://developer2.lanrar.com");
        MultiMap h = lanrarPageHeaders(origin);
        webClientSession.getAbs(downUrl).timeout(DOWNLOAD_TIMEOUT).putHeaders(h).send()
                .onSuccess(res -> {
                    String location = firstHttpLocation(res);
                    if (isIosInstallRedirect(location)) {
                        log.warn("下载域按 iOS 在线安装跳转，忽略 plist: {}", location);
                    } else if (isDirectLink(location)) {
                        completeDownloadLink(location);
                        return;
                    }
                    String html = asText(res);
                    if (html != null && html.contains(ARG1_MARK)) {
                        if (!arg1Retried && applyArg1Cookie(html, downUrl)) {
                            followFileUrl(downUrl, true);
                            return;
                        }
                        fail("蓝奏下载域反爬校验失败，请稍后重试");
                        return;
                    }
                    if (html != null && html.contains("down_r") && html.contains("ajax.php")) {
                        postVerifyAjax(downUrl, origin, html, 0);
                        return;
                    }
                    log.warn("下载域未出验证页也未跳转 status={} host={}", res.statusCode(), host);
                    fail("蓝奏下载域未返回直链，请稍后重试");
                })
                .onFailure(t -> {
                    log.warn("下载域请求失败: {}", t.getMessage());
                    fail("蓝奏下载域请求失败: " + t.getMessage());
                });
    }

    private void postVerifyAjax(String downUrl, String origin, String html, int attempt) {
        Matcher file = P_VERIFY_FILE.matcher(html);
        Matcher sign = P_SIGN.matcher(html);
        if (!file.find() || !sign.find()) {
            fail("蓝奏验证页缺少 file/sign");
            return;
        }
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("file", file.group(1));
        form.add("el", "2");
        form.add("sign", sign.group(1));
        String ajaxUrl = origin + "/file/ajax.php";
        MultiMap h = lanrarAjaxHeaders(downUrl);
        h.set("Origin", origin);
        // 验证页 loading ~2s；过早 POST → zt=1 但 url=?SignError
        long delay = attempt == 0 ? 2200 : 2000;
        WebClientVertxInit.get().setTimer(delay, id ->
                webClientSession.postAbs(ajaxUrl).timeout(DOWNLOAD_TIMEOUT).putHeaders(h).sendForm(form)
                        .onSuccess(res -> {
                            JsonObject json = parseLzJson(asText(res));
                            String url = json == null ? null : jsonString(json, "url");
                            if (isDirectLink(url)) {
                                completeDownloadLink(url);
                                return;
                            }
                            log.warn("验证 ajax 未返回直链 attempt={} url={}", attempt, url);
                            if (attempt < 1) {
                                retryVerifyWithFreshPage(downUrl, origin, attempt);
                                return;
                            }
                            fail("蓝奏二次验证未返回直链");
                        })
                        .onFailure(t -> {
                            log.warn("验证 ajax 失败 attempt={}: {}", attempt, t.getMessage());
                            if (attempt < 1) {
                                retryVerifyWithFreshPage(downUrl, origin, attempt);
                                return;
                            }
                            fail("蓝奏二次验证请求失败: " + t.getMessage());
                        }));
    }

    /** SignError / 请求失败都要重新拉验证页换新 file/sign，复用旧 sign 必然再失败。 */
    private void retryVerifyWithFreshPage(String downUrl, String origin, int attempt) {
        webClientSession.getAbs(downUrl).timeout(DOWNLOAD_TIMEOUT).putHeaders(lanrarPageHeaders(origin)).send()
                .onSuccess(res -> {
                    String html = asText(res);
                    if (html != null && html.contains("down_r")) {
                        postVerifyAjax(downUrl, origin, html, attempt + 1);
                    } else {
                        fail("蓝奏二次验证失败");
                    }
                })
                .onFailure(t -> fail("蓝奏二次验证刷新失败: " + t.getMessage()));
    }

    private void completeDownloadLink(String location) {
        if (isIntermediateLanrar(location) || isIosInstallRedirect(location)) {
            fail("蓝奏未解析到 CDN 直链");
            return;
        }
        completeWithShareDate(location);
    }

    private static boolean isIntermediateLanrar(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = url.toLowerCase();
        return u.contains("lanrar.com/file/") || (u.contains("/file/?") && u.contains("lanrar"));
    }

    /** Mac/iOS UA 会把 ipa 当成「Safari 在线安装」，302 到 itms-services/plist，不是直链。 */
    private static boolean isIosInstallRedirect(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = url.toLowerCase();
        return u.startsWith("itms-services:") || u.startsWith("itms:")
                || u.contains(".plist")
                || u.contains("ipa.lanrar.com");
    }

    /** 最后一步 GET 用完整浏览器导航头，裸 curl / 无 UA 会固定落到验证页。 */
    private static MultiMap lanrarPageHeaders(String referer) {
        MultiMap h = MultiMap.caseInsensitiveMultiMap();
        h.addAll(PAGE_HEADERS);
        h.set("referer", referer);
        return h;
    }

    private static MultiMap lanrarAjaxHeaders(String referer) {
        MultiMap h = HeaderUtils.parseHeaders(VERIFY_AJAX_HEADERS);
        h.set("referer", referer);
        copyHeader(h, "User-Agent", "User-Agent");
        copyHeader(h, "Sec-CH-UA", "sec-ch-ua");
        copyHeader(h, "Sec-CH-UA-Mobile", "sec-ch-ua-mobile");
        copyHeader(h, "Sec-CH-UA-Platform", "sec-ch-ua-platform");
        return h;
    }

    private static void copyHeader(MultiMap target, String from, String to) {
        String v = PAGE_HEADERS.get(from);
        if (v != null && !v.isBlank()) {
            target.set(to, v);
        }
    }

    private static String firstHttpLocation(HttpResponse<?> res) {
        String loc = res.headers().get("Location");
        if (loc == null) {
            loc = res.headers().get("location");
        }
        return loc;
    }

    private static boolean isDirectLink(String url) {
        if (url == null || url.isBlank() || url.contains("SignError") || url.contains("/file/?")) {
            return false;
        }
        if (isIosInstallRedirect(url)) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private void putSessionCookie(String name, String value, String url, String fallbackDomain) {
        DefaultCookie hostCookie = new DefaultCookie(name, value);
        hostCookie.setPath("/");
        hostCookie.setSecure(false);
        hostCookie.setHttpOnly(false);
        webClientSession.cookieStore().put(hostCookie);
        String host = null;
        try {
            host = new URL(url).getHost();
        } catch (Exception ignored) {}
        if (host == null || host.isBlank()) {
            host = fallbackDomain;
        }
        if (host != null && host.startsWith(".")) {
            host = host.substring(1);
        }
        if (host != null && !host.isBlank()) {
            DefaultCookie domainCookie = new DefaultCookie(name, value);
            domainCookie.setDomain(host);
            domainCookie.setPath("/");
            domainCookie.setSecure(false);
            domainCookie.setHttpOnly(false);
            webClientSession.cookieStore().put(domainCookie);
        }
    }

    private static String originOf(String url, String fallback) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String hostOf(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /** u1305138.dmpdmp.com → dmpdmp.com；developer2.lanrar.com → lanrar.com */
    private static String registrableDomain(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        int last = host.lastIndexOf('.');
        if (last <= 0) {
            return host;
        }
        int prev = host.lastIndexOf('.', last - 1);
        return prev >= 0 ? host.substring(prev + 1) : host;
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

    private void completeWithShareDate(String downloadUrl) {
        // 分享时间 提取url中的时间戳格式：lanzoui.com/abc/abc/yyyy/mm/dd/
        Matcher matcher = P_URL_DATE.matcher(downloadUrl);
        if (matcher.find() && shareLinkInfo.getOtherParam().get("fileInfo") instanceof FileInfo fileInfo) {
            fileInfo.setCreateTime(matcher.group().replace("/", "-"));
        }
        complete(downloadUrl);
    }

    /** 目录列表 filemoreajax.php 用移动端 UA。 */
    private static MultiMap folderListHeaders(String referer) {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.set("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/111.0.0.0 Mobile Safari/537.36");
        headers.set("referer", referer);
        headers.set("sec-ch-ua-platform", "Android");
        headers.set("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
        headers.set("sec-ch-ua-mobile", "?1");
        return headers;
    }


    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> listPromise = Promise.promise();

        final String sUrl = resolveShareUrl();
        final String pwd = shareLinkInfo.getSharePassword();

        getWithArg1Retry(sUrl, PAGE_HEADERS)
                .onSuccess(html -> handleFileListParse(html, pwd, sUrl, listPromise))
                .onFailure(listPromise::fail);
        return listPromise.future();
    }

    private void handleFileListParse(String html, String pwd, String sUrl, Promise<List<FileInfo>> listPromise) {
        if (!isLzFolderShare(sUrl, html)) {
            listPromise.fail(baseMsg() + "该链接为蓝奏云文件分享，请使用文件解析接口");
            return;
        }
        try {
            String webpage = extractWebpage(sUrl, html);
            if (webpage != null) {
                shareLinkInfo.getOtherParam().put("webpage", webpage);
            }
            AjaxCall call = extractFolderAjax(html, pwd);
            if (call == null) {
                call = folderAjaxFromJs(html, pwd);
            }
            log.debug("解析参数: {}", call.form());

            String url = joinUrl(originOf(sUrl, SHARE_ORIGIN) + "/", call.path());
            postFormWithArg1Retry(url, folderListHeaders(sUrl), call.toForm())
                    .onSuccess(body -> handleFileListResponse(body, listPromise))
                    .onFailure(listPromise::fail);
        } catch (ScriptException | NoSuchMethodException | RuntimeException e) {
            listPromise.fail(e);
        }
    }

    /** 正则匹配不到 filemoreajax 块时，回退到执行页面 JS 取参数。 */
    private AjaxCall folderAjaxFromJs(String html, String pwd) throws ScriptException, NoSuchMethodException {
        String jsText = getJsByPwd(pwd, html, "var urls =window.location.href");
        ScriptObjectMirror mirror = JsExecUtils.executeDynamicJs(jsText, "file");
        Map<String, Object> raw = CastUtil.cast(mirror.get("data"));
        Map<String, String> form = new LinkedHashMap<>();
        raw.forEach((k, v) -> form.put(k, String.valueOf(v)));
        return new AjaxCall("/filemoreajax.php?file=" + form.get("fid"), form);
    }

    private boolean isLzFolderShare(String sUrl, String html) {
        return isLzFolderUrl(sUrl)
                || isLzFolderUrl(shareLinkInfo.getShareUrl())
                || isLzFolderUrl(shareLinkInfo.getShareKey())
                || isLzFolderHtml(html);
    }

    /** 目录：shareKey 以 b 开头（/b710887、/b0xxx），或 /s/ 自定义目录。没有 /b/ 这种路径。 */
    static boolean isLzFolderUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String path = lzSharePath(url);
        if (path.isEmpty()) {
            return false;
        }
        if (path.startsWith("s/")) {
            return true;
        }
        // 单段路径才是分享 key；/tp/xxx 是手机端文件页
        if (path.indexOf('/') >= 0) {
            return false;
        }
        return path.charAt(0) == 'b' || path.charAt(0) == 'B';
    }

    private static boolean isLzFolderHtml(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        return html.contains("filemoreajax") || html.contains("id=\"infos\"") || html.contains("id='infos'");
    }

    private static String lzSharePath(String url) {
        String u = url.trim();
        int q = u.indexOf('?');
        if (q >= 0) {
            u = u.substring(0, q);
        }
        int hash = u.indexOf('#');
        if (hash >= 0) {
            u = u.substring(0, hash);
        }
        int schemeEnd = u.indexOf("://");
        if (schemeEnd >= 0) {
            int pathStart = u.indexOf('/', schemeEnd + 3);
            if (pathStart < 0) {
                return "";
            }
            u = u.substring(pathStart + 1);
        } else if (u.indexOf('/') >= 0 && u.indexOf('.') >= 0 && u.indexOf('.') < u.indexOf('/')) {
            u = u.substring(u.indexOf('/') + 1);
        }
        while (u.startsWith("/")) {
            u = u.substring(1);
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private void handleFileListResponse(String responseBody, Promise<List<FileInfo>> listPromise) {
        try {
            JsonObject fileListJson = parseLzJson(responseBody);
            if (fileListJson == null) {
                listPromise.fail(baseMsg() + "目录列表返回非JSON");
                return;
            }
            Integer zt = fileListJson.getInteger("zt");
            if (zt == null || zt != 1) {
                listPromise.fail(baseMsg() + fileListJson.getString("info"));
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
                        .put("fileName", fileName)
                        .put("shareKey", shareLinkInfo.getShareKey());
                String webpage = currentWebpage();
                if (webpage != null) {
                    paramJson.put("webpage", webpage);
                }
                String param = CommonUtils.urlBase64Encode(paramJson.encode());
                fileInfo.setFileName(fileName)
                        .setFileId(id)
                        .setCreateTime(fileJson.getString("time"))
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
            listPromise.complete(list);
        } catch (Exception e) {
            listPromise.fail(e);
        }
    }

    @Override
    public Future<String> parseById() {
        JsonObject paramJson = (JsonObject) shareLinkInfo.getOtherParam().get("paramJson");
        String id = paramJson.getString("id");
        if (id == null || id.isBlank()) {
            fail("缺少文件ID");
            return promise.future();
        }
        // getFileDownInfo 用 shareKey="-" 占位；目录内文件要按文件ID打开，并带上 webpage
        String webpage = paramJson.getString("webpage");
        if (webpage == null || webpage.isBlank()) {
            webpage = extractWebpage(shareLinkInfo.getShareUrl(), null);
        }
        String fileUrl = SHARE_URL_PREFIX + id;
        if (webpage != null && !webpage.isBlank()) {
            fileUrl = fileUrl + "?webpage=" + webpage;
        }
        shareLinkInfo.setShareKey(id);
        shareLinkInfo.setShareUrl(fileUrl);
        shareLinkInfo.setStandardUrl(fileUrl);
        return parse();
    }

    private String currentWebpage() {
        Object cached = shareLinkInfo.getOtherParam().get("webpage");
        if (cached instanceof String s && !s.isBlank()) {
            return s;
        }
        return extractWebpage(shareLinkInfo.getShareUrl(), null);
    }

    private static String extractWebpage(String url, String html) {
        if (url != null) {
            Matcher m = P_WEBPAGE.matcher(url);
            if (m.find()) {
                return m.group(1);
            }
        }
        if (html != null) {
            Matcher m = P_WEBPAGE.matcher(html);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    void setFileInfo(String html, ShareLinkInfo shareLinkInfo) {
        FileInfo fileInfo = new FileInfo();
        shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
        try {
            String sizeStr = CommonUtils.extract(html, P_FI_SIZE);
            fileInfo.setFileName(CommonUtils.extract(html, P_FI_NAME))
                    .setCreateBy(CommonUtils.extract(html, P_FI_AUTHOR))
                    .setPanType(shareLinkInfo.getType())
                    .setDescription(CommonUtils.extract(html, P_FI_DESC))
                    .setFileType("file")
                    .setFileId(CommonUtils.extract(html, P_FI_ID))
                    .setCreateTime(CommonUtils.extract(html, P_FI_TIME));
            if (sizeStr != null && !sizeStr.isBlank()) {
                long bytes = FileSizeConverter.convertToBytes(sizeStr);
                fileInfo.setSize(bytes).setSizeStr(FileSizeConverter.convertToReadableSize(bytes));
            }
        } catch (Exception e) {
            log.warn("文件信息解析异常", e);
        }
    }
}
