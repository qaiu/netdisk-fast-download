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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.uritemplate.UriTemplate;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * 小飞机网盘
 *
 * @version V016_230609
 */
public class FjTool extends PanBase {
    public static final String REFERER_URL = "https://share.feijipan.com/";
    private static final String API_URL_PREFIX = "https://api.feijipan.com/ws/";

    private static final String FIRST_REQUEST_URL = API_URL_PREFIX + "recommend/list?devType=6&devModel=Chrome" +
            "&uuid={uuid}&extra=2&timestamp={ts}&shareId={shareId}&type=0&offset=1&limit=60";
    /// recommend/list?devType=6&devModel=Chrome&uuid={uuid}&extra=2&timestamp={ts}&shareId={shareId}&type=0&offset=1&limit=60
    //  recommend/list?devType=6&devModel=Chrome&uuid={uuid}&extra=2&timestamp={ts}&shareId=JoUTkZYj&type=0&offset=1&limit=60


    private static final String SECOND_REQUEST_URL = API_URL_PREFIX + "file/redirect?downloadId={fidEncode}&enable=1" +
            "&devType=6&uuid={uuid}&timestamp={ts}&auth={auth}&shareId={dataKey}";
    // https://api.feijipan.com/ws/file/redirect?downloadId={fidEncode}&enable=1&devType=6&uuid={uuid}&timestamp={ts}&auth={auth}&shareId={dataKey}


    private static final String VIP_REQUEST_URL = API_URL_PREFIX + "/buy/vip/list?devType=6&devModel=Chrome&uuid" +
            "={uuid}&extra=2&timestamp={ts}";
    // https://api.feijipan.com/ws/buy/vip/list?devType=6&devModel=Chrome&uuid=WQAl5yBy1naGudJEILBvE&extra=2&timestamp=E2C53155F6D09417A27981561134CB73

    // https://api.feijipan.com/ws/share/list?devType=6&devModel=Chrome&uuid=pwRWqwbk1J-KMTlRZowrn&extra=2&timestamp=C5F8A68C53121AB21FA35BA3529E8758&shareId=fmAuOh3m&folderId=28986333&offset=1&limit=60

    private static final String FILE_LIST_URL = API_URL_PREFIX + "/share/list?devType=6&devModel=Chrome&uuid" +
            "={uuid}&extra=2&timestamp={ts}&shareId={shareId}&folderId" +
            "={folderId}&offset=1&limit=60";

    private static final MultiMap header;


    long nowTs = System.currentTimeMillis();
    String tsEncode = AESUtils.encrypt2Hex(Long.toString(nowTs));
    String uuid = UUIDUtil.fjUuid(); // 也可以使用 UUID.randomUUID().toString()

    static {
        header = MultiMap.caseInsensitiveMultiMap();
        header.set("Accept", "application/json, text/plain, */*");
        header.set("Accept-Encoding", "gzip, deflate, br, zstd");
        header.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        header.set("Cache-Control", "no-cache");
        header.set("Connection", "keep-alive");
        header.set("Content-Length", "0");
        header.set("DNT", "1");
        header.set("Host", "api.feijipan.com");
        header.set("Origin", "https://www.feijix.com");
        header.set("Pragma", "no-cache");
        header.set("Referer", "https://www.feijix.com/");
        header.set("Sec-Fetch-Dest", "empty");
        header.set("Sec-Fetch-Mode", "cors");
        header.set("Sec-Fetch-Site", "cross-site");
        header.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        header.set("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"");
        header.set("sec-ch-ua-mobile", "?0");
        header.set("sec-ch-ua-platform", "\"Windows\"");
    }

    public FjTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {

        // 240530 此处shareId又改为了原始的shareId
        // String.valueOf(AESUtils.idEncrypt(dataKey));
        final String shareId = shareLinkInfo.getShareKey();

        // 24.5.12 飞机盘 规则修改 需要固定UUID先请求会员接口, 再请求后续接口
        client.postAbs(UriTemplate.of(VIP_REQUEST_URL))
                .setTemplateParam("uuid", uuid)
                .setTemplateParam("ts", tsEncode)
                .send().onSuccess(r0 -> { // 忽略res

                    // long nowTs0 = System.currentTimeMillis();
                    String tsEncode0 = AESUtils.encrypt2Hex(Long.toString(nowTs));
                    // 第一次请求 获取文件信息
                    // POST https://api.feijipan.com/ws/recommend/list?devType=6&devModel=Chrome&extra=2&shareId=146731&type=0&offset=1&limit=60
                    client.postAbs(UriTemplate.of(FIRST_REQUEST_URL))
                            .putHeaders(header)
                            .setTemplateParam("shareId", shareId)
                            .setTemplateParam("uuid", uuid)
                            .setTemplateParam("ts", tsEncode)
                            .send().onSuccess(res -> {
                                // 处理GZ压缩
                                // 使用GZIPInputStream来解压数据
                                String decompressedString;
                                try (ByteArrayInputStream bais = new ByteArrayInputStream(res.body().getBytes());
                                     GZIPInputStream gzis = new GZIPInputStream(bais);
                                     BufferedReader reader = new BufferedReader(new InputStreamReader(gzis, StandardCharsets.UTF_8))) {

                                    // 用于存储解压后的字符串
                                    StringBuilder decompressedData = new StringBuilder();

                                    // 逐行读取解压后的数据
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        decompressedData.append(line);
                                    }

                                    // 此时decompressedData.toString()包含了解压后的字符串
                                    decompressedString = decompressedData.toString();

                                } catch (IOException e) {
                                    // 处理可能的IO异常
                                    fail(FIRST_REQUEST_URL + " 响应异常");
                                    return;
                                }
                                // 处理GZ压缩结束

                                JsonObject resJson = new JsonObject(decompressedString);
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
                                long nowTs2 = System.currentTimeMillis();
                                String tsEncode2 = AESUtils.encrypt2Hex(Long.toString(nowTs2));
                                String fidEncode = AESUtils.encrypt2Hex(fileId + "|" + userId);
                                String auth = AESUtils.encrypt2Hex(fileId + "|" + nowTs2);

                                // 第二次请求
                                HttpRequest<Buffer> httpRequest =
                                        clientNoRedirects.getAbs(UriTemplate.of(SECOND_REQUEST_URL))
                                        .putHeaders(header)
                                        .setTemplateParam("fidEncode", fidEncode)
                                        .setTemplateParam("uuid", uuid)
                                        .setTemplateParam("ts", tsEncode2)
                                        .setTemplateParam("auth", auth)
                                        .setTemplateParam("dataKey", shareId);
                                System.out.println(httpRequest.toString());
                                httpRequest.send().onSuccess(res2 -> {
                                    MultiMap headers = res2.headers();
                                    if (!headers.contains("Location")) {
                                        fail(SECOND_REQUEST_URL + " 未找到重定向URL: \n" + res.headers());
                                        return;
                                    }
                                    promise.complete(headers.get("Location"));
                                }).onFailure(handleFail(SECOND_REQUEST_URL));
                            }).onFailure(handleFail(FIRST_REQUEST_URL));

                }).onFailure(handleFail(FIRST_REQUEST_URL));

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
                        /*
                        {
                                   "iconId" : 13,
                                   "fileName" : "酷我音乐车机版 6.4.2.20.apk",
                                   "fileSaves" : 52,
                                   "fileStars" : 5.0,
                                   "type" : 1,
                                   "userId" : 1392902,
                                   "fileComments" : 0,
                                   "fileSize" : 68854,
                                   "fileIcon" : "https://d.feijix.com/storage/files/icon/2024/06/08/7/8146637/6534494874910391.gz?t=67a5ea7c&rlimit=20&us=nMfuftjBN5&sign=f72be03007a301217f90dcc20333bd9a",
                                   "updTime" : "2024-06-10 17:26:53",
                                   "sortId" : 1487918143,
                                   "name" : "酷我音乐车机版 6.4.2.20.apk",
                                   "fileDownloads" : 109,
                                   "fileUrl" : null,
                                   "fileLikes" : 0,
                                   "fileType" : 1,
                                   "fileId" : 1487918143
                          }
                        */
                        JsonArray list = jsonObject.getJsonArray("list");
                        ArrayList<FileInfo> result = new ArrayList<>();
                        list.forEach(item->{
                            JsonObject fileJson = (JsonObject) item;
                            FileInfo fileInfo = new FileInfo();
                            // 映射已知字段
                            fileInfo.setFileName(fileJson.getString("fileName"))
                                    .setFileId(fileJson.getString("fileId"))
                                    .setCreateTime(fileJson.getString("createTime"))
                                    .setFileType(fileJson.getString("fileType"))
                                    .setSize(fileJson.getLong("fileSize"))
                                    .setSizeStr(FileSizeConverter.convertToReadableSize(fileJson.getLong("fileSize")))
                                    .setCreateBy(fileJson.getLong("userId").toString())
                                    .setDownloadCount(fileJson.getInteger("fileDownloads"))
                                    .setCreateTime(fileJson.getString("updTime"))
                                    .setFileIcon(fileJson.getString("fileIcon"));
                            result.add(fileInfo);
                        });
                        promise.complete(result);
                    });
        });
        return promise.future();
    }
}
