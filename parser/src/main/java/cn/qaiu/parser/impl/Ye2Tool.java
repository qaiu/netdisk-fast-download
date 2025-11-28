package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.CommonUtils;
import cn.qaiu.util.FileSizeConverter;
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
import java.util.*;
import java.util.zip.CRC32;

import static cn.qaiu.util.RandomStringGenerator.gen36String;

/**
 * 123盘解析器 v2 - 使用Android平台API
 * 支持账号密码或token配置
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class Ye2Tool extends PanBase {

    public static final String SHARE_URL_PREFIX = "https://www.123pan.com/s/";
    public static final String FIRST_REQUEST_URL = SHARE_URL_PREFIX + "{key}.html";
    private static final String GET_SHARE_INFO_URL = "https://www.123pan.com/b/api/share/get?limit=100&next=1&orderBy=share_id&orderDirection=desc&shareKey={shareKey}&SharePwd={pwd}&ParentFileId={ParentFileId}&Page=1";
    private static final String DOWNLOAD_API_URL = "https://www.123pan.com/b/api/file/download_info";
    private static final String BATCH_DOWNLOAD_API_URL = "https://www.123pan.com/b/api/file/batch_download_share_info";
    private static final String LOGIN_URL = "https://login.123pan.com/api/user/sign_in";

    // 字符映射表
    private static final String CHAR_MAP = "adefghlmyijnopkqrstubcvwsz";

    private final MultiMap header = MultiMap.caseInsensitiveMultiMap();

    // Token管理
    private static String ssoToken;
    private static long tokenExpireTime = 0L; // 毫秒时间戳

    public Ye2Tool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
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

    /**
     * 判断 token 是否过期
     */
    private boolean isTokenExpired() {
        return System.currentTimeMillis() > tokenExpireTime - 60_000; // 提前1分钟刷新
    }

    /**
     * 计算CRC32并转换为16进制字符串
     */
    private String crc32(String data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data.getBytes());
        long value = crc32.getValue();
        return String.format("%08x", value);
    }

    /**
     * 16进制转10进制
     */
    private long hexToInt(String hexStr) {
        return Long.parseLong(hexStr, 16);
    }

    /**
     * 123盘的URL加密算法
     * 参考Python代码中的encode123函数
     *
     * @param url 请求路径
     * @param way 平台标识（如"android"）
     * @param version 版本号（如"55"）
     * @param timestamp 时间戳（毫秒）
     * @return 加密后的URL参数，格式：?{y}={time_long}-{a}-{final_crc}
     */
    private String encode123(String url, String way, String version, String timestamp) {
        Random random = new Random();
        // 生成随机数 a = int(10000000 * random.randint(1, 10000000) / 10000)
        int randomInt = random.nextInt(10000000) + 1;
        long a = (10000000L * randomInt) / 10000;

        // 将时间戳转换为时间格式
        long timeLong = Long.parseLong(timestamp) / 1000;
        java.time.LocalDateTime dateTime = java.time.Instant.ofEpochSecond(timeLong)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
        String timeStr = dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));

        // 根据时间字符串生成g
        StringBuilder g = new StringBuilder();
        for (char c : timeStr.toCharArray()) {
            int digit = Character.getNumericValue(c);
            if (digit == 0) {
                g.append(CHAR_MAP.charAt(0));
            } else {
                // 数字1对应索引0，数字2对应索引1，以此类推
                g.append(CHAR_MAP.charAt(digit - 1));
            }
        }

        // 计算y值（CRC32的十进制）
        String y = String.valueOf(hexToInt(crc32(g.toString())));

        // 计算最终的CRC32
        String finalCrcInput = String.format("%d|%d|%s|%s|%s|%s", timeLong, a, url, way, version, y);
        String finalCrc = String.valueOf(hexToInt(crc32(finalCrcInput)));

        // 返回加密后的URL参数
        return String.format("?%s=%d-%d-%s", y, timeLong, a, finalCrc);
    }

    public Future<String> parse() {
        Future<String> tokenFuture;

        // 检查是否直接提供了token
        MultiMap auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
        if (auths != null && auths.contains("token")) {
            String providedToken = auths.get("token");
            if (StringUtils.isNotEmpty(providedToken)) {
                ssoToken = providedToken;
                tokenFuture = Future.succeededFuture(providedToken);
            } else {
                // 如果没有提供token，尝试登录
                if (ssoToken == null || isTokenExpired()) {
                    tokenFuture = loginAndGetToken();
                } else {
                    tokenFuture = Future.succeededFuture(ssoToken);
                }
            }
        } else {
            // 如果没有提供token，尝试登录
            if (ssoToken == null || isTokenExpired()) {
                tokenFuture = loginAndGetToken();
            } else {
                tokenFuture = Future.succeededFuture(ssoToken);
            }
        }

        // 1. 登录获取 sso-token 或使用提供的token
        tokenFuture.onSuccess(token -> {
            if (!token.equals("nologin")) {
                // 2. 设置 header
                ssoToken = token;
                header.set("Authorization", "Bearer " + token);
            }

            final String dataKey = shareLinkInfo.getShareKey().replace(".html", "");
            final String pwd = shareLinkInfo.getSharePassword();

            // 3. 获取分享信息
            client.getAbs(UriTemplate.of(GET_SHARE_INFO_URL))
                    .setTemplateParam("shareKey", dataKey)
                    .setTemplateParam("pwd", StringUtils.isEmpty(pwd) ? "" : pwd)
                    .setTemplateParam("ParentFileId", "0")
                    .putHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .putHeader("Referer", "https://www.123pan.com/")
                    .putHeader("Origin", "https://www.123pan.com")
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

                        // 获取第一个文件信息
                        JsonObject fileInfo = data.getJsonArray("InfoList").getJsonObject(0);

                        // 检查是否需要登录
                        if (token.equals("nologin")) {
                            fail("该分享需要登录才能下载，请提供账号密码或token");
                            return;
                        }

                        // 判断是否为文件夹: Type: 1为文件夹, 0为文件
                        if (fileInfo.getInteger("Type", 0) == 1) {
                            // 4. 获取文件夹打包下载链接
                            getZipDownUrl(client, fileInfo);
                        } else {
                            // 4. 获取文件下载链接
                            getDownUrl(client, fileInfo);
                        }
                    })
                    .onFailure(this.handleFail(GET_SHARE_INFO_URL));
        }).onFailure(err -> {
            fail("登录获取token失败: {}", err.getMessage());
        });

        return promise.future();
    }

    /**
     * 登录并获取token
     */
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
                    ssoToken = data.getString("token");
                    String expireStr = data.getString("expire");
                    // 解析过期时间
                    if (StringUtils.isNotEmpty(expireStr)) {
                        tokenExpireTime = OffsetDateTime.parse(expireStr)
                                .toInstant().toEpochMilli();
                    } else {
                        // 如果没有过期时间，默认1小时后过期
                        tokenExpireTime = System.currentTimeMillis() + 3600_000;
                    }
                    log.info("登录成功，token: {}", ssoToken);
                    promise.complete(ssoToken);
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    /**
     * 获取下载链接（使用Android平台API）
     */
    private void getDownUrl(WebClient client, JsonObject fileInfo) {
        setFileInfo(fileInfo);

        // 构建请求数据
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("driveId", 0);
        jsonObject.put("etag", fileInfo.getString("Etag"));
        jsonObject.put("fileId", fileInfo.getInteger("FileId"));
        jsonObject.put("fileName", fileInfo.getString("FileName"));
        jsonObject.put("s3keyFlag", fileInfo.getString("S3KeyFlag"));
        jsonObject.put("size", fileInfo.getLong("Size"));
        jsonObject.put("type", 0);

        // 使用encode123加密URL参数
        String timestamp = String.valueOf(System.currentTimeMillis());
        String encryptedParams = encode123("/b/api/file/download_info", "android", "55", timestamp);
        String apiUrl = DOWNLOAD_API_URL + encryptedParams;

        log.info("Ye2 API URL: {}", apiUrl);

        HttpRequest<Buffer> bufferHttpRequest = client.postAbs(apiUrl);
        bufferHttpRequest.putHeader("platform", "android");
        bufferHttpRequest.putHeader("App-Version", "55");
        bufferHttpRequest.putHeader("Authorization", "Bearer " + ssoToken);
        bufferHttpRequest.putHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
        bufferHttpRequest.putHeader("Content-Type", "application/json");

        bufferHttpRequest
                .sendJsonObject(jsonObject)
                .onSuccess(res2 -> {
                    JsonObject downURLJson = asJson(res2);
                    try {
                        if (downURLJson.getInteger("code") != 0) {
                            fail("Ye2: downURLJson返回值异常->" + downURLJson);
                            return;
                        }
                    } catch (Exception ignored) {
                        fail("Ye2: downURLJson格式异常->" + downURLJson);
                        return;
                    }

                    String downURL = downURLJson.getJsonObject("data").getString("DownloadUrl");
                    if (StringUtils.isEmpty(downURL)) {
                        downURL = downURLJson.getJsonObject("data").getString("DownloadURL");
                    }

                    if (StringUtils.isEmpty(downURL)) {
                        fail("Ye2: 未获取到下载链接");
                        return;
                    }

                    try {
                        Map<String, String> urlParams = CommonUtils.getURLParams(downURL);
                        String params = urlParams.get("params");
                        if (StringUtils.isEmpty(params)) {
                            // 如果没有params参数，直接使用downURL
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
                                    fail("Ye2: downUrl2返回值异常->" + res3Json);
                                    return;
                                }
                            } catch (Exception ignored) {
                                fail("Ye2: downUrl2格式异常->" + downURLJson);
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
                        // 如果解析失败，直接使用downURL
                        complete(downURL);
                    } catch (Exception e) {
                        fail("urlParams解析异常: " + e.getMessage());
                    }
                }).onFailure(err -> fail("下载接口失败: " + err.getMessage()));
    }

    /**
     * 获取文件夹打包下载链接（使用Android平台API）
     */
    private void getZipDownUrl(WebClient client, JsonObject fileInfo) {
        // 构建请求数据
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("shareKey", shareLinkInfo.getShareKey().replace(".html", ""));
        jsonObject.put("fileIdList", new JsonArray().add(JsonObject.of("fileId", fileInfo.getInteger("FileId"))));

        // 使用encode123加密URL参数
        String timestamp = String.valueOf(System.currentTimeMillis());
        String encryptedParams = encode123("/b/api/file/batch_download_share_info", "android", "55", timestamp);
        String apiUrl = BATCH_DOWNLOAD_API_URL + encryptedParams;

        log.info("Ye2 Batch Download API URL: {}", apiUrl);

        HttpRequest<Buffer> bufferHttpRequest = client.postAbs(apiUrl);
        bufferHttpRequest.putHeader("platform", "android");
        bufferHttpRequest.putHeader("App-Version", "55");
        bufferHttpRequest.putHeader("Authorization", "Bearer " + ssoToken);
        bufferHttpRequest.putHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
        bufferHttpRequest.putHeader("Content-Type", "application/json");

        bufferHttpRequest
                .sendJsonObject(jsonObject)
                .onSuccess(res2 -> {
                    JsonObject downURLJson = asJson(res2);
                    try {
                        if (downURLJson.getInteger("code") != 0) {
                            fail("Ye2: 文件夹打包下载接口返回值异常->" + downURLJson);
                            return;
                        }
                    } catch (Exception ignored) {
                        fail("Ye2: 文件夹打包下载接口格式异常->" + downURLJson);
                        return;
                    }

                    String downURL = downURLJson.getJsonObject("data").getString("DownloadUrl");
                    if (StringUtils.isEmpty(downURL)) {
                        downURL = downURLJson.getJsonObject("data").getString("DownloadURL");
                    }

                    if (StringUtils.isEmpty(downURL)) {
                        fail("Ye2: 未获取到文件夹打包下载链接");
                        return;
                    }

                    try {
                        Map<String, String> urlParams = CommonUtils.getURLParams(downURL);
                        String params = urlParams.get("params");
                        if (StringUtils.isEmpty(params)) {
                            // 如果没有params参数，直接使用downURL
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
                                    fail("Ye2: 文件夹打包下载重定向返回值异常->" + res3Json);
                                    return;
                                }
                            } catch (Exception ignored) {
                                fail("Ye2: 文件夹打包下载重定向格式异常->" + downURLJson);
                                return;
                            }
                            String redirectUrl = res3Json.getJsonObject("data").getString("redirect_url");
                            if (StringUtils.isNotEmpty(redirectUrl)) {
                                complete(redirectUrl);
                            } else {
                                complete(downUrl2);
                            }
                        }).onFailure(err -> fail("获取文件夹打包下载直链失败: " + err.getMessage()));
                    } catch (MalformedURLException e) {
                        // 如果解析失败，直接使用downURL
                        complete(downURL);
                    } catch (Exception e) {
                        fail("文件夹打包下载urlParams解析异常: " + e.getMessage());
                    }
                }).onFailure(err -> fail("文件夹打包下载接口失败: " + err.getMessage()));
    }

    /**
     * 设置文件信息
     */
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

    /**
     * 解析文件夹中的文件列表
     */
    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> promise = Promise.promise();

        String shareKey = shareLinkInfo.getShareKey().replace(".html", "");
        String pwd = shareLinkInfo.getSharePassword();
        String parentFileId = "0"; // 根目录的文件ID

        // 如果参数里的目录ID不为空，则直接解析目录
        String dirId = (String) shareLinkInfo.getOtherParam().get("dirId");
        if (StringUtils.isNotBlank(dirId)) {
            parentFileId = dirId;
        }

        // 确保已登录
        Future<String> tokenFuture;
        MultiMap auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
        if (auths != null && auths.contains("token")) {
            String providedToken = auths.get("token");
            if (StringUtils.isNotEmpty(providedToken)) {
                ssoToken = providedToken;
                tokenFuture = Future.succeededFuture(providedToken);
            } else {
                if (ssoToken == null || isTokenExpired()) {
                    tokenFuture = loginAndGetToken();
                } else {
                    tokenFuture = Future.succeededFuture(ssoToken);
                }
            }
        } else {
            if (ssoToken == null || isTokenExpired()) {
                tokenFuture = loginAndGetToken();
            } else {
                tokenFuture = Future.succeededFuture(ssoToken);
            }
        }

        String finalParentFileId = parentFileId;
        tokenFuture.onSuccess(token -> {
            if (token.equals("nologin")) {
                promise.fail("该分享需要登录才能访问，请提供账号密码或token");
                return;
            }

            // 构造文件列表接口的URL
            client.getAbs(UriTemplate.of(GET_SHARE_INFO_URL))
                    .setTemplateParam("shareKey", shareKey)
                    .setTemplateParam("pwd", StringUtils.isEmpty(pwd) ? "" : pwd)
                    .setTemplateParam("ParentFileId", finalParentFileId)
                    .putHeader("Authorization", "Bearer " + token)
                    .putHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .putHeader("Referer", "https://www.123pan.com/")
                    .putHeader("Origin", "https://www.123pan.com")
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

                        // 遍历返回的文件和目录信息
                        for (int i = 0; i < infoList.size(); i++) {
                            JsonObject item = infoList.getJsonObject(i);
                            FileInfo fileInfo = new FileInfo();

                            // 构建下载参数
                            JsonObject postData = JsonObject.of()
                                    .put("driveId", 0)
                                    .put("etag", item.getString("Etag"))
                                    .put("fileId", item.getInteger("FileId"))
                                    .put("fileName", item.getString("FileName"))
                                    .put("s3keyFlag", item.getString("S3KeyFlag"))
                                    .put("size", item.getLong("Size"))
                                    .put("type", 0);

                            String param = CommonUtils.urlBase64Encode(postData.encode());

                            if (item.getInteger("Type") == 0) { // 文件
                                fileInfo.setFileName(item.getString("FileName"))
                                        .setFileId(item.getInteger("FileId").toString())
                                        .setFileType("file")
                                        .setSize(item.getLong("Size"))
                                        .setHash(item.getString("Etag"))
                                        .setSizeStr(FileSizeConverter.convertToReadableSize(item.getLong("Size")));

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

                                fileInfo.setParserUrl(String.format("%s/v2/redirectUrl/%s/%s", getDomainName(),
                                                shareLinkInfo.getType(), param))
                                        .setPreviewUrl(String.format("%s/v2/viewUrl/%s/%s", getDomainName(),
                                                shareLinkInfo.getType(), param));
                                result.add(fileInfo);
                            } else if (item.getInteger("Type") == 1) { // 目录
                                fileInfo.setFileName(item.getString("FileName"))
                                        .setFileId(item.getInteger("FileId").toString())
                                        .setFileType("folder")
                                        .setSize(0L);

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
        }).onFailure(err -> promise.fail("登录获取token失败: " + err.getMessage()));

        return promise.future();
    }

    /**
     * 通过ID解析特定文件
     */
    @Override
    public Future<String> parseById() {
        JsonObject paramJson = (JsonObject) shareLinkInfo.getOtherParam().get("paramJson");

        // 确保已登录
        Future<String> tokenFuture;
        MultiMap auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
        if (auths != null && auths.contains("token")) {
            String providedToken = auths.get("token");
            if (StringUtils.isNotEmpty(providedToken)) {
                ssoToken = providedToken;
                tokenFuture = Future.succeededFuture(providedToken);
            } else {
                if (ssoToken == null || isTokenExpired()) {
                    tokenFuture = loginAndGetToken();
                } else {
                    tokenFuture = Future.succeededFuture(ssoToken);
                }
            }
        } else {
            if (ssoToken == null || isTokenExpired()) {
                tokenFuture = loginAndGetToken();
            } else {
                tokenFuture = Future.succeededFuture(ssoToken);
            }
        }

        tokenFuture.onSuccess(token -> {
            if (token.equals("nologin")) {
                fail("该分享需要登录才能下载，请提供账号密码或token");
                return;
            }

            // 使用encode123加密URL参数
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
                    .onSuccess(res2 -> {
                        JsonObject downURLJson = asJson(res2);
                        try {
                            if (downURLJson.getInteger("code") != 0) {
                                fail("Ye2: downURLJson返回值异常->" + downURLJson);
                                return;
                            }
                        } catch (Exception ignored) {
                            fail("Ye2: downURLJson格式异常->" + downURLJson);
                            return;
                        }

                        String downURL = downURLJson.getJsonObject("data").getString("DownloadUrl");
                        if (StringUtils.isEmpty(downURL)) {
                            downURL = downURLJson.getJsonObject("data").getString("DownloadURL");
                        }

                        if (StringUtils.isEmpty(downURL)) {
                            fail("Ye2: 未获取到下载链接");
                            return;
                        }

                        try {
                            Map<String, String> urlParams = CommonUtils.getURLParams(downURL);
                            String params = urlParams.get("params");
                            if (StringUtils.isEmpty(params)) {
                                // 如果没有params参数，直接使用downURL
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
                                        fail("Ye2: downUrl2返回值异常->" + res3Json);
                                        return;
                                    }
                                } catch (Exception ignored) {
                                    fail("Ye2: downUrl2格式异常->" + downURLJson);
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
                            // 如果解析失败，直接使用downURL
                            complete(downURL);
                        } catch (Exception e) {
                            fail("urlParams解析异常: " + e.getMessage());
                        }
                    }).onFailure(err -> fail("下载接口失败: " + err.getMessage()));
        }).onFailure(err -> fail("登录获取token失败: " + err.getMessage()));

        return promise.future();
    }
}

