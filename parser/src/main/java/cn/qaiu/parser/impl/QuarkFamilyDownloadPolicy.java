package cn.qaiu.parser.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 夸克 / UC 单文件下载策略。
 * <p>
 * 体积不大于 {@link #NORMAL_PARSE_MAX_BYTES}（10MiB，即 {@code 10 * 1024 * 1024}，等于阈值仍走原解析）时，
 * 调用方保持现有分享下载。更大且能读到体积的文件：先用会员接口确认未过期的 SVIP/VIP，
 * 再用该账号已有的 {@code /file/download} 直链；否则只接受 H5 预览返回、并经匿名探测确认为文件字节的
 * {@code https://dl-c-* .drive.quark.cn|drive.uc.cn} 地址，附上真实文件名。
 * 预览网页、HTML、JSON、m3u8 播放列表和登录态 CDN 都不当作下载成功。
 * UC 不因为尝试 H5 就变成免登录。
 * </p>
 */
public final class QuarkFamilyDownloadPolicy {

    /** 10MiB。仓库没有另一套 10MB 常量，按二进制兆字节 {@code 10 * 1024 * 1024}。 */
    public static final long NORMAL_PARSE_MAX_BYTES = 10L * 1024 * 1024;

    private static final Set<String> PAID_TYPES = Set.of(
            "SUPER_VIP", "EXP_SVIP", "Z_VIP", "VIP", "EXP_VIP");
    private static final List<String> BARE_QUERY = List.of("auth_key", "sp", "token");
    private static final String GUEST_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int PROBE_PREFIX_BYTES = 64;

    private QuarkFamilyDownloadPolicy() {
    }

    /** 已知体积且严格大于 10MiB。缺少体积或无法解析时返回 false，调用方继续原解析。 */
    public static boolean knownLarge(long size) {
        return size > NORMAL_PARSE_MAX_BYTES && size < Long.MAX_VALUE;
    }

    public static long sizeOf(JsonObject item) {
        if (item == null || !item.containsKey("size")) {
            return Long.MAX_VALUE;
        }
        return sizeOf(item.getValue("size"));
    }

    public static long sizeOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return Long.MAX_VALUE;
            }
        }
        return Long.MAX_VALUE;
    }

    public static final class Membership {
        public final boolean validPaid;
        public final String memberType;
        public final Long expAtMs;
        public final String reason;

        Membership(boolean validPaid, String memberType, Long expAtMs, String reason) {
            this.validPaid = validPaid;
            this.memberType = memberType;
            this.expAtMs = expAtMs;
            this.reason = reason;
        }

        static Membership notPaid(String type, Long expAtMs, String reason) {
            return new Membership(false, type, expAtMs, reason);
        }
    }

    /**
     * 只认会员接口的 {@code member_type} 和 {@code exp_at}。Cookie 是否存在不参与判断。
     * 体验会员（EXP_SVIP / EXP_VIP）和 Z_VIP 在到期前算有效；NORMAL、未知档位、缺少到期时间或已到期都无效。
     */
    public static Membership judgeMembership(JsonObject data, long nowMs) {
        if (data == null) {
            return Membership.notPaid(null, null, "会员接口没有 data");
        }
        String raw = data.getString("member_type");
        if (raw == null || raw.isBlank()) {
            return Membership.notPaid(null, null, "会员接口没有 member_type");
        }
        String type = raw.trim().toUpperCase(Locale.ROOT);
        Long expAt = epochMillis(data.getValue("exp_at"));
        if (!PAID_TYPES.contains(type)) {
            return Membership.notPaid(type, expAt, "当前档位不是未过期的 SVIP/VIP");
        }
        if (expAt == null) {
            return Membership.notPaid(type, null, "会员接口没有 exp_at，不能确认未过期");
        }
        if (expAt <= nowMs) {
            return Membership.notPaid(type, expAt, "会员已过期");
        }
        return new Membership(true, type, expAt, null);
    }

    static Long epochMillis(Object value) {
        Long raw = null;
        if (value instanceof Number number) {
            raw = number.longValue();
        } else if (value instanceof String text && !text.isBlank()) {
            try {
                raw = Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (raw == null || raw <= 0) {
            return null;
        }
        return raw < 100_000_000_000L ? raw * 1000L : raw;
    }

    public static Future<Membership> fetchMembership(WebClient client, MultiMap apiHeaders, boolean uc) {
        String url = uc
                ? "https://pc-api.uc.cn/1/clouddrive/member?pr=UCBrowser&fr=pc&fetch_subscribe=true&_ch=home&fetch_identity=true"
                : "https://drive-pc.quark.cn/1/clouddrive/member?pr=ucpro&fr=pc&fetch_subscribe=true&_ch=home&fetch_identity=true";
        return fetchMembership(client, apiHeaders, url, uc);
    }

    static Future<Membership> fetchMembership(WebClient client, MultiMap apiHeaders, String memberUrl, boolean uc) {
        String cookie = apiHeaders == null ? null : apiHeaders.get(HttpHeaders.COOKIE);
        if (cookie == null || cookie.isBlank()) {
            return Future.succeededFuture(Membership.notPaid(null, null, "未配置 Cookie"));
        }
        String referer = uc ? "https://drive.uc.cn/" : "https://pan.quark.cn/";
        String ua = apiHeaders.get(HttpHeaders.USER_AGENT);
        if (ua == null || ua.isBlank()) {
            ua = GUEST_UA;
        }
        return client.getAbs(memberUrl)
                .putHeader(HttpHeaders.COOKIE.toString(), cookie)
                .putHeader(HttpHeaders.USER_AGENT.toString(), ua)
                .putHeader(HttpHeaders.REFERER.toString(), referer)
                .timeout(10_000)
                .send()
                .map(res -> membershipFromResponse(res))
                .recover(err -> Future.succeededFuture(
                        Membership.notPaid(null, null, "会员接口不可用")));
    }

    static Membership membershipFromResponse(HttpResponse<?> res) {
        int status = res.statusCode();
        if (status == 401 || status == 403) {
            return Membership.notPaid(null, null, "会员接口拒绝访问 HTTP " + status);
        }
        String type = res.getHeader(HttpHeaders.CONTENT_TYPE.toString());
        if (type != null && type.toLowerCase(Locale.ROOT).contains("text/html")) {
            return Membership.notPaid(null, null, "会员接口返回了 HTML");
        }
        JsonObject json;
        try {
            json = res.bodyAsJsonObject();
        } catch (Exception e) {
            return Membership.notPaid(null, null, "会员接口没有返回 JSON");
        }
        if (json == null) {
            return Membership.notPaid(null, null, "会员接口没有返回 JSON");
        }
        Integer code = json.getInteger("code");
        if (code != null && code != 0) {
            return Membership.notPaid(null, null, "会员接口失败 code=" + code);
        }
        return judgeMembership(json.getJsonObject("data"), System.currentTimeMillis());
    }

    /**
     * H5 预览签发的公开文件地址。不带登录 Cookie。探测只读响应头和最多 64 字节，不把文件收进内存。
     */
    public static Future<String> resolveH5Download(WebClient client, Vertx vertx, String pan,
                                                    String pwdId, String stoken, String fid,
                                                    String fidToken, String fileName) {
        if (isBlank(fid) || isBlank(fidToken) || isBlank(pwdId) || isBlank(stoken)) {
            return Future.failedFuture("分享参数不完整，无法请求 H5 预览");
        }
        if (isBlank(fileName)) {
            return Future.failedFuture("没有真实文件名，不能把预览地址当作下载");
        }
        return requestRichText(client, pan, pwdId, stoken, fid, fidToken)
                .compose(raw -> acceptProbed(vertx, pan, raw, fileName))
                .recover(richErr -> requestVideo(client, pan, pwdId, stoken, fid, fidToken)
                        .compose(raw -> acceptProbed(vertx, pan, raw, fileName))
                        .recover(videoErr -> Future.failedFuture(joinReasons(richErr, videoErr))));
    }

    private static Future<String> acceptProbed(Vertx vertx, String pan, String raw, String fileName) {
        if (raw != null && raw.toLowerCase(Locale.ROOT).contains(".m3u8")) {
            return Future.failedFuture("只得到 m3u8 播放列表，不是文件");
        }
        if (!isPublicFileUrl(raw, pan)) {
            return Future.failedFuture("预览地址不是公开文件 CDN");
        }
        return probeBody(vertx, raw, pan, 1).compose(hit -> {
            if (!hit.ok) {
                return Future.failedFuture(hit.reason);
            }
            return Future.succeededFuture(withFilename(bareFileUrl(raw), fileName));
        });
    }

    private static Future<String> requestRichText(WebClient client, String pan, String pwdId, String stoken,
                                                   String fid, String fidToken) {
        boolean uc = isUc(pan);
        String base = uc ? "https://pc-api.uc.cn/1/clouddrive" : "https://drive.quark.cn/1/clouddrive";
        String site = uc ? "https://drive.uc.cn" : "https://vt.quark.cn";
        return requestPreview(client, pan, base, "h5", site, "/share/sharepage/rich_text/preview",
                pwdId, stoken, fid, fidToken, false);
    }

    private static Future<String> requestVideo(WebClient client, String pan, String pwdId, String stoken,
                                                String fid, String fidToken) {
        List<String[]> attempts = isUc(pan)
                ? List.of(
                new String[]{"https://pc-api.uc.cn/1/clouddrive", "h5", "https://drive.uc.cn"},
                new String[]{"https://pc-api.uc.cn/1/clouddrive", "pc", "https://drive.uc.cn"})
                : List.of(
                new String[]{"https://drive.quark.cn/1/clouddrive", "h5", "https://vt.quark.cn"},
                new String[]{"https://drive.quark.cn/1/clouddrive", "pc", "https://pan.quark.cn"},
                new String[]{"https://drive-h.quark.cn/1/clouddrive", "h5", "https://pan.quark.cn"});
        return requestVideoAt(client, pan, attempts, 0, pwdId, stoken, fid, fidToken, null);
    }

    private static Future<String> requestVideoAt(WebClient client, String pan, List<String[]> attempts, int index,
                                                  String pwdId, String stoken, String fid, String fidToken,
                                                  Throwable last) {
        if (index >= attempts.size()) {
            return Future.failedFuture(last == null ? new IllegalStateException("视频预览没有公开文件地址") : last);
        }
        String[] attempt = attempts.get(index);
        return requestPreview(client, pan, attempt[0], attempt[1], attempt[2],
                "/share/sharepage/video/preview", pwdId, stoken, fid, fidToken, true)
                .recover(err -> requestVideoAt(client, pan, attempts, index + 1, pwdId, stoken, fid, fidToken, err));
    }

    private static Future<String> requestPreview(WebClient client, String pan, String base, String fr, String site,
                                                  String path, String pwdId, String stoken, String fid,
                                                  String fidToken, boolean video) {
        String pr = isUc(pan) ? "UCBrowser" : "ucpro";
        var req = client.getAbs(base + path)
                .putHeader(HttpHeaders.USER_AGENT.toString(), GUEST_UA)
                .putHeader(HttpHeaders.ACCEPT.toString(), "application/json, text/plain, */*")
                .putHeader(HttpHeaders.REFERER.toString(), site + "/")
                .putHeader("Origin", site)
                .putHeader("x-clouddrive-st", "null")
                .addQueryParam("fid", fid)
                .addQueryParam("fid_token", fidToken)
                .addQueryParam("fr", fr)
                .addQueryParam("pr", pr)
                .addQueryParam("pwd_id", pwdId)
                .addQueryParam("stoken", stoken);
        if (video) {
            req.addQueryParam("resolution", "low,normal,high,super,2k,4k");
            req.addQueryParam("supports", "fmp4,m3u8,dolby_vision");
        }
        return req.timeout(10_000).send().compose(res -> interpretPreview(res, pan, video));
    }

    static Future<String> interpretPreview(HttpResponse<?> res, String pan, boolean video) {
        int status = res.statusCode();
        String contentType = res.getHeader(HttpHeaders.CONTENT_TYPE.toString());
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
            return Future.failedFuture("H5 预览返回了 HTML 页面，不是文件");
        }
        if (status >= 400) {
            return Future.failedFuture("H5 预览 HTTP " + status);
        }
        JsonObject json;
        try {
            json = res.bodyAsJsonObject();
        } catch (Exception e) {
            return Future.failedFuture("H5 预览不是 JSON");
        }
        if (json == null) {
            return Future.failedFuture("H5 预览不是 JSON");
        }
        int code = json.getInteger("code", -1);
        if (code != 0) {
            return Future.failedFuture("H5 预览失败: " + sanitize(json.getString("message", "code=" + code)));
        }
        JsonObject data = json.getJsonObject("data", new JsonObject());
        if (video) {
            String picked = pickProgressiveFileUrl(data, pan);
            if (isBlank(picked)) {
                return Future.failedFuture("视频预览没有公开文件地址");
            }
            return Future.succeededFuture(picked);
        }
        String download = data.getString("download_url", "");
        if (isBlank(download)) {
            return Future.failedFuture("H5 预览没有 download_url");
        }
        return Future.succeededFuture(download);
    }

    /** 从视频预览里选公开 CDN 上的渐进文件。m3u8 和登录态节点丢掉。 */
    static String pickProgressiveFileUrl(JsonObject data, String pan) {
        if (data == null) {
            return "";
        }
        String best = "";
        int bestWidth = -1;
        JsonArray list = data.getJsonArray("video_list");
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                Object raw = list.getValue(i);
                if (!(raw instanceof JsonObject item)) {
                    continue;
                }
                JsonObject info = item.getJsonObject("video_info");
                String candidate = publicFileCandidate(info == null ? "" : info.getString("url", ""), pan);
                if (isBlank(candidate)) {
                    candidate = publicFileCandidate(item.getString("url", ""), pan);
                }
                if (isBlank(candidate) || candidate.toLowerCase(Locale.ROOT).contains(".m3u8")) {
                    continue;
                }
                int width = widthOf(info != null ? info : item);
                if (width >= bestWidth) {
                    best = candidate;
                    bestWidth = width;
                }
            }
        }
        if (isBlank(best)) {
            String direct = publicFileCandidate(data.getString("download_url", ""), pan);
            if (!isBlank(direct) && !direct.toLowerCase(Locale.ROOT).contains(".m3u8")) {
                best = direct;
            }
        }
        return best;
    }

    private static String publicFileCandidate(String url, String pan) {
        if (isBlank(url) || !isPublicFileUrl(bareFileUrl(url), pan)) {
            return "";
        }
        return withoutFilename(url);
    }

    private static int widthOf(JsonObject obj) {
        if (obj == null || !obj.containsKey("width")) {
            return 0;
        }
        Object value = obj.getValue("width");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    /**
     * 公开文件 CDN。登录态节点（dl-pc、dl-guest、*.u-drive）和预览网页都不是。
     */
    public static boolean isPublicFileUrl(String url, String pan) {
        if (isBlank(url) || !url.startsWith("https://")) {
            return false;
        }
        String host = hostOf(url);
        if (host == null) {
            return false;
        }
        String suffix = isUc(pan) ? ".drive.uc.cn" : ".drive.quark.cn";
        return host.startsWith("dl-c-")
                && host.endsWith(suffix)
                && !host.contains(".u-drive.")
                && host.length() > "dl-c-".length() + suffix.length();
    }

    private static String hostOf(String url) {
        int start = "https://".length();
        int end = url.length();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        if (end <= start) {
            return null;
        }
        String host = url.substring(start, end);
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        return host.toLowerCase(Locale.ROOT);
    }

    public static String bareFileUrl(String url) {
        if (isBlank(url) || !url.startsWith("http")) {
            return url;
        }
        int q = url.indexOf('?');
        if (q < 0) {
            return url;
        }
        String base = url.substring(0, q);
        StringBuilder kept = new StringBuilder();
        for (String pair : url.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            if (BARE_QUERY.contains(key)) {
                kept.append(kept.length() == 0 ? '?' : '&').append(pair);
            }
        }
        return base + kept;
    }

    public static String withoutFilename(String url) {
        if (isBlank(url) || url.indexOf('?') < 0 || !url.contains("filename=")) {
            return url;
        }
        int q = url.indexOf('?');
        String base = url.substring(0, q);
        StringBuilder kept = new StringBuilder();
        for (String pair : url.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            if ("filename".equals(key)) {
                continue;
            }
            kept.append(kept.length() == 0 ? '?' : '&').append(pair);
        }
        return base + kept;
    }

    /** 登录态接口如果实际给了公开 CDN，去掉埋点并补上文件名，调用方不要再附带 Cookie / Referer。 */
    public static String presentPublicDownload(String url, String pan, String fileName) {
        if (!isPublicFileUrl(bareFileUrl(url), pan)) {
            return url;
        }
        String bare = bareFileUrl(url);
        if (isBlank(fileName) || bare.contains("filename=")) {
            return bare;
        }
        return withFilename(bare, fileName);
    }

    /** CDN 用 {@code filename} 生成 Content-Disposition。跨域时浏览器会忽略 a[download]。 */
    public static String withFilename(String url, String fileName) {
        if (isBlank(url) || isBlank(fileName)) {
            return url;
        }
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return url + (url.contains("?") ? "&" : "?") + "filename=" + encoded;
    }

    static final class ProbeHit {
        final boolean ok;
        final String reason;
        final int prefixLength;

        ProbeHit(boolean ok, String reason, int prefixLength) {
            this.ok = ok;
            this.reason = reason;
            this.prefixLength = prefixLength;
        }

        static ProbeHit ok(int prefixLength) {
            return new ProbeHit(true, null, prefixLength);
        }

        static ProbeHit fail(String reason) {
            return new ProbeHit(false, reason, 0);
        }
    }

    /**
     * 匿名探测。只连接已经通过 {@link #isPublicFileUrl} 的地址；测试可直接调用以检查截断和 HTML 判断。
     * 读满 {@value #PROBE_PREFIX_BYTES} 字节后立刻断开，不缓冲整份文件。
     */
    static Future<ProbeHit> probeBody(Vertx vertx, String url, String pan, int redirectsLeft) {
        if (isBlank(url) || !(url.startsWith("https://") || url.startsWith("http://127.0.0.1")
                || url.startsWith("http://localhost"))) {
            return Future.succeededFuture(ProbeHit.fail("拒绝探测非公开文件地址"));
        }
        if (url.startsWith("https://") && !isPublicFileUrl(url, pan)) {
            return Future.succeededFuture(ProbeHit.fail("拒绝探测非公开文件地址"));
        }
        HttpClient client = vertx.createHttpClient(new HttpClientOptions()
                .setConnectTimeout(8_000)
                .setIdleTimeout(10)
                .setKeepAlive(false)
                .setMaxHeaderSize(16 * 1024)
                .setTrustAll(false)
                .setVerifyHost(true));
        RequestOptions options = new RequestOptions()
                .setMethod(HttpMethod.GET)
                .setAbsoluteURI(url)
                .setTimeout(8_000);
        Promise<ProbeHit> promise = Promise.promise();
        client.request(options).compose(request -> {
            request.setFollowRedirects(false);
            request.putHeader(HttpHeaders.USER_AGENT.toString(), GUEST_UA);
            request.putHeader(HttpHeaders.ACCEPT.toString(), "*/*");
            request.putHeader("Range", "bytes=0-15");
            return request.send();
        }).compose(resp -> readPrefix(resp).compose(prefix -> {
            int status = resp.statusCode();
            if (status >= 300 && status < 400 && redirectsLeft > 0) {
                String location = resp.getHeader(HttpHeaders.LOCATION.toString());
                if (location != null && isPublicFileUrl(location, pan)) {
                    return probeBody(vertx, location, pan, redirectsLeft - 1);
                }
                return Future.succeededFuture(ProbeHit.fail("跳转目标不是公开文件 CDN"));
            }
            return Future.succeededFuture(judgeProbe(status,
                    resp.getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                    resp.getHeader("Content-Range"),
                    resp.getHeader(HttpHeaders.CONTENT_LENGTH.toString()),
                    prefix));
        })).onComplete(ar -> {
            client.close();
            if (ar.succeeded()) {
                promise.tryComplete(ar.result());
            } else {
                promise.tryComplete(ProbeHit.fail("探测失败"));
            }
        });
        return promise.future();
    }

    private static Future<Buffer> readPrefix(HttpClientResponse resp) {
        Promise<Buffer> promise = Promise.promise();
        Buffer prefix = Buffer.buffer();
        boolean[] finished = {false};
        Runnable finish = () -> {
            if (finished[0]) {
                return;
            }
            finished[0] = true;
            try {
                resp.request().reset();
            } catch (Exception ignored) {
                // 对端已结束时 reset 会失败，前缀已经够用。
            }
            promise.tryComplete(prefix);
        };
        resp.handler(buf -> {
            if (prefix.length() < PROBE_PREFIX_BYTES) {
                int n = Math.min(buf.length(), PROBE_PREFIX_BYTES - prefix.length());
                prefix.appendBuffer(buf, 0, n);
            }
            if (prefix.length() >= PROBE_PREFIX_BYTES) {
                finish.run();
            }
        });
        resp.endHandler(v -> finish.run());
        resp.exceptionHandler(err -> {
            if (!finished[0]) {
                promise.tryFail(err);
            }
        });
        resp.fetch(PROBE_PREFIX_BYTES);
        return promise.future();
    }

    static ProbeHit judgeProbe(int status, String contentType, String contentRange, String contentLength, Buffer prefix) {
        if (status != 200 && status != 206) {
            return ProbeHit.fail("HTTP " + status);
        }
        if (looksLikeHtml(contentType, prefix)) {
            return ProbeHit.fail("响应是 HTML，不是文件");
        }
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json")) {
            return ProbeHit.fail("响应是 JSON，不是文件");
        }
        boolean ranged = status == 206 && contentRange != null && !contentRange.isBlank();
        long length = -1;
        if (contentLength != null && !contentLength.isBlank()) {
            try {
                length = Long.parseLong(contentLength.trim());
            } catch (NumberFormatException ignored) {
                length = -1;
            }
        }
        if (!ranged && length == 0) {
            return ProbeHit.fail("响应没有文件内容");
        }
        if (!ranged && (contentType == null || contentType.isBlank())) {
            return ProbeHit.fail("响应没有 Content-Type，不能确认是文件");
        }
        return ProbeHit.ok(prefix == null ? 0 : prefix.length());
    }

    static boolean looksLikeHtml(String contentType, Buffer prefix) {
        if (contentType != null) {
            String type = contentType.toLowerCase(Locale.ROOT);
            if (type.contains("text/html") || type.contains("application/xhtml")) {
                return true;
            }
        }
        if (prefix == null || prefix.length() == 0) {
            return false;
        }
        int n = Math.min(prefix.length(), 32);
        String head = prefix.getString(0, n, "UTF-8").stripLeading().toLowerCase(Locale.ROOT);
        return head.startsWith("<!doctype") || head.startsWith("<html") || head.startsWith("<head");
    }

    /**
     * 公开文件地址不沿用目录参数里的账号请求头。返回值表示浏览器是否必须改走下载器。
     */
    public static boolean prepareDownloader(Map<String, String> headers, boolean paramNeedsDownloader, boolean publicDirect) {
        if (publicDirect) {
            if (headers != null) {
                headers.clear();
            }
            return false;
        }
        return paramNeedsDownloader || (headers != null && !headers.isEmpty());
    }

    public static String largeFileError(String pan, Throwable directError, Throwable h5Error) {
        boolean uc = isUc(pan);
        StringBuilder message = new StringBuilder();
        if (uc) {
            message.append("UC 超过 10MiB（").append(NORMAL_PARSE_MAX_BYTES)
                    .append(" 字节）的文件不能免登录下载。当前没有未过期的 SVIP/VIP，");
        } else {
            message.append("夸克超过 10MiB（").append(NORMAL_PARSE_MAX_BYTES)
                    .append(" 字节）的文件没有可用的未过期 SVIP/VIP 直链。");
        }
        message.append("H5 预览也没有返回可下载的文件地址。");
        if (h5Error != null) {
            message.append(" 预览: ").append(sanitize(h5Error.getMessage()));
        }
        if (directError != null) {
            message.append(" 账号直链: ").append(sanitize(directError.getMessage()));
        }
        if (uc) {
            message.append(" 请配置未过期的 UC SVIP/VIP Cookie：auths.uc.cookie。");
        } else {
            message.append(" 请配置未过期的夸克 SVIP/VIP Cookie：auths.qk.cookie。普通或已过期账号不会当作会员。");
        }
        return message.toString();
    }

    static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "无详情";
        }
        String text = message.replaceAll("(?i)(cookie|__pus|__puus|__kp|__kps|__uid)=[^\\s;&,]+", "$1=***");
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("<html") || lower.contains("<!doctype") || lower.contains("<head")) {
            return "上游返回了 HTML 页面";
        }
        if (text.length() > 180) {
            return text.substring(0, 180);
        }
        return text;
    }

    private static String joinReasons(Throwable first, Throwable second) {
        String a = first == null ? "" : sanitize(first.getMessage());
        String b = second == null ? "" : sanitize(second.getMessage());
        if (a.isBlank()) {
            return b;
        }
        if (b.isBlank() || b.equals(a)) {
            return a;
        }
        return a + "；" + b;
    }

    private static boolean isUc(String pan) {
        return pan != null && pan.equalsIgnoreCase("uc");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
