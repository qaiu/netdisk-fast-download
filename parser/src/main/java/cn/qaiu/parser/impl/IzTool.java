package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.AESUtils;
import cn.qaiu.util.AcwScV2Generator;
import cn.qaiu.util.FileSizeConverter;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientSession;
import io.vertx.uritemplate.UriTemplate;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * 蓝奏云优享
 * v019b22
 */
public class IzTool extends PanBase {

    WebClientSession webClientSession = WebClientSession.create(clientNoRedirects);

    private static final String API_URL_PREFIX = "https://api.ilanzou.com/unproved/";

    private static final String FIRST_REQUEST_URL = API_URL_PREFIX + "recommend/list?devType=6&devModel=Chrome" +
            "&uuid={uuid}&extra=2&timestamp={ts}&shareId={shareId}&type=0&offset=1&limit=60";

    private static final String SECOND_REQUEST_URL = API_URL_PREFIX + "file/redirect?downloadId={fidEncode}&enable=1" +
            "&devType=6&uuid={uuid}&timestamp={ts}&auth={auth}&shareId={dataKey}";
    // downloadId=x&enable=1&devType=6&uuid=x&timestamp=x&auth=x&shareId=lGFndCM

    private static final String VIP_REQUEST_URL = API_URL_PREFIX + "/buy/vip/list?devType=6&devModel=Chrome&uuid" +
            "={uuid}&extra=2&timestamp={ts}";

    private static final String FILE_LIST_URL = API_URL_PREFIX + "/share/list?devType=6&devModel=Chrome&uuid" +
            "={uuid}&extra=2&timestamp={ts}&shareId={shareId}&folderId" +
            "={folderId}&offset=1&limit=60";

    long nowTs = System.currentTimeMillis();
    String tsEncode = AESUtils.encrypt2HexIz(Long.toString(nowTs));
    String uuid = UUID.randomUUID().toString();

    private static final MultiMap header;

    static {
        header = MultiMap.caseInsensitiveMultiMap();
        header.set("Accept", "application/json, text/plain, */*");
        header.set("Accept-Encoding", "gzip, deflate, br, zstd");
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

    public IzTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

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

    public Future<String> parse() {
        String shareId = shareLinkInfo.getShareKey();

        // 24.5.12 ilanzou改规则无需计算shareId
        // String shareId = String.valueOf(AESUtils.idEncryptIz(dataKey));

        // 第一次请求 获取文件信息
        // POST https://api.ilanzou.com/ws/recommend/list?devType=6&devModel=Chrome&extra=2&shareId=146731&type=0&offset=1&limit=60
        String url = StringUtils.isBlank(shareLinkInfo.getSharePassword()) ? FIRST_REQUEST_URL
                : (FIRST_REQUEST_URL + "&code=" + shareLinkInfo.getSharePassword());
        webClientSession.postAbs(UriTemplate.of(VIP_REQUEST_URL))
                .setTemplateParam("uuid", uuid)
                .setTemplateParam("ts", tsEncode)
                .send().onSuccess(r0 -> { // 忽略res
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
                                                handleParseResponse(asText(res2), shareId);
                                            }).onFailure(handleFail(FIRST_REQUEST_URL));
                                    return;
                                }
                                handleParseResponse(resBody, shareId);
                            }).onFailure(handleFail(FIRST_REQUEST_URL));
                });
        return promise.future();
    }

    private void handleParseResponse(String resBody, String shareId) {
        JsonObject resJson;
        try {
            resJson = new JsonObject(resBody);
        } catch (Exception e) {
            fail(FIRST_REQUEST_URL + " 解析JSON失败: " + resBody);
            return;
        }
        if (resJson.isEmpty()) {
            fail(FIRST_REQUEST_URL + " 返回内容为空");
            return;
        }
        if (resJson.getInteger("code") != 200) {
            fail(FIRST_REQUEST_URL + " 返回异常: " + resJson);
            return;
        }
        if (resJson.getJsonArray("list").isEmpty()) {
            fail(FIRST_REQUEST_URL + " 解析文件列表为空: " + resJson);
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

        String fileId = fileInfo.getString("fileIds");
        String userId = fileInfo.getString("userId");
        // 其他参数
        // String fidEncode = AESUtils.encrypt2HexIz(fileId + "|");
        String fidEncode = AESUtils.encrypt2HexIz(fileId + "|" + userId);
        String auth = AESUtils.encrypt2HexIz(fileId + "|" + nowTs);
        // 第二次请求
        webClientSession.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
                .setTemplateParam("fidEncode", fidEncode)
                .setTemplateParam("uuid", uuid)
                .setTemplateParam("ts", tsEncode)
                .setTemplateParam("auth", auth)
                .setTemplateParam("shareId", shareId)
                .putHeaders(header).send().onSuccess(res2 -> {
                    MultiMap headers = res2.headers();
                    if (!headers.contains("Location")) {
                        fail(SECOND_REQUEST_URL + " 未找到重定向URL: \n" + headers);
                        return;
                    }
                    promise.complete(headers.get("Location"));
                }).onFailure(handleFail(SECOND_REQUEST_URL));
    }

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
            if (id != null && id.matches("^[a-zA-Z0-9]+$")) {
                parserDir(id, shareId, promise);
            } else {
                promise.fail("解析目录ID失败");
            }
        }).onFailure(failRes -> {
            log.error("解析目录失败: {}", failRes.getMessage());
            promise.fail(failRes);
        });
        return promise.future();
    }

    private void parserDir(String id, String shareId, Promise<List<FileInfo>> promise) {
        log.debug("开始解析目录: {}, shareId: {}, uuid: {}, ts: {}", id, shareId, uuid, tsEncode);
        // 开始解析目录: 164312216, shareId: bPMsbg5K, uuid: 0fmVWTx2Ea4zFwkpd7KXf, ts: 20865d7b7f00828279f437cd1f097860
        // 拿到目录ID
        webClientSession.postAbs(UriTemplate.of(FILE_LIST_URL))
                .putHeaders(header)
                .setTemplateParam("shareId", shareId)
                .setTemplateParam("uuid", uuid)
                .setTemplateParam("ts", tsEncode)
                .setTemplateParam("folderId", id)
                .send().onSuccess(res -> {
                    JsonObject jsonObject;
                    try {
                        jsonObject = asJson(res);
                    } catch (Exception e) {
                        promise.fail(FIRST_REQUEST_URL + " 解析JSON失败: " + res.bodyAsString());
                        return;
                    }
                    // System.out.println(jsonObject.encodePrettily());
                    JsonArray list = jsonObject.getJsonArray("list");
                    ArrayList<FileInfo> result = new ArrayList<>();
                    list.forEach(item->{
                        JsonObject fileJson = (JsonObject) item;
                        FileInfo fileInfo = new FileInfo();

                        // 映射已知字段
                        String fileId = fileJson.getString("fileId");
                        String userId = fileJson.getString("userId");

                        // 回传用到的参数
                        //"fidEncode", paramJson.getString("fidEncode"))
                        //"uuid", paramJson.getString("uuid"))
                        //"ts", paramJson.getString("ts"))
                        //"auth", paramJson.getString("auth"))
                        //"shareId", paramJson.getString("shareId"))
                        String fidEncode = AESUtils.encrypt2HexIz(fileId + "|" + userId);
                        String auth = AESUtils.encrypt2HexIz(fileId + "|" + nowTs);
                        JsonObject entries = JsonObject.of(
                                "fidEncode", fidEncode,
                                "uuid", uuid,
                                "ts", tsEncode,
                                "auth", auth,
                                "shareId", shareId);
                        byte[] encode = Base64.getEncoder().encode(entries.encode().getBytes());
                        String param = new String(encode);

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
                }).onFailure(failRes -> {
                    log.error("解析目录请求失败: {}", failRes.getMessage());
                    promise.fail(failRes);
                });
    }

    @Override
    public Future<String> parseById() {
        // 第二次请求
        JsonObject paramJson = (JsonObject)shareLinkInfo.getOtherParam().get("paramJson");
        webClientSession.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
                .setTemplateParam("fidEncode", paramJson.getString("fidEncode"))
                .setTemplateParam("uuid", paramJson.getString("uuid"))
                .setTemplateParam("ts", paramJson.getString("ts"))
                .setTemplateParam("auth", paramJson.getString("auth"))
                .setTemplateParam("shareId", paramJson.getString("shareId"))
                .putHeaders(header).send().onSuccess(res2 -> {
                    MultiMap headers = res2.headers();
                    if (!headers.contains("Location")) {
                        fail(SECOND_REQUEST_URL + " 未找到重定向URL: \n" + res2.headers());
                        return;
                    }
                    promise.complete(headers.get("Location"));
                }).onFailure(handleFail(SECOND_REQUEST_URL));
        return promise.future();
    }
}
