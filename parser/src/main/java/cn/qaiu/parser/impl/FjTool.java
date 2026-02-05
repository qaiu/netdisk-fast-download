package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.*;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.uritemplate.UriTemplate;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 小飞机网盘
 *
 * @version V016_230609
 */
public class FjTool extends PanBase {

    public static final String API_URL0 = "https://api.feijipan.com";
    private static final String API_URL_PREFIX = "https://api.feijipan.com/ws/";

    private static final String FIRST_REQUEST_URL = API_URL_PREFIX + "recommend/list?devType=6&devModel=Chrome" +
            "&uuid={uuid}&extra=2&timestamp={ts}&shareId={shareId}&type=0&offset=1&limit=60";

    private static final String LOGIN_URL = API_URL_PREFIX +
            "login?uuid={uuid}&devType=6&devCode={uuid}&devModel=chrome&devVersion=127&appVersion=&timestamp={ts}&appToken=&extra=2";

    private static final String TOKEN_VERIFY_URL = API_URL0 +
            "/app/user/info/map?devType=6&devModel=Chrome&uuid={uuid}&extra=2&timestamp={ts}";

    private static final String SECOND_REQUEST_URL = API_URL_PREFIX + "file/redirect?downloadId={fidEncode}&enable=1" +
            "&devType=6&uuid={uuid}&timestamp={ts}&auth={auth}&shareId={dataKey}";

    //https://api.feijipan.com/ws/file/redirect?
    // downloadId=DBD34FFEDB71708FA5C284527F78E9EC104A9667FFEEA62CB6E00B54A3E0F5BB
    // &enable=1
    // &devType=6
    // &uuid=rTaNVSgmwY5MbEEuiMmQL
    // &timestamp=839E6B5E19223B8DF730A52F44062D48
    // &auth=F799422BCD9D05D7CCC5C9C53C1092C7029B420536135C3B4B7E064F49459DCC
    // &shareId=4wF7grHR
    private static final String SECOND_REQUEST_URL_VIP = API_URL_PREFIX +
            "file/redirect?downloadId={fidEncode}&enable=1&devType=6&uuid={uuid}&timestamp={ts}&auth={auth}&shareId={dataKey}";


    private static final String VIP_REQUEST_URL = API_URL_PREFIX + "/buy/vip/list?devType=6&devModel=Chrome&uuid" +
            "={uuid}&extra=2&timestamp={ts}";
    // https://api.feijipan.com/ws/buy/vip/list?devType=6&devModel=Chrome&uuid=WQAl5yBy1naGudJEILBvE&extra=2&timestamp=E2C53155F6D09417A27981561134CB73

    private static final String FILE_LIST_URL = API_URL_PREFIX + "/share/list?devType=6&devModel=Chrome&uuid" +
            "={uuid}&extra=2&timestamp={ts}&shareId={shareId}&folderId" +
            "={folderId}&offset=1&limit=60";

    private static final MultiMap header;
    private static final MultiMap header0;

    long nowTs = System.currentTimeMillis();
    String tsEncode = AESUtils.encrypt2Hex(Long.toString(nowTs));
    String uuid = UUIDUtil.fjUuid(); // 也可以使用 UUID.randomUUID().toString()

    static {
        header0 = MultiMap.caseInsensitiveMultiMap();
        header0.set("Accept-Encoding", "gzip, deflate");
        header0.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        header0.set("Cache-Control", "no-cache");
        header0.set("Connection", "keep-alive");
        header0.set("Content-Length", "0");
        header0.set("DNT", "1");
        header0.set("Pragma", "no-cache");
        header0.set("Referer", "https://www.feijipan.com/");
        header0.set("Sec-Fetch-Dest", "empty");
        header0.set("Sec-Fetch-Mode", "cors");
        header0.set("Sec-Fetch-Site", "cross-site");
        header0.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        header0.set("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"");
        header0.set("sec-ch-ua-mobile", "?0");
        header0.set("sec-ch-ua-platform", "\"Windows\"");

        header = HeaderUtils.parseHeaders("""
                Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7
                Accept-Language: zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6
                Cache-Control: no-cache
                Connection: keep-alive
                DNT: 1
                Pragma: no-cache
                Referer: https://www.feijix.com/
                Sec-Fetch-Dest: document
                Sec-Fetch-Mode: navigate
                Sec-Fetch-Site: cross-site
                Sec-Fetch-User: ?1
                Upgrade-Insecure-Requests: 1
                user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0
                sec-ch-ua: "Microsoft Edge";v="135", "Not-A.Brand";v="8", "Chromium";v="135"
                sec-ch-ua-mobile: ?0
                sec-ch-ua-platform: "Windows"
                """);
    }

    // String uuid = UUID.randomUUID().toString().toLowerCase(); // 也可以使用 UUID.randomUUID().toString()

    static String token = null;
    static String userId = null;
    public static boolean authFlag = true;

    public FjTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {

        String shareId = shareLinkInfo.getShareKey(); // String.valueOf(AESUtils.idEncrypt(dataKey));
        long nowTs = System.currentTimeMillis();
        String tsEncode = AESUtils.encrypt2Hex(Long.toString(nowTs));

        if (shareLinkInfo.getOtherParam().containsKey("auths")) {
            MultiMap auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
            // 获取用户id
            if (auths.contains("userId")) {
                FjTool.userId = auths.get("userId");
                log.info("已配置用户ID: {}", FjTool.userId);
            } else {
                log.warn("未配置用户ID, 可能会导致解析失败");
            }
        }

        // 24.5.12 飞机盘 规则修改 需要固定UUID先请求会员接口, 再请求后续接口
        String url = StringUtils.isBlank(shareLinkInfo.getSharePassword()) ? FIRST_REQUEST_URL
                : (FIRST_REQUEST_URL + "&code=" + shareLinkInfo.getSharePassword());

        client.postAbs(UriTemplate.of(VIP_REQUEST_URL))
                .setTemplateParam("uuid", uuid)
                .setTemplateParam("ts", tsEncode)
                .send().onSuccess(r0 -> { // 忽略res
                    // 第一次请求 获取文件信息
                    // POST https://api.feijipan.com/ws/recommend/list?devType=6&devModel=Chrome&extra=2&shareId=146731&type=0&offset=1&limit=60
                    client.postAbs(UriTemplate.of(url))
                            .putHeaders(header0)
                            .setTemplateParam("shareId", shareId)
                            .setTemplateParam("uuid", uuid)
                            .setTemplateParam("ts", tsEncode)
                            .send().onSuccess(res -> {

                                JsonObject resJson;
                                try {
                                    resJson = asJson(res);
                                } catch (Exception e) {
                                    log.error("获取文件信息失败: {}", res.bodyAsString());
                                    return;
                                }
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
                                JsonObject fileList = fileInfo.getJsonArray("fileList").getJsonObject(0);
                                if (fileList.getInteger("fileType") == 2) {
                                    promise.complete(fileList.getInteger("folderId").toString());
                                    return;
                                }
                                // 提取文件信息
                                extractFileInfo(fileList, fileInfo);
                                getDownURL(resJson);
                            }).onFailure(handleFail("请求1"));

                }).onFailure(handleFail("请求1"));

        return promise.future();
    }

    private void getDownURL(JsonObject resJson) {
        String dataKey = shareLinkInfo.getShareKey();
        // 文件Id
        JsonObject fileInfo = resJson.getJsonArray("list").getJsonObject(0);
        String fileId = fileInfo.getString("fileIds");
        String userId = fileInfo.getString("userId");
        // 其他参数
        long nowTs2 = System.currentTimeMillis();
        String tsEncode2 = AESUtils.encrypt2Hex(Long.toString(nowTs2));
        String fidEncode = AESUtils.encrypt2Hex(fileId + "|" + FjTool.userId);
        String auth = AESUtils.encrypt2Hex(fileId + "|" + nowTs2);

        // 检查是否有认证信息
        if (shareLinkInfo.getOtherParam().containsKey("auths")) {
            // 检查是否为临时认证（临时认证每次都尝试登录）
            boolean isTempAuth = shareLinkInfo.getOtherParam().containsKey("__TEMP_AUTH_ADDED");
            // 如果是临时认证，或者是后台配置且authFlag为true，则尝试使用认证
            if (isTempAuth || authFlag) {
                log.debug("尝试使用认证信息解析, isTempAuth={}, authFlag={}", isTempAuth, authFlag);
                HttpRequest<Buffer> httpRequest =
                        clientNoRedirects.getAbs(UriTemplate.of(SECOND_REQUEST_URL_VIP))
                                .setTemplateParam("uuid", uuid)
                                .setTemplateParam("ts", tsEncode2)
                                .setTemplateParam("auth", auth)
                                .setTemplateParam("dataKey", dataKey)
                        ;
                MultiMap auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
                if (token == null) {
                    // 执行登录
                    login(tsEncode2, auths).onFailure(failRes-> {
                        log.warn("登录失败: {}", failRes.getMessage());
                        fail(failRes.getMessage());
                    }).onSuccess(r-> {
                        httpRequest.setTemplateParam("fidEncode", AESUtils.encrypt2Hex(fileId + "|" + FjTool.userId))
                                .putHeaders(header);
                        httpRequest.send().onSuccess(this::down).onFailure(handleFail("请求2"));
                    });
                } else {
                    // 验证token
                    client.postAbs(UriTemplate.of(TOKEN_VERIFY_URL))
                            .setTemplateParam("uuid", uuid)
                            .setTemplateParam("ts", tsEncode2)
                            .putHeaders(header0).send().onSuccess(res -> {
                                if (asJson(res).getInteger("code") != 200) {
                                    login(tsEncode2, auths).onFailure(failRes -> {
                                        log.warn("重新登录失败: {}", failRes.getMessage());
                                        fail(failRes.getMessage());
                                    }).onSuccess(r-> {
                                        httpRequest
                                                .setTemplateParam("fidEncode", fidEncode)
                                                .putHeaders(header);
                                        httpRequest.send().onSuccess(this::down).onFailure(handleFail("请求2"));
                                    });
                                } else {
                                    httpRequest
                                            .setTemplateParam("fidEncode", AESUtils.encrypt2Hex(fileId + "|" + FjTool.userId))
                                            .putHeaders(header);
                                    httpRequest.send().onSuccess(this::down).onFailure(handleFail("请求2"));
                                }
                            }).onFailure(handleFail("Token验证"));
                }
            } else {
                // authFlag 为 false，使用免登录解析
                log.debug("authFlag=false，使用免登录解析");
                clientNoRedirects.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
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
            clientNoRedirects.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
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
        // 如果配置了用户ID 则不登录
        if (FjTool.userId != null) {
            promise1.complete();
            return promise1.future();
        }
        client.postAbs(UriTemplate.of(LOGIN_URL))
                .setTemplateParam("uuid",uuid)
                .setTemplateParam("ts", tsEncode2)
                .putHeaders(header0)
                .sendJsonObject(JsonObject.of("loginName", auths.get("username"), "loginPwd", auths.get("password")))
                .onSuccess(res2->{
                    JsonObject json = asJson(res2);
                    if (json.getInteger("code") == 200) {
                        token = json.getJsonObject("data").getString("appToken");
                        header0.set("appToken", token);
                        log.info("登录成功 token: {}", token);
                        client.postAbs(UriTemplate.of(TOKEN_VERIFY_URL))
                                .setTemplateParam("uuid", uuid)
                                .setTemplateParam("ts", tsEncode2)
                                .putHeaders(header0).send().onSuccess(res -> {
                                    if (asJson(res).getInteger("code") == 200) {
                                        if (FjTool.userId == null) {
                                            FjTool.userId = asJson(res).getJsonObject("map").getString("userId");
                                        }
                                        log.info("验证成功 userId: {}", FjTool.userId);
                                        promise1.complete();
                                    } else {
                                        promise1.fail("验证失败: " + res.bodyAsString());
                                    }
                                });
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
                    .setFileId(fileId != null ? fileId.toString() : null)
                    .setSize(fileSize)
                    .setSizeStr(FileSizeConverter.convertToReadableSize(fileSize))
                    .setFileType(fileType == 1 ? "file" : "folder")
                    .setFileIcon(fileIcon)
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
        if (!headers.contains("Location") || headers.get("Location") == null) {
            fail("找不到下载链接可能服务器已被禁止或者配置的认证信息有误: " + res2.bodyAsString());
            return;
        }
        promise.complete(headers.get("Location"));
    }

    // 目录解析
    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> promise0 = Promise.promise();

        String shareId = shareLinkInfo.getShareKey(); // String.valueOf(AESUtils.idEncrypt(dataKey));

        // 如果参数里的目录ID不为空，则直接解析目录
        String dirId = (String) shareLinkInfo.getOtherParam().get("dirId");
        if (dirId != null && !dirId.isEmpty()) {
            uuid = shareLinkInfo.getOtherParam().get("uuid").toString();
            parserDir(dirId, shareId, promise0);
            return promise0.future();
        }
        parse().onSuccess(id -> {
            parserDir(id, shareId, promise0);
        }).onFailure(failRes -> {
            log.error("解析目录失败: {}", failRes.getMessage());
            promise0.fail(failRes);
        });
        return promise0.future();
    }

    private void parserDir(String id, String shareId, Promise<List<FileInfo>> promise) {
        // id以http开头直接返回 封装数组返回
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

        log.debug("开始解析目录: {}, shareId: {}, uuid: {}, ts: {}", id, shareId, uuid, tsEncode);
        // 开始解析目录: 164312216, shareId: bPMsbg5K, uuid: 0fmVWTx2Ea4zFwkpd7KXf, ts: 20865d7b7f00828279f437cd1f097860
        // 拿到目录ID
        client.postAbs(UriTemplate.of(FILE_LIST_URL))
                .putHeaders(header0)
                .setTemplateParam("shareId", shareId)
                .setTemplateParam("uuid", uuid)
                .setTemplateParam("ts", tsEncode)
                .setTemplateParam("folderId", id)
                .send().onSuccess(res -> {
                    JsonArray list;
                    try {
                        JsonObject jsonObject = asJson(res);
                        System.out.println(jsonObject.encodePrettily());
                        list = jsonObject.getJsonArray("list");
                    } catch (Exception e) {
                        log.error("解析目录失败: {}", res.bodyAsString());
                        return;
                    }
                    ArrayList<FileInfo> result = new ArrayList<>();
                    list.forEach(item->{
                        JsonObject fileJson = (JsonObject) item;
                        FileInfo fileInfo = new FileInfo();

                        // 映射已知字段fileInfo
                        String fileId = fileJson.getString("fileId");
                        String userId = fileJson.getString("userId");

                        // 其他参数
                        long nowTs2 = System.currentTimeMillis();
                        String tsEncode2 = AESUtils.encrypt2Hex(Long.toString(nowTs2));
                        String fidEncode = AESUtils.encrypt2Hex(fileId + "|" + FjTool.userId);
                        String auth = AESUtils.encrypt2Hex(fileId + "|" + nowTs2);

                        // 回传用到的参数
                        //"fidEncode", paramJson.getString("fidEncode"))
                        //"uuid", paramJson.getString("uuid"))
                        //"ts", paramJson.getString("ts"))
                        //"auth", paramJson.getString("auth"))
                        //"shareId", paramJson.getString("shareId"))
                        JsonObject entries = JsonObject.of(
                                "fidEncode", fidEncode,
                                "uuid", uuid,
                                "ts", tsEncode2,
                                "auth", auth,
                                "shareId", shareId);
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
                                .setFileId(fileJson.getString("fileId"))
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
                });
    }

    @Override
    public Future<String> parseById() {

        // 第二次请求
        JsonObject paramJson = (JsonObject)shareLinkInfo.getOtherParam().get("paramJson");
        clientNoRedirects.getAbs(UriTemplate.of(SECOND_REQUEST_URL_VIP))
                .setTemplateParam("fidEncode", paramJson.getString("fidEncode"))
                .setTemplateParam("uuid", paramJson.getString("uuid"))
                .setTemplateParam("ts", paramJson.getString("ts"))
                .setTemplateParam("auth", paramJson.getString("auth"))
                .setTemplateParam("dataKey", paramJson.getString("shareId"))
                .putHeaders(header).send().onSuccess(res2 -> {
                    MultiMap headers = res2.headers();
                    if (!headers.contains("Location")) {
                        fail(SECOND_REQUEST_URL_VIP + " 未找到重定向URL: \n" + res2.headers());
                        return;
                    }
                    promise.complete(headers.get("Location"));
                }).onFailure(handleFail(SECOND_REQUEST_URL_VIP));
        return promise.future();
    }

    public static void resetToken() {
        token = null;
        authFlag = true;
    }
}
