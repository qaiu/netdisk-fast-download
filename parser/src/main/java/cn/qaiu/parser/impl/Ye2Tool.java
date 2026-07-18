package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.parser.TokenCache;
import cn.qaiu.util.CommonUtils;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.CRC32;

import static cn.qaiu.util.RandomStringGenerator.gen36String;

/**
 * 123盘解析器 v2 - 使用Android平台API
 * 支持账号密码或token配置
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class Ye2Tool extends PanBase {

    private static final String API_BASE = "https://api.123278.com";
    private static final String GET_SHARE_INFO_URL = API_BASE + "/b/api/share/get?limit=100&next=1&orderBy=share_id&orderDirection=desc&shareKey={shareKey}&SharePwd={pwd}&ParentFileId={ParentFileId}&Page=1";
    private static final String DOWNLOAD_API_URL = API_BASE + "/b/api/file/download_info";
    private static final String DOWNLOAD_API_V2_BASE = "https://api.123278.com";
    private static final String DOWNLOAD_API_V2_PATH = "/b/api/v2/share/download/info";
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
        if (StringUtils.isNotEmpty(token)) {
            return token;
        }
        return auths.get("authorization");
    }

    private boolean isTokenExpired() {
        return TokenCache.isExpired(cacheKey);
    }

    private String crc32(String data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data.getBytes());
        long value = crc32.getValue();
        return String.format("%08x", value);
    }

    private long hexToInt(String hexStr) {
        return Long.parseLong(hexStr, 16);
    }

    private String encode123(String url, String way, String version, String timestamp) {
        Random random = new Random();
        int randomInt = random.nextInt(10000000) + 1;
        long a = (10000000L * randomInt) / 10000;

        long timeLong = Long.parseLong(timestamp) / 1000;
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

        String y = String.valueOf(hexToInt(crc32(g.toString())));
        String finalCrcInput = String.format("%d|%d|%s|%s|%s|%s", timeLong, a, url, way, version, y);
        String finalCrc = String.valueOf(hexToInt(crc32(finalCrcInput)));
        return String.format("?%s=%d-%d-%s", y, timeLong, a, finalCrc);
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
                            fail("获取分享信息失败: " + shareInfoJson.getString("message"));
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
                        if (token.equals("nologin")) {
                            fail("该分享需要登录才能下载，请配置认证信息");
                            return;
                        }

                        if (fileInfo.getInteger("Type", 0) == 1) {
                            getZipDownUrl(client, fileInfo);
                        } else {
                            getDownUrl(client, fileInfo);
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
                    JsonObject json = res.bodyAsJsonObject();
                    if (json == null) {
                        promise.fail("登录响应格式异常: " + res.bodyAsString());
                        return;
                    }
                    if (!json.containsKey("code")) {
                        promise.fail("登录响应格式异常: " + res.bodyAsString());
                        return;
                    }
                    if (json.getInteger("code") != 200) {
                        promise.fail("登录失败: " + json.getString("message"));
                        return;
                    }
                    JsonObject data = json.getJsonObject("data");
                    if (data == null || !data.containsKey("token")) {
                        promise.fail("未获取到token");
                        return;
                    }
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

    private void getDownUrl(WebClient client, JsonObject fileInfo) {
        setFileInfo(fileInfo);

        String normalizedShareKey = YeShareHostUtil.normalizeShareKey(shareLinkInfo.getShareKey());
        if (StringUtils.isNotEmpty(normalizedShareKey)) {
            JsonObject v2Body = new JsonObject()
                    .put("ShareKey", normalizedShareKey)
                    .put("FileID", fileInfo.getInteger("FileId"))
                    .put("S3keyFlag", fileInfo.getString("S3KeyFlag"))
                    .put("Size", fileInfo.getLong("Size"))
                    .put("Etag", fileInfo.getString("Etag"));
            requestShareV2Download(normalizedShareKey, v2Body).onSuccess(v2Url -> {
                if (StringUtils.isNotEmpty(v2Url)) {
                    complete(v2Url);
                    return;
                }
                requestLegacyDownUrl(client, fileInfo);
            }).onFailure(err -> {
                log.warn("Ye2 v2分享下载接口失败，回退旧接口: {}", err.getMessage());
                requestLegacyDownUrl(client, fileInfo);
            });
            return;
        }

        requestLegacyDownUrl(client, fileInfo);
    }

    private void requestLegacyDownUrl(WebClient client, JsonObject fileInfo) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("driveId", 0);
        jsonObject.put("etag", fileInfo.getString("Etag"));
        jsonObject.put("fileId", fileInfo.getInteger("FileId"));
        jsonObject.put("fileName", fileInfo.getString("FileName"));
        jsonObject.put("s3keyFlag", fileInfo.getString("S3KeyFlag"));
        jsonObject.put("size", fileInfo.getLong("Size"));
        jsonObject.put("type", 0);

        String timestamp = String.valueOf(System.currentTimeMillis());
        String encryptedParams = encode123("/b/api/file/download_info", "android", "55", timestamp);
        String apiUrl = DOWNLOAD_API_URL + encryptedParams;

        log.info("Ye2 API URL: {}", apiUrl);

        HttpRequest<Buffer> bufferHttpRequest = client.postAbs(apiUrl);
        bufferHttpRequest.putHeader("platform", "android");
        bufferHttpRequest.putHeader("App-Version", "55");
        bufferHttpRequest.putHeader("Authorization", "Bearer " + TokenCache.getToken(cacheKey));
        bufferHttpRequest.putHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
        bufferHttpRequest.putHeader("Content-Type", "application/json");

        bufferHttpRequest
                .sendJsonObject(jsonObject)
                .onSuccess(res2 -> handleDownloadUrlResponse(client, asJson(res2), "Ye2"))
                .onFailure(err -> fail("下载接口失败: " + err.getMessage()));
    }

    private Future<String> requestShareV2Download(String shareKey, JsonObject body) {
        Promise<String> promise = Promise.promise();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String encryptedParams = encode123(DOWNLOAD_API_V2_PATH, "web", "3", timestamp);
        String apiUrl = DOWNLOAD_API_V2_BASE + DOWNLOAD_API_V2_PATH + encryptedParams;
        String shareOrigin = resolveYeShareOrigin(shareKey);
        String shareReferer = buildShareReferer(shareOrigin, shareKey, shareLinkInfo.getSharePassword());

        HttpRequest<Buffer> request = client.postAbs(apiUrl);
        request.putHeader("Accept", "*/*");
        request.putHeader("Authorization", "Bearer " + TokenCache.getToken(cacheKey));
        request.putHeader("App-Version", "3");
        request.putHeader("platform", "web");
        request.putHeader("Origin", shareOrigin);
        request.putHeader("Referer", shareReferer);
        request.putHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36");
        request.putHeader("Content-Type", "application/json;charset=UTF-8");

        request.sendJsonObject(body).onSuccess(resp -> {
            JsonObject json = asJson(resp);
            if (json == null || json.getInteger("code", -1) != 0) {
                promise.fail("v2接口返回异常: " + (json == null ? "null" : json.encode()));
                return;
            }
            JsonObject data = json.getJsonObject("data", new JsonObject());
            JsonArray dispatchList = data.getJsonArray("dispatchList", new JsonArray());
            String downloadPath = data.getString("downloadPath");
            if (StringUtils.isBlank(downloadPath)) {
                promise.complete("");
                return;
            }
            String prefix = "";
            if (dispatchList.size() > 0) {
                JsonObject firstDispatch = dispatchList.getJsonObject(0);
                if (firstDispatch != null) {
                    prefix = firstDispatch.getString("prefix", "");
                }
            }
            if (StringUtils.isBlank(prefix)) {
                promise.complete(downloadPath);
                return;
            }
            String finalUrl = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
            finalUrl += downloadPath.startsWith("/") ? downloadPath : "/" + downloadPath;
            promise.complete(finalUrl);
        }).onFailure(promise::fail);

        return promise.future();
    }

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

    private void handleDownloadUrlResponse(WebClient client, JsonObject downURLJson, String failPrefix) {
        try {
            if (downURLJson.getInteger("code") != 0) {
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

            byte[] decodeByte = Base64.getDecoder().decode(params);
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
            if (token.equals("nologin")) {
                promise.fail("该分享需要登录才能访问，请配置认证信息");
                return;
            }

            String normalizedShareKey = YeShareHostUtil.normalizeShareKey(shareKey);
            String shareOrigin = resolveYeShareOrigin(normalizedShareKey);
            String shareReferer = buildShareReferer(shareOrigin, normalizedShareKey, pwd);

            client.getAbs(UriTemplate.of(GET_SHARE_INFO_URL))
                    .setTemplateParam("shareKey", shareKey)
                    .setTemplateParam("pwd", StringUtils.isEmpty(pwd) ? "" : pwd)
                    .setTemplateParam("ParentFileId", finalParentFileId)
                    .putHeader("Authorization", "Bearer " + token)
                    .putHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .putHeader("Referer", shareReferer)
                    .putHeader("Origin", shareOrigin)
                    .send().onSuccess(res -> {
                        JsonObject response = asJson(res);
                        if (response.getInteger("code") != 0) {
                            promise.fail("API错误: " + response.getString("message"));
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
            if (token.equals("nologin")) {
                fail("该分享需要登录才能下载，请配置认证信息");
                return;
            }

            String normalizedShareKey = YeShareHostUtil.normalizeShareKey(shareLinkInfo.getShareKey());
            if (StringUtils.isNotEmpty(normalizedShareKey)) {
                JsonObject v2Body = new JsonObject()
                        .put("ShareKey", normalizedShareKey)
                        .put("FileID", paramJson.getInteger("fileId", paramJson.getInteger("FileID", 0)))
                        .put("S3keyFlag", paramJson.getString("s3keyFlag", paramJson.getString("S3keyFlag", "")))
                        .put("Size", paramJson.getLong("size", paramJson.getLong("Size", 0L)))
                        .put("Etag", paramJson.getString("etag", paramJson.getString("Etag", "")));
                requestShareV2Download(normalizedShareKey, v2Body).onSuccess(v2Url -> {
                    if (StringUtils.isNotEmpty(v2Url)) {
                        complete(v2Url);
                        return;
                    }
                    parseByIdLegacy(token, paramJson);
                }).onFailure(err -> {
                    log.warn("Ye2 parseById v2接口失败，回退旧接口: {}", err.getMessage());
                    parseByIdLegacy(token, paramJson);
                });
                return;
            }

            parseByIdLegacy(token, paramJson);
        }).onFailure(err -> fail("123盘解析异常: " + err.getMessage()));

        return promise.future();
    }

    private void parseByIdLegacy(String token, JsonObject paramJson) {
        if (paramJson.containsKey("FileID") && !paramJson.containsKey("fileId")) {
            paramJson.put("fileId", paramJson.getValue("FileID"));
        }
        if (paramJson.containsKey("S3keyFlag") && !paramJson.containsKey("s3keyFlag")) {
            paramJson.put("s3keyFlag", paramJson.getValue("S3keyFlag"));
        }
        if (paramJson.containsKey("Size") && !paramJson.containsKey("size")) {
            paramJson.put("size", paramJson.getValue("Size"));
        }
        if (paramJson.containsKey("Etag") && !paramJson.containsKey("etag")) {
            paramJson.put("etag", paramJson.getValue("Etag"));
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String encryptedParams = encode123("/b/api/file/download_info", "android", "55", timestamp);
        String apiUrl = DOWNLOAD_API_URL + encryptedParams;

        log.info("Ye2 parseById API URL: {}", apiUrl);

        HttpRequest<Buffer> bufferHttpRequest = client.postAbs(apiUrl);
        bufferHttpRequest.putHeader("platform", "android");
        bufferHttpRequest.putHeader("App-Version", "55");
        bufferHttpRequest.putHeader("Authorization", "Bearer " + token);
        bufferHttpRequest.putHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
        bufferHttpRequest.putHeader("Content-Type", "application/json");

        bufferHttpRequest
                .sendJsonObject(paramJson)
                .onSuccess(res2 -> handleDownloadUrlResponse(client, asJson(res2), "Ye2"))
                .onFailure(err -> fail("下载接口失败: " + err.getMessage()));
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
