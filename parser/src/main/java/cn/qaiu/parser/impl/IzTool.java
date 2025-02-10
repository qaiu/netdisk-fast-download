package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.AESUtils;
import cn.qaiu.util.FileSizeConverter;
import cn.qaiu.util.UUIDUtil;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.UriTemplate;

import java.util.*;

/**
 * 蓝奏云优享
 *
 */
public class IzTool extends PanBase {

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

    public Future<String> parse() {
        String shareId = shareLinkInfo.getShareKey();

        // 24.5.12 ilanzou改规则无需计算shareId
        // String shareId = String.valueOf(AESUtils.idEncryptIz(dataKey));

        // 第一次请求 获取文件信息
        // POST https://api.ilanzou.com/ws/recommend/list?devType=6&devModel=Chrome&extra=2&shareId=146731&type=0&offset=1&limit=60

        client.postAbs(UriTemplate.of(VIP_REQUEST_URL))
                .setTemplateParam("uuid", uuid)
                .setTemplateParam("ts", tsEncode)
                .send().onSuccess(r0 -> { // 忽略res
                    // 第一次请求 获取文件信息
                    // POST https://api.feijipan.com/ws/recommend/list?devType=6&devModel=Chrome&extra=2&shareId=146731&type=0&offset=1&limit=60
                    client.postAbs(UriTemplate.of(
                            shareLinkInfo.getSharePassword() == null ?
                                    FIRST_REQUEST_URL : (FIRST_REQUEST_URL + "&code=" + shareLinkInfo.getSharePassword()))
                            )
                            .putHeaders(header)
                            .setTemplateParam("shareId", shareId)
                            .setTemplateParam("uuid", uuid)
                            .setTemplateParam("ts", tsEncode)
                            .send().onSuccess(res -> {
                                JsonObject resJson = asJson(res);
                                if (resJson.getInteger("code") != 200) {
                                    fail(FIRST_REQUEST_URL + " 返回异常: " + resJson);
                                    return;
                                }
                                if (resJson.getJsonArray("list").size() == 0) {
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

                                String fileId = fileInfo.getString("fileIds");
                                String userId = fileInfo.getString("userId");
                                // 其他参数
                                // String fidEncode = AESUtils.encrypt2HexIz(fileId + "|");
                                String fidEncode = AESUtils.encrypt2HexIz(fileId + "|" + userId);
                                String auth = AESUtils.encrypt2HexIz(fileId + "|" + nowTs);
                                // 第二次请求
                                clientNoRedirects.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
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
                            }).onFailure(handleFail(FIRST_REQUEST_URL));
                });
        return promise.future();
    }

    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> promise = Promise.promise();

        String shareId = shareLinkInfo.getShareKey(); // String.valueOf(AESUtils.idEncrypt(dataKey));
        parse().onSuccess(id -> {
            // 拿到目录ID
            client.postAbs(UriTemplate.of(FILE_LIST_URL))
                    .putHeaders(header)
                    .setTemplateParam("shareId", shareId)
                    .setTemplateParam("uuid", uuid)
                    .setTemplateParam("ts", tsEncode)
                    .setTemplateParam("folderId", id)
                    .send().onSuccess(res -> {
                        JsonObject jsonObject = asJson(res);
                        System.out.println(jsonObject.encodePrettily());
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


                            long fileSize = fileJson.getLong("fileSize") * 1024;
                            fileInfo.setFileName(fileJson.getString("fileName"))
                                    .setFileId(fileId)
                                    .setCreateTime(fileJson.getString("createTime"))
                                    .setFileType(fileJson.getString("fileType"))
                                    .setSize(fileSize)
                                    .setSizeStr(FileSizeConverter.convertToReadableSize(fileSize))
                                    .setCreateBy(fileJson.getLong("userId").toString())
                                    .setDownloadCount(fileJson.getInteger("fileDownloads"))
                                    .setCreateTime(fileJson.getString("updTime"))
                                    .setFileIcon(fileJson.getString("fileIcon"))
                                    .setPanType(shareLinkInfo.getType())
                                    .setParserUrl(String.format("%s/v2/redirectUrl/%s/%s", getDomainName(),
                                            shareLinkInfo.getType(), param));
                            result.add(fileInfo);
                        });
                        promise.complete(result);
                    });
        });
        return promise.future();
    }

    @Override
    public Future<String> parseById() {
        // 第二次请求
        JsonObject paramJson = (JsonObject)shareLinkInfo.getOtherParam().get("paramJson");
        clientNoRedirects.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
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
