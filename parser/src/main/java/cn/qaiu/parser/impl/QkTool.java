package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.parser.TokenCache;
import cn.qaiu.util.CommonUtils;
import cn.qaiu.util.CookieUtils;
import cn.qaiu.util.FileSizeConverter;
import cn.qaiu.util.HeaderUtils;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 夸克网盘解析
 */
public class QkTool extends PanBase {
    
    public static final String SHARE_URL_PREFIX = "https://pan.quark.cn/s/";
    
    private static final String TOKEN_URL = "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/token";
    private static final String DETAIL_URL = "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/detail";
    private static final String DOWNLOAD_URL = "https://drive-pc.quark.cn/1/clouddrive/file/download";
    
    // Cookie 刷新 API
    private static final String FLUSH_URL = "https://drive-pc.quark.cn/1/clouddrive/auth/pc/flush";
    
    // 转存相关 API
    private static final String SAVE_DIR_URL = "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/dir";
    private static final String SAVE_URL = "https://drive-h.quark.cn/1/clouddrive/share/sharepage/save";
    private static final String TASK_URL = "https://drive-pc.quark.cn/1/clouddrive/task";
    
    // 多账号隔离：用 TokenCache 代替 static cookie 缓存
    // __puus 有效期，默认 55 分钟（服务器实际 1 小时过期，提前 5 分钟刷新）
    private static final long PUUS_TTL_MS = 55 * 60 * 1000L;
    
    // 静态变量：缓存已转存的文件 (分享key -> 转存文件ID)
    private static final Map<String, String> savedFileCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    private final MultiMap header = HeaderUtils.parseHeaders("""
            User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) quark-cloud-drive/2.5.20 Chrome/100.0.4896.160 Electron/18.3.5.4-b478491100 Safari/537.36 Channel/pckk_other_ch
            Content-Type: application/json;charset=UTF-8
            Referer: https://pan.quark.cn/
            Origin: https://pan.quark.cn
            Accept: application/json, text/plain, */*
            """);

    // 保存 auths 引用，用于更新 cookie
    private MultiMap auths;
    private final String cacheKey;

    public QkTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
        String accountId = "_default";
        // 参考 UcTool 实现，从认证配置中取 cookie 放到请求头
        if (shareLinkInfo.getOtherParam() != null && shareLinkInfo.getOtherParam().containsKey("auths")) {
            auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
            if (auths.contains("_configId")) {
                accountId = auths.get("_configId");
            } else if (auths.contains("cookie")) {
                accountId = auths.get("cookie").substring(0, Math.min(16, auths.get("cookie").length()));
            }
            String cookie = auths.get("cookie");
            if (cookie != null && !cookie.isEmpty()) {
                // 过滤出夸克网盘所需的 cookie 字段
                cookie = CookieUtils.filterUcQuarkCookie(cookie);
                
                // 如果有缓存的 __puus 且未过期，使用缓存的值更新 cookie
                String cachedPuus = TokenCache.getToken(TokenCache.key("qk", accountId));
                long puusExpire = TokenCache.getExpire(TokenCache.key("qk", accountId));
                if (cachedPuus != null && System.currentTimeMillis() < puusExpire) {
                    cookie = CookieUtils.updateCookieValue(cookie, "__puus", cachedPuus);
                    log.debug("夸克: 使用缓存的 __puus (剩余有效期: {}s)", (puusExpire - System.currentTimeMillis()) / 1000);
                }
                header.set(HttpHeaders.COOKIE, cookie);
                // 同步更新 auths
                auths.set("cookie", cookie);
            }
        }
        this.cacheKey = TokenCache.key("qk", accountId);
        this.client = clientDisableUA;
        
        // 如果 __puus 已过期或不存在，触发异步刷新
        if (needRefreshPuus()) {
            log.debug("夸克: __puus 需要刷新，触发异步刷新");
            refreshPuusCookie();
        }
    }
    
    /**
     * 判断是否需要刷新 __puus
     * @return true 表示需要刷新
     */
    private boolean needRefreshPuus() {
        String currentCookie = header.get(HttpHeaders.COOKIE);
        if (currentCookie == null || currentCookie.isEmpty()) {
            return false;
        }
        // 必须包含 __pus 才能刷新
        if (!currentCookie.contains("__pus=")) {
            return false;
        }
        // 缓存过期或不存在时需要刷新
        return TokenCache.getToken(cacheKey) == null || System.currentTimeMillis() >= TokenCache.getExpire(cacheKey);
    }

    /**
     * 刷新 __puus Cookie
     * 通过调用 auth/pc/flush API，服务器会返回 set-cookie 来更新 __puus
     * @return Future 包含是否刷新成功
     */
    public Future<Boolean> refreshPuusCookie() {
        Promise<Boolean> refreshPromise = Promise.promise();
        
        String currentCookie = header.get(HttpHeaders.COOKIE);
        if (currentCookie == null || currentCookie.isEmpty()) {
            log.debug("夸克: 无 cookie，跳过刷新");
            refreshPromise.complete(false);
            return refreshPromise.future();
        }
        
        // 检查是否包含 __pus（用于获取 __puus）
        if (!currentCookie.contains("__pus=")) {
            log.debug("夸克: cookie 中不包含 __pus，跳过刷新");
            refreshPromise.complete(false);
            return refreshPromise.future();
        }
        
        log.debug("夸克: 开始刷新 __puus cookie");
        
        client.getAbs(FLUSH_URL)
                .addQueryParam("pr", "ucpro")
                .addQueryParam("fr", "pc")
                .addQueryParam("uc_param_str", "")
                .putHeaders(header)
                .send()
                .onSuccess(res -> {
                    // 从响应头获取 set-cookie
                    List<String> setCookies = res.cookies();
                    String newPuus = null;
                    
                    for (String cookie : setCookies) {
                        if (cookie.startsWith("__puus=")) {
                            // 提取 __puus 值（只取到分号前的部分）
                            int endIndex = cookie.indexOf(';');
                            newPuus = endIndex > 0 ? cookie.substring(0, endIndex) : cookie;
                            break;
                        }
                    }
                    
                    if (newPuus != null) {
                        // 更新 cookie：替换或添加 __puus
                        String updatedCookie = CookieUtils.updateCookieValue(currentCookie, "__puus", newPuus);
                        header.set(HttpHeaders.COOKIE, updatedCookie);
                        
                        // 同步更新 auths 中的 cookie
                        if (auths != null) {
                            auths.set("cookie", updatedCookie);
                        }
                        
                        // 更新静态缓存
                        TokenCache.putToken(cacheKey, newPuus);
                        TokenCache.putExpire(cacheKey, System.currentTimeMillis() + PUUS_TTL_MS);
                        
                        log.info("夸克: __puus cookie 刷新成功，有效期至: {}ms", TokenCache.getExpire(cacheKey));
                        refreshPromise.complete(true);
                    } else {
                        log.debug("夸克: 响应中未包含 __puus，可能 cookie 仍然有效");
                        refreshPromise.complete(false);
                    }
                })
                .onFailure(t -> {
                    log.warn("夸克: 刷新 __puus cookie 失败: {}", t.getMessage());
                    refreshPromise.complete(false);
                });
        
        return refreshPromise.future();
    }

    @Override
    public Future<String> parse() {
        String pwdId = shareLinkInfo.getShareKey();
        String passcode = shareLinkInfo.getSharePassword();
        if (passcode == null) {
            passcode = "";
        }
        
        log.debug("开始解析夸克网盘分享，pwd_id: {}, passcode: {}", pwdId, passcode.isEmpty() ? "无" : "有");
        
        // 第一步：获取分享 token
        JsonObject tokenRequest = new JsonObject()
                .put("pwd_id", pwdId)
                .put("passcode", passcode);
        
        client.postAbs(TOKEN_URL)
                .addQueryParam("pr", "ucpro")
                .addQueryParam("fr", "pc")
                .putHeaders(header)
                .sendJsonObject(tokenRequest)
                .onSuccess(res -> {
                    log.debug("第一阶段响应: {}", res.bodyAsString());
                    JsonObject resJson = asJson(res);
                    
                    if (resJson.getInteger("code") != 0) {
                        fail(TOKEN_URL + " 返回异常: " + resJson);
                        return;
                    }
                    
                    String stoken = resJson.getJsonObject("data").getString("stoken");
                    if (stoken == null || stoken.isEmpty()) {
                        fail("无法获取分享 token，可能的原因：1. Cookie 已过期 2. 分享链接已失效 3. 需要提取码但未提供");
                        return;
                    }
                    
                    log.debug("成功获取 stoken: {}", stoken);
                    
                    // 第二步：获取文件列表
                    client.getAbs(DETAIL_URL)
                            .addQueryParam("pr", "ucpro")
                            .addQueryParam("fr", "pc")
                            .addQueryParam("pwd_id", pwdId)
                            .addQueryParam("stoken", stoken)
                            .addQueryParam("pdir_fid", "0")
                            .addQueryParam("force", "0")
                            .addQueryParam("_page", "1")
                            .addQueryParam("_size", "50")
                            .addQueryParam("_fetch_banner", "1")
                            .addQueryParam("_fetch_share", "1")
                            .addQueryParam("_fetch_total", "1")
                            .addQueryParam("_sort", "file_type:asc,updated_at:desc")
                            .putHeaders(header)
                            .send()
                            .onSuccess(res2 -> {
                                log.debug("第二阶段响应: {}", res2.bodyAsString());
                                JsonObject resJson2 = asJson(res2);
                                
                                if (resJson2.getInteger("code") != 0) {
                                    fail(DETAIL_URL + " 返回异常: " + resJson2);
                                    return;
                                }
                                
                                JsonArray fileList = resJson2.getJsonObject("data").getJsonArray("list");
                                if (fileList == null || fileList.isEmpty()) {
                                    fail("未找到文件");
                                    return;
                                }
                                
                                // 过滤出文件（排除文件夹）
                                List<JsonObject> files = new ArrayList<>();
                                for (int i = 0; i < fileList.size(); i++) {
                                    JsonObject item = fileList.getJsonObject(i);
                                    // 判断是否为文件：file=true 或 obj_category 不为空
                                    if (item.getBoolean("file", false) || 
                                        (item.getString("obj_category") != null && !item.getString("obj_category").isEmpty())) {
                                        files.add(item);
                                    }
                                }
                                
                                if (files.isEmpty()) {
                                    fail("没有可下载的文件（可能都是文件夹）");
                                    return;
                                }
                                
                                log.debug("找到 {} 个文件", files.size());
                                
                                // 提取第一个文件的信息并保存到 otherParam
                                try {
                                    JsonObject firstFile = files.get(0);
                                    FileInfo fileInfo = new FileInfo();
                                    fileInfo.setFileId(firstFile.getString("fid"))
                                            .setFileName(firstFile.getString("file_name"))
                                            .setSize(firstFile.getLong("size", 0L))
                                            .setSizeStr(FileSizeConverter.convertToReadableSize(firstFile.getLong("size", 0L)))
                                            .setFileType(firstFile.getBoolean("file", true) ? "file" : "folder")
                                            .setCreateTime(formatEpochMs(firstFile.getLong("updated_at", 0L)))
                                            .setUpdateTime(formatEpochMs(firstFile.getLong("updated_at", 0L)))
                                            .setPanType(shareLinkInfo.getType());
                                    
                                    // 保存到 otherParam，供 CacheServiceImpl 使用
                                    shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
                                    log.debug("夸克提取文件信息: {}", fileInfo.getFileName());
                                } catch (Exception e) {
                                    log.warn("夸克提取文件信息失败，继续解析: {}", e.getMessage());
                                }
                                
                                // 提取文件ID列表
                                List<String> fileIds = new ArrayList<>();
                                for (JsonObject file : files) {
                                    String fid = file.getString("fid");
                                    if (fid != null && !fid.isEmpty()) {
                                        fileIds.add(fid);
                                    }
                                }
                                
                                if (fileIds.isEmpty()) {
                                    fail("无法提取文件ID");
                                    return;
                                }
                                
                                // 第三步：解析下载链接（先直链，必要时才转存）
                                JsonObject firstFileForDownload = files.get(0);
                                String firstFid = firstFileForDownload.getString("fid");
                                String firstShareFidToken = firstFileForDownload.getString("share_fid_token");

                                resolveDownloadUrl(firstFid, pwdId, stoken, firstShareFidToken)
                                        .onSuccess(downloadUrl -> completeWithMeta(downloadUrl, buildDownloadHeaders(null)))
                                        .onFailure(handleFail("获取下载链接"));
                                
                            }).onFailure(handleFail(DETAIL_URL));
                })
                .onFailure(handleFail(TOKEN_URL));
        
        return promise.future();
    }

    /**
     * 统一下载入口（parse / parseById 共用）：
     * <ol>
     *   <li>先尝试分享直链——成功则无需转存</li>
     *   <li>仅当接口明确要求转存（如 23018 大文件限制）才进入转存</li>
     *   <li>转存前查本地 {@link #savedFileCache}，命中则直接下，避免重复转存</li>
     *   <li>转存时服务端 search_exit=true 会复用已有文件，不会建副本</li>
     * </ol>
     */
    private Future<String> resolveDownloadUrl(String fid, String pwdId, String stoken, String shareFidToken) {
        return getShareDownloadLink(fid, pwdId, stoken, shareFidToken)
                .recover(t -> {
                    if (!needsTransferFallback(t)) {
                        // 登录失效等错误直接失败，不盲目转存
                        return Future.failedFuture(t);
                    }
                    log.warn("夸克分享直链不可用，按需转存: {}", t.getMessage());
                    return saveAndDownload(pwdId, stoken, fid, shareFidToken);
                });
    }

    /**
     * 是否属于「必须转存」类错误。
     * 典型：code 23018 download file size limit（分享直链有大小限制）。
     */
    private static boolean needsTransferFallback(Throwable t) {
        String msg = t == null || t.getMessage() == null ? "" : t.getMessage();
        return msg.contains("23018")
                || msg.contains("size limit")
                || msg.contains("download file size limit");
    }
    
    /**
     * 直接从分享获取下载地址（无需转存的优先路径）
     */
    private Future<String> getShareDownloadLink(String fid, String pwdId, String stoken, String shareFidToken) {
        Promise<String> promise = Promise.promise();

        JsonObject bodyJson = JsonObject.of()
                .put("fids", JsonArray.of(fid))
                .put("pwd_id", pwdId)
                .put("stoken", stoken);

        if (shareFidToken != null && !shareFidToken.isEmpty()) {
            bodyJson.put("fids_token", JsonArray.of(shareFidToken));
        }

        client.postAbs(DOWNLOAD_URL)
                .addQueryParam("pr", "ucpro")
                .addQueryParam("fr", "pc")
                .putHeaders(header)
                .sendJsonObject(bodyJson)
                .onSuccess(res -> {
                    JsonObject resJson = asJson(res);
                    Integer code = resJson.getInteger("code");

                    if (code != null && code == 31001) {
                        promise.fail("未登录或 Cookie 已失效");
                        return;
                    }

                    if (code == null || code != 0) {
                        // 保留 code/message，供 needsTransferFallback 识别 23018
                        promise.fail("分享直链获取失败(code=" + code + "): "
                                + resJson.getString("message") + " -> " + resJson);
                        return;
                    }

                    JsonArray dataList = resJson.getJsonArray("data");
                    if (dataList == null || dataList.isEmpty()) {
                        promise.fail("分享直链返回数据为空");
                        return;
                    }

                    String downloadUrl = dataList.getJsonObject(0).getString("download_url");
                    if (downloadUrl == null || downloadUrl.isEmpty()) {
                        promise.fail("分享直链为空");
                        return;
                    }

                    log.debug("夸克分享直链成功（无需转存）");
                    promise.complete(downloadUrl);
                })
                .onFailure(t -> promise.fail("分享直链请求失败: " + t.getMessage()));

        return promise.future();
    }

    // 目录解析
    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> promise = Promise.promise();
        
        String pwdId = shareLinkInfo.getShareKey();
        String passcode = shareLinkInfo.getSharePassword();
        final String finalPasscode = (passcode == null) ? "" : passcode;
        
        // 如果参数里的目录ID不为空，则直接解析目录
        String dirId = (String) shareLinkInfo.getOtherParam().get("dirId");
        if (dirId != null && !dirId.isEmpty()) {
            String stoken = (String) shareLinkInfo.getOtherParam().get("stoken");
            if (stoken != null) {
                parseDir(dirId, pwdId, finalPasscode, stoken, promise);
                return promise.future();
            }
        }
        
        // 第一步：获取 stoken
        JsonObject tokenRequest = new JsonObject()
                .put("pwd_id", pwdId)
                .put("passcode", finalPasscode);
        
        client.postAbs(TOKEN_URL)
                .addQueryParam("pr", "ucpro")
                .addQueryParam("fr", "pc")
                .putHeaders(header)
                .sendJsonObject(tokenRequest)
                .onSuccess(res -> {
                    JsonObject resJson = asJson(res);
                    if (resJson.getInteger("code") != 0) {
                        promise.fail(TOKEN_URL + " 返回异常: " + resJson);
                        return;
                    }
                    JsonObject data = resJson.getJsonObject("data");
                    if (data != null) {
                        String title = data.getString("title");
                        if (title != null && !title.isBlank()) {
                            shareLinkInfo.getOtherParam().put("title", title);
                        }
                    }
                    String stoken = data == null ? null : data.getString("stoken");
                    if (stoken == null || stoken.isEmpty()) {
                        promise.fail("无法获取分享 token");
                        return;
                    }
                    // 解析根目录（dirId = "0"）
                    String rootDirId = dirId != null ? dirId : "0";
                    parseDir(rootDirId, pwdId, finalPasscode, stoken, promise);
                })
                .onFailure(t -> promise.fail("获取 token 失败: " + t.getMessage()));
        
        return promise.future();
    }

    private void parseDir(String dirId, String pwdId, String passcode, String stoken, Promise<List<FileInfo>> promise) {
        // 第二步：获取文件列表（支持指定目录）
        // 夸克 API 使用 pdir_fid 参数指定父目录 ID，根目录为 "0"
        log.info("夸克 parseDir 开始: dirId={}, pwdId={}, stoken={}", dirId, pwdId, stoken);
        
        client.getAbs(DETAIL_URL)
                .addQueryParam("pr", "ucpro")
                .addQueryParam("fr", "pc")
                .addQueryParam("pwd_id", pwdId)
                .addQueryParam("stoken", stoken)
                .addQueryParam("pdir_fid", dirId != null ? dirId : "0")  // 关键参数：父目录 ID
                .addQueryParam("force", "0")
                .addQueryParam("_page", "1")
                .addQueryParam("_size", "50")
                .addQueryParam("_fetch_banner", "1")
                .addQueryParam("_fetch_share", "1")
                .addQueryParam("_fetch_total", "1")
                .addQueryParam("_sort", "file_type:asc,file_name:asc")
                .putHeaders(header)
                .send()
                .onSuccess(res -> {
                    JsonObject resJson = asJson(res);
                    if (resJson.getInteger("code") != 0) {
                        promise.fail(DETAIL_URL + " 返回异常: " + resJson);
                        return;
                    }
                    
                    JsonArray fileList = resJson.getJsonObject("data").getJsonArray("list");
                    if (fileList == null || fileList.isEmpty()) {
                        log.warn("夸克 API 返回的文件列表为空，dirId: {}, response: {}", dirId, resJson.encodePrettily());
                        promise.complete(new ArrayList<>());
                        return;
                    }
                    
                    log.info("夸克 API 返回文件列表，总数: {}, dirId: {}", fileList.size(), dirId);
                    List<FileInfo> result = new ArrayList<>();
                    for (int i = 0; i < fileList.size(); i++) {
                        JsonObject item = fileList.getJsonObject(i);
                        FileInfo fileInfo = new FileInfo();
                        
                        // 调试：打印前3个 item 的完整结构
                        if (i < 3) {
                            log.info("夸克 API 返回的 item[{}] 结构: {}", i, item.encodePrettily());
                            log.info("夸克 API item[{}] 所有字段名: {}", i, item.fieldNames());
                        }
                        
                        String fid = item.getString("fid");
                        String fileName = item.getString("file_name");
                        Boolean isFile = item.getBoolean("file", true);
                        Long fileSize = item.getLong("size", 0L);
                        String updatedAt = formatEpochMs(item.getLong("updated_at", 0L));
                        String objCategory = item.getString("obj_category");
                        String shareFidToken = item.getString("share_fid_token");
                        String parentId = item.getString("parent_id");
                        
                        log.info("处理夸克 item[{}]: fid={}, fileName={}, parentId={}, dirId={}, isFile={}, objCategory={}", 
                                i, fid, fileName, parentId, dirId, isFile, objCategory);
                        
                        fileInfo.setFileId(fid)
                                .setFileName(fileName)
                                .setSize(fileSize)
                                .setSizeStr(FileSizeConverter.convertToReadableSize(fileSize))
                                .setCreateTime(updatedAt)
                                .setUpdateTime(updatedAt)
                                .setPanType(shareLinkInfo.getType());
                        
                        // 判断是否为文件：file=true 或 obj_category 不为空
                        if (isFile || (objCategory != null && !objCategory.isEmpty())) {
                            // 文件
                            fileInfo.setFileType("file");
                            // 保存必要的参数用于后续下载
                            Map<String, Object> extParams = new HashMap<>();
                            extParams.put("fid", fid);
                            extParams.put("pwd_id", pwdId);
                            extParams.put("stoken", stoken);
                            if (shareFidToken != null) {
                                extParams.put("share_fid_token", shareFidToken);
                            }
                            extParams.put("needDownloader", true);
                            Map<String, String> dlHeaders = new HashMap<>();
                            String listCookie = header.get(HttpHeaders.COOKIE);
                            if (listCookie != null && !listCookie.isEmpty()) {
                                dlHeaders.put(HttpHeaders.COOKIE.toString(), listCookie);
                            }
                            dlHeaders.put(HttpHeaders.USER_AGENT.toString(), header.get(HttpHeaders.USER_AGENT));
                            dlHeaders.put(HttpHeaders.REFERER.toString(), "https://pan.quark.cn/");
                            extParams.put("downloadHeaders", dlHeaders);
                            fileInfo.setExtParameters(extParams);
                            // 设置解析URL（用于下载）；透传 auth，避免大文件转存时 guest
                            JsonObject paramJson = new JsonObject(extParams);
                            paramJson.put("fileName", fileName);
                            String param = CommonUtils.urlBase64Encode(paramJson.encode());
                            fileInfo.setParserUrl(appendAuthQuery(String.format("%s/v2/redirectUrl/%s/%s",
                                    getDomainName(), shareLinkInfo.getType(), param)));
                        } else {
                            // 文件夹
                            fileInfo.setFileType("folder");
                            fileInfo.setSize(0L);
                            fileInfo.setSizeStr("0B");
                            // 设置目录解析URL（递归子目录须透传 auth，否则会变成 guest）
                            try {
                                String encodedUrl = URLEncoder.encode(shareLinkInfo.getShareUrl(), StandardCharsets.UTF_8.toString());
                                String encodedDirId = URLEncoder.encode(fid, StandardCharsets.UTF_8.toString());
                                String encodedStoken = URLEncoder.encode(stoken, StandardCharsets.UTF_8.toString());
                                fileInfo.setParserUrl(appendAuthQuery(String.format(
                                        "%s/v2/getFileList?url=%s&dirId=%s&stoken=%s",
                                        getDomainName(), encodedUrl, encodedDirId, encodedStoken)));
                            } catch (Exception e) {
                                fileInfo.setParserUrl(appendAuthQuery(String.format(
                                        "%s/v2/getFileList?url=%s&dirId=%s&stoken=%s",
                                        getDomainName(), shareLinkInfo.getShareUrl(), fid, stoken)));
                            }
                        }
                        
                        result.add(fileInfo);
                    }
                    
                    promise.complete(result);
                })
                .onFailure(t -> promise.fail("解析目录失败: " + t.getMessage()));
    }

    @Override
    public Future<String> parseById() {
        Promise<String> promise = Promise.promise();
        
        // 从 paramJson 中提取参数
        JsonObject paramJson = (JsonObject) shareLinkInfo.getOtherParam().get("paramJson");
        if (paramJson == null) {
            promise.fail("缺少必要的参数");
            return promise.future();
        }

        // 会话无 cookie 时，回退使用入口参数中已带的 cookie
        ensureCookieFromParam(paramJson);
        
        String fid = paramJson.getString("fid");
        String pwdId = paramJson.getString("pwd_id");
        String stoken = paramJson.getString("stoken");
        String shareFidToken = paramJson.getString("share_fid_token");
        
        if (fid == null || pwdId == null || stoken == null) {
            promise.fail("缺少必要的参数: fid, pwd_id 或 stoken");
            return promise.future();
        }
        
        log.debug("夸克 parseById: fid={}, pwd_id={}, stoken={}", fid, pwdId, stoken);

        resolveDownloadUrl(fid, pwdId, stoken, shareFidToken)
                .onSuccess(downloadUrl -> {
                    Map<String, String> downloadHeaders = buildDownloadHeaders(paramJson);
                    shareLinkInfo.getOtherParam().put("downloadHeaders", downloadHeaders);
                    shareLinkInfo.getOtherParam().put("fileName", paramJson.getString("fileName", ""));
                    shareLinkInfo.getOtherParam().put("needDownloader", true);
                    promise.complete(downloadUrl);
                })
                .onFailure(promise::fail);

        return promise.future();
    }

    /**
     * 会话无 cookie 时，从入口参数 downloadHeaders 回填到请求头。
     */
    private void ensureCookieFromParam(JsonObject paramJson) {
        String cookie = header.get(HttpHeaders.COOKIE);
        if (cookie != null && !cookie.isEmpty()) {
            return;
        }
        String paramCookie = extractCookieFromParam(paramJson);
        if (paramCookie != null && !paramCookie.isEmpty()) {
            header.set(HttpHeaders.COOKIE, CookieUtils.filterUcQuarkCookie(paramCookie));
        }
    }

    private static String extractCookieFromParam(JsonObject paramJson) {
        if (paramJson == null) {
            return null;
        }
        JsonObject paramHeaders = paramJson.getJsonObject("downloadHeaders");
        if (paramHeaders == null) {
            return null;
        }
        String cookie = paramHeaders.getString("cookie");
        return cookie != null ? cookie : paramHeaders.getString("Cookie");
    }

    /**
     * 构建下载请求头：会话 cookie 优先，缺失时回退入口参数中的 downloadHeaders。
     */
    private Map<String, String> buildDownloadHeaders(JsonObject paramJson) {
        Map<String, String> downloadHeaders = new HashMap<>();
        String cookie = header.get(HttpHeaders.COOKIE);
        if (cookie == null || cookie.isEmpty()) {
            cookie = extractCookieFromParam(paramJson);
        }
        if (cookie != null && !cookie.isEmpty()) {
            downloadHeaders.put(HttpHeaders.COOKIE.toString(), cookie);
        }
        String ua = header.get(HttpHeaders.USER_AGENT);
        if (ua == null || ua.isEmpty()) {
            ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
        }
        downloadHeaders.put(HttpHeaders.USER_AGENT.toString(), ua);
        downloadHeaders.put(HttpHeaders.REFERER.toString(), "https://pan.quark.cn/");
        return downloadHeaders;
    }
    
    /**
     * 转存并下载（仅在 {@link #resolveDownloadUrl} 判定需要转存时调用）。
     * <p>防重复两层：</p>
     * <ul>
     *   <li>本地 {@link #savedFileCache}：同一分享文件命中则跳过转存 API</li>
     *   <li>服务端 search_exit：已存在则复用网盘内文件，不创建副本</li>
     * </ul>
     */
    private Future<String> saveAndDownload(String pwdId, String stoken, String shareFid, String shareFidToken) {
        Promise<String> promise = Promise.promise();
        String fileCacheKey = pwdId + "_" + (shareFid == null ? "" : shareFid);

        // 本地缓存命中 → 跳过转存，直接用已转存 fid 取链
        String cachedFid = savedFileCache.get(fileCacheKey);
        if (cachedFid != null && !cachedFid.isEmpty()) {
            log.info("夸克：本地已有转存记录，跳过转存，直接取链。savedFid={}", cachedFid);
            getDownloadLinkForSavedFile(cachedFid)
                    .onSuccess(promise::complete)
                    .onFailure(t -> {
                        log.warn("夸克：缓存的转存文件已失效，清除后重新转存: {}", t.getMessage());
                        savedFileCache.remove(fileCacheKey);
                        doSaveAndDownload(pwdId, stoken, shareFid, shareFidToken, fileCacheKey, promise, false);
                    });
            return promise.future();
        }

        log.info("夸克：无本地转存缓存，执行转存（服务端仍会 search_exit 去重）...");
        doSaveAndDownload(pwdId, stoken, shareFid, shareFidToken, fileCacheKey, promise, false);
        return promise.future();
    }
    
    /**
     * 执行实际的转存和下载流程
     * @param isRetry 是否为重试（防止无限循环）
     */
    private void doSaveAndDownload(String pwdId, String stoken, String shareFid, String shareFidToken,
                                   String fileCacheKey, Promise<String> promise, boolean isRetry) {
        log.info("夸克转存下载：开始转存 shareFid={}，isRetry={}", shareFid, isRetry);

        getSaveDirFid()
                .compose(toPdirFid -> {
                    log.info("夸克转存目录: {}", toPdirFid);
                    return saveToCloud(pwdId, stoken, toPdirFid, shareFid, shareFidToken);
                })
                .onSuccess(taskId -> {
                    log.info("夸克转存任务已创建: {}", taskId);
                    checkTaskStatus(taskId, 0)
                            .onSuccess(saveResult -> {
                                JsonObject saveAs = saveResult.getJsonObject("save_as");
                                if (saveAs == null) {
                                    promise.fail("转存成功但未返回 save_as");
                                    return;
                                }
                                JsonArray savedFids = saveAs.getJsonArray("save_as_top_fids");
                                if (savedFids == null || savedFids.isEmpty()) {
                                    promise.fail("转存成功但未找到文件ID");
                                    return;
                                }

                                String savedFid = savedFids.getString(0);
                                // search_exit=true：服务端检测到已存在，复用已有文件（未创建新副本）
                                boolean searchExit = Boolean.TRUE.equals(saveAs.getBoolean("search_exit", false));
                                if (searchExit) {
                                    log.info("夸克转存：search_exit=true，复用已有文件 FID: {}", savedFid);
                                } else {
                                    log.info("夸克转存成功（新文件），文件ID: {}", savedFid);
                                }

                                savedFileCache.put(fileCacheKey, savedFid);
                                log.debug("夸克：已缓存转存映射 {} -> {}", fileCacheKey, savedFid);

                                getDownloadLinkForSavedFile(savedFid)
                                        .onSuccess(promise::complete)
                                        .onFailure(t -> {
                                            savedFileCache.remove(fileCacheKey);
                                            if (searchExit) {
                                                // dedup 指向的文件可能已进回收站，再转存仍会返回同一失效 fid
                                                log.warn("夸克：search_exit 复用文件不可用，FID: {}，错误: {}", savedFid, t.getMessage());
                                                promise.fail("转存文件不存在，可能已被移入回收站，请清空夸克网盘回收站后重试。原始错误: " + t.getMessage());
                                            } else if (!isRetry) {
                                                log.warn("夸克：转存后取链失败: {}，FID: {}，重试一次...", t.getMessage(), savedFid);
                                                doSaveAndDownload(pwdId, stoken, shareFid, shareFidToken, fileCacheKey, promise, true);
                                            } else {
                                                log.error("夸克：转存后取链仍失败（已重试）: {}，FID: {}", t.getMessage(), savedFid);
                                                promise.fail("转存文件下载链接获取失败（已重试）: " + t.getMessage());
                                            }
                                        });
                            })
                            .onFailure(promise::fail);
                })
                .onFailure(promise::fail);
    }

    /**
     * 获取夸克分享转存默认目录FID（通常为“来自：分享”）
     */
    private Future<String> getSaveDirFid() {
        Promise<String> promise = Promise.promise();

        client.getAbs(SAVE_DIR_URL)
                .addQueryParam("pr", "ucpro")
                .addQueryParam("fr", "pc")
                .addQueryParam("uc_param_str", "")
                .addQueryParam("aver", "1")
                .putHeaders(header)
                .send()
                .onSuccess(res -> {
                    JsonObject resJson = asJson(res);
                    if (resJson.getInteger("code") != 0) {
                        promise.fail("获取转存目录失败: " + resJson.getString("message"));
                        return;
                    }

                    String pdirFid = resJson.getJsonObject("data").getString("pdir_fid");
                    if (pdirFid == null || pdirFid.isEmpty()) {
                        promise.fail("获取转存目录失败: pdir_fid 为空");
                        return;
                    }
                    promise.complete(pdirFid);
                })
                .onFailure(t -> promise.fail("获取转存目录请求失败: " + t.getMessage()));

        return promise.future();
    }
    
    /**
     * 转存文件到网盘。
     * 有 shareFid 时按指定文件转存（目录多文件场景）；否则整包保存。
     * 服务端会通过 search_exit 自动去重，避免重复占空间。
     */
    private Future<String> saveToCloud(String pwdId, String stoken, String toPdirFid,
                                       String shareFid, String shareFidToken) {
        Promise<String> promise = Promise.promise();

        JsonObject saveRequest = new JsonObject()
                .put("pwd_id", pwdId)
                .put("stoken", stoken)
                .put("pdir_fid", "0")
                .put("to_pdir_fid", toPdirFid)
                .put("scene", "link");

        if (shareFid != null && !shareFid.isEmpty()) {
            saveRequest.put("fid_list", JsonArray.of(shareFid));
            if (shareFidToken != null && !shareFidToken.isEmpty()) {
                saveRequest.put("fid_token_list", JsonArray.of(shareFidToken));
            }
            saveRequest.put("pdir_save_all", false);
        } else {
            // 兼容：无指定文件时整包转存（服务端 search_exit 仍会去重）
            saveRequest.put("pdir_save_all", true);
        }

        log.debug("夸克转存请求: {}", saveRequest.encodePrettily());

        client.postAbs(SAVE_URL)
                .addQueryParam("pr", "ucpro")
                .addQueryParam("fr", "pc")
                .addQueryParam("uc_param_str", "")
                .putHeaders(header)
                .sendJsonObject(saveRequest)
                .onSuccess(res -> {
                    log.debug("夸克转存响应: {}", res.bodyAsString());
                    JsonObject resJson = asJson(res);

                    if (resJson.getInteger("code") != 0) {
                        promise.fail("转存失败: " + resJson.getString("message"));
                        return;
                    }

                    String taskId = resJson.getJsonObject("data").getString("task_id");
                    if (taskId == null || taskId.isEmpty()) {
                        promise.fail("转存响应中未找到任务ID");
                        return;
                    }

                    promise.complete(taskId);
                })
                .onFailure(t -> promise.fail("转存请求失败: " + t.getMessage()));

        return promise.future();
    }
    
    /**
     * 查询任务状态（带重试）
     * @param taskId 任务ID
     * @param retryIndex 重试次数
     * @return Future<JsonObject> 任务结果
     */
    private Future<JsonObject> checkTaskStatus(String taskId, int retryIndex) {
        Promise<JsonObject> promise = Promise.promise();
        
        // 最多重试20次，每次间隔1秒
        if (retryIndex > 20) {
            promise.fail("任务查询超时，重试次数已达上限");
            return promise.future();
        }
        
        client.getAbs(TASK_URL)
                .addQueryParam("pr", "ucpro")
                .addQueryParam("fr", "pc")
                .addQueryParam("uc_param_str", "")
                .addQueryParam("task_id", taskId)
                .addQueryParam("retry_index", String.valueOf(retryIndex))
                .putHeaders(header)
                .send()
                .onSuccess(res -> {
                    log.debug("夸克任务状态响应 (重试{}): {}", retryIndex, res.bodyAsString());
                    JsonObject resJson = asJson(res);
                    
                    if (resJson.getInteger("code") != 0) {
                        promise.fail("查询任务状态失败: " + resJson.getString("message"));
                        return;
                    }
                    
                    JsonObject taskData = resJson.getJsonObject("data");
                    Integer status = taskData.getInteger("status");
                    
                    if (status == null) {
                        promise.fail("任务状态响应异常");
                        return;
                    }
                    
                    // status: 0=待处理, 1=处理中, 2=已完成, 3=失败
                    if (status == 2) {
                        log.info("夸克转存任务完成: taskId={}", taskId);
                        promise.complete(taskData);
                    } else if (status == 3) {
                        promise.fail("转存任务失败");
                    } else {
                        // 任务未完成，1秒后重试
                        log.debug("夸克转存任务进行中，状态: {}, 1秒后重试", status);
                        cn.qaiu.WebClientVertxInit.get().setTimer(1000, id -> {
                            checkTaskStatus(taskId, retryIndex + 1)
                                    .onSuccess(promise::complete)
                                    .onFailure(promise::fail);
                        });
                    }
                })
                .onFailure(t -> promise.fail("查询任务状态请求失败: " + t.getMessage()));
        
        return promise.future();
    }
    
    /**
     * 获取转存文件的下载链接
     * @param fid 文件ID
     * @return Future<String> 下载链接
     */
    private Future<String> getDownloadLinkForSavedFile(String fid) {
        Promise<String> promise = Promise.promise();
        
        JsonObject downloadRequest = new JsonObject()
                .put("fids", new JsonArray().add(fid));
        
        client.postAbs(DOWNLOAD_URL)
                .addQueryParam("pr", "ucpro")
                .addQueryParam("fr", "pc")
                .putHeaders(header)
                .sendJsonObject(downloadRequest)
                .onSuccess(res -> {
                    log.debug("夸克转存文件下载链接响应: {}", res.bodyAsString());
                    JsonObject resJson = asJson(res);
                    
                    if (resJson.getInteger("code") != 0) {
                        promise.fail("获取转存文件下载链接失败: " + resJson.getString("message"));
                        return;
                    }
                    
                    JsonArray dataList = resJson.getJsonArray("data");
                    if (dataList == null || dataList.isEmpty()) {
                        promise.fail("转存文件下载链接列表为空");
                        return;
                    }
                    
                    String downloadUrl = dataList.getJsonObject(0).getString("download_url");
                    if (downloadUrl == null || downloadUrl.isEmpty()) {
                        promise.fail("转存文件下载链接为空");
                        return;
                    }
                    
                    promise.complete(downloadUrl);
                })
                .onFailure(t -> promise.fail("获取转存文件下载链接请求失败: " + t.getMessage()));
        
        return promise.future();
    }

    /**
     * 将夸克 API 返回的 epoch 毫秒时间戳转换为 "yyyy-MM-dd HH:mm:ss" 格式字符串
     */
    private static String formatEpochMs(long epochMs) {
        if (epochMs <= 0) return "";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}