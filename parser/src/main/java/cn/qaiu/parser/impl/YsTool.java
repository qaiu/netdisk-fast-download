package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.CommonUtils;
import cn.qaiu.util.FileSizeConverter;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 永硕E盘（主 ysepan.com / ys168.com，备 cccpan.com / ysupan.com / uupan.net / ysok.net）
 * <p>
 * 空间分享形如 https://{space}.ysepan.com/ ，需空间访问密码时通过 sharePassword 传入。
 */
public class YsTool extends PanBase {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36";

    private static final Pattern ANTIFORGERY_PATTERN =
            Pattern.compile("RequestVerificationToken'\\s*:\\s*'([^']+)'");
    private static final Pattern HTXX_PATTERN =
            Pattern.compile("window\\.htxx\\s*=\\s*(\\{.*?});", Pattern.DOTALL);
    private static final Pattern JWT_COOKIE_PATTERN =
            Pattern.compile("jwttk_[^=]+=([^;\\s]+)");

    private static final String PARAM_DIR_ID = "dirId";
    /** 永硕目录内的子目录名（API 字段 zml），用于二级层级 */
    private static final String PARAM_ZML = "zml";

    public YsTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    @Override
    public Future<String> parse() {
        ensureSession().onSuccess(session -> {
            Object dirIdObj = shareLinkInfo.getOtherParam().get(PARAM_DIR_ID);
            if (dirIdObj != null && StringUtils.isNotBlank(dirIdObj.toString())) {
                int dirId = Integer.parseInt(dirIdObj.toString());
                fetchFiles(session, dirId).onSuccess(filesResp -> {
                    List<JsonObject> files = downloadableFiles(filesResp);
                    if (files.isEmpty()) {
                        fail("目录内没有可下载文件");
                        return;
                    }
                    completeDownload(session, filesResp, files.get(0));
                }).onFailure(err -> fail(err, err.getMessage()));
                return;
            }

            fetchDirectories(session).compose(dirs -> collectDownloadableFiles(session, dirs))
                    .onSuccess(collected -> {
                        if (collected.isEmpty()) {
                            fail("空间内没有可下载文件，请检查密码或目录权限");
                            return;
                        }
                        if (collected.size() > 1) {
                            fail("空间包含多个文件(共{}个)，请使用文件列表接口后再按文件解析", collected.size());
                            return;
                        }
                        CollectedFile only = collected.get(0);
                        completeDownload(session, only.filesResp, only.file);
                    }).onFailure(err -> fail(err, err.getMessage()));
        }).onFailure(err -> fail(err, err.getMessage()));
        return promise.future();
    }

    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> listPromise = Promise.promise();
        ensureSession().onSuccess(session -> {
            Object dirIdObj = shareLinkInfo.getOtherParam().get(PARAM_DIR_ID);
            if (dirIdObj != null && StringUtils.isNotBlank(dirIdObj.toString())) {
                int dirId = Integer.parseInt(dirIdObj.toString());
                String zmlFilter = currentZmlFilter();
                fetchFiles(session, dirId).onSuccess(filesResp -> {
                    try {
                        listPromise.complete(mapFiles(session, dirId, filesResp, zmlFilter));
                    } catch (Exception e) {
                        listPromise.fail(baseMsg() + " - 解析文件列表失败: " + e.getMessage());
                    }
                }).onFailure(listPromise::fail);
                return;
            }

            fetchDirectories(session).onSuccess(dirs -> {
                try {
                    listPromise.complete(mapDirectories(session, dirs));
                } catch (Exception e) {
                    listPromise.fail(baseMsg() + " - 解析目录列表失败: " + e.getMessage());
                }
            }).onFailure(listPromise::fail);
        }).onFailure(listPromise::fail);
        return listPromise.future();
    }

    @Override
    public Future<String> parseById() {
        JsonObject paramJson = (JsonObject) shareLinkInfo.getOtherParam().get("paramJson");
        if (paramJson == null) {
            fail("缺少 paramJson 参数");
            return promise.future();
        }

        String downloadUrl = paramJson.getString("downloadUrl");
        if (StringUtils.isNotBlank(downloadUrl)) {
            completeWithMeta(downloadUrl, downloadHeaders(paramJson.getString("referer")));
            return promise.future();
        }

        String space = paramJson.getString("space", spaceName());
        String xzpz = paramJson.getString("xzpz");
        String pz = paramJson.getString("pz");
        String fwq = paramJson.getString("fwq");
        String fileName = paramJson.getString("fileName");
        if (StringUtils.isAnyBlank(space, xzpz, pz, fwq, fileName)) {
            fail("下载参数不完整: {}", paramJson);
            return promise.future();
        }
        String url = buildDownloadUrl(space, xzpz, pz, fwq, fileName);
        completeWithMeta(url, downloadHeaders(paramJson.getString("referer", spaceOrigin())));
        return promise.future();
    }

    private Future<Session> ensureSession() {
        Promise<Session> p = Promise.promise();
        String space = spaceName();
        if (StringUtils.isBlank(space)) {
            p.fail(baseMsg() + " - 空间名为空");
            return p.future();
        }

        String origin = spaceOrigin();
        clientSession.getAbs(origin + "/")
                .putHeader("User-Agent", BROWSER_UA)
                .putHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .send()
                .compose(res -> handleSpaceHome(origin, space, res))
                .onSuccess(p::complete)
                .onFailure(p::fail);
        return p.future();
    }

    private Future<Session> handleSpaceHome(String origin, String space, HttpResponse<Buffer> res) {
        String html = res.bodyAsString() != null ? res.bodyAsString() : "";
        String jwt = extractJwt(res);
        JsonObject htxx = extractHtxx(html);

        // 已进入空间
        if (htxx != null && StringUtils.isNotBlank(htxx.getString("qqdz"))) {
            if (StringUtils.isBlank(jwt)) {
                return Future.failedFuture(baseMsg() + " - 无法获取会话令牌");
            }
            return Future.succeededFuture(toSession(origin, space, jwt, htxx));
        }

        // 需要空间密码
        if (html.contains("VerifyPassword") || html.contains("loginPopup")) {
            String pwd = shareLinkInfo.getSharePassword();
            if (StringUtils.isBlank(pwd)) {
                return Future.failedFuture(baseMsg() + " - 空间需要访问密码");
            }
            String antiforgery = extractAntiforgery(html);
            if (StringUtils.isBlank(antiforgery)) {
                return Future.failedFuture(baseMsg() + " - 无法获取防伪令牌");
            }
            return verifyPassword(origin, space, pwd, antiforgery)
                    .compose(verifiedJwt -> reloadSpace(origin, space, verifiedJwt));
        }

        return Future.failedFuture(baseMsg() + " - 无法解析空间信息");
    }

    private Future<String> verifyPassword(String origin, String space, String pwd, String antiforgery) {
        Promise<String> p = Promise.promise();
        JsonObject body = new JsonObject()
                .put("password", pwd)
                .put("dlmc", space)
                .put("remember", false);

        clientSession.postAbs(origin + "/?handler=VerifyPassword")
                .putHeader("User-Agent", BROWSER_UA)
                .putHeader("Content-Type", "application/json")
                .putHeader("Origin", origin)
                .putHeader("Referer", origin + "/")
                .putHeader("RequestVerificationToken", antiforgery)
                .sendJsonObject(body)
                .onSuccess(res -> {
                    try {
                        JsonObject json = res.bodyAsJsonObject();
                        if (json == null || !Boolean.TRUE.equals(json.getBoolean("success"))) {
                            String msg = json != null ? json.getString("message", "密码错误") : "密码验证失败";
                            p.fail(baseMsg() + " - " + msg);
                            return;
                        }
                        String jwt = extractJwt(res);
                        if (StringUtils.isBlank(jwt)) {
                            p.fail(baseMsg() + " - 密码验证成功但未返回会话令牌");
                            return;
                        }
                        p.complete(jwt);
                    } catch (Exception e) {
                        p.fail(baseMsg() + " - 密码验证响应异常: " + e.getMessage());
                    }
                })
                .onFailure(t -> p.fail(baseMsg() + " - 密码验证请求失败: " + t.getMessage()));
        return p.future();
    }

    private Future<Session> reloadSpace(String origin, String space, String verifiedJwt) {
        Promise<Session> p = Promise.promise();
        clientSession.getAbs(origin + "/")
                .putHeader("User-Agent", BROWSER_UA)
                .putHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .send()
                .onSuccess(res -> {
                    String html = res.bodyAsString() != null ? res.bodyAsString() : "";
                    JsonObject htxx = extractHtxx(html);
                    String jwt = StringUtils.defaultIfBlank(extractJwt(res), verifiedJwt);
                    if (htxx == null || StringUtils.isBlank(htxx.getString("qqdz"))) {
                        p.fail(baseMsg() + " - 密码验证后仍无法进入空间");
                        return;
                    }
                    if (StringUtils.isBlank(jwt)) {
                        p.fail(baseMsg() + " - 密码验证后无法获取会话令牌");
                        return;
                    }
                    p.complete(toSession(origin, space, jwt, htxx));
                })
                .onFailure(t -> p.fail(baseMsg() + " - 重新加载空间失败: " + t.getMessage()));
        return p.future();
    }

    private Session toSession(String origin, String space, String jwt, JsonObject htxx) {
        String apiBase = htxx.getString("qqdz");
        if (apiBase != null && !apiBase.endsWith("/")) {
            apiBase = apiBase + "/";
        }
        String dlmc = htxx.getString("dlmc", space);
        return new Session(origin, dlmc, jwt, apiBase);
    }

    private Future<JsonArray> fetchDirectories(Session session) {
        Promise<JsonArray> p = Promise.promise();
        apiPost(session, "ml/mldq", new JsonObject())
                .onSuccess(json -> {
                    JsonArray lb = json.getJsonArray("lb");
                    p.complete(lb != null ? lb : new JsonArray());
                })
                .onFailure(p::fail);
        return p.future();
    }

    private Future<JsonObject> fetchFiles(Session session, int dirId) {
        Promise<JsonObject> p = Promise.promise();
        JsonObject body = new JsonObject()
                .put("mlbh", dirId)
                .put("kqmm", "")
                .put("wjbh", 0)
                .put("ip1", "");
        apiPost(session, "wj/wjdq", body)
                .onSuccess(p::complete)
                .onFailure(p::fail);
        return p.future();
    }

    private Future<JsonObject> apiPost(Session session, String path, JsonObject body) {
        Promise<JsonObject> p = Promise.promise();
        String url = session.apiBase + path;
        HttpRequest<Buffer> req = clientSession.postAbs(url)
                .putHeader("User-Agent", BROWSER_UA)
                .putHeader("Accept", "application/json, text/plain, */*")
                .putHeader("Content-Type", "application/json")
                .putHeader("Origin", session.origin)
                .putHeader("Referer", session.origin + "/")
                .putHeader("Authorization", "Bearer " + session.jwt);
        req.sendJsonObject(body)
                .onSuccess(res -> {
                    try {
                        if (res.statusCode() >= 400) {
                            p.fail(baseMsg() + " - API " + path + " HTTP " + res.statusCode());
                            return;
                        }
                        JsonObject json = asJson(res);
                        if (json == null || json.isEmpty()) {
                            p.fail(baseMsg() + " - API " + path + " 返回空响应");
                            return;
                        }
                        p.complete(json);
                    } catch (Exception e) {
                        p.fail(baseMsg() + " - API " + path + " 响应异常: " + e.getMessage());
                    }
                })
                .onFailure(t -> p.fail(baseMsg() + " - API " + path + " 请求失败: " + t.getMessage()));
        return p.future();
    }

    private Future<List<CollectedFile>> collectDownloadableFiles(Session session, JsonArray dirs) {
        Promise<List<CollectedFile>> p = Promise.promise();
        List<CollectedFile> collected = new ArrayList<>();
        List<Integer> dirIds = new ArrayList<>();
        for (int i = 0; i < dirs.size(); i++) {
            JsonObject dir = dirs.getJsonObject(i);
            if (isAccessibleDirectory(dir)) {
                dirIds.add(dir.getInteger("bh"));
            }
        }
        if (dirIds.isEmpty()) {
            p.complete(collected);
            return p.future();
        }

        fetchNextDirFiles(session, dirIds, 0, collected, p);
        return p.future();
    }

    private void fetchNextDirFiles(Session session, List<Integer> dirIds, int index,
                                   List<CollectedFile> collected, Promise<List<CollectedFile>> promise) {
        if (index >= dirIds.size()) {
            promise.complete(collected);
            return;
        }
        int dirId = dirIds.get(index);
        fetchFiles(session, dirId).onSuccess(filesResp -> {
            for (JsonObject file : downloadableFiles(filesResp)) {
                collected.add(new CollectedFile(filesResp, file));
            }
            fetchNextDirFiles(session, dirIds, index + 1, collected, promise);
        }).onFailure(promise::fail);
    }

    private List<FileInfo> mapDirectories(Session session, JsonArray dirs) {
        List<FileInfo> result = new ArrayList<>();
        for (int i = 0; i < dirs.size(); i++) {
            JsonObject dir = dirs.getJsonObject(i);
            if (!isAccessibleDirectory(dir)) {
                continue;
            }
            Integer bh = dir.getInteger("bh");
            if (bh == null) {
                continue;
            }
            String title = StringUtils.defaultIfBlank(dir.getString("bt"), "目录" + bh);
            FileInfo info = new FileInfo()
                    .setFileName(title)
                    .setFileId(bh.toString())
                    .setFileType("folder")
                    .setSize(0L)
                    .setSizeStr("0B")
                    .setDescription(dir.getString("sm", ""))
                    .setCreateTime(normalizeTime(dir.getString("sj")))
                    .setPanType(shareLinkInfo.getType())
                    .setParserUrl(String.format("%s/v2/getFileList?url=%s&dirId=%s&pwd=%s",
                            getDomainName(),
                            urlEncode(shareLinkInfo.getShareUrl()),
                            bh,
                            urlEncode(StringUtils.defaultString(shareLinkInfo.getSharePassword()))));
            result.add(info);
        }
        return result;
    }

    /**
     * 将目录内文件按 zml（子目录）分层。
     * <ul>
     *   <li>zmlFilter 为空：返回子目录（folder）+ 根级文件</li>
     *   <li>zmlFilter 非空：仅返回该子目录下的文件/链接</li>
     * </ul>
     */
    private List<FileInfo> mapFiles(Session session, int dirId, JsonObject filesResp, String zmlFilter) {
        List<FileInfo> result = new ArrayList<>();
        String xzpz = filesResp.getJsonObject("ml", new JsonObject()).getString("xzpz", "");
        JsonArray lb = filesResp.getJsonArray("lb", new JsonArray());
        boolean listingSubdir = StringUtils.isNotBlank(zmlFilter);

        // 未进入子目录时，先按出现顺序收集 zml 作为二级文件夹
        if (!listingSubdir) {
            java.util.LinkedHashSet<String> subdirs = new java.util.LinkedHashSet<>();
            for (int i = 0; i < lb.size(); i++) {
                JsonObject item = lb.getJsonObject(i);
                if (item == null) {
                    continue;
                }
                String zml = StringUtils.defaultString(item.getString("zml")).trim();
                if (StringUtils.isNotBlank(zml)) {
                    subdirs.add(zml);
                }
            }
            for (String zml : subdirs) {
                result.add(new FileInfo()
                        .setFileName(zml)
                        .setFileId(dirId + ":" + zml)
                        .setFileType("folder")
                        .setSize(0L)
                        .setSizeStr("0B")
                        .setFilePath(zml)
                        .setPanType(shareLinkInfo.getType())
                        .setParserUrl(String.format("%s/v2/getFileList?url=%s&dirId=%s&zml=%s&pwd=%s",
                                getDomainName(),
                                urlEncode(shareLinkInfo.getShareUrl()),
                                dirId,
                                urlEncode(zml),
                                urlEncode(StringUtils.defaultString(shareLinkInfo.getSharePassword())))));
            }
        }

        for (int i = 0; i < lb.size(); i++) {
            JsonObject item = lb.getJsonObject(i);
            if (item == null) {
                continue;
            }
            String itemZml = StringUtils.defaultString(item.getString("zml")).trim();
            if (listingSubdir) {
                if (!zmlFilter.equals(itemZml)) {
                    continue;
                }
            } else if (StringUtils.isNotBlank(itemZml)) {
                // 根级列表只展示无 zml 的条目，有 zml 的归入子目录
                continue;
            }

            Integer bh = item.getInteger("bh");
            if (bh == null) {
                continue;
            }
            String wjlx = item.getString("wjlx", "");

            // URL / 公告条目
            if ("url".equalsIgnoreCase(wjlx)) {
                String link = StringUtils.defaultString(item.getString("wjm")).trim();
                String title = StringUtils.defaultString(item.getString("bt")).trim();
                // 空占位（标题和链接都空）跳过，与官网展示一致
                if (StringUtils.isAllBlank(title, link)) {
                    continue;
                }
                if (StringUtils.isBlank(title)) {
                    title = StringUtils.defaultIfBlank(link, "链接");
                }
                FileInfo urlInfo = new FileInfo()
                        .setFileName(title)
                        .setFileId(bh.toString())
                        .setFileType("url")
                        .setSize(0L)
                        .setSizeStr("0B")
                        .setFilePath(itemZml)
                        .setCreateTime(normalizeTime(item.getString("sj")))
                        .setPanType(shareLinkInfo.getType())
                        .setPreviewUrl(link)
                        .setDescription(link);
                // parserUrl 置空，避免前端误走下载；打开走 previewUrl
                result.add(urlInfo);
                continue;
            }

            String fileName = item.getString("wjm");
            String fwq = item.getString("fwq");
            String pz = item.getString("pz");
            if (StringUtils.isAnyBlank(fileName, fwq, pz, xzpz)) {
                continue;
            }

            long size = item.getLong("dx", 0L);
            String downloadUrl = buildDownloadUrl(session.space, xzpz, pz, fwq, fileName);
            JsonObject param = new JsonObject()
                    .put("space", session.space)
                    .put("xzpz", xzpz)
                    .put("pz", pz)
                    .put("fwq", fwq)
                    .put("fileName", fileName)
                    .put("mlbh", dirId)
                    .put("fileId", bh)
                    .put("downloadUrl", downloadUrl)
                    .put("referer", session.origin + "/");
            String paramEncoded = CommonUtils.urlBase64Encode(param.encode());

            FileInfo fileInfo = new FileInfo()
                    .setFileName(fileName)
                    .setFileId(bh.toString())
                    .setFileType("file")
                    .setSize(size)
                    .setSizeStr(FileSizeConverter.convertToReadableSize(size))
                    .setFilePath(itemZml)
                    .setCreateTime(normalizeTime(item.getString("sj")))
                    .setPanType(shareLinkInfo.getType())
                    .setParserUrl(String.format("%s/v2/redirectUrl/%s/%s",
                            getDomainName(), shareLinkInfo.getType(), paramEncoded))
                    .setPreviewUrl(String.format("%s/v2/viewUrl/%s/%s",
                            getDomainName(), shareLinkInfo.getType(), paramEncoded));
            result.add(fileInfo);
        }
        return result;
    }

    private String currentZmlFilter() {
        Object zml = shareLinkInfo.getOtherParam().get(PARAM_ZML);
        if (zml == null) {
            return "";
        }
        try {
            return java.net.URLDecoder.decode(zml.toString(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return zml.toString().trim();
        }
    }

    private List<JsonObject> downloadableFiles(JsonObject filesResp) {
        List<JsonObject> files = new ArrayList<>();
        String xzpz = filesResp.getJsonObject("ml", new JsonObject()).getString("xzpz", "");
        if (StringUtils.isBlank(xzpz)) {
            return files;
        }
        JsonArray lb = filesResp.getJsonArray("lb", new JsonArray());
        for (int i = 0; i < lb.size(); i++) {
            JsonObject item = lb.getJsonObject(i);
            if (item == null) {
                continue;
            }
            if ("url".equalsIgnoreCase(item.getString("wjlx", ""))) {
                continue;
            }
            if (StringUtils.isAnyBlank(item.getString("wjm"), item.getString("fwq"), item.getString("pz"))) {
                continue;
            }
            files.add(item);
        }
        return files;
    }

    private void completeDownload(Session session, JsonObject filesResp, JsonObject file) {
        String xzpz = filesResp.getJsonObject("ml", new JsonObject()).getString("xzpz");
        String url = buildDownloadUrl(session.space, xzpz, file.getString("pz"),
                file.getString("fwq"), file.getString("wjm"));

        FileInfo fileInfo = new FileInfo()
                .setFileName(file.getString("wjm"))
                .setFileId(String.valueOf(file.getInteger("bh")))
                .setSize(file.getLong("dx", 0L))
                .setSizeStr(FileSizeConverter.convertToReadableSize(file.getLong("dx", 0L)))
                .setFileType("file")
                .setFilePath(file.getString("zml", ""))
                .setCreateTime(normalizeTime(file.getString("sj")))
                .setPanType(shareLinkInfo.getType());
        shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
        completeWithMeta(url, downloadHeaders(session.origin + "/"));
    }

    /**
     * 拼装直链。注意：不要在 xzpz 前加 "_"，官方页面直链无此前缀，加了会 404。
     */
    static String buildDownloadUrl(String space, String xzpz, String pz, String fwq, String fileName) {
        String host = "X".equalsIgnoreCase(fwq)
                ? "y.ys168.com:8000"
                : "ys-" + fwq.toLowerCase() + ".ysepan.com";
        return "https://" + host + "/wap/"
                + encodePathSegment(space) + "/"
                + encodePathSegment(xzpz) + "/"
                + encodePathSegment(pz) + "/"
                + encodePathSegment(fileName);
    }

    private static String encodePathSegment(String value) {
        // 保留永硕 token 中常见的 . , _ - 等字符，仅编码必须转义的部分
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2E", ".")
                .replace("%2C", ",")
                .replace("%5F", "_")
                .replace("%2D", "-");
    }

    private Map<String, String> downloadHeaders(String referer) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", BROWSER_UA);
        if (StringUtils.isNotBlank(referer)) {
            headers.put("Referer", referer);
        }
        return headers;
    }

    private boolean isAccessibleDirectory(JsonObject dir) {
        if (dir == null) {
            return false;
        }
        // 无可下载权限或需特殊打开方式的目录忽略
        if (!Boolean.TRUE.equals(dir.getBoolean("qxz", true))) {
            return false;
        }
        Integer kqfs = dir.getInteger("kqfs", 0);
        return kqfs == null || kqfs == 0;
    }

    private String spaceName() {
        String key = shareLinkInfo.getShareKey();
        if (StringUtils.isBlank(key)) {
            return null;
        }
        try {
            return java.net.URLDecoder.decode(key, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return key;
        }
    }

    private String spaceOrigin() {
        String shareUrl = shareLinkInfo.getShareUrl();
        if (StringUtils.isNotBlank(shareUrl)) {
            try {
                java.net.URI uri = java.net.URI.create(shareUrl);
                String scheme = uri.getScheme() != null ? uri.getScheme() : "https";
                return scheme + "://" + uri.getHost();
            } catch (Exception ignored) {
            }
        }
        return "https://" + spaceName() + ".ysepan.com";
    }

    private static String extractAntiforgery(String html) {
        Matcher m = ANTIFORGERY_PATTERN.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private static JsonObject extractHtxx(String html) {
        Matcher m = HTXX_PATTERN.matcher(html);
        if (!m.find()) {
            return null;
        }
        try {
            return new JsonObject(m.group(1));
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractJwt(HttpResponse<Buffer> res) {
        List<String> setCookies = res.cookies();
        if (setCookies != null) {
            for (String c : setCookies) {
                Matcher m = JWT_COOKIE_PATTERN.matcher(c);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        String raw = res.getHeader("Set-Cookie");
        if (raw != null) {
            Matcher m = JWT_COOKIE_PATTERN.matcher(raw);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static String normalizeTime(String sj) {
        if (StringUtils.isBlank(sj)) {
            return sj;
        }
        // 2024-10-26T10:12:54.98 -> 2024-10-26 10:12:54
        String t = sj.replace('T', ' ');
        int dot = t.indexOf('.');
        if (dot > 0) {
            t = t.substring(0, dot);
        }
        return t;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8);
    }

    private record Session(String origin, String space, String jwt, String apiBase) {
    }

    private record CollectedFile(JsonObject filesResp, JsonObject file) {
    }
}
