package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 城通新版分享接口（api.ctfile.com/url/*）。
 * <p>
 * 一次解析专用一个 HttpClient：资源接口签发的 verifycode 绑定本次连接的出口 IP，
 * 下载必须跟在同一次连接上的 resource 之后。访问令牌 access_pass 约 24 小时、按分享者复用，
 * 用来跳过重复的 ALTCHA 计算。
 */
final class CtfileSession implements AutoCloseable {

    static final String API = "https://api.ctfile.com";
    private static final String CHALLENGE_PATH = "/url/captcha/altcha?captcha_action=share_passcode";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int PAGE_SIZE = 200;
    private static final long PASS_SKEW_MILLIS = 60_000L;

    private static final ConcurrentHashMap<String, CachedPass> ACCESS_PASSES = new ConcurrentHashMap<>();

    private final HttpClient client;
    private final String origin;
    private final String referer;

    private CtfileSession(HttpClient client, String origin, String referer) {
        this.client = client;
        this.origin = origin;
        this.referer = referer;
    }

    static CtfileSession open(ShareLinkInfo info) {
        String shareUrl = info == null ? "" : info.getShareUrl();
        String origin = originOf(shareUrl);
        if (origin.isBlank()) {
            origin = "https://www.ctfile.com";
        }
        String referer = shareUrl == null || shareUrl.isBlank() ? origin + "/" : shareUrl;
        return new CtfileSession(newClient(info), origin, referer);
    }

    JsonObject loadResource(String kind, String id, ShareTarget target, String password, String turnstileToken)
            throws Exception {
        JsonObject body = getResource(kind, id, target, password, null, null, null);
        Decision decision = classify(body, 200, password);
        if (decision.kind == Kind.OK) {
            remember(decision, password, null);
            return body;
        }
        // 带密码分享的首次响应经常标成 turnstile，但提交一次 ALTCHA 后正确密码会直接 200。
        // 过期的 access_pass 同样会回到这个分支，丢弃后重新计算。
        if (shouldSolveAltcha(decision)) {
            String cached = cachedPass(decision.ownerId, password);
            if (cached != null) {
                JsonObject retry = getResource(kind, id, target, password, null, null, cached);
                Decision again = classify(retry, 200, password);
                if (again.kind == Kind.OK) {
                    remember(again, password, cached);
                    return retry;
                }
                forget(decision.ownerId, password);
            }
            JsonObject challenge = challenge();
            if (expiresSoon(challenge)) {
                challenge = challenge();
            }
            AltchaV2Solver.Solution solution = AltchaV2Solver.solve(challenge);
            String token = AltchaV2Solver.payload(challenge, solution);
            JsonObject retry = getResource(kind, id, target, password, token, null, null);
            Decision solved = classify(retry, 200, password);
            if (solved.kind == Kind.OK) {
                remember(solved, password, null);
                return retry;
            }
            if (solved.kind == Kind.ALTCHA) {
                throw new CtfileException("ALTCHA 人机验证未通过，请重试");
            }
            if (solved.kind == Kind.TURNSTILE && turnstileToken != null && !turnstileToken.isBlank()) {
                JsonObject withTurnstile = getResource(kind, id, target, password, null, turnstileToken, null);
                Decision afterTurnstile = classify(withTurnstile, 200, password);
                if (afterTurnstile.kind == Kind.OK) {
                    remember(afterTurnstile, password, null);
                    return withTurnstile;
                }
                throw new CtfileException(afterTurnstile.message);
            }
            throw new CtfileException(solved.message);
        }
        throw new CtfileException(decision.message);
    }

    /** 匿名分享优先解 ALTCHA。首次响应写成 turnstile 时，正确密码仍可能被 ALTCHA 放行。 */
    static boolean shouldSolveAltcha(Decision decision) {
        return decision != null && (decision.kind == Kind.ALTCHA || decision.kind == Kind.TURNSTILE);
    }

    DirectLink download(String id, ShareTarget target, String password, JsonObject resource) throws Exception {
        JsonObject data = dataOf(resource);
        JsonObject file = data == null ? null : data.getJsonObject("file");
        if (file == null) {
            throw new CtfileException("下载参数不完整：上游没有返回文件信息");
        }
        String pass = firstNonBlank(data.getString("access_pass"), cachedPass(ownerId(data), password));
        return postDownload(id, target, password, file, pass, 0);
    }

    JsonObject listing(String kind, String id, ShareTarget target, String password, JsonObject resource,
                       int start, int length) throws Exception {
        JsonObject data = dataOf(resource);
        String pass = data == null ? null : firstNonBlank(data.getString("access_pass"), cachedPass(ownerId(data), password));
        Map<String, String> query = resourceQuery(kind, id, target, password, null, null, pass);
        query.put("start", String.valueOf(Math.max(0, start)));
        query.put("length", String.valueOf(Math.max(1, length)));
        return exchange("GET", "/url/listing?" + form(query), null, origin, referer);
    }

    static int pageSize() {
        return PAGE_SIZE;
    }

    private DirectLink postDownload(String id, ShareTarget target, String password, JsonObject file, String pass,
                                    int waitedSeconds) throws Exception {
        if (waitedSeconds > 120) {
            throw new CtfileException("下载需要等待的时间过长");
        }
        String verify = file.getString("verifycode", "");
        String fileChk = file.getString("file_chk", "");
        if (verify.isBlank() || fileChk.isBlank() || file.getValue("file_id") == null || file.getValue("userid") == null) {
            throw new CtfileException("下载参数不完整：缺少 file_chk 或 verifycode");
        }
        JsonObject context = new JsonObject()
                .put("kind", "file")
                .put("id", id)
                .put("folder_id", target.folderId)
                .put("fk", target.fk)
                .put("passcode", password == null ? "" : password)
                .put("type_id", target.typeId);
        if (pass != null && !pass.isBlank()) {
            context.put("access_pass", pass);
        }
        JsonObject original = new JsonObject()
                .put("file_id", file.getValue("file_id"))
                .put("userid", file.getValue("userid"))
                .put("file_chk", fileChk)
                .put("start_time", file.getValue("start_time"))
                .put("wait_seconds", file.getValue("wait_seconds"));
        JsonObject body = new JsonObject()
                .put("context", context)
                .put("app", false)
                .put("verifycode", verify)
                .put("original_file", original)
                .put("carrier", 0);
        JsonObject response = exchange("POST", "/url/download", body.encode().getBytes(StandardCharsets.UTF_8),
                origin, referer);
        int top = codeOf(response, 200);
        JsonObject data = dataOf(response);
        int code = data != null && data.getValue("code") != null ? codeOf(data, top) : top;
        String message = firstNonBlank(response.getString("msg"), response.getString("message"),
                data == null ? "" : firstNonBlank(data.getString("msg"), data.getString("message")));
        if (code == 218) {
            int remaining = intValue(data == null ? null : data.getValue("remaining_seconds"),
                    intValue(response.getValue("remaining_seconds"), 1));
            remaining = Math.max(1, Math.min(remaining, 30));
            sleepSeconds(remaining);
            return postDownload(id, target, password, file, pass, waitedSeconds + remaining);
        }
        if (code == 219) {
            throw new CtfileException("需要登录后才能下载");
        }
        if (code == 403 && message.toLowerCase().contains("verification")) {
            throw new VerificationMismatch("下载校验未通过或已过期，请重试");
        }
        String downurl = data == null ? null : data.getString("downurl");
        if (code != 200 || downurl == null || downurl.isBlank()) {
            throw new CtfileException(message.isBlank()
                    ? "未返回下载地址 (code=" + code + ")"
                    : "未返回下载地址: " + message);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        if (data.getValue("headers") instanceof JsonObject headerJson) {
            for (String key : headerJson.fieldNames()) {
                Object value = headerJson.getValue(key);
                if (key != null && value != null && !value.toString().isBlank()) {
                    headers.put(key, value.toString());
                }
            }
        }
        return new DirectLink(downurl, headers);
    }

    private JsonObject getResource(String kind, String id, ShareTarget target, String password,
                                   String altchaToken, String turnstileToken, String accessPass) throws Exception {
        Map<String, String> params = resourceQuery(kind, id, target, password, altchaToken, turnstileToken, accessPass);
        return exchange("GET", "/url/resource?" + form(params), null, origin, referer);
    }

    private JsonObject challenge() throws Exception {
        return exchange("GET", CHALLENGE_PATH, null, "https://h5.ctfile.com",
                "https://h5.ctfile.com/captcha/altcha.html");
    }

    private JsonObject exchange(String method, String path, byte[] body, String requestOrigin, String requestReferer)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(API + path))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Origin", requestOrigin)
                .header("Referer", requestReferer);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        }
        HttpResponse<String> response;
        try {
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CtfileException("请求被中断");
        } catch (IOException e) {
            throw new CtfileException("请求失败: " + e.getMessage());
        }
        String text = response.body();
        if (text == null || text.isBlank()) {
            throw new CtfileException("上游返回空响应 (HTTP " + response.statusCode() + ")");
        }
        try {
            return new JsonObject(text);
        } catch (Exception e) {
            throw new CtfileException("上游返回了无法识别的响应 (HTTP " + response.statusCode() + ")");
        }
    }

    static Map<String, String> resourceQuery(String kind, String id, ShareTarget target, String password,
                                             String altchaToken, String turnstileToken, String accessPass) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("kind", kind == null || kind.isBlank() ? "file" : kind);
        query.put("id", id == null ? "" : id);
        query.put("folder_id", target.folderId);
        query.put("fk", target.fk);
        query.put("passcode", password == null ? "" : password);
        query.put("type_id", target.typeId);
        if (altchaToken != null && !altchaToken.isBlank()) {
            query.put("altcha_token", altchaToken);
        }
        if (turnstileToken != null && !turnstileToken.isBlank()) {
            query.put("turnstile_token", turnstileToken);
        }
        if (accessPass != null && !accessPass.isBlank()) {
            query.put("access_pass", accessPass);
        }
        return query;
    }

    static String form(Map<String, String> query) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    static Decision classify(JsonObject body, int httpStatus, String password) {
        int code = codeOf(body, httpStatus);
        JsonObject data = dataOf(body);
        String message = firstNonBlank(body == null ? "" : body.getString("msg"),
                body == null ? "" : body.getString("message"));
        String errorType = data == null ? "" : text(data.getValue("error_type"));
        String captcha = data == null ? "" : text(data.getValue("captcha_type"));
        JsonObject capabilities = data == null ? null : data.getJsonObject("capabilities");
        boolean passcodeRequired = capabilities != null && Boolean.TRUE.equals(capabilities.getValue("passcodeRequired"));
        String owner = capabilities == null ? "" : text(capabilities.getValue("ownerId"));
        boolean passwordMissing = password == null || password.isBlank();
        boolean captchaAsked = "captcha_required".equals(errorType) || errorType.startsWith("captcha_");

        if (code == 200) {
            return Decision.ok(owner, accessPass(data));
        }
        if (code == 404) {
            return Decision.of(Kind.NOT_FOUND, owner, "分享不存在或已过期" + suffix(message));
        }
        if (code == 403) {
            return Decision.of(Kind.INVALID, owner, "分享链接无效或已失效" + suffix(message));
        }
        if (code == 423) {
            if ("passcode_incorrect".equals(errorType)) {
                return Decision.of(Kind.BAD_PASSWORD, owner, "访问密码不正确" + suffix(message));
            }
            if (passwordMissing && passcodeRequired && !captchaAsked) {
                return Decision.of(Kind.NEED_PASSWORD, owner, "需要访问密码");
            }
            if ("turnstile".equalsIgnoreCase(captcha)) {
                return Decision.of(Kind.TURNSTILE, owner, "触发 Cloudflare Turnstile 人机验证，暂不支持服务端自动通过");
            }
            if ("altcha".equalsIgnoreCase(captcha)) {
                return Decision.of(Kind.ALTCHA, owner, "需要完成 ALTCHA 人机验证");
            }
            if (passwordMissing && passcodeRequired) {
                return Decision.of(Kind.NEED_PASSWORD, owner, "需要访问密码");
            }
            return Decision.of(Kind.OTHER, owner, "分享暂时无法访问" + suffix(message) + " (code=423)");
        }
        return Decision.of(Kind.OTHER, owner,
                "上游返回异常 (code=" + code + ")" + suffix(message));
    }

    private static void remember(Decision decision, String password, String fallbackPass) {
        String token = firstNonBlank(decision.accessPass, fallbackPass);
        if (decision.ownerId == null || decision.ownerId.isBlank() || token == null || token.isBlank()) {
            return;
        }
        long expiry = passExpiry(token);
        if (expiry < System.currentTimeMillis() + PASS_SKEW_MILLIS) {
            return;
        }
        ACCESS_PASSES.put(cacheKey(decision.ownerId, password), new CachedPass(token, expiry));
    }

    private static String cachedPass(String ownerId, String password) {
        if (ownerId == null || ownerId.isBlank()) {
            return null;
        }
        CachedPass cached = ACCESS_PASSES.get(cacheKey(ownerId, password));
        if (cached == null) {
            return null;
        }
        if (cached.expiresAtMillis < System.currentTimeMillis() + PASS_SKEW_MILLIS) {
            ACCESS_PASSES.remove(cacheKey(ownerId, password), cached);
            return null;
        }
        return cached.token;
    }

    private static void forget(String ownerId, String password) {
        if (ownerId != null && !ownerId.isBlank()) {
            ACCESS_PASSES.remove(cacheKey(ownerId, password));
        }
    }

    static void clearCachedPasses() {
        ACCESS_PASSES.clear();
    }

    private static String cacheKey(String ownerId, String password) {
        return ownerId + "\n" + (password == null ? "" : password);
    }

    private static long passExpiry(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return System.currentTimeMillis() + Duration.ofMinutes(30).toMillis();
            }
            byte[] json = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            JsonObject payload = new JsonObject(new String(json, StandardCharsets.UTF_8));
            Object expiry = payload.getValue("e");
            if (expiry instanceof Number) {
                return ((Number) expiry).longValue() * 1000L;
            }
        } catch (Exception ignored) {
            // 解析不了有效期时按半小时使用，失效后会重新计算 ALTCHA。
        }
        return System.currentTimeMillis() + Duration.ofMinutes(30).toMillis();
    }

    private static boolean expiresSoon(JsonObject challenge) {
        JsonObject parameters = challenge.getJsonObject("parameters");
        if (parameters == null) {
            return false;
        }
        Object expiry = parameters.getValue("expiresAt");
        if (!(expiry instanceof Number)) {
            return false;
        }
        long expiresAtMillis = ((Number) expiry).longValue() * 1000L;
        return expiresAtMillis < System.currentTimeMillis() + 15_000L;
    }

    private static String accessPass(JsonObject data) {
        return data == null ? "" : text(data.getValue("access_pass"));
    }

    private static String ownerId(JsonObject data) {
        if (data == null) {
            return "";
        }
        JsonObject capabilities = data.getJsonObject("capabilities");
        return capabilities == null ? "" : text(capabilities.getValue("ownerId"));
    }

    static JsonObject dataOf(JsonObject body) {
        if (body == null || !(body.getValue("data") instanceof JsonObject)) {
            return null;
        }
        return body.getJsonObject("data");
    }

    static JsonArray rowsOf(JsonObject listing) {
        JsonObject data = dataOf(listing);
        if (data == null || !(data.getValue("rows") instanceof JsonArray)) {
            return new JsonArray();
        }
        return data.getJsonArray("rows");
    }

    static boolean hasMore(JsonObject listing, int rowCount) {
        JsonObject data = dataOf(listing);
        if (data != null && data.getValue("hasMore") instanceof Boolean) {
            return data.getBoolean("hasMore");
        }
        return rowCount >= PAGE_SIZE;
    }

    private static int codeOf(JsonObject body, int fallback) {
        if (body == null) {
            return fallback;
        }
        return intValue(body.getValue("code"), fallback);
    }

    static int intValue(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static long longValue(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String suffix(String message) {
        return message == null || message.isBlank() ? "" : "（" + message + "）";
    }

    static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String padBase64(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "=".repeat(4 - mod);
    }

    private static void sleepSeconds(int seconds) throws CtfileException {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CtfileException("下载等待被中断");
        }
    }

    private static String originOf(String shareUrl) {
        if (shareUrl == null || shareUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(shareUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "";
            }
            String origin = uri.getScheme() + "://" + uri.getHost();
            return uri.getPort() > 0 ? origin + ":" + uri.getPort() : origin;
        } catch (Exception e) {
            return "";
        }
    }

    static String queryParam(String url, String name) {
        if (url == null || name == null) {
            return "";
        }
        int query = url.indexOf('?');
        if (query < 0) {
            return "";
        }
        String raw = url.substring(query + 1);
        int hash = raw.indexOf('#');
        if (hash >= 0) {
            raw = raw.substring(0, hash);
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = decode(part.substring(0, eq));
            if (name.equals(key)) {
                return decode(part.substring(eq + 1));
            }
        }
        return "";
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private static HttpClient newClient(ShareLinkInfo info) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER);
        Object proxyValue = info == null || info.getOtherParam() == null ? null : info.getOtherParam().get("proxy");
        if (proxyValue instanceof JsonObject proxy) {
            String host = proxy.getString("host");
            Integer port = proxy.getInteger("port");
            if (host != null && !host.isBlank() && port != null) {
                builder.proxy(ProxySelector.of(new InetSocketAddress(host, port)));
                String username = proxy.getString("username");
                String password = proxy.getString("password", "");
                if (username != null && !username.isBlank()) {
                    builder.authenticator(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            if (getRequestorType() == RequestorType.PROXY) {
                                return new PasswordAuthentication(username, password.toCharArray());
                            }
                            return null;
                        }
                    });
                }
            }
        }
        return builder.build();
    }

    @Override
    public void close() {
        try {
            client.getClass().getMethod("close").invoke(client);
        } catch (NoSuchMethodException ignored) {
            // JDK 17 的 HttpClient 还不能关闭，连接会按空闲超时回收。
        } catch (Exception ignored) {
            // 关闭失败不影响解析结果。
        }
    }

    static final class ShareTarget {
        final String folderId;
        final String fk;
        final String typeId;

        ShareTarget(String folderId, String fk, String typeId) {
            this.folderId = folderId == null || folderId.isBlank() ? "0" : folderId;
            this.fk = fk == null ? "" : fk;
            this.typeId = typeId == null || typeId.isBlank() ? "0" : typeId;
        }

        static ShareTarget from(ShareLinkInfo info) {
            String dirId = "";
            if (info.getOtherParam() != null && info.getOtherParam().get("dirId") != null) {
                dirId = decode(info.getOtherParam().get("dirId").toString().trim());
            }
            if (!dirId.isBlank()) {
                String[] parts = dirId.split(":", 2);
                return new ShareTarget(parts[0], parts.length > 1 ? parts[1] : "",
                        queryParam(info.getShareUrl(), "type_id"));
            }
            String shareUrl = info.getShareUrl();
            String folderId = firstNonBlank(queryParam(shareUrl, "folder_id"), queryParam(shareUrl, "d"));
            return new ShareTarget(folderId, queryParam(shareUrl, "fk"), queryParam(shareUrl, "type_id"));
        }
    }

    static final class DirectLink {
        final String url;
        final Map<String, String> headers;

        DirectLink(String url, Map<String, String> headers) {
            this.url = url;
            this.headers = headers;
        }
    }

    enum Kind {
        OK, NOT_FOUND, INVALID, NEED_PASSWORD, BAD_PASSWORD, TURNSTILE, ALTCHA, OTHER
    }

    static final class Decision {
        final Kind kind;
        final String ownerId;
        final String accessPass;
        final String message;

        private Decision(Kind kind, String ownerId, String accessPass, String message) {
            this.kind = kind;
            this.ownerId = ownerId == null ? "" : ownerId;
            this.accessPass = accessPass == null ? "" : accessPass;
            this.message = message == null ? "" : message;
        }

        static Decision ok(String ownerId, String accessPass) {
            return new Decision(Kind.OK, ownerId, accessPass, "");
        }

        static Decision of(Kind kind, String ownerId, String message) {
            return new Decision(kind, ownerId, "", message);
        }
    }

    static class CtfileException extends Exception {
        CtfileException(String message) {
            super(message);
        }
    }

    static final class VerificationMismatch extends CtfileException {
        VerificationMismatch(String message) {
            super(message);
        }
    }

    private static final class CachedPass {
        final String token;
        final long expiresAtMillis;

        CachedPass(String token, long expiresAtMillis) {
            this.token = token;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
