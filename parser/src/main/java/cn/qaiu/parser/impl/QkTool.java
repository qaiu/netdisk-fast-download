package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
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
    
    private static final int BATCH_SIZE = 15; // 批量获取下载链接的批次大小
    
    // 静态变量：缓存 __puus cookie 和过期时间
    private static volatile String cachedPuus = null;
    private static volatile long puusExpireTime = 0;
    // __puus 有效期，默认 55 分钟（服务器实际 1 小时过期，提前 5 分钟刷新）
    private static final long PUUS_TTL_MS = 55 * 60 * 1000L;
    
    private final MultiMap header = HeaderUtils.parseHeaders("""
            User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) quark-cloud-drive/2.5.20 Chrome/100.0.4896.160 Electron/18.3.5.4-b478491100 Safari/537.36 Channel/pckk_other_ch
            Content-Type: application/json;charset=UTF-8
            Referer: https://pan.quark.cn/
            Origin: https://pan.quark.cn
            Accept: application/json, text/plain, */*
            """);

    // 保存 auths 引用，用于更新 cookie
    private MultiMap auths;

    public QkTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
        // 参考 UcTool 实现，从认证配置中取 cookie 放到请求头
        if (shareLinkInfo.getOtherParam() != null && shareLinkInfo.getOtherParam().containsKey("auths")) {
            auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
            String cookie = auths.get("cookie");
            if (cookie != null && !cookie.isEmpty()) {
                // 过滤出夸克网盘所需的 cookie 字段
                cookie = CookieUtils.filterUcQuarkCookie(cookie);
                
                // 如果有缓存的 __puus 且未过期，使用缓存的值更新 cookie
                if (cachedPuus != null && System.currentTimeMillis() < puusExpireTime) {
                    cookie = CookieUtils.updateCookieValue(cookie, "__puus", cachedPuus);
                    log.debug("夸克: 使用缓存的 __puus (剩余有效期: {}s)", (puusExpireTime - System.currentTimeMillis()) / 1000);
                }
                header.set(HttpHeaders.COOKIE, cookie);
                // 同步更新 auths
                auths.set("cookie", cookie);
            }
        }
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
        return cachedPuus == null || System.currentTimeMillis() >= puusExpireTime;
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
                        cachedPuus = newPuus;
                        puusExpireTime = System.currentTimeMillis() + PUUS_TTL_MS;
                        
                        log.info("夸克: __puus cookie 刷新成功，有效期至: {}ms", puusExpireTime);
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
                                            .setCreateTime(firstFile.getString("updated_at"))
                                            .setUpdateTime(firstFile.getString("updated_at"))
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
                                
                                // 第三步：批量获取下载链接
                                getDownloadLinksBatch(fileIds, stoken)
                                        .onSuccess(downloadData -> {
                                            if (downloadData.isEmpty()) {
                                                fail("未能获取到下载链接");
                                                return;
                                            }

                                            // 获取第一个文件的下载链接
                                            String downloadUrl = downloadData.get(0).getString("download_url");
                                            if (downloadUrl == null || downloadUrl.isEmpty()) {
                                                fail("下载链接为空");
                                                return;
                                            }

                                            // 夸克网盘需要配合下载请求头，保存下载请求头
                                            Map<String, String> downloadHeaders = new HashMap<>();
                                            downloadHeaders.put(HttpHeaders.COOKIE.toString(), header.get(HttpHeaders.COOKIE));
                                            downloadHeaders.put(HttpHeaders.USER_AGENT.toString(), header.get(HttpHeaders.USER_AGENT));
                                            downloadHeaders.put(HttpHeaders.REFERER.toString(), "https://pan.quark.cn/");

                                            log.debug("成功获取下载链接: {}", downloadUrl);
                                            completeWithMeta(downloadUrl, downloadHeaders);
                                        })
                                        .onFailure(handleFail(DOWNLOAD_URL));
                                
                            }).onFailure(handleFail(DETAIL_URL));
                })
                .onFailure(handleFail(TOKEN_URL));
        
        return promise.future();
    }
    
    /**
     * 批量获取下载链接（分批处理）
     */
    private Future<List<JsonObject>> getDownloadLinksBatch(List<String> fileIds, String stoken) {
        List<JsonObject> allResults = new ArrayList<>();
        Promise<List<JsonObject>> promise = Promise.promise();

        // 同步处理每个批次
        processBatch(fileIds, stoken, 0, allResults, promise);

        return promise.future();
    }

    private void processBatch(List<String> fileIds, String stoken, int startIndex, List<JsonObject> allResults, Promise<List<JsonObject>> promise) {
        if (startIndex >= fileIds.size()) {
            // 所有批次处理完成
            promise.complete(allResults);
            return;
        }

        int endIndex = Math.min(startIndex + BATCH_SIZE, fileIds.size());
        List<String> batch = fileIds.subList(startIndex, endIndex);

        log.debug("正在获取第 {} 批下载链接 ({} 个文件)", startIndex / BATCH_SIZE + 1, batch.size());

        JsonObject downloadRequest = new JsonObject()
                .put("fids", new JsonArray(batch));

        client.postAbs(DOWNLOAD_URL)
                .addQueryParam("pr", "ucpro")
                .addQueryParam("fr", "pc")
                .putHeaders(header)
                .sendJsonObject(downloadRequest)
                .onSuccess(res -> {
                    log.debug("下载链接响应: {}", res.bodyAsString());
                    JsonObject resJson = asJson(res);

                    if (resJson.getInteger("code") == 31001) {
                        promise.fail("未登录或 Cookie 已失效");
                        return;
                    }

                    if (resJson.getInteger("code") != 0) {
                        promise.fail(DOWNLOAD_URL + " 返回异常: " + resJson);
                        return;
                    }

                    JsonArray batchData = resJson.getJsonArray("data");
                    if (batchData != null) {
                        for (int i = 0; i < batchData.size(); i++) {
                            allResults.add(batchData.getJsonObject(i));
                        }
                        log.debug("成功获取 {} 个下载链接", batchData.size());
                    }

                    // 处理下一批次
                    processBatch(fileIds, stoken, endIndex, allResults, promise);
                })
                .onFailure(t -> promise.fail("获取下载链接失败: " + t.getMessage()));
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
                    String stoken = resJson.getJsonObject("data").getString("stoken");
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
                        String updatedAt = item.getString("updated_at");
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
                            fileInfo.setExtParameters(extParams);
                            // 设置解析URL（用于下载）
                            JsonObject paramJson = new JsonObject(extParams);
                            String param = CommonUtils.urlBase64Encode(paramJson.encode());
                            fileInfo.setParserUrl(String.format("%s/v2/redirectUrl/%s/%s", 
                                    getDomainName(), shareLinkInfo.getType(), param));
                        } else {
                            // 文件夹
                            fileInfo.setFileType("folder");
                            fileInfo.setSize(0L);
                            fileInfo.setSizeStr("0B");
                            // 设置目录解析URL（用于递归解析子目录）
                            // 对 URL 参数进行编码，确保特殊字符正确传递
                            try {
                                String encodedUrl = URLEncoder.encode(shareLinkInfo.getShareUrl(), StandardCharsets.UTF_8.toString());
                                String encodedDirId = URLEncoder.encode(fid, StandardCharsets.UTF_8.toString());
                                String encodedStoken = URLEncoder.encode(stoken, StandardCharsets.UTF_8.toString());
                                fileInfo.setParserUrl(String.format("%s/v2/getFileList?url=%s&dirId=%s&stoken=%s", 
                                        getDomainName(), encodedUrl, encodedDirId, encodedStoken));
                            } catch (Exception e) {
                                // 如果编码失败，使用原始值
                                fileInfo.setParserUrl(String.format("%s/v2/getFileList?url=%s&dirId=%s&stoken=%s", 
                                        getDomainName(), shareLinkInfo.getShareUrl(), fid, stoken));
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
        
        String fid = paramJson.getString("fid");
        String pwdId = paramJson.getString("pwd_id");
        String stoken = paramJson.getString("stoken");
        String shareFidToken = paramJson.getString("share_fid_token");
        
        if (fid == null || pwdId == null || stoken == null) {
            promise.fail("缺少必要的参数: fid, pwd_id 或 stoken");
            return promise.future();
        }
        
        log.debug("夸克 parseById: fid={}, pwd_id={}, stoken={}", fid, pwdId, stoken);
        
        // 调用下载链接 API
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
                    log.debug("夸克 parseById 响应: {}", res.bodyAsString());
                    JsonObject resJson = asJson(res);
                    
                    if (resJson.getInteger("code") == 31001) {
                        promise.fail("未登录或 Cookie 已失效");
                        return;
                    }
                    
                    if (resJson.getInteger("code") != 0) {
                        promise.fail(DOWNLOAD_URL + " 返回异常: " + resJson);
                        return;
                    }
                    
                    try {
                        JsonArray dataList = resJson.getJsonArray("data");
                        if (dataList == null || dataList.isEmpty()) {
                            promise.fail("夸克 API 返回的下载链接列表为空");
                            return;
                        }
                        String downloadUrl = dataList.getJsonObject(0).getString("download_url");
                        if (downloadUrl == null || downloadUrl.isEmpty()) {
                            promise.fail("未找到下载链接");
                            return;
                        }
                        promise.complete(downloadUrl);
                    } catch (Exception e) {
                        promise.fail("解析夸克下载链接失败: " + e.getMessage());
                    }
                })
                .onFailure(t -> promise.fail("请求下载链接失败: " + t.getMessage()));
        
        return promise.future();
    }
}