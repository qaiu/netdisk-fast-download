package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.AESUtils;
import cn.qaiu.util.AcwScV2Generator;
import cn.qaiu.util.CommonUtils;
import cn.qaiu.util.FileSizeConverter;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClientSession;
import io.vertx.uritemplate.UriTemplate;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 蓝奏云优享 - 需要登录版本（支持大文件）
 */
public class IzToolWithAuth extends PanBase {

    private static final String API_URL0 = "https://api.ilanzou.com/";
    private static final String API_URL_PREFIX = "https://api.ilanzou.com/unproved/";

    private static final String FIRST_REQUEST_URL = API_URL_PREFIX + "recommend/list?devType=6&devModel=Chrome" +
            "&uuid={uuid}&extra=2&timestamp={ts}&shareId={shareId}&type=0&offset=1&limit=60";

    private static final String LOGIN_URL = API_URL_PREFIX +
            "login?uuid={uuid}&devType=6&devCode={uuid}&devModel=chrome&devVersion=127&appVersion=&timestamp={ts}&appToken=&extra=2";

    // https://api.ilanzou.com/proved/user/info/map?devType=3&devModel=Chrome&uuid=TInRHH3QzRaMo-Ajl2PkJ&extra=2&timestamp=EC2C6E7F45EB21338A17A7621E0BB437
    private static final String TOKEN_VERIFY_URL = API_URL0 +
            "proved/user/info/map?devType=6&devModel=Chrome&uuid={uuid}&extra=2&timestamp={ts}";

    private static final String SECOND_REQUEST_URL = API_URL_PREFIX + "file/redirect?downloadId={fidEncode}&enable=1" +
            "&devType=6&uuid={uuid}&timestamp={ts}&auth={auth}&shareId={dataKey}";

    private static final String SECOND_REQUEST_URL_VIP = API_URL_PREFIX + "file/redirect?uuid={uuid}&devType=6&devCode={uuid}" +
            "&devModel=chrome&devVersion=127&appVersion=&timestamp={ts}&appToken={appToken}&enable=1&downloadId={fidEncode}&auth={auth}";


    private static final String VIP_REQUEST_URL = API_URL_PREFIX + "/buy/vip/list?devType=6&devModel=Chrome&uuid" +
            "={uuid}&extra=2&timestamp={ts}";

    private static final String FILE_LIST_URL = API_URL_PREFIX + "/share/list?devType=6&devModel=Chrome&uuid" +
            "={uuid}&extra=2&timestamp={ts}&shareId={shareId}&folderId" +
            "={folderId}&offset=1&limit=60";


    WebClientSession webClientSession = WebClientSession.create(clientNoRedirects);

    private static final MultiMap header;

    static {
        header = MultiMap.caseInsensitiveMultiMap();
        header.set("Accept", "application/json, text/plain, */*");
        header.set("Accept-Encoding", "gzip, deflate");
        header.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        header.set("Cache-Control", "no-cache");
        header.set("Connection", "keep-alive");
        header.set("Content-Length", "0");
        header.set("DNT", "1");
        header.set("Host", "api.ilanzou.com");
        header.set("Origin", "https://www.ilanzou.com/");
        header.set("Pragma", "no-cache");
        header.set("Referer", "https://www.ilanzou.com/");
        header.set("Sec-Fetch-Dest", "empty");
        header.set("Sec-Fetch-Mode", "cors");
        header.set("Sec-Fetch-Site", "cross-site");
        header.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        header.set("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"");
        header.set("sec-ch-ua-mobile", "?0");
        header.set("sec-ch-ua-platform", "\"Windows\"");
    }
    public IzToolWithAuth(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    String uuid = UUID.randomUUID().toString().toLowerCase(); // 也可以使用 UUID.randomUUID().toString()

    public static volatile String token = null;
    public static volatile boolean authFlag = true;

    public Future<String> parse() {

        String shareId = shareLinkInfo.getShareKey(); // String.valueOf(AESUtils.idEncrypt(dataKey));
        long nowTs = System.currentTimeMillis();
        String tsEncode = AESUtils.encrypt2HexIz(Long.toString(nowTs));

        // 24.5.12 飞机盘 规则修改 需要固定UUID先请求会员接口, 再请求后续接口
        webClientSession.postAbs(UriTemplate.of(VIP_REQUEST_URL))
                .setTemplateParam("uuid", uuid)
                .setTemplateParam("ts", tsEncode)
                .send().onSuccess(r0 -> { // 忽略res

                    String url = StringUtils.isBlank(shareLinkInfo.getSharePassword()) ? FIRST_REQUEST_URL
                            : (FIRST_REQUEST_URL + "&code=" + shareLinkInfo.getSharePassword());
                    // 第一次请求 获取文件信息
                    // POST https://api.feijipan.com/ws/recommend/list?devType=6&devModel=Chrome&extra=2&shareId=146731&type=0&offset=1&limit=60
                    webClientSession.postAbs(UriTemplate.of(url))
                            .putHeaders(header)
                            .setTemplateParam("shareId", shareId)
                            .setTemplateParam("uuid", uuid)
                            .setTemplateParam("ts", tsEncode)
                            .send().onSuccess(res -> {
                                String resBody = asText(res);
                                // 检查是否包含 cookie 验证
                                if (resBody.contains("var arg1='")) {
                                    webClientSession = WebClientSession.create(clientNoRedirects);
                                    setCookie(resBody);
                                    // 重新请求
                                    webClientSession.postAbs(UriTemplate.of(url))
                                            .putHeaders(header)
                                            .setTemplateParam("shareId", shareId)
                                            .setTemplateParam("uuid", uuid)
                                            .setTemplateParam("ts", tsEncode)
                                            .send().onSuccess(res2 -> {
                                                processFirstResponse(res2);
                                            }).onFailure(handleFail("请求1-重试"));
                                    return;
                                }
                                processFirstResponse(res);
                            }).onFailure(handleFail("请求1"));

                }).onFailure(handleFail("请求1"));

        return promise.future();
    }

    /**
     * 设置 cookie
     */
    private void setCookie(String html) {
        int beginIndex = html.indexOf("arg1='") + 6;
        String arg1 = html.substring(beginIndex, html.indexOf("';", beginIndex));
        String acw_sc__v2 = AcwScV2Generator.acwScV2Simple(arg1);
        // 创建一个 Cookie 并放入 CookieStore
        DefaultCookie nettyCookie = new DefaultCookie("acw_sc__v2", acw_sc__v2);
        nettyCookie.setDomain(".ilanzou.com"); // 设置域名
        nettyCookie.setPath("/");             // 设置路径
        nettyCookie.setSecure(false);
        nettyCookie.setHttpOnly(false);
        webClientSession.cookieStore().put(nettyCookie);
    }

    /**
     * 处理第一次请求的响应
     */
    private void processFirstResponse(HttpResponse<Buffer> res) {
        JsonObject resJson = asJson(res);
        if (resJson.getInteger("code") != 200) {
            fail(FIRST_REQUEST_URL + " 返回异常: " + resJson);
            return;
        }
        if (!resJson.containsKey("list") || resJson.getJsonArray("list").isEmpty()) {
            fail(FIRST_REQUEST_URL + " 解析文件列表为空: " + resJson);
            return;
        }
        // 文件Id
        JsonObject fileInfo = resJson.getJsonArray("list").getJsonObject(0);
        // 如果是目录返回目录ID
        if (!fileInfo.containsKey("fileList") || fileInfo.getJsonArray("fileList").isEmpty()) {
            fail(FIRST_REQUEST_URL + " 文件列表为空: " + fileInfo);
            return;
        }
        JsonObject fileList = fileInfo.getJsonArray("fileList").getJsonObject(0);
        if (fileList.getInteger("fileType") == 2) {
            promise.complete(fileList.getInteger("folderId").toString());
            return;
        }
        // 提取文件信息
        extractFileInfo(fileList, fileInfo);
        getDownURL(resJson);
    }

    private void getDownURL(JsonObject resJson) {
        String dataKey = shareLinkInfo.getShareKey();
        // 文件Id
        JsonObject fileInfo = resJson.getJsonArray("list").getJsonObject(0);
        String fileId = fileInfo.getString("fileIds");
        String userId = fileInfo.getString("userId");
        // 其他参数
        long nowTs2 = System.currentTimeMillis();
        String tsEncode2 = AESUtils.encrypt2HexIz(Long.toString(nowTs2));
        String fidEncode = AESUtils.encrypt2HexIz(fileId + "|" + userId);
        String auth = AESUtils.encrypt2HexIz(fileId + "|" + nowTs2);

        // 检查是否有认证信息
        if (shareLinkInfo.getOtherParam().containsKey("auths")) {
            // 检查是否为临时认证（临时认证每次都尝试登录）
            boolean isTempAuth = shareLinkInfo.getOtherParam().containsKey("__TEMP_AUTH_ADDED");
            // 如果是临时认证，或者是后台配置且authFlag为true，则尝试使用认证
            if (isTempAuth || authFlag) {
                log.debug("尝试使用认证信息解析, isTempAuth={}, authFlag={}", isTempAuth, authFlag);
                HttpRequest<Buffer> httpRequest =
                        webClientSession.getAbs(UriTemplate.of(SECOND_REQUEST_URL_VIP))
                                .setTemplateParam("fidEncode", fidEncode)
                                .setTemplateParam("uuid", uuid)
                                .setTemplateParam("ts", tsEncode2)
                                .setTemplateParam("auth", auth)
                                .setTemplateParam("dataKey", dataKey);
                MultiMap auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
                if (token == null) {
                    // 执行登录
                    login(tsEncode2, auths).onFailure(failRes-> {
                        log.warn("登录失败: {}", failRes.getMessage());
                        fail(failRes.getMessage());
                    }).onSuccess(r-> {
                        httpRequest.setTemplateParam("appToken", token)
                                .putHeaders(header);
                        httpRequest.send().onSuccess(this::down).onFailure(handleFail("请求2"));
                    });
                } else {
                    // 验证token
                    webClientSession.postAbs(UriTemplate.of(TOKEN_VERIFY_URL))
                            .setTemplateParam("uuid", uuid)
                            .setTemplateParam("ts", tsEncode2)
                            .putHeaders(header).send().onSuccess(res -> {
                                // log.info("res: {}",asJson(res));
                                if (asJson(res).getInteger("code") != 200) {
                                    login(tsEncode2, auths).onFailure(failRes -> {
                                        log.warn("重新登录失败: {}", failRes.getMessage());
                                        fail(failRes.getMessage());
                                    }).onSuccess(r-> {
                                        httpRequest.setTemplateParam("appToken", token)
                                                .putHeaders(header);
                                        httpRequest.send().onSuccess(this::down).onFailure(handleFail("请求2"));
                                    });
                                } else {
                                    httpRequest.setTemplateParam("appToken", token)
                                            .putHeaders(header);
                                    httpRequest.send().onSuccess(this::down).onFailure(handleFail("请求2"));
                                }
                            }).onFailure(handleFail("Token验证"));
                }
            } else {
                // authFlag 为 false，使用免登录解析
                log.debug("authFlag=false，使用免登录解析");
                webClientSession.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
                        .putHeaders(header)
                        .setTemplateParam("fidEncode", fidEncode)
                        .setTemplateParam("uuid", uuid)
                        .setTemplateParam("ts", tsEncode2)
                        .setTemplateParam("auth", auth)
                        .setTemplateParam("dataKey", dataKey).send()
                        .onSuccess(this::down).onFailure(handleFail("请求2"));
            }
        } else {
            // 没有认证信息，使用免登录解析
            log.debug("无认证信息，使用免登录解析");
            webClientSession.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
                    .putHeaders(header)
                    .setTemplateParam("fidEncode", fidEncode)
                    .setTemplateParam("uuid", uuid)
                    .setTemplateParam("ts", tsEncode2)
                    .setTemplateParam("auth", auth)
                    .setTemplateParam("dataKey", dataKey).send()
                    .onSuccess(this::down).onFailure(handleFail("请求2"));
        }
    }

    private Future<Void> login(String tsEncode2, MultiMap auths) {
        Promise<Void> promise1 = Promise.promise();
        webClientSession.postAbs(UriTemplate.of(LOGIN_URL))
                .setTemplateParam("uuid",uuid)
                .setTemplateParam("ts", tsEncode2)
                .putHeaders(header)
                .sendJsonObject(JsonObject.of("loginName", auths.get("username"), "loginPwd", auths.get("password")))
                .onSuccess(res2->{
                    JsonObject json = asJson(res2);
                    if (json.getInteger("code") == 200) {
                        token = json.getJsonObject("data").getString("appToken");
                        MultiMap h = MultiMap.caseInsensitiveMultiMap();
                        h.addAll(header);
                        h.set("appToken", token);
                        log.info("登录成功 token: {}...", token.substring(0, Math.min(8, token.length())));
                        promise1.complete();
                    } else {
                        // 检查是否为临时认证
                        boolean isTempAuth = shareLinkInfo.getOtherParam().containsKey("__TEMP_AUTH_ADDED");
                        if (isTempAuth) {
                            // 临时认证失败，直接返回错误，不影响后台配置的认证
                            log.warn("临时认证失败: {}", json.getString("msg"));
                            promise1.fail("临时认证失败: " + json.getString("msg"));
                        } else {
                            // 后台配置的认证失败，设置authFlag并返回失败，让下次请求使用免登陆解析
                            log.warn("后台配置认证失败: {}, authFlag将设为false，请重新解析", json.getString("msg"));
                            authFlag = false;
                            promise1.fail("认证失败: " + json.getString("msg") + ", 请重新解析将使用免登陆模式");
                        }
                    }
                }).onFailure(err -> {
                    log.error("登录请求异常: {}", err.getMessage());
                    promise1.fail("登录请求异常: " + err.getMessage());
                });
        return promise1.future();
    }

    /**
     * 从接口返回数据中提取文件信息
     */
    private void extractFileInfo(JsonObject fileList, JsonObject shareInfo) {
        try {
            // 文件名
            String fileName = fileList.getString("fileName");
            shareLinkInfo.getOtherParam().put("fileName", fileName);
            
            // 文件大小 (KB -> Bytes)
            Long fileSize = fileList.getLong("fileSize", 0L) * 1024;
            shareLinkInfo.getOtherParam().put("fileSize", fileSize);
            shareLinkInfo.getOtherParam().put("fileSizeFormat", FileSizeConverter.convertToReadableSize(fileSize));
            
            // 文件图标
            String fileIcon = fileList.getString("fileIcon");
            if (StringUtils.isNotBlank(fileIcon)) {
                shareLinkInfo.getOtherParam().put("fileIcon", fileIcon);
            }
            
            // 文件ID
            Long fileId = fileList.getLong("fileId");
            if (fileId != null) {
                shareLinkInfo.getOtherParam().put("fileId", fileId.toString());
            }
            
            // 文件类型 (1=文件, 2=目录)
            Integer fileType = fileList.getInteger("fileType", 1);
            shareLinkInfo.getOtherParam().put("fileType", fileType == 1 ? "file" : "folder");
            
            // 下载次数
            Integer downloads = fileList.getInteger("fileDownloads", 0);
            shareLinkInfo.getOtherParam().put("downloadCount", downloads);
            
            // 点赞数
            Integer likes = fileList.getInteger("fileLikes", 0);
            shareLinkInfo.getOtherParam().put("likeCount", likes);
            
            // 评论数
            Integer comments = fileList.getInteger("fileComments", 0);
            shareLinkInfo.getOtherParam().put("commentCount", comments);
            
            // 评分
            Double stars = fileList.getDouble("fileStars", 0.0);
            shareLinkInfo.getOtherParam().put("stars", stars);
            
            // 更新时间
            String updateTime = fileList.getString("updTime");
            if (StringUtils.isNotBlank(updateTime)) {
                shareLinkInfo.getOtherParam().put("updateTime", updateTime);
            }
            
            // 创建时间
            String createTime = null;
            
            // 分享信息
            if (shareInfo != null) {
                // 分享ID
                Integer shareId = shareInfo.getInteger("shareId");
                if (shareId != null) {
                    shareLinkInfo.getOtherParam().put("shareId", shareId.toString());
                }
                
                // 上传时间
                String addTime = shareInfo.getString("addTime");
                if (StringUtils.isNotBlank(addTime)) {
                    shareLinkInfo.getOtherParam().put("createTime", addTime);
                    createTime = addTime;
                }
                
                // 预览次数
                Integer previewNum = shareInfo.getInteger("previewNum", 0);
                shareLinkInfo.getOtherParam().put("previewCount", previewNum);
                
                // 用户信息
                JsonObject userMap = shareInfo.getJsonObject("map");
                if (userMap != null) {
                    String userName = userMap.getString("userName");
                    if (StringUtils.isNotBlank(userName)) {
                        shareLinkInfo.getOtherParam().put("userName", userName);
                    }
                    
                    // VIP信息
                    Integer isVip = userMap.getInteger("isVip", 0);
                    shareLinkInfo.getOtherParam().put("isVip", isVip == 1);
                }
            }
            
            // 创建 FileInfo 对象并存入 otherParam
            FileInfo fileInfoObj = new FileInfo()
                    .setPanType(shareLinkInfo.getType())
                    .setFileName(fileName)
                    .setFileId(fileList.getLong("fileId") != null ? fileList.getLong("fileId").toString() : null)
                    .setSize(fileSize)
                    .setSizeStr(FileSizeConverter.convertToReadableSize(fileSize))
                    .setFileType(fileType == 1 ? "file" : "folder")
                    .setFileIcon(fileList.getString("fileIcon"))
                    .setDownloadCount(downloads)
                    .setCreateTime(createTime)
                    .setUpdateTime(updateTime);
            shareLinkInfo.getOtherParam().put("fileInfo", fileInfoObj);
            
            log.debug("提取文件信息成功: fileName={}, fileSize={}, downloads={}", 
                    fileName, fileSize, downloads);
        } catch (Exception e) {
            log.warn("提取文件信息失败: {}", e.getMessage());
        }
    }

    private void down(HttpResponse<Buffer> res2) {
        MultiMap headers = res2.headers();
        if (!headers.contains("Location") || StringUtils.isBlank(headers.get("Location"))) {
            fail("找不到下载链接可能服务器已被禁止或者配置的认证信息有误");
            return;
        }
        promise.complete(headers.get("Location"));
    }

    // 目录解析
    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> promise = Promise.promise();

        String shareId = shareLinkInfo.getShareKey(); // String.valueOf(AESUtils.idEncrypt(dataKey));

        // 如果参数里的目录ID不为空，则直接解析目录
        String dirId = (String) shareLinkInfo.getOtherParam().get("dirId");
        if (dirId != null && !dirId.isEmpty()) {
            uuid = shareLinkInfo.getOtherParam().get("uuid").toString();
            parserDir(dirId, shareId, promise);
            return promise.future();
        }
        parse().onSuccess(id -> {
            parserDir(id, shareId, promise);
        }).onFailure(failRes -> {
            log.error("解析目录失败: {}", failRes.getMessage());
            promise.fail(failRes);
        });
        return promise.future();
    }

    private void parserDir(String id, String shareId, Promise<List<FileInfo>> promise) {
        if (id != null && (id.startsWith("http://") || id.startsWith("https://"))) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileName(id)
                    .setFileId(id)
                    .setFileType("file")
                    .setParserUrl(id)
                    .setPanType(shareLinkInfo.getType());
            List<FileInfo> result = new ArrayList<>();
            result.add(fileInfo);
            promise.complete(result);
            return;
        }

        long nowTs = System.currentTimeMillis();
        String tsEncode = AESUtils.encrypt2HexIz(Long.toString(nowTs));

        log.debug("开始解析目录: {}, shareId: {}, uuid: {}, ts: {}", id, shareId, uuid, tsEncode);
        
        // 检查是否需要登录（有认证信息且需要使用认证）
        if (shareLinkInfo.getOtherParam().containsKey("auths")) {
            boolean isTempAuth = shareLinkInfo.getOtherParam().containsKey("__TEMP_AUTH_ADDED");
            log.debug("目录解析检查认证: isTempAuth={}, authFlag={}, token={}", isTempAuth, authFlag, token != null ? "已有" : "null");
            
            if ((isTempAuth || authFlag) && token == null) {
                MultiMap auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
                log.info("目录解析需要登录，开始执行登录...");
                // 先登录获取 token
                login(tsEncode, auths)
                        .onFailure(err -> {
                            log.warn("目录解析登录失败，使用免登录模式: {}", err.getMessage());
                            // 登录失败，继续使用免登录
                            requestDirList(id, shareId, tsEncode, promise);
                        })
                        .onSuccess(r -> {
                            log.info("目录解析登录成功，token={}, 使用 VIP 模式", token != null ? token.substring(0, Math.min(8, token.length())) + "..." : "null");
                            requestDirList(id, shareId, tsEncode, promise);
                        });
                return;
            } else if (token != null) {
                log.debug("目录解析已有 token，直接使用 VIP 模式");
            } else {
                log.debug("目录解析: authFlag=false 或为临时认证但已失败，使用免登录模式");
            }
        } else {
            log.debug("目录解析无认证信息，使用免登录模式");
        }
        
        // 无需登录或已登录，直接请求
        requestDirList(id, shareId, tsEncode, promise);
    }

    /**
     * 请求目录列表
     */
    private void requestDirList(String id, String shareId, String tsEncode, Promise<List<FileInfo>> promise) {
        webClientSession.postAbs(UriTemplate.of(FILE_LIST_URL))
                .putHeaders(header)
                .setTemplateParam("shareId", shareId)
                .setTemplateParam("uuid", uuid)
                .setTemplateParam("ts", tsEncode)
                .setTemplateParam("folderId", id)
                .send().onSuccess(res -> {
                    String resBody = asText(res);
                    // 检查是否包含 cookie 验证
                    if (resBody.contains("var arg1='")) {
                        log.debug("目录解析需要 cookie 验证，重新创建 session");
                        webClientSession = WebClientSession.create(clientNoRedirects);
                        setCookie(resBody);
                        // 重新请求目录列表
                        webClientSession.postAbs(UriTemplate.of(FILE_LIST_URL))
                                .putHeaders(header)
                                .setTemplateParam("shareId", shareId)
                                .setTemplateParam("uuid", uuid)
                                .setTemplateParam("ts", tsEncode)
                                .setTemplateParam("folderId", id)
                                .send().onSuccess(res2 -> {
                                    processDirResponse(res2, shareId, promise);
                                }).onFailure(err -> {
                                    log.error("目录解析重试失败: {}", err.getMessage());
                                    promise.fail("目录解析失败: " + err.getMessage());
                                });
                        return;
                    }
                    processDirResponse(res, shareId, promise);
                }).onFailure(err -> {
                    log.error("目录解析请求失败: {}", err.getMessage());
                    promise.fail("目录解析失败: " + err.getMessage());
                });
    }

    /**
     * 处理目录解析响应
     */
    private void processDirResponse(HttpResponse<Buffer> res, String shareId, Promise<List<FileInfo>> promise) {
        try {
            JsonObject jsonObject = asJson(res);
            log.debug("目录解析响应: {}", jsonObject.encodePrettily());
            
            if (!jsonObject.containsKey("list")) {
                log.error("目录解析响应缺少 list 字段: {}", jsonObject);
                promise.fail("目录解析失败: 响应格式错误");
                return;
            }
            
            JsonArray list = jsonObject.getJsonArray("list");
            ArrayList<FileInfo> result = new ArrayList<>();
            list.forEach(item->{
                JsonObject fileJson = (JsonObject) item;
                FileInfo fileInfo = new FileInfo();

                // 映射已知字段
                String fileId = fileJson.getString("fileId");
                String userId = fileJson.getString("userId");

                // 其他参数 - 每个文件使用新的时间戳
                long nowTs2 = System.currentTimeMillis();
                String tsEncode2 = AESUtils.encrypt2HexIz(Long.toString(nowTs2));
                String fidEncode = AESUtils.encrypt2HexIz(fileId + "|" + userId);
                String auth = AESUtils.encrypt2HexIz(fileId + "|" + nowTs2);

                // 回传用到的参数（包含 token）
                JsonObject entries = JsonObject.of(
                        "fidEncode", fidEncode,
                        "uuid", uuid,
                        "ts", tsEncode2,
                        "auth", auth,
                        "shareId", shareId,
                        "appToken", token != null ? token : "");
                String param = CommonUtils.urlBase64Encode(entries.encode());

                if (fileJson.getInteger("fileType") == 2) {
                    // 如果是目录
                    fileInfo.setFileName(fileJson.getString("name"))
                            .setFileId(fileJson.getString("folderId"))
                            .setCreateTime(fileJson.getString("updTime"))
                            .setFileType("folder")
                            .setSize(0L)
                            .setSizeStr("0B")
                            .setCreateBy(fileJson.getLong("userId").toString())
                            .setDownloadCount(fileJson.getInteger("fileDownloads"))
                            .setCreateTime(fileJson.getString("updTime"))
                            .setFileIcon(fileJson.getString("fileIcon"))
                            .setPanType(shareLinkInfo.getType())
                            // 设置目录解析的URL
                            .setParserUrl(String.format("%s/v2/getFileList?url=%s&dirId=%s&uuid=%s", getDomainName(),
                                    shareLinkInfo.getShareUrl(), fileJson.getString("folderId"), uuid));
                    result.add(fileInfo);
                    return;
                }
                long fileSize = fileJson.getLong("fileSize") * 1024;
                fileInfo.setFileName(fileJson.getString("fileName"))
                        .setFileId(fileId)
                        .setCreateTime(fileJson.getString("createTime"))
                        .setFileType("file")
                        .setSize(fileSize)
                        .setSizeStr(FileSizeConverter.convertToReadableSize(fileSize))
                        .setCreateBy(fileJson.getLong("userId").toString())
                        .setDownloadCount(fileJson.getInteger("fileDownloads"))
                        .setCreateTime(fileJson.getString("updTime"))
                        .setFileIcon(fileJson.getString("fileIcon"))
                        .setPanType(shareLinkInfo.getType())
                        .setParserUrl(String.format("%s/v2/redirectUrl/%s/%s", getDomainName(),
                                shareLinkInfo.getType(), param))
                        .setPreviewUrl(String.format("%s/v2/viewUrl/%s/%s", getDomainName(),
                                shareLinkInfo.getType(), param));
                result.add(fileInfo);
            });
            promise.complete(result);
        } catch (Exception e) {
            log.error("处理目录响应异常: {}", e.getMessage(), e);
            promise.fail("目录解析失败: " + e.getMessage());
        }
    }

    @Override
    public Future<String> parseById() {
        JsonObject paramJson = (JsonObject) shareLinkInfo.getOtherParam().get("paramJson");
        String appToken = paramJson.getString("appToken", "");
        
        // 如果有 token，使用 VIP 接口
        if (StringUtils.isNotBlank(appToken)) {
            log.debug("parseById 使用 VIP 接口, appToken={}", appToken.substring(0, Math.min(8, appToken.length())) + "...");
            webClientSession.getAbs(UriTemplate.of(SECOND_REQUEST_URL_VIP))
                    .putHeaders(header)
                    .setTemplateParam("fidEncode", paramJson.getString("fidEncode"))
                    .setTemplateParam("uuid", paramJson.getString("uuid"))
                    .setTemplateParam("ts", paramJson.getString("ts"))
                    .setTemplateParam("auth", paramJson.getString("auth"))
                    .setTemplateParam("appToken", appToken)
                    .send().onSuccess(this::down).onFailure(handleFail("parseById-VIP"));
        } else {
            // 无 token，使用免登录接口
            log.debug("parseById 使用免登录接口");
            webClientSession.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
                    .putHeaders(header)
                    .setTemplateParam("fidEncode", paramJson.getString("fidEncode"))
                    .setTemplateParam("uuid", paramJson.getString("uuid"))
                    .setTemplateParam("ts", paramJson.getString("ts"))
                    .setTemplateParam("auth", paramJson.getString("auth"))
                    .setTemplateParam("dataKey", paramJson.getString("shareId"))
                    .send().onSuccess(this::down).onFailure(handleFail("parseById"));
        }
        return promise.future();
    }

    public static void resetToken() {
        token = null;
        authFlag = true;
    }
}
