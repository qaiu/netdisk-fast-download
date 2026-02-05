package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.CommonUtils;
import cn.qaiu.util.CookieUtils;
import cn.qaiu.util.DateTimeUtils;
import cn.qaiu.util.FileSizeConverter;
import cn.qaiu.util.HeaderUtils;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.UriTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UC网盘解析
 */
public class UcTool extends PanBase {
    private static final String API_URL_PREFIX = "https://pc-api.uc.cn/1/clouddrive/";
    
    // 静态变量：缓存 __puus cookie 和过期时间
    private static volatile String cachedPuus = null;
    private static volatile long puusExpireTime = 0;
    // __puus 有效期，默认 55 分钟（服务器实际 1 小时过期，提前 5 分钟刷新）
    private static final long PUUS_TTL_MS = 55 * 60 * 1000L;

    public static final String SHARE_URL_PREFIX = "https://fast.uc.cn/s/";

    private static final String FIRST_REQUEST_URL = API_URL_PREFIX + "share/sharepage/token?entry=ft&fr=pc&pr" +
            "=UCBrowser";

    private static final String SECOND_REQUEST_URL = API_URL_PREFIX + "transfer_share/detail?pwd_id={pwd_id}&passcode" +
            "={passcode}&stoken={stoken}";

    private static final String THIRD_REQUEST_URL = API_URL_PREFIX + "file/download?entry=ft&fr=pc&pr=UCBrowser";

    // Cookie 刷新 API
    private static final String FLUSH_URL = API_URL_PREFIX + "member?entry=ft&fr=pc&pr=UCBrowser&fetch_subscribe=true&_ch=home";

    private final MultiMap header = HeaderUtils.parseHeaders("""
            accept-language: zh-CN,zh;q=0.9,en;q=0.8
            cache-control: no-cache
            dnt: 1
            origin: https://drive.uc.cn
            pragma: no-cache
            priority: u=1, i
            referer: https://drive.uc.cn/
            sec-ch-ua: "Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"
            sec-ch-ua-mobile: ?0
            sec-ch-ua-platform: "Windows"
            sec-fetch-dest: empty
            sec-fetch-mode: cors
            sec-fetch-site: same-site
            user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36
            """);

    // 保存 auths 引用，用于更新 cookie
    private MultiMap auths;

    public UcTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
        // 参考其它网盘实现，从认证配置中取 cookie 放到请求头
        if (shareLinkInfo.getOtherParam() != null && shareLinkInfo.getOtherParam().containsKey("auths")) {
            auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
            String cookie = auths.get("cookie");
            if (cookie != null && !cookie.isEmpty()) {
                // 过滤出 UC 网盘所需的 cookie 字段
                cookie = CookieUtils.filterUcQuarkCookie(cookie);
                
                // 如果有缓存的 __puus 且未过期，使用缓存的值更新 cookie
                if (cachedPuus != null && System.currentTimeMillis() < puusExpireTime) {
                    cookie = CookieUtils.updateCookieValue(cookie, "__puus", cachedPuus);
                    log.debug("UC: 使用缓存的 __puus (剩余有效期: {}s)", (puusExpireTime - System.currentTimeMillis()) / 1000);
                }
                header.set(HttpHeaders.COOKIE, cookie);
                // 同步更新 auths
                auths.set("cookie", cookie);
            }
        }
        
        // 如果 __puus 已过期或不存在，触发异步刷新
        if (needRefreshPuus()) {
            log.debug("UC: __puus 需要刷新，触发异步刷新");
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
     * 通过调用 member API，服务器会返回 set-cookie 来更新 __puus
     * @return Future 包含是否刷新成功
     */
    public Future<Boolean> refreshPuusCookie() {
        Promise<Boolean> refreshPromise = Promise.promise();
        
        String currentCookie = header.get(HttpHeaders.COOKIE);
        if (currentCookie == null || currentCookie.isEmpty()) {
            log.debug("UC: 无 cookie，跳过刷新");
            refreshPromise.complete(false);
            return refreshPromise.future();
        }
        
        // 检查是否包含 __pus（用于获取 __puus）
        if (!currentCookie.contains("__pus=")) {
            log.debug("UC: cookie 中不包含 __pus，跳过刷新");
            refreshPromise.complete(false);
            return refreshPromise.future();
        }
        
        log.debug("UC: 开始刷新 __puus cookie");
        
        client.getAbs(FLUSH_URL)
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
                        
                        log.info("UC: __puus cookie 刷新成功，有效期至: {}ms", puusExpireTime);
                        refreshPromise.complete(true);
                    } else {
                        log.debug("UC: 响应中未包含 __puus，可能 cookie 仍然有效");
                        refreshPromise.complete(false);
                    }
                })
                .onFailure(t -> {
                    log.warn("UC: 刷新 __puus cookie 失败: {}", t.getMessage());
                    refreshPromise.complete(false);
                });
        
        return refreshPromise.future();
    }

    public Future<String> parse() {
        String dataKey = shareLinkInfo.getShareKey();
        String pwd = shareLinkInfo.getShareKey();

        var passcode =  (pwd == null) ? "" : pwd;
        var jsonObject = JsonObject.of("share_for_transfer", true);
        jsonObject.put("pwd_id", dataKey);
        jsonObject.put("passcode", passcode);
        // 第一次请求 获取文件信息
        client.postAbs(FIRST_REQUEST_URL)
                .putHeaders(header).sendJsonObject(jsonObject).onSuccess(res -> {
                    log.debug("第一阶段 {}", res.body());
                    var resJson = res.bodyAsJsonObject();
                    if (resJson.getInteger("code") != 0) {
                        fail(FIRST_REQUEST_URL + " 返回异常: " + resJson);
                        return;
                    }
                    var stoken = resJson.getJsonObject("data").getString("stoken");
                    // 第二次请求
                    client.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
                            .setTemplateParam("pwd_id", dataKey)
                            .setTemplateParam("passcode", passcode)
                            .setTemplateParam("stoken", stoken)
                            .putHeaders(header)
                            .send().onSuccess(res2 -> {
                                log.debug("第二阶段 {}", res2.body());
                                JsonObject resJson2 = res2.bodyAsJsonObject();
                                if (resJson2.getInteger("code") != 0) {
                                    fail(FIRST_REQUEST_URL + " 返回异常: " + resJson2);
                                    return;
                                }
                                try {
                                    // 文件信息
                                    JsonArray list = resJson2.getJsonObject("data").getJsonArray("list");
                                    if (list == null || list.isEmpty()) {
                                        fail("UC API 返回的文件列表为空");
                                        return;
                                    }
                                    var info = list.getJsonObject(0);
                                    
                                    // 提取文件信息并保存到 otherParam
                                    try {
                                        FileInfo fileInfo = new FileInfo();
                                        fileInfo.setFileId(info.getString("fid"))
                                                .setFileName(info.getString("file_name"))
                                                .setSize(info.getLong("size", 0L))
                                                .setSizeStr(FileSizeConverter.convertToReadableSize(info.getLong("size", 0L)))
                                                .setFileType(info.getBoolean("file", true) ? "file" : "folder")
                                                .setCreateTime(DateTimeUtils.formatTimestampToDateTime(info.getString("created_at")))
                                                .setUpdateTime(DateTimeUtils.formatTimestampToDateTime(info.getString("updated_at")))
                                                .setPanType(shareLinkInfo.getType());
                                        
                                        // 保存到 otherParam，供 CacheServiceImpl 使用
                                        shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
                                        log.debug("UC 提取文件信息: {}", fileInfo.getFileName());
                                    } catch (Exception e) {
                                        log.warn("UC 提取文件信息失败，继续解析: {}", e.getMessage());
                                    }
                                    
                                    // 第三次请求获取下载链接
                                    var bodyJson = JsonObject.of()
                                            .put("fids", JsonArray.of(info.getString("fid")))
                                            .put("pwd_id", dataKey)
                                            .put("stoken", stoken)
                                            .put("fids_token", JsonArray.of(info.getString("share_fid_token")));
                                    client.postAbs(THIRD_REQUEST_URL)
                                            .putHeaders(header)
                                            .sendJsonObject(bodyJson)
                                            .onSuccess(res3 -> {
                                                log.debug("第三阶段 {}", res3.body());
                                                var resJson3 = res3.bodyAsJsonObject();
                                                if (resJson3.getInteger("code") != 0) {
                                                    fail(FIRST_REQUEST_URL + " 返回异常: " + resJson2);
                                                    return;
                                                }
                                                try {
                                                    JsonArray dataList = resJson3.getJsonArray("data");
                                                    if (dataList == null || dataList.isEmpty()) {
                                                        fail("UC API 返回的下载链接列表为空");
                                                        return;
                                                    }
                                                    String downloadUrl = dataList.getJsonObject(0).getString("download_url");
                                                    // UC网盘需要配合aria2下载，保存下载请求头
                                                    Map<String, String> downloadHeaders = new HashMap<>();
                                                    // 将header转换为Map 只需要包含cookie,user-agent,referer
                                                    downloadHeaders.put(HttpHeaders.COOKIE.toString(), header.get(HttpHeaders.COOKIE));
                                                    downloadHeaders.put(HttpHeaders.USER_AGENT.toString(), header.get(HttpHeaders.USER_AGENT));
                                                    downloadHeaders.put(HttpHeaders.REFERER.toString(), "https://fast.uc.cn/");
                                                    completeWithMeta(downloadUrl, downloadHeaders);
                                                } catch (Exception e) {
                                                    fail("解析 UC 下载链接失败: " + e.getMessage());
                                                }
                                            }).onFailure(handleFail(THIRD_REQUEST_URL));
                                } catch (Exception e) {
                                    fail("解析 UC 文件信息失败: " + e.getMessage());
                                }

                            }).onFailure(handleFail(SECOND_REQUEST_URL));
                }
        ).onFailure(handleFail(FIRST_REQUEST_URL));
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
        JsonObject tokenRequest = JsonObject.of("share_for_transfer", true)
                .put("pwd_id", pwdId)
                .put("passcode", finalPasscode);
        
        client.postAbs(FIRST_REQUEST_URL)
                .putHeaders(header)
                .sendJsonObject(tokenRequest)
                .onSuccess(res -> {
                    JsonObject resJson = res.bodyAsJsonObject();
                    if (resJson.getInteger("code") != 0) {
                        promise.fail(FIRST_REQUEST_URL + " 返回异常: " + resJson);
                        return;
                    }
                    String stoken = resJson.getJsonObject("data").getString("stoken");
                    if (stoken == null || stoken.isEmpty()) {
                        promise.fail("无法获取分享 token");
                        return;
                    }
                    // 解析根目录（dirId = "0" 或空）
                    String rootDirId = dirId != null ? dirId : "0";
                    parseDir(rootDirId, pwdId, finalPasscode, stoken, promise);
                })
                .onFailure(t -> promise.fail("获取 token 失败: " + t.getMessage()));
        
        return promise.future();
    }

    private void parseDir(String dirId, String pwdId, String passcode, String stoken, Promise<List<FileInfo>> promise) {
        // 第二步：获取文件列表
        // UC API 使用 pdir_fid 参数指定父目录 ID，根目录为 "0"
        log.info("UC parseDir 开始: dirId={}, pwdId={}, stoken={}", dirId, pwdId, stoken);
        
        client.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
                .setTemplateParam("pwd_id", pwdId)
                .setTemplateParam("passcode", passcode)
                .setTemplateParam("stoken", stoken)
                .addQueryParam("entry", "ft")
                .addQueryParam("pdir_fid", dirId != null ? dirId : "0")  // 关键参数：父目录 ID
                .addQueryParam("fetch_file_list", "1")
                .addQueryParam("_page", "1")
                .addQueryParam("_size", "50")
                .addQueryParam("_fetch_total", "1")
                .addQueryParam("_fetch_share", "1")
                .addQueryParam("_sort", "file_type:asc,file_name:asc")
                .addQueryParam("fr", "pc")
                .addQueryParam("pr", "UCBrowser")
                .putHeaders(header)
                .send()
                .onSuccess(res -> {
                    JsonObject resJson = res.bodyAsJsonObject();
                    Integer code = resJson.getInteger("code");
                    String message = resJson.getString("message");
                    // 如果 stoken 失效（code=14001 或错误消息包含"token"），重新获取 stoken 后重试
                    if ((code != null && code == 14001) || 
                        (message != null && (message.contains("token") || message.contains("Token") || message.contains("非法token")))) {
                        log.debug("stoken 已失效，重新获取: {}", resJson);
                        // 重新获取 stoken
                        JsonObject tokenRequest = JsonObject.of("share_for_transfer", true)
                                .put("pwd_id", pwdId)
                                .put("passcode", passcode);
                        client.postAbs(FIRST_REQUEST_URL)
                                .putHeaders(header)
                                .sendJsonObject(tokenRequest)
                                .onSuccess(res2 -> {
                                    JsonObject resJson2 = res2.bodyAsJsonObject();
                                    if (resJson2.getInteger("code") != 0) {
                                        promise.fail(FIRST_REQUEST_URL + " 返回异常: " + resJson2);
                                        return;
                                    }
                                    String newStoken = resJson2.getJsonObject("data").getString("stoken");
                                    if (newStoken == null || newStoken.isEmpty()) {
                                        promise.fail("无法获取分享 token");
                                        return;
                                    }
                                    // 使用新的 stoken 重试
                                    parseDir(dirId, pwdId, passcode, newStoken, promise);
                                })
                                .onFailure(t -> promise.fail("重新获取 token 失败: " + t.getMessage()));
                        return;
                    }
                    if (resJson.getInteger("code") != 0) {
                        promise.fail(SECOND_REQUEST_URL + " 返回异常: " + resJson);
                        return;
                    }
                    
                    JsonArray fileList = resJson.getJsonObject("data").getJsonArray("list");
                    if (fileList == null || fileList.isEmpty()) {
                        log.warn("UC API 返回的文件列表为空，dirId: {}, response: {}", dirId, resJson.encodePrettily());
                        promise.complete(new ArrayList<>());
                        return;
                    }
                    
                    log.info("UC API 返回文件列表，总数: {}, dirId: {}", fileList.size(), dirId);
                    List<FileInfo> result = new ArrayList<>();
                    for (int i = 0; i < fileList.size(); i++) {
                        JsonObject item = fileList.getJsonObject(i);
                        FileInfo fileInfo = new FileInfo();
                        
                        // 调试：打印前3个 item 的完整结构，方便排查字段名
                        if (i < 3) {
                            log.info("UC API 返回的 item[{}] 结构: {}", i, item.encodePrettily());
                            log.info("UC API item[{}] 所有字段名: {}", i, item.fieldNames());
                        }
                        
                        String fid = item.getString("fid");
                        // UC API 可能使用 file_name 或 name，优先尝试 file_name
                        String fileName = item.getString("file_name");
                        if (fileName == null || fileName.isEmpty()) {
                            fileName = item.getString("name");
                        }
                        // 如果还是为空，尝试其他可能的字段名
                        if (fileName == null || fileName.isEmpty()) {
                            fileName = item.getString("fileName");
                        }
                        if (fileName == null || fileName.isEmpty()) {
                            fileName = item.getString("title");
                        }
                        
                        // 如果文件名仍为空，记录警告
                        if (fileName == null || fileName.isEmpty()) {
                            log.warn("UC API 返回的 item 中未找到文件名字段，item: {}", item.encode());
                        }
                        Boolean isFile = item.getBoolean("file", true);
                        Long fileSize = item.getLong("size", 0L);
                        String updatedAt = item.getString("updated_at");
                        String shareFidToken = item.getString("share_fid_token");
                        String parentId = item.getString("parent_id");
                        
                        // 临时移除过滤逻辑，查看 API 实际返回数据
                        log.info("准备处理 item[{}]: fid={}, fileName={}, parentId={}, dirId={}, isFile={}", i, fid, fileName, parentId, dirId, isFile);
                        
                        // 如果当前项的 fid 等于请求的 dirId，说明是当前目录本身，跳过
                        if (fid != null && fid.equals(dirId) && !"0".equals(dirId)) {
                            log.info("跳过当前目录本身: fid={}, dirId={}, fileName={}", fid, dirId, fileName);
                            continue;
                        }
                        
                        // UC API 可能不支持目录参数，返回所有文件
                        // 暂时不过滤，返回所有文件，查看实际数据
                        log.info("添加文件到结果[{}]: fid={}, fileName={}, parentId={}, dirId={}, isFile={}", i, fid, fileName, parentId, dirId, isFile);
                        
                        fileInfo.setFileId(fid)
                                .setFileName(fileName)
                                .setSize(fileSize)
                                .setSizeStr(FileSizeConverter.convertToReadableSize(fileSize))
                                .setCreateTime(DateTimeUtils.formatTimestampToDateTime(updatedAt))
                                .setUpdateTime(DateTimeUtils.formatTimestampToDateTime(updatedAt))
                                .setPanType(shareLinkInfo.getType());
                        
                        if (isFile) {
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
        
        log.debug("UC parseById: fid={}, pwd_id={}, stoken={}", fid, pwdId, stoken);
        
        // 调用第三次请求获取下载链接
        JsonObject bodyJson = JsonObject.of()
                .put("fids", JsonArray.of(fid))
                .put("pwd_id", pwdId)
                .put("stoken", stoken);
        
        if (shareFidToken != null && !shareFidToken.isEmpty()) {
            bodyJson.put("fids_token", JsonArray.of(shareFidToken));
        }
        
        client.postAbs(THIRD_REQUEST_URL)
                .putHeaders(header)
                .sendJsonObject(bodyJson)
                .onSuccess(res -> {
                    log.debug("UC parseById 响应: {}", res.body());
                    JsonObject resJson = res.bodyAsJsonObject();
                    if (resJson.getInteger("code") != 0) {
                        promise.fail(THIRD_REQUEST_URL + " 返回异常: " + resJson);
                        return;
                    }
                    try {
                        JsonArray dataList = resJson.getJsonArray("data");
                        if (dataList == null || dataList.isEmpty()) {
                            promise.fail("UC API 返回的下载链接列表为空");
                            return;
                        }
                        String downloadUrl = dataList.getJsonObject(0).getString("download_url");
                        if (downloadUrl == null || downloadUrl.isEmpty()) {
                            promise.fail("未找到下载链接");
                            return;
                        }
                        promise.complete(downloadUrl);
                    } catch (Exception e) {
                        promise.fail("解析 UC 下载链接失败: " + e.getMessage());
                    }
                })
                .onFailure(t -> promise.fail("请求下载链接失败: " + t.getMessage()));
        
        return promise.future();
    }

//    public static void main(String[] args) {
//        // https://drive.uc.cn/s/12450d1694844?public=1
//        new UcTool(ShareLinkInfo.newBuilder().shareKey("12450d1694844").build()).parse().onSuccess(
//                System.out::println
//        );
//    }
}
