package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.parser.PasswordLoginRisk;
import cn.qaiu.parser.TokenCache;
import cn.qaiu.util.CommonUtils;
import cn.qaiu.util.Base64Decoder;
import cn.qaiu.util.FileSizeConverter;
import cn.qaiu.util.YeShareHostUtil;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.uritemplate.UriTemplate;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.CRC32;

import static cn.qaiu.util.RandomStringGenerator.gen36String;

/**
 * 123盘解析器。
 * 分享单文件走分享域名：先 POST /api/share/download/traffic/check，
 * 再 POST /api/v2/share/download/info。platform web，App-Version 149。
 * 域名只由 shareKey 解码出的数字 uid 组成，不使用调用方提供的任意 host。
 * 文件夹打包下载仍走 App-Version 55 的 batch_download_share_info。
 * 支持账号密码或 token 配置。
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class Ye2Tool extends PanBase {

    private static final String API_BASE = "https://api.123278.com";
    private static final String GET_SHARE_INFO_URL = API_BASE + "/b/api/share/get?limit=100&next=1&orderBy=share_id&orderDirection=desc&shareKey={shareKey}&SharePwd={pwd}&ParentFileId={ParentFileId}&Page=1";
    /** 分享页脚本把 /share/download/traffic/check 加上 /api 前缀，发到当前分享域名。 */
    static final String SHARE_TRAFFIC_CHECK_PATH = "/api/share/download/traffic/check";
    /** 分享页脚本把 /v2/share/download/info 加上 /api 前缀，不是 api.123278.com/b/api。 */
    static final String SHARE_DOWNLOAD_INFO_PATH = "/api/v2/share/download/info";
    static final String SHARE_PLATFORM = "web";
    /** 当前分享页静态资源里的 App-Version。个人文件接口 App-Version 55 会返回 5066。 */
    static final String SHARE_WEB_APP_VERSION = "149";
    private static final String BATCH_DOWNLOAD_API_URL = API_BASE + "/b/api/file/batch_download_share_info";
    private static final String LOGIN_URL = "https://login.123pan.com/api/user/sign_in";

    private static final String CHAR_MAP = "adefghlmyijnopkqrstubcvwsz";

    private final MultiMap header = MultiMap.caseInsensitiveMultiMap();
    private final String cacheKey;

    public Ye2Tool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
        this.cacheKey = TokenCache.key("ye2", resolveAccountId());
        header.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6");
        header.set("App-Version", "55");
        header.set("Cache-Control", "no-cache");
        header.set("Connection", "keep-alive");
        header.set("LoginUuid", gen36String());
        header.set("Pragma", "no-cache");
        header.set("Referer", shareLinkInfo.getStandardUrl());
        header.set("Sec-Fetch-Dest", "empty");
        header.set("Sec-Fetch-Mode", "cors");
        header.set("Sec-Fetch-Site", "same-origin");
        header.set("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36");
        header.set("platform", "android");
        header.set("Content-Type", "application/json");
    }

    private String resolveAccountId() {
        String accountId = "_default";
        if (!shareLinkInfo.getOtherParam().containsKey("auths")) {
            return accountId;
        }
        MultiMap auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
        if (auths.contains("_configId")) {
            accountId = auths.get("_configId");
        } else if (auths.contains("username")) {
            accountId = auths.get("username");
        } else {
            String token = resolveProvidedToken(auths);
            if (StringUtils.isNotEmpty(token)) {
                accountId = token.substring(0, Math.min(16, token.length()));
            }
        }
        return accountId;
    }

    /**
     * 从配置/临时认证参数中取出可直接使用的 Bearer token。
     * 兼容两种 key：
     * - token：URLParamUtil 处理 auth= 临时参数(authType=accesstoken/authorization)时写入的字段名
     * - authorization：app-dev.yml 静态配置中更符合直觉的写法（auths.ye.authorization: xxx）
     */
    private String resolveProvidedToken(MultiMap auths) {
        if (auths == null) {
            return null;
        }
        String token = auths.get("token");
        if (StringUtils.isEmpty(token)) {
            token = auths.get("authorization");
        }
        return stripBearerPrefix(token);
    }

    /**
     * 用户/捐赠账号提供的 token 经常是从浏览器开发者工具里连同 "Bearer " 前缀一起复制过来的，
     * 如果不做清理，后续 header.set("Authorization", "Bearer " + token) 会拼出
     * "Bearer Bearer xxx"，导致123网盘接口报错 tokenstring should not contain 'bearer '。
     */
    private String stripBearerPrefix(String token) {
        if (StringUtils.isBlank(token)) {
            return token;
        }
        String trimmed = token.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            trimmed = trimmed.substring(7).trim();
        }
        return trimmed;
    }

    private boolean isTokenExpired() {
        return TokenCache.isExpired(cacheKey);
    }

    private String encode123(String url, String way, String version, String timestamp) {
        return signQuery(url, way, version, Long.parseLong(timestamp), nextSignRandom());
    }

    private static long nextSignRandom() {
        int randomInt = new Random().nextInt(10000000) + 1;
        return (10000000L * randomInt) / 10000;
    }

    /**
     * 签名原文：unix秒|随机数|实际路径|platform|App-Version|时间混淆值。
     * 分享下载的路径是 /api/...，platform 是 web，版本是 149。
     */
    static String signMaterial(String url, String way, String version, long timeMillis, long randomA) {
        long timeLong = timeMillis / 1000;
        java.time.LocalDateTime dateTime = java.time.Instant.ofEpochSecond(timeLong)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
        String timeStr = dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        StringBuilder g = new StringBuilder();
        for (char c : timeStr.toCharArray()) {
            int digit = Character.getNumericValue(c);
            if (digit == 0) {
                g.append(CHAR_MAP.charAt(0));
            } else {
                g.append(CHAR_MAP.charAt(digit - 1));
            }
        }
        String y = String.valueOf(crc32Decimal(g.toString()));
        return timeLong + "|" + randomA + "|" + url + "|" + way + "|" + version + "|" + y;
    }

    static String signQuery(String url, String way, String version, long timeMillis, long randomA) {
        String material = signMaterial(url, way, version, timeMillis, randomA);
        String y = material.substring(material.lastIndexOf('|') + 1);
        long timeLong = timeMillis / 1000;
        String finalCrc = String.valueOf(crc32Decimal(material));
        return "?" + y + "=" + timeLong + "-" + randomA + "-" + finalCrc;
    }

    private static long crc32Decimal(String data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return crc32.getValue();
    }

    public Future<String> parse() {
        Future<String> tokenFuture = resolveTokenFuture();

        tokenFuture.onSuccess(token -> {
            if (!token.equals("nologin")) {
                TokenCache.putToken(cacheKey, token);
                header.set("Authorization", "Bearer " + token);
            }

            final String dataKey = YeShareHostUtil.normalizeShareKey(shareLinkInfo.getShareKey());
            final String pwd = shareLinkInfo.getSharePassword();
            final String shareOrigin = resolveYeShareOrigin(dataKey);
            final String shareReferer = buildShareReferer(shareOrigin, dataKey, pwd);

            client.getAbs(UriTemplate.of(GET_SHARE_INFO_URL))
                    .setTemplateParam("shareKey", dataKey)
                    .setTemplateParam("pwd", StringUtils.isEmpty(pwd) ? "" : pwd)
                    .setTemplateParam("ParentFileId", "0")
                    .putHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .putHeader("Referer", shareReferer)
                    .putHeader("Origin", shareOrigin)
                    .send()
                    .onSuccess(res -> {
                        JsonObject shareInfoJson = asJson(res);
                        if (shareInfoJson.getInteger("code") != 0) {
                            String message = shareInfoJson.getString("message");
                            if (isYeLoginRequired(shareInfoJson.getInteger("code"), message)) {
                                fail(loginRequiredDownloadMessage(!"nologin".equals(token), message));
                                return;
                            }
                            fail("获取分享信息失败: " + message);
                            return;
                        }

                        if (!shareInfoJson.containsKey("data") || !shareInfoJson.getJsonObject("data").containsKey("InfoList")) {
                            fail("返回数据格式错误");
                            return;
                        }

                        JsonObject data = shareInfoJson.getJsonObject("data");
                        if (data.getJsonArray("InfoList").size() == 0) {
                            fail("分享中没有文件");
                            return;
                        }

                        JsonObject fileInfo = data.getJsonArray("InfoList").getJsonObject(0);
                        if (fileInfo.getInteger("Type", 0) == 1) {
                            if (!hasUsableToken(token)) {
                                fail(loginRequiredDownloadMessage(false, null));
                                return;
                            }
                            getZipDownUrl(client, fileInfo);
                        } else {
                            getDownUrl(fileInfo, token);
                        }
                    })
                    .onFailure(this.handleFail(GET_SHARE_INFO_URL));
        }).onFailure(err -> fail("123盘解析异常: {}", err.getMessage()));

        return promise.future();
    }

    private Future<String> resolveTokenFuture() {
        MultiMap auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
        if (auths != null) {
            // 当同时提供了用户名+密码时，优先走真实登录流程。
            // 注：前端/auth参数中 authType=password|username_password 时，URLParamUtil 出于兼容旧解析器的目的
            // 会把 authToken(用户名) 同时写入 token 字段，如果这里无条件信任 token，会把用户名当成
            // Bearer token 使用，导致真实的账号密码登录被跳过。
            boolean hasCredential = StringUtils.isNotEmpty(auths.get("username"))
                    && StringUtils.isNotEmpty(auths.get("password"));
            String providedToken = resolveProvidedToken(auths);
            if (!hasCredential && StringUtils.isNotEmpty(providedToken)) {
                TokenCache.putToken(cacheKey, providedToken);
                return Future.succeededFuture(providedToken);
            }
        }

        String cached = TokenCache.getToken(cacheKey);
        if (cached == null || isTokenExpired()) {
            return loginAndGetToken();
        }
        return Future.succeededFuture(cached);
    }

    private Future<String> loginAndGetToken() {
        MultiMap auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
        if (auths == null) {
            return Future.succeededFuture("nologin");
        }

        String username = auths.get("username");
        String password = auths.get("password");

        if (username == null || password == null) {
            return Future.succeededFuture("nologin");
        }

        Promise<String> promise = Promise.promise();
        String loginUuid = gen36String();

        JsonObject loginBody = new JsonObject()
                .put("passport", username)
                .put("password", password)
                .put("remember", true);

        client.postAbs(LOGIN_URL)
                .putHeader("Content-Type", "application/json")
                .putHeader("LoginUuid", loginUuid)
                .putHeader("App-Version", "55")
                .putHeader("platform", "web")
                .sendJsonObject(loginBody)
                .onSuccess(res -> {
                    JsonObject json;
                    try {
                        json = res.bodyAsJsonObject();
                    } catch (Exception e) {
                        promise.fail(PasswordLoginRisk.nonJsonLoginFailure(res.bodyAsString()));
                        return;
                    }
                    if (json == null) {
                        promise.fail(PasswordLoginRisk.nonJsonLoginFailure(res.bodyAsString()));
                        return;
                    }
                    if (!json.containsKey("code")) {
                        promise.fail("登录响应格式异常: " + res.bodyAsString());
                        return;
                    }
                    // 验证码/风控只提示改用 Authorization，不求解、不重试。
                    if (!PasswordLoginRisk.isLoginSuccess(json)) {
                        promise.fail(PasswordLoginRisk.describeLoginFailure(json));
                        return;
                    }
                    JsonObject data = json.getJsonObject("data");
                    String ssoToken = data.getString("token");
                    String expireStr = data.getString("expire");
                    long expireMs;
                    if (StringUtils.isNotEmpty(expireStr)) {
                        expireMs = OffsetDateTime.parse(expireStr)
                                .toInstant().toEpochMilli() - 60_000;
                    } else {
                        expireMs = System.currentTimeMillis() + 3600_000;
                    }
                    TokenCache.putToken(cacheKey, ssoToken);
                    TokenCache.putExpire(cacheKey, expireMs);
                    log.info("登录成功，token: {}", ssoToken);
                    promise.complete(ssoToken);
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    /**
     * 分享单文件：流量预检通过后走分享域名 v2。不回退个人文件接口，也不创建订单。
     */
    private void getDownUrl(JsonObject fileInfo, String token) {
        setFileInfo(fileInfo);

        String normalizedShareKey = YeShareHostUtil.normalizeShareKey(shareLinkInfo.getShareKey());
        applyResolvedShareKey(shareLinkInfo, normalizedShareKey);
        Integer fileId = fileInfo.getInteger("FileId");
        if (!usableShareKey(normalizedShareKey) || fileId == null) {
            fail("123网盘公开下载参数缺少分享标识");
            return;
        }
        long size = fileInfo.getLong("Size") == null ? 0L : fileInfo.getLong("Size");
        requestShareDownload(normalizedShareKey, fileId, fileInfo.getString("S3KeyFlag"), size,
                fileInfo.getString("Etag"), token).onSuccess(this::complete)
                .onFailure(err -> fail(err.getMessage()));
    }

    private Future<String> requestShareDownload(String shareKey, long fileId, String s3keyFlag, long size,
                                                String etag, String token) {
        return executeShareFileDownload(this::postShareJson, new ShareFileRequest(
                shareLinkInfo.getShareUrl(),
                shareLinkInfo.getStandardUrl(),
                shareKey,
                shareLinkInfo.getSharePassword(),
                fileId,
                s3keyFlag,
                size,
                etag,
                token,
                System.currentTimeMillis(),
                nextSignRandom()));
    }

    private Future<JsonObject> postShareJson(ShareHttpCall call) {
        Promise<JsonObject> promise = Promise.promise();
        HttpRequest<Buffer> request = client.postAbs(call.url());
        call.headers.forEach(entry -> request.putHeader(entry.getKey(), entry.getValue()));
        request.sendJsonObject(call.body)
                .onSuccess(resp -> promise.complete(asJson(resp)))
                .onFailure(promise::fail);
        return promise.future();
    }

    /**
     * 文件夹打包下载仍使用 App-Version 55 的 /b/api/file/batch_download_share_info。
     * 分享页脚本已改走 /v2/file/batch_download_share_info，这次不改这条路径。
     */
    private void getZipDownUrl(WebClient client, JsonObject fileInfo) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("shareKey", YeShareHostUtil.normalizeShareKey(shareLinkInfo.getShareKey()));
        jsonObject.put("fileIdList", new JsonArray().add(JsonObject.of("fileId", fileInfo.getInteger("FileId"))));

        String timestamp = String.valueOf(System.currentTimeMillis());
        String encryptedParams = encode123("/b/api/file/batch_download_share_info", "android", "55", timestamp);
        String apiUrl = BATCH_DOWNLOAD_API_URL + encryptedParams;

        log.info("Ye2 Batch Download API URL: {}", apiUrl);

        HttpRequest<Buffer> bufferHttpRequest = client.postAbs(apiUrl);
        bufferHttpRequest.putHeader("platform", "android");
        bufferHttpRequest.putHeader("App-Version", "55");
        bufferHttpRequest.putHeader("Authorization", "Bearer " + TokenCache.getToken(cacheKey));
        bufferHttpRequest.putHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
        bufferHttpRequest.putHeader("Content-Type", "application/json");

        bufferHttpRequest
                .sendJsonObject(jsonObject)
                .onSuccess(res2 -> handleDownloadUrlResponse(client, asJson(res2), "Ye2: 文件夹打包下载"))
                .onFailure(err -> fail("文件夹打包下载接口失败: " + err.getMessage()));
    }

    /**
     * 123 下载接口要求登录时的说明。分享列表本身经常可以匿名读取，
     * 真正拦下载的是 5112 / 请登录后下载，需要调用方自己的 Authorization。
     */
    static String loginRequiredDownloadMessage(boolean authConfigured, String upstream) {
        String msg = authConfigured
                ? "123网盘仍要求登录或触发了风控。请在本机检查 auths.ye.authorization（与 auths.ye.token 等价）"
                + "或账号密码是否仍然有效。公共云解析没有共享 123 账号。"
                + "上游常见返回：5112、请登录后下载、您需要注册登录或付费后下载。"
                : "123网盘这个分享需要登录后才能下载。请在本机部署，并配置 auths.ye.authorization"
                + "（与 auths.ye.token 等价；浏览器登录后的 authorToken、请求头 authorization 或 Cookie sso-token，不要重复带 Bearer），"
                + "或配置 auths.ye.username 与 password。公共云解析没有共享 123 账号，不能代登。"
                + "约 10MB 的小文件有时可以直链，大文件、文件夹打包以及 5112/请登录后下载必须使用你自己的 Authorization。"
                + "短链格式是 ye:分享key@提取码；链接里的 # 片段会去掉。"
                + "yun.123pan.com、www.123pan.com 和 *.share.123pan.cn 都是同一类分享。";
        if (StringUtils.isNotBlank(upstream)) {
            String brief = upstream.length() > 180 ? upstream.substring(0, 180) : upstream;
            msg = msg + " 上游信息: " + brief;
        }
        return msg;
    }

    static boolean isYeLoginRequired(Integer code, String message) {
        if (code != null && (code == 5112 || code == 401)) {
            return true;
        }
        if (message == null) {
            return false;
        }
        return message.contains("请登录") || message.contains("需要登录") || message.contains("注册登录");
    }

    private void handleDownloadUrlResponse(WebClient client, JsonObject downURLJson, String failPrefix) {
        try {
            Integer code = downURLJson.getInteger("code");
            if (code != 0) {
                String message = downURLJson.getString("message");
                if (isYeLoginRequired(code, message)) {
                    fail(loginRequiredDownloadMessage(true, message));
                    return;
                }
                fail(failPrefix + "返回值异常->" + downURLJson);
                return;
            }
        } catch (Exception ignored) {
            fail(failPrefix + "格式异常->" + downURLJson);
            return;
        }

        String downURL = downURLJson.getJsonObject("data").getString("DownloadUrl");
        if (StringUtils.isEmpty(downURL)) {
            downURL = downURLJson.getJsonObject("data").getString("DownloadURL");
        }

        if (StringUtils.isEmpty(downURL)) {
            fail(failPrefix + "未获取到下载链接");
            return;
        }

        try {
            Map<String, String> urlParams = CommonUtils.getURLParams(downURL);
            String params = urlParams.get("params");
            if (StringUtils.isEmpty(params)) {
                complete(downURL);
                return;
            }

            byte[] decodeByte = Base64Decoder.decode(params);
            String downUrl2 = new String(decodeByte);

            clientNoRedirects.getAbs(downUrl2).putHeaders(header).send().onSuccess(res3 -> {
                if (res3.statusCode() == 302 || res3.statusCode() == 301) {
                    String redirectUrl = res3.getHeader("Location");
                    if (StringUtils.isBlank(redirectUrl)) {
                        fail("重定向链接为空");
                        return;
                    }
                    complete(redirectUrl);
                    return;
                }
                JsonObject res3Json = asJson(res3);
                try {
                    if (res3Json.getInteger("code") != 0) {
                        fail(failPrefix + "重定向返回值异常->" + res3Json);
                        return;
                    }
                } catch (Exception ignored) {
                    fail(failPrefix + "重定向格式异常->" + downURLJson);
                    return;
                }
                String redirectUrl = res3Json.getJsonObject("data").getString("redirect_url");
                if (StringUtils.isNotEmpty(redirectUrl)) {
                    complete(redirectUrl);
                } else {
                    complete(downUrl2);
                }
            }).onFailure(err -> fail("获取直链失败: " + err.getMessage()));
        } catch (MalformedURLException e) {
            complete(downURL);
        } catch (Exception e) {
            fail("urlParams解析异常: " + e.getMessage());
        }
    }

    void setFileInfo(JsonObject reqBodyJson) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(reqBodyJson.getInteger("FileId").toString());
        fileInfo.setFileName(reqBodyJson.getString("FileName"));
        fileInfo.setSize(reqBodyJson.getLong("Size"));
        fileInfo.setHash(reqBodyJson.getString("Etag"));

        String createAt = reqBodyJson.getString("CreateAt");
        if (StringUtils.isNotEmpty(createAt)) {
            fileInfo.setCreateTime(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(OffsetDateTime.parse(createAt).toLocalDateTime()));
        }

        String updateAt = reqBodyJson.getString("UpdateAt");
        if (StringUtils.isNotEmpty(updateAt)) {
            fileInfo.setUpdateTime(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(OffsetDateTime.parse(updateAt).toLocalDateTime()));
        }

        shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
    }

    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> promise = Promise.promise();

        String shareKey = YeShareHostUtil.normalizeShareKey(shareLinkInfo.getShareKey());
        String pwd = shareLinkInfo.getSharePassword();
        String parentFileId = "0";

        String dirId = (String) shareLinkInfo.getOtherParam().get("dirId");
        if (StringUtils.isNotBlank(dirId)) {
            parentFileId = dirId;
        }

        Future<String> tokenFuture = resolveTokenFuture();
        String finalParentFileId = parentFileId;
        tokenFuture.onSuccess(token -> {
            // 分享列表接口可以不登录。下载才经常返回 5112。
            boolean loggedIn = StringUtils.isNotEmpty(token) && !"nologin".equals(token);
            if (loggedIn) {
                header.set("Authorization", "Bearer " + token);
            }

            String normalizedShareKey = YeShareHostUtil.normalizeShareKey(shareKey);
            String shareOrigin = resolveYeShareOrigin(normalizedShareKey);
            String shareReferer = buildShareReferer(shareOrigin, normalizedShareKey, pwd);

            HttpRequest<Buffer> request = client.getAbs(UriTemplate.of(GET_SHARE_INFO_URL))
                    .setTemplateParam("shareKey", shareKey)
                    .setTemplateParam("pwd", StringUtils.isEmpty(pwd) ? "" : pwd)
                    .setTemplateParam("ParentFileId", finalParentFileId)
                    .putHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .putHeader("Referer", shareReferer)
                    .putHeader("Origin", shareOrigin);
            if (loggedIn) {
                request.putHeader("Authorization", "Bearer " + token);
            }
            request.send().onSuccess(res -> {
                        JsonObject response = asJson(res);
                        if (response.getInteger("code") != 0) {
                            String message = response.getString("message");
                            if (isYeLoginRequired(response.getInteger("code"), message)) {
                                promise.fail(loginRequiredDownloadMessage(loggedIn, message));
                                return;
                            }
                            promise.fail("API错误: " + message);
                            return;
                        }

                        if (!response.containsKey("data") || !response.getJsonObject("data").containsKey("InfoList")) {
                            promise.fail("返回数据格式错误");
                            return;
                        }

                        JsonArray infoList = response.getJsonObject("data").getJsonArray("InfoList");
                        List<FileInfo> result = new ArrayList<>();

                        for (int i = 0; i < infoList.size(); i++) {
                            JsonObject item = infoList.getJsonObject(i);
                            FileInfo fileInfo = new FileInfo();

                            JsonObject postData = JsonObject.of()
                                    .put("shareKey", shareLinkInfo.getShareKey())
                                    .put("driveId", 0)
                                    .put("etag", item.getString("Etag"))
                                    .put("fileId", item.getInteger("FileId"))
                                    .put("fileName", item.getString("FileName"))
                                    .put("s3keyFlag", item.getString("S3KeyFlag"))
                                    .put("size", item.getLong("Size"))
                                    .put("type", 0);

                            String param = CommonUtils.urlBase64Encode(postData.encode());

                            if (item.getInteger("Type") == 0) {
                                fileInfo.setFileName(item.getString("FileName"))
                                        .setFileId(item.getInteger("FileId").toString())
                                        .setFileType("file")
                                        .setSize(item.getLong("Size"))
                                        .setHash(item.getString("Etag"))
                                        .setSizeStr(FileSizeConverter.convertToReadableSize(item.getLong("Size")));

                                setFileTimes(item, fileInfo);

                                fileInfo.setParserUrl(String.format("%s/v2/redirectUrl/%s/%s", getDomainName(),
                                                shareLinkInfo.getType(), param))
                                        .setPreviewUrl(String.format("%s/v2/viewUrl/%s/%s", getDomainName(),
                                                shareLinkInfo.getType(), param));
                                result.add(fileInfo);
                            } else if (item.getInteger("Type") == 1) {
                                fileInfo.setFileName(item.getString("FileName"))
                                        .setFileId(item.getInteger("FileId").toString())
                                        .setFileType("folder")
                                        .setSize(0L);

                                setFileTimes(item, fileInfo);

                                fileInfo.setParserUrl(
                                        String.format("%s/v2/getFileList?url=%s&dirId=%s&pwd=%s",
                                                getDomainName(),
                                                shareLinkInfo.getShareUrl(),
                                                item.getInteger("FileId"),
                                                pwd)
                                );
                                result.add(fileInfo);
                            }
                        }
                        promise.complete(result);
                    }).onFailure(promise::fail);
        }).onFailure(err -> promise.fail("123盘解析异常: " + err.getMessage()));

        return promise.future();
    }

    @Override
    public Future<String> parseById() {
        JsonObject paramJson = (JsonObject) shareLinkInfo.getOtherParam().get("paramJson");
        Future<String> tokenFuture = resolveTokenFuture();

        tokenFuture.onSuccess(token -> {
            if (hasUsableToken(token)) {
                TokenCache.putToken(cacheKey, token);
            }
            String normalizedShareKey = resolveDownloadShareKey(paramJson, shareLinkInfo.getShareKey());
            applyResolvedShareKey(shareLinkInfo, normalizedShareKey);
            if (!usableShareKey(normalizedShareKey)) {
                fail("123网盘公开下载参数缺少分享标识");
                return;
            }
            long fileId = paramJson.getLong("fileId", paramJson.getLong("FileID", 0L));
            String s3keyFlag = firstNonBlank(paramJson, "s3keyFlag", "S3keyFlag", "S3KeyFlag");
            long size = paramJson.getLong("size", paramJson.getLong("Size", 0L));
            String etag = firstNonBlank(paramJson, "etag", "Etag");
            requestShareDownload(normalizedShareKey, fileId, s3keyFlag, size, etag, token)
                    .onSuccess(this::complete)
                    .onFailure(err -> fail(err.getMessage()));
        }).onFailure(err -> fail("123盘解析异常: " + err.getMessage()));

        return promise.future();
    }

    static final class ShareFileRequest {
        final String shareUrl;
        final String standardUrl;
        final String shareKey;
        final String password;
        final long fileId;
        final String s3keyFlag;
        final long size;
        final String etag;
        final String token;
        final long timeMillis;
        final long randomA;

        ShareFileRequest(String shareUrl, String standardUrl, String shareKey, String password, long fileId,
                         String s3keyFlag, long size, String etag, String token, long timeMillis, long randomA) {
            this.shareUrl = shareUrl;
            this.standardUrl = standardUrl;
            this.shareKey = shareKey;
            this.password = password;
            this.fileId = fileId;
            this.s3keyFlag = s3keyFlag;
            this.size = size;
            this.etag = etag;
            this.token = token;
            this.timeMillis = timeMillis;
            this.randomA = randomA;
        }
    }

    static final class ShareHttpCall {
        final String origin;
        final String path;
        final String query;
        final JsonObject body;
        final MultiMap headers;

        ShareHttpCall(String origin, String path, String query, JsonObject body, MultiMap headers) {
            this.origin = origin;
            this.path = path;
            this.query = query;
            this.body = body;
            this.headers = headers;
        }

        String url() {
            return origin + path + query;
        }
    }

    @FunctionalInterface
    interface ShareJsonPost {
        Future<JsonObject> post(ShareHttpCall call);
    }

    /**
     * 分享接口只发到 key 解码出的 https://{uid}.share.123pan.cn。
     * mshare、/s/ 和调用方写入的其它 host 都不能改变这个目标。
     */
    static String shareApiOrigin(String shareUrl, String standardUrl, String shareKey) {
        String uid = YeShareHostUtil.getNumericSubdomainIdByShareKey(shareKey);
        if (!uid.matches("\\d+")) {
            return null;
        }
        if (hostileShareHost(shareUrl) || hostileShareHost(standardUrl)) {
            return null;
        }
        return "https://" + uid + ".share.123pan.cn";
    }

    /**
     * 调用方给出的地址如果带了 host，只允许空、普通网页，或与 shareKey 一致的数字分享域名。
     * 127.0.0.1、云元数据、以及伪装成 123 域名的其它 host 直接拒绝。
     */
    static boolean hostileShareHost(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        java.net.URI uri;
        try {
            uri = java.net.URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return true;
        }
        String host = uri.getHost();
        if (host == null) {
            return true;
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return true;
        }
        if (uri.getUserInfo() != null || uri.getPort() > 0) {
            return true;
        }
        if (host.equalsIgnoreCase("www.123pan.com") || host.equalsIgnoreCase("yun.123pan.com")
                || host.equalsIgnoreCase("123pan.com")) {
            return false;
        }
        return !host.matches("(?i)\\d+\\.(?:mshare|share)\\.123pan\\.cn");
    }

    static JsonObject trafficCheckBody(String shareKey, long fileId) {
        return new JsonObject()
                .put("shareKey", shareKey)
                .put("fileIds", new JsonArray().add(fileId));
    }

    static JsonObject downloadInfoBody(String shareKey, long fileId, String s3keyFlag, long size, String etag) {
        return new JsonObject()
                .put("ShareKey", shareKey)
                .put("FileID", fileId)
                .put("S3keyFlag", s3keyFlag == null ? "" : s3keyFlag)
                .put("Size", size)
                .put("Etag", etag == null ? "" : etag);
    }

    static String trafficStopMessage(JsonObject data) {
        if (data == null) {
            return null;
        }
        if (Boolean.TRUE.equals(data.getBoolean("isBlocked"))) {
            return "123网盘限制了这个分享的下载。解析器不会自动下单或选择付费流量。";
        }
        if (Boolean.TRUE.equals(data.getBoolean("isPay"))) {
            return "123网盘这个文件需要付费后下载。解析器不会自动下单或选择付费流量。";
        }
        if (Boolean.TRUE.equals(data.getBoolean("isTrafficExceeded"))) {
            return "123网盘分享提取流量不足。解析器不会自动购买或选择付费流量。";
        }
        return null;
    }

    static String joinDownloadUrl(String downloadPath, String prefix) {
        if (StringUtils.isBlank(downloadPath)) {
            return "";
        }
        String path = downloadPath.trim();
        if (path.startsWith("//")) {
            return "";
        }
        if (isAbsoluteHttp(path)) {
            return path;
        }
        if (StringUtils.isBlank(prefix) || !isAbsoluteHttp(prefix.trim())) {
            return "";
        }
        String normalizedPrefix = prefix.trim();
        if (normalizedPrefix.endsWith("/") && path.startsWith("/")) {
            return normalizedPrefix.substring(0, normalizedPrefix.length() - 1) + path;
        }
        if (!normalizedPrefix.endsWith("/") && !path.startsWith("/")) {
            return normalizedPrefix + "/" + path;
        }
        return normalizedPrefix + path;
    }

    private static boolean isAbsoluteHttp(String value) {
        return value.startsWith("https://") || value.startsWith("http://");
    }

    static Future<String> executeShareFileDownload(ShareJsonPost post, ShareFileRequest request) {
        String origin = shareApiOrigin(request.shareUrl, request.standardUrl, request.shareKey);
        if (origin == null || !usableShareKey(request.shareKey)) {
            return Future.failedFuture("123网盘公开下载参数缺少可识别的分享域名");
        }
        String referer = origin + "/123pan/" + YeShareHostUtil.normalizeShareKey(request.shareKey);
        if (StringUtils.isNotBlank(request.password)) {
            referer = referer + "?pwd=" + request.password;
        }
        MultiMap headers = shareHeaders(origin, referer, request.token);
        ShareHttpCall traffic = new ShareHttpCall(
                origin,
                SHARE_TRAFFIC_CHECK_PATH,
                signQuery(SHARE_TRAFFIC_CHECK_PATH, SHARE_PLATFORM, SHARE_WEB_APP_VERSION,
                        request.timeMillis, request.randomA),
                trafficCheckBody(request.shareKey, request.fileId),
                headers);
        ShareHttpCall download = new ShareHttpCall(
                origin,
                SHARE_DOWNLOAD_INFO_PATH,
                signQuery(SHARE_DOWNLOAD_INFO_PATH, SHARE_PLATFORM, SHARE_WEB_APP_VERSION,
                        request.timeMillis, request.randomA),
                downloadInfoBody(request.shareKey, request.fileId, request.s3keyFlag, request.size, request.etag),
                headers);
        return post.post(traffic).compose(json -> {
            if (json == null || json.getInteger("code", -1) != 0) {
                return Future.failedFuture(shareV2FailureMessage(json, request.token));
            }
            String stop = trafficStopMessage(json.getJsonObject("data"));
            if (stop != null) {
                return Future.failedFuture(stop);
            }
            return post.post(download);
        }).compose(json -> {
            if (json == null || json.getInteger("code", -1) != 0) {
                return Future.failedFuture(shareV2FailureMessage(json, request.token));
            }
            JsonObject data = json.getJsonObject("data", new JsonObject());
            JsonArray dispatchList = data.getJsonArray("dispatchList", new JsonArray());
            String prefix = "";
            if (!dispatchList.isEmpty() && dispatchList.getJsonObject(0) != null) {
                prefix = dispatchList.getJsonObject(0).getString("prefix", "");
            }
            String url = joinDownloadUrl(data.getString("downloadPath"), prefix);
            if (StringUtils.isBlank(url)) {
                return Future.failedFuture("123网盘公开下载接口未返回下载链接");
            }
            return Future.succeededFuture(url);
        });
    }

    private static MultiMap shareHeaders(String origin, String referer, String token) {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.set("Accept", "*/*");
        headers.set("App-Version", SHARE_WEB_APP_VERSION);
        headers.set("platform", SHARE_PLATFORM);
        headers.set("Origin", origin);
        headers.set("Referer", referer);
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36");
        headers.set("Content-Type", "application/json;charset=UTF-8");
        String authorization = bearerAuthorization(token);
        if (authorization != null) {
            headers.set("Authorization", authorization);
        }
        return headers;
    }

    private static String firstNonBlank(JsonObject json, String... keys) {
        if (json == null) {
            return "";
        }
        for (String key : keys) {
            String value = json.getString(key);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    static String resolveDownloadShareKey(JsonObject paramJson, String fallbackShareKey) {
        String shareKey = paramJson == null ? null : paramJson.getString("shareKey");
        if (StringUtils.isBlank(shareKey) && paramJson != null) {
            shareKey = paramJson.getString("ShareKey");
        }
        if (StringUtils.isBlank(shareKey)) {
            shareKey = fallbackShareKey;
        }
        return YeShareHostUtil.normalizeShareKey(shareKey);
    }

    static boolean usableShareKey(String shareKey) {
        return StringUtils.isNotBlank(shareKey) && !"-".equals(shareKey);
    }

    /**
     * getFileDownInfo / redirectUrl 用 shareKey="-" 占位，错误文案会变成 /s/-。
     * 目录参数里已有真实 shareKey 时，只替换这个占位地址。
     */
    static void applyResolvedShareKey(ShareLinkInfo info, String shareKey) {
        if (info == null || !usableShareKey(shareKey)) {
            return;
        }
        info.setShareKey(shareKey);
        String rebuilt = "https://www.123pan.com/s/" + shareKey;
        if (isPlaceholderYeShareUrl(info.getShareUrl())) {
            info.setShareUrl(rebuilt);
        }
        if (isPlaceholderYeShareUrl(info.getStandardUrl())) {
            info.setStandardUrl(rebuilt);
        }
    }

    static boolean isPlaceholderYeShareUrl(String url) {
        return StringUtils.isNotBlank(url) && (url.endsWith("/s/-") || url.endsWith("/s/-.html"));
    }

    static String bearerAuthorization(String token) {
        return hasUsableToken(token) ? "Bearer " + token : null;
    }

    /**
     * v2 非 0 的说明。5112 仍用现有登录提示；5113、5066 原样留在 v2 错误里，不再改写成个人文件接口的文案。
     */
    static String shareV2FailureMessage(JsonObject json, String token) {
        Integer code = json == null ? null : json.getInteger("code");
        String message = json == null ? null : json.getString("message");
        if (isYeLoginRequired(code, message)) {
            return loginRequiredDownloadMessage(hasUsableToken(token), message);
        }
        return "v2接口返回异常: " + (json == null ? "null" : json.encode());
    }

    private static boolean hasUsableToken(String token) {
        return StringUtils.isNotBlank(token) && !"nologin".equals(token);
    }

    private void setFileTimes(JsonObject item, FileInfo fileInfo) {
        String createAt = item.getString("CreateAt");
        if (StringUtils.isNotEmpty(createAt)) {
            fileInfo.setCreateTime(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(OffsetDateTime.parse(createAt).toLocalDateTime()));
        }

        String updateAt = item.getString("UpdateAt");
        if (StringUtils.isNotEmpty(updateAt)) {
            fileInfo.setUpdateTime(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(OffsetDateTime.parse(updateAt).toLocalDateTime()));
        }
    }

    private String resolveYeShareOrigin(String shareKey) {
        String origin = extractYeShareOrigin(shareLinkInfo.getShareUrl());
        if (StringUtils.isNotBlank(origin)) {
            return origin;
        }
        origin = extractYeShareOrigin(shareLinkInfo.getStandardUrl());
        if (StringUtils.isNotBlank(origin)) {
            return origin;
        }
        String uid = YeShareHostUtil.getNumericSubdomainIdByShareKey(shareKey);
        if (StringUtils.isNotBlank(uid)) {
            return "https://" + uid + ".share.123pan.cn";
        }
        return "https://www.123pan.com";
    }

    private String extractYeShareOrigin(String url) {
        if (StringUtils.isBlank(url) || !url.matches("^https?://[a-zA-Z\\d-]+\\.(?:mshare|share)\\.123pan\\.cn(?:[:/].*)?$")) {
            return "";
        }
        int idx = url.indexOf('/', url.indexOf("//") + 2);
        return idx > 0 ? url.substring(0, idx) : url;
    }

    private String buildShareReferer(String shareOrigin, String shareKey, String pwd) {
        String key = YeShareHostUtil.normalizeShareKey(shareKey);
        if (StringUtils.isBlank(key)) {
            return shareOrigin + "/";
        }
        String referer = shareOrigin + "/123pan/" + key;
        if (StringUtils.isNotBlank(pwd)) {
            referer += "?pwd=" + pwd;
        }
        return referer;
    }
}
