package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.TokenCache;
import cn.qaiu.parser.impl.CtfileSession.CtfileException;
import cn.qaiu.util.CommonUtils;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 城通官方客户端协议 {@code /v4}。带访问密码的分享在网页接口上会被 Turnstile 拦住，
 * 登录自己的免费账号后改走这里列目录和取直链。
 */
final class CtfileV4Client {

    static final String API = "https://api.ctfile.com";
    private static final String UA = "Mozilla/5.0 CTFileApp/5.1.24";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final long EXPIRY_SKEW_MILLIS = 60_000L;
    private static final int PAGE_SIZE = 100;

    private static final ConcurrentHashMap<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final ShareLinkInfo shareLinkInfo;

    CtfileV4Client(ShareLinkInfo shareLinkInfo) {
        this.shareLinkInfo = shareLinkInfo;
    }

    static boolean hasCredentials(ShareLinkInfo info) {
        return credentials(info).usable();
    }

    static String missingAccountMessage() {
        return "未配置城通账号。带访问密码的分享需要登录后的令牌："
                + "在 app-dev.yml 配置 auths.ct.refresh_token，或请求里用 auth 传入。"
                + "refresh_token 来自浏览器登录 pan.ctfile.com 后 Session Storage 中的 refresh_token；"
                + "也可以同时填写 token 作为当前 access token。目录分享的类型是 ctd，未单独配置时会回退使用 ct。";
    }

    DirectLink downloadShare(String shareKey, boolean directory) throws Exception {
        String explicitFileId = text(shareLinkInfo.getOtherParam().get("ctV4FileId"));
        String xtlink = text(shareLinkInfo.getOtherParam().get("ctXtlink"));
        if (xtlink.isBlank()) {
            xtlink = CtfileXtlink.fromShare(shareKey, shareLinkInfo.getSharePassword(), directory && explicitFileId.isBlank());
        }
        if (xtlink == null) {
            throw new CtfileException("这个分享标识不能转换成官方 xtlink");
        }
        String fileId = explicitFileId.isBlank()
                ? CtfileXtlink.prefixedId(shareId(shareKey), false)
                : CtfileXtlink.prefixedId(explicitFileId, false);
        if (fileId.isBlank()) {
            throw new CtfileException("分享标识里没有文件 id，无法取直链");
        }
        String token = accessToken(false);
        JsonObject listed = null;
        try {
            listed = post("/v4/public/browser/list", listBody(xtlink, "0", 0, 20), token);
        } catch (V4CallException e) {
            if (e.authenticationFailure()) {
                token = recoverAuth(e, token);
                try {
                    listed = post("/v4/public/browser/list", listBody(xtlink, "0", 0, 20), token);
                } catch (V4CallException again) {
                    if (terminal(again)) {
                        throw again;
                    }
                }
            } else if (terminal(e)) {
                throw e;
            }
        }
        JsonObject matched = listed == null ? null : findItem(listed, fileId);
        String url = fetchDownloadUrl(xtlink, fileId, token);
        JsonObject file = new JsonObject()
                .put("file_name", matched == null ? shareLinkInfo.getShareKey() : itemName(matched))
                .put("file_id", CtfileXtlink.bareId(fileId))
                .put("file_size_bytes", matched == null ? 0 : itemSize(matched))
                .put("file_size", matched == null ? "" : String.valueOf(itemSize(matched)))
                .put("username", "");
        if (matched != null && !itemDate(matched).isBlank()) {
            file.put("file_time", itemDate(matched));
        }
        return new DirectLink(url, file);
    }

    List<FileInfo> listShare(String shareKey, String folderId, int maxPages) throws Exception {
        boolean directory = true;
        String xtlink = CtfileXtlink.fromShare(shareKey, shareLinkInfo.getSharePassword(), directory);
        if (xtlink == null) {
            throw new CtfileException("这个分享标识不能转换成官方 xtlink");
        }
        String token = accessToken(false);
        List<FileInfo> files = new ArrayList<>();
        int start = 0;
        String apiFolder = folderId == null || folderId.isBlank() || "0".equals(folderId) ? "0" : CtfileXtlink.bareId(folderId);
        for (int page = 0; page < maxPages; page++) {
            JsonObject body;
            try {
                body = post("/v4/public/browser/list", listBody(xtlink, apiFolder, start, PAGE_SIZE), token);
            } catch (V4CallException e) {
                token = recoverAuth(e, token);
                body = post("/v4/public/browser/list", listBody(xtlink, apiFolder, start, PAGE_SIZE), token);
            }
            JsonArray rows = resultsOf(body);
            if (rows.isEmpty()) {
                return files;
            }
            for (int i = 0; i < rows.size(); i++) {
                if (rows.getValue(i) instanceof JsonObject row) {
                    FileInfo info = toFileInfo(row, shareLinkInfo, xtlink);
                    if (info != null) {
                        files.add(info);
                    }
                }
            }
            int total = totalOf(body);
            start += rows.size();
            if (rows.size() < PAGE_SIZE || (total >= 0 && start >= total)) {
                return files;
            }
        }
        return files;
    }

    private String fetchDownloadUrl(String xtlink, String fileId, String token) throws Exception {
        JsonObject auth;
        try {
            auth = post("/v4/public/browser/create-download-auth", authBody(xtlink, CtfileXtlink.prefixedId(fileId, false)), token);
        } catch (V4CallException e) {
            token = recoverAuth(e, token);
            auth = post("/v4/public/browser/create-download-auth", authBody(xtlink, CtfileXtlink.prefixedId(fileId, false)), token);
        }
        String signature = signatureOf(auth);
        if (signature.isBlank()) {
            throw new CtfileException("城通没有返回下载签名，可能是密码错误、分享失效或账号没有下载权限");
        }
        JsonObject fetched;
        try {
            fetched = post("/v4/public/browser/fetch-url", fetchBody(xtlink, CtfileXtlink.bareId(fileId), signature), token);
        } catch (V4CallException e) {
            token = recoverAuth(e, token);
            fetched = post("/v4/public/browser/fetch-url", fetchBody(xtlink, CtfileXtlink.bareId(fileId), signature), token);
        }
        String url = downloadUrlOf(fetched);
        if (url.isBlank()) {
            throw new CtfileException("城通没有返回下载直链");
        }
        return url;
    }

    private String recoverAuth(V4CallException error, String currentToken) throws Exception {
        if (!error.authenticationFailure()) {
            throw error;
        }
        String refreshed = accessToken(true);
        if (refreshed == null || refreshed.isBlank() || refreshed.equals(currentToken)) {
            throw error;
        }
        return refreshed;
    }

    private String accessToken(boolean forceRefresh) throws Exception {
        Credentials credentials = credentials(shareLinkInfo);
        if (!credentials.usable()) {
            throw new CtfileException(missingAccountMessage());
        }
        Session session = SESSIONS.computeIfAbsent(credentials.accountKey, key -> new Session());
        synchronized (session) {
            long now = System.currentTimeMillis();
            if ((session.refreshToken == null || session.refreshToken.isBlank()) && credentials.refreshToken != null) {
                session.refreshToken = credentials.refreshToken;
            }
            if ((session.accessToken == null || session.accessToken.isBlank()) && credentials.accessToken != null) {
                session.accessToken = credentials.accessToken;
            }
            if (!forceRefresh && session.accessToken != null && !session.accessToken.isBlank()
                    && (session.expiresAtMillis == 0 || session.expiresAtMillis - EXPIRY_SKEW_MILLIS > now)) {
                return session.accessToken;
            }
            if (session.refreshToken == null || session.refreshToken.isBlank()) {
                if (session.accessToken != null && !session.accessToken.isBlank() && !forceRefresh) {
                    session.expiresAtMillis = 0;
                    return session.accessToken;
                }
                throw new CtfileException("城通 access token 已失效，且没有可用的 refresh_token。请重新登录后更新配置。");
            }
            RefreshResult refreshed = refresh(session.refreshToken);
            session.accessToken = refreshed.accessToken;
            session.expiresAtMillis = now + refreshed.expiresInSeconds * 1000L;
            if (refreshed.refreshToken != null && !refreshed.refreshToken.isBlank()
                    && !refreshed.refreshToken.equals(session.refreshToken)) {
                session.refreshToken = refreshed.refreshToken;
                rememberRefreshToken(credentials, refreshed.refreshToken);
            } else {
                session.refreshToken = refreshed.refreshToken == null || refreshed.refreshToken.isBlank()
                        ? session.refreshToken : refreshed.refreshToken;
            }
            TokenCache.putToken(credentials.cacheKey(), session.accessToken);
            TokenCache.putExpire(credentials.cacheKey(), session.expiresAtMillis);
            TokenCache.queueCachedTokenPersist(credentials.cacheKey(), session.accessToken, session.expiresAtMillis);
            return session.accessToken;
        }
    }

    private RefreshResult refresh(String refreshToken) throws Exception {
        V4CallException first = null;
        try {
            return readRefresh(post("/v4/user/auth/refresh-token", refreshBody(refreshToken, false), null), refreshToken);
        } catch (V4CallException e) {
            first = e;
        }
        try {
            return readRefresh(post("/v4/user/auth/refresh-token", refreshBody(refreshToken, true), null), refreshToken);
        } catch (V4CallException e) {
            throw first.messageKey.contains("invalid_refresh") ? first : e;
        }
    }

    private JsonObject post(String path, JsonObject body, String accessToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(API + path))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-Client-Type", "native")
                .header("X-Client-Version", "5.1.24")
                .header("X-Client-Platform", "linux")
                .header("X-Client-OS-Version", "6.1.0")
                .header("X-Client-Arch", "x64")
                .header("X-Client-Captcha", "image,turnstile,altcha,cap")
                .POST(HttpRequest.BodyPublishers.ofString(body.encode()));
        if (accessToken != null && !accessToken.isBlank()) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        HttpResponse<String> response;
        try {
            response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CtfileException("城通客户端请求被中断");
        } catch (IOException e) {
            throw new CtfileException("城通客户端请求失败: " + e.getMessage());
        }
        String text = response.body() == null ? "" : response.body();
        JsonObject json;
        try {
            json = new JsonObject(text);
        } catch (Exception e) {
            throw new CtfileException("城通客户端返回了无法识别的响应 (HTTP " + response.statusCode() + ")");
        }
        int code = CtfileSession.intValue(json.getValue("code"), response.statusCode());
        if (code != 200) {
            throw new V4CallException(code, json.getString("message", ""), json.getString("message_key", ""));
        }
        return json;
    }

    private void rememberRefreshToken(Credentials credentials, String refreshToken) {
        if (credentials.auths != null) {
            credentials.auths.set("refresh_token", refreshToken);
        }
        TokenCache.queueCredentialUpdate(credentials.cacheKey(), refreshToken);
        try {
            Object auths = WebClientVertxInit.get().sharedData().getLocalMap("local").get("auths");
            if (auths instanceof JsonObject json) {
                for (String type : new String[] {"ct", "ctd"}) {
                    JsonObject config = json.getJsonObject(type);
                    if (config != null && config.containsKey("refresh_token")) {
                        config.put("refresh_token", refreshToken);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 单测或未启动服务时没有共享配置，进程内的 TokenCache 仍然保存新令牌。
        }
    }

    static JsonObject listBody(String xtlink, String folderId, int start, int pageSize) {
        Object folder = folderId != null && folderId.matches("\\d+") ? Long.parseLong(folderId) : folderId;
        return new JsonObject()
                .put("xtlink", xtlink)
                .put("folder_id", folder)
                .put("start", start)
                .put("pagesize", pageSize);
    }

    static JsonObject authBody(String xtlink, String ids) {
        return new JsonObject().put("xtlink", xtlink).put("ids", ids);
    }

    static JsonObject fetchBody(String xtlink, String fileId, String signature) {
        return new JsonObject()
                .put("file_id", fileId)
                .put("download_sign", signature)
                .put("xtlink", xtlink)
                .put("carrier", 0);
    }

    static JsonObject refreshBody(String refreshToken, boolean rotate) {
        JsonObject body = new JsonObject().put("refresh_token", refreshToken);
        if (rotate) {
            body.put("rotate_token", true);
        }
        return body;
    }

    static String describeFailure(int code, String message, String messageKey) {
        String text = message == null ? "" : message;
        String key = messageKey == null ? "" : messageKey;
        String lower = (text + " " + key).toLowerCase();
        if (lower.contains("invalid_refresh") || lower.contains("refresh token") || text.contains("refresh token")) {
            return "城通 refresh_token 无效或已过期，请重新登录 pan.ctfile.com 后更新 auths.ct.refresh_token";
        }
        if (text.contains("扫码") || text.contains("登录保护") || lower.contains("qr") || lower.contains("captcha_required")) {
            return "城通账号需要扫码或完成登录保护后，再更新 refresh_token";
        }
        if (text.contains("锁定") && !text.contains("无效的令牌") && !text.contains("令牌无效")) {
            return "城通账号已锁定";
        }
        if (code == 401 || text.contains("无效的令牌") || text.contains("未登录") || text.contains("令牌无效")) {
            return "城通登录令牌无效或账号已锁定";
        }
        if (code == 404 || text.contains("不存在") || text.contains("失效") || lower.contains("not found")) {
            return "分享不存在或已过期";
        }
        if (text.contains("密码") || lower.contains("passcode") || lower.contains("password")) {
            return "访问密码不正确";
        }
        if (code == 403) {
            return "城通拒绝了这次下载" + (text.isBlank() ? "" : "（" + text + "）");
        }
        return "城通客户端接口返回错误 (code=" + code + ")" + (text.isBlank() ? "" : "：" + text);
    }

    static FileInfo toFileInfo(JsonObject item, ShareLinkInfo info, String xtlink) {
        if (item == null) {
            return null;
        }
        String name = itemName(item);
        if (name.isBlank()) {
            return null;
        }
        String key = itemKey(item);
        boolean folder = isFolder(item, key);
        String password = info.getSharePassword() == null ? "" : info.getSharePassword();
        FileInfo fileInfo = new FileInfo()
                .setFileName(name)
                .setPanType(info.getType())
                .setFileId(CtfileXtlink.bareId(key));
        String date = itemDate(item);
        if (!date.isBlank()) {
            fileInfo.setCreateTime(date).setUpdateTime(date);
        }
        String domain = info.getOtherParam() == null ? "" : String.valueOf(info.getOtherParam().getOrDefault("domainName", ""));
        if (folder) {
            return fileInfo.setFileType("folder")
                    .setSize(0L)
                    .setSizeStr("0B")
                    .setParserUrl(domain + "/v2/getFileList?url=" + url(info.getShareUrl())
                            + "&dirId=" + url(CtfileXtlink.bareId(key))
                            + (password.isBlank() ? "" : "&pwd=" + url(password)));
        }
        long size = itemSize(item);
        JsonObject param = new JsonObject()
                .put("id", CtfileXtlink.prefixedId(key, false))
                .put("pwd", password)
                .put("fileName", name)
                .put("xtlink", xtlink)
                .put("shareKey", info.getShareKey() == null ? "" : info.getShareKey());
        String kind = CtfileSession.firstNonBlank(text(item.getValue("icon")), text(item.getValue("ext")), "file");
        return fileInfo.setFileType(kind)
                .setSize(size)
                .setSizeStr(size > 0 ? size + "B" : text(item.getValue("size")))
                .setParserUrl(domain + "/v2/redirectUrl/" + info.getType() + "/"
                        + CommonUtils.urlBase64Encode(param.encode()));
    }

    static Credentials credentials(ShareLinkInfo info) {
        MultiMap auths = null;
        if (info != null && info.getOtherParam() != null && info.getOtherParam().get("auths") instanceof MultiMap map) {
            auths = map;
        }
        String refresh = firstAuth(auths, "refresh_token", "refreshToken");
        String access = firstAuth(auths, "token", "authorization", "access_token", "accessToken");
        String accountId = firstAuth(auths, "_configId");
        if ((refresh == null || refresh.isBlank()) && (access == null || access.isBlank())) {
            JsonObject shared = sharedAuth(info == null ? "" : info.getType());
            if (shared != null) {
                refresh = firstJson(shared, "refresh_token", "refreshToken");
                access = firstJson(shared, "token", "authorization", "access_token", "accessToken");
            }
        }
        if (accountId == null || accountId.isBlank()) {
            accountId = refresh != null && !refresh.isBlank() ? "refresh" : "access";
        }
        return new Credentials(auths, accountId, blankToNull(refresh), blankToNull(stripBearer(access)));
    }

    static void clearSessions() {
        SESSIONS.clear();
    }

    private static JsonObject sharedAuth(String type) {
        try {
            Object auths = WebClientVertxInit.get().sharedData().getLocalMap("local").get("auths");
            if (!(auths instanceof JsonObject json)) {
                return null;
            }
            JsonObject config = type == null ? null : json.getJsonObject(type);
            if ((config == null || config.isEmpty()) && "ctd".equalsIgnoreCase(type)) {
                config = json.getJsonObject("ct");
            }
            return config;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static RefreshResult readRefresh(JsonObject json, String previous) throws CtfileException {
        String token = firstJson(json, "token", "access_token", "accessToken");
        JsonObject data = json.getJsonObject("data");
        if ((token == null || token.isBlank()) && data != null) {
            token = firstJson(data, "token", "access_token", "accessToken");
        }
        if (token == null || token.isBlank()) {
            throw new CtfileException("刷新城通登录状态失败：上游没有返回 access token");
        }
        String rotated = firstJson(json, "refresh_token", "refreshToken");
        if ((rotated == null || rotated.isBlank()) && data != null) {
            rotated = firstJson(data, "refresh_token", "refreshToken");
        }
        int expires = CtfileSession.intValue(json.getValue("expires_in"), -1);
        if (expires < 0 && data != null) {
            expires = CtfileSession.intValue(data.getValue("expires_in"), 900);
        }
        if (expires < 0) {
            expires = 900;
        }
        return new RefreshResult(token, rotated == null || rotated.isBlank() ? previous : rotated, expires);
    }

    private static String signatureOf(JsonObject json) {
        JsonObject data = json.getJsonObject("data");
        String signature = data == null ? "" : data.getString("download_signature", "");
        if (signature.isBlank()) {
            signature = json.getString("download_signature", "");
        }
        return signature;
    }

    private static String downloadUrlOf(JsonObject json) {
        JsonObject data = json.getJsonObject("data");
        String url = data == null ? "" : data.getString("download_url", "");
        if (url.isBlank() && data != null) {
            url = data.getString("url", "");
        }
        if (url.isBlank()) {
            url = json.getString("download_url", "");
        }
        return url;
    }

    private static JsonArray resultsOf(JsonObject json) {
        JsonObject data = json.getJsonObject("data");
        if (data != null && data.getValue("results") instanceof JsonArray rows) {
            return rows;
        }
        if (json.getValue("results") instanceof JsonArray rows) {
            return rows;
        }
        return new JsonArray();
    }

    private static int totalOf(JsonObject json) {
        JsonObject data = json.getJsonObject("data");
        if (data == null) {
            return -1;
        }
        int total = CtfileSession.intValue(data.getValue("totalNum"), -1);
        return total >= 0 ? total : CtfileSession.intValue(data.getValue("num"), -1);
    }

    private static JsonObject findItem(JsonObject listed, String fileId) {
        String bare = CtfileXtlink.bareId(fileId);
        for (int i = 0; i < resultsOf(listed).size(); i++) {
            if (!(resultsOf(listed).getValue(i) instanceof JsonObject row)) {
                continue;
            }
            if (bare.equals(CtfileXtlink.bareId(itemKey(row)))) {
                return row;
            }
        }
        JsonArray rows = resultsOf(listed);
        if (rows.size() == 1 && rows.getValue(0) instanceof JsonObject only && !isFolder(only, itemKey(only))) {
            return only;
        }
        return null;
    }

    private static boolean isFolder(JsonObject item, String key) {
        if (Boolean.TRUE.equals(item.getValue("is_folder"))) {
            return true;
        }
        String type = item.getString("type", "");
        String icon = item.getString("icon", "");
        String ext = item.getString("ext", "");
        return "folder".equalsIgnoreCase(type) || "folder".equalsIgnoreCase(icon) || "folder".equalsIgnoreCase(ext)
                || (key != null && key.startsWith("d"));
    }

    private static String itemKey(JsonObject item) {
        return CtfileSession.firstNonBlank(text(item.getValue("fid")), text(item.getValue("key")), text(item.getValue("id")));
    }

    private static String itemName(JsonObject item) {
        return item.getString("name", "").trim();
    }

    private static long itemSize(JsonObject item) {
        long size = CtfileSession.longValue(item.getValue("size_bytes"), -1);
        if (size >= 0) {
            return size;
        }
        return CtfileSession.longValue(item.getValue("size"), 0);
    }

    private static String itemDate(JsonObject item) {
        return CtfileSession.firstNonBlank(text(item.getValue("date")), text(item.getValue("file_time")));
    }

    private static String shareId(String shareKey) {
        if (shareKey == null) {
            return "";
        }
        String[] parts = shareKey.split("-");
        return parts.length >= 2 ? parts[1] : "";
    }

    private static String firstAuth(MultiMap auths, String... keys) {
        if (auths == null) {
            return null;
        }
        for (String key : keys) {
            String value = auths.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String firstJson(JsonObject json, String... keys) {
        if (json == null) {
            return null;
        }
        for (String key : keys) {
            String value = json.getString(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String stripBearer(String token) {
        if (token == null) {
            return null;
        }
        String value = token.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String url(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    static final class Credentials {
        final MultiMap auths;
        final String accountId;
        final String refreshToken;
        final String accessToken;
        final String accountKey;

        Credentials(MultiMap auths, String accountId, String refreshToken, String accessToken) {
            this.auths = auths;
            this.accountId = accountId;
            this.refreshToken = refreshToken;
            this.accessToken = accessToken;
            this.accountKey = accountId == null ? "default" : accountId;
        }

        boolean usable() {
            return (refreshToken != null && !refreshToken.isBlank()) || (accessToken != null && !accessToken.isBlank());
        }

        String cacheKey() {
            return TokenCache.key("ct", accountKey);
        }
    }

    static final class DirectLink {
        final String url;
        final JsonObject file;

        DirectLink(String url, JsonObject file) {
            this.url = url;
            this.file = file;
        }
    }

    private static final class Session {
        String refreshToken;
        String accessToken;
        long expiresAtMillis;
    }

    private static final class RefreshResult {
        final String accessToken;
        final String refreshToken;
        final int expiresInSeconds;

        RefreshResult(String accessToken, String refreshToken, int expiresInSeconds) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    static final class V4CallException extends CtfileException {
        final int code;
        final String messageKey;

        V4CallException(int code, String message, String messageKey) {
            super(describeFailure(code, message, messageKey));
            this.code = code;
            this.messageKey = messageKey == null ? "" : messageKey;
        }

        boolean authenticationFailure() {
            return code == 401 || getMessage().contains("令牌") || getMessage().contains("锁定");
        }
    }

    private static boolean terminal(V4CallException error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        return message.contains("密码") || message.contains("不存在") || message.contains("已过期") || message.contains("失效");
    }
}
