package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.CommonUtils;
import cn.qaiu.util.HeaderUtils;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QQ闪传 <br>
 * 支持多文件、多级目录解析。通过 GetFileList API 获取文件列表，BatchDownload API 获取下载直链。<br>
 * 有效期默认7天。
 */
public class QQscTool extends PanBase {

    Logger LOG = LoggerFactory.getLogger(QQscTool.class);

    private static final String BATCH_DOWNLOAD_API =
            "https://qfile.qq.com/http2rpc/gotrpc/noauth/trpc.qqntv2.richmedia.InnerProxy/BatchDownload";

    private static final String GET_FILE_LIST_API =
            "https://qfile.qq.com/http2rpc/gotrpc/noauth/trpc.file.FileFlashTrans/GetFileList";

    private static final MultiMap BATCH_DOWNLOAD_HEADERS = HeaderUtils.parseHeaders("""
            Accept-Encoding: gzip, deflate
            Accept-Language: zh-CN,zh;q=0.9
            Connection: keep-alive
            Cookie: uin=9000002; p_uin=9000002
            DNT: 1
            Origin: https://qfile.qq.com
            Sec-Fetch-Dest: empty
            Sec-Fetch-Mode: cors
            Sec-Fetch-Site: same-origin
            User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Edg/138.0.0.0
            accept: application/json
            content-type: application/json
            sec-ch-ua: "Not)A;Brand";v="8", "Chromium";v="138", "Microsoft Edge";v="138"
            sec-ch-ua-mobile: ?0
            sec-ch-ua-platform: "macOS"
            x-oidb: {"uint32_command":"0x9248", "uint32_service_type":"4"}
            """);

    private static final MultiMap GET_FILE_LIST_HEADERS = HeaderUtils.parseHeaders("""
            Accept-Encoding: gzip, deflate
            Cookie: uin=9000002; p_uin=9000002
            Origin: https://qfile.qq.com
            User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Edg/138.0.0.0
            content-type: application/json
            x-oidb: {"uint32_command":"0x93d4", "uint32_service_type":"1"}
            """);

    public QQscTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    @Override
    public Future<String> parse() {
        client.getAbs(shareLinkInfo.getShareUrl()).send(result -> {
            if (result.failed()) {
                LOG.error("请求失败: {}", result.cause().getMessage());
                promise.fail(result.cause());
                return;
            }
            String html = result.result().bodyAsString();
            String fileName = extractFileNameFromTitle(html);
            if (fileName != null) {
                FileInfo fileInfo = new FileInfo();
                fileInfo.setFileName(fileName);
                shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
            }
            // 尝试用 GetFileList API 获取第一个文件的下载链接
            String filesetId = extractFilesetId(html);
            if (filesetId != null) {
                fetchFileList(filesetId, "").onSuccess(fileList -> {
                    for (int i = 0; i < fileList.size(); i++) {
                        JsonObject file = fileList.getJsonObject(i);
                        if (!file.getBoolean("is_dir", false)) {
                            String physicalId = file.getJsonObject("physical").getString("id");
                            String name = file.getString("name");
                            downloadFile(physicalId, name);
                            return;
                        }
                    }
                    promise.fail("未找到可下载的文件");
                }).onFailure(e -> {
                    LOG.warn("GetFileList 失败，回退到旧解析方式: {}", e.getMessage());
                    parseLegacy(html, fileName);
                });
            } else {
                parseLegacy(html, fileName);
            }
        });
        return promise.future();
    }

    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> resultPromise = Promise.promise();
        String dirId = (String) shareLinkInfo.getOtherParam().get("dirId");

        client.getAbs(shareLinkInfo.getShareUrl()).send(result -> {
            if (result.failed()) {
                resultPromise.fail(result.cause());
                return;
            }
            String html = result.result().bodyAsString();
            String filesetId = extractFilesetId(html);
            if (filesetId == null) {
                resultPromise.fail("无法从页面提取 filesetId");
                return;
            }
            String parentId = dirId != null ? dirId : "";
            fetchFileList(filesetId, parentId).onSuccess(fileList -> {
                try {
                    List<FileInfo> list = new ArrayList<>();
                    String panType = shareLinkInfo.getType();
                    for (int i = 0; i < fileList.size(); i++) {
                        JsonObject file = fileList.getJsonObject(i);
                        FileInfo fileInfo = new FileInfo();
                        String name = file.getString("name");
                        String cliFileid = file.getString("cli_fileid");
                        boolean isDir = file.getBoolean("is_dir", false);
                        String sizeStr = file.getString("file_size");

                        fileInfo.setFileName(name)
                                .setFileId(cliFileid)
                                .setPanType(panType)
                                .setSizeStr(sizeStr);

                        if (isDir) {
                            fileInfo.setFileType("folder")
                                    .setParserUrl(String.format("%s/v2/getFileList?url=%s&dirId=%s",
                                            getDomainName(),
                                            URLEncoder.encode(shareLinkInfo.getShareUrl(), StandardCharsets.UTF_8),
                                            cliFileid));
                        } else {
                            String physicalId = file.getJsonObject("physical").getString("id");
                            JsonObject paramJson = new JsonObject()
                                    .put("fileId", physicalId)
                                    .put("fileName", name)
                                    .put("cliFileid", cliFileid);
                            String param = CommonUtils.urlBase64Encode(paramJson.encode());
                            fileInfo.setFileType("file")
                                    .setParserUrl(String.format("%s/v2/redirectUrl/%s/%s",
                                            getDomainName(), panType, param));
                        }
                        list.add(fileInfo);
                    }
                    resultPromise.complete(list);
                } catch (Exception e) {
                    resultPromise.fail(e);
                }
            }).onFailure(resultPromise::fail);
        });
        return resultPromise.future();
    }

    @Override
    public Future<String> parseById() {
        JsonObject paramJson = (JsonObject) shareLinkInfo.getOtherParam().get("paramJson");
        String fileId = paramJson.getString("fileId");
        String fileName = paramJson.getString("fileName");

        Promise<String> p = Promise.promise();
        callBatchDownload(fileId, fileName, p);
        return p.future();
    }

    // ========== 内部方法 ==========

    /**
     * 调用 BatchDownload API 获取单个文件的下载直链
     */
    private void downloadFile(String physicalId, String fileName) {
        callBatchDownload(physicalId, fileName, promise);
    }

    private void callBatchDownload(String physicalId, String fileName, Promise<String> p) {
        String body = """
                {"req_head":{"agent":8},"download_info":[{"batch_id":"%s","scene":{"business_type":4,"app_type":22,"scene_type":5},"index_node":{"file_uuid":"%s"},"url_type":2,"download_scene":0}],"scene_type":103}
                """.formatted(physicalId, physicalId);

        client.postAbs(BATCH_DOWNLOAD_API)
                .putHeaders(BATCH_DOWNLOAD_HEADERS)
                .sendJsonObject(new JsonObject(body))
                .onSuccess(resp -> {
                    if (resp.statusCode() != 200) {
                        p.fail("BatchDownload 请求失败，状态码: " + resp.statusCode());
                        return;
                    }
                    JsonObject respBody = asJson(resp);
                    if (!respBody.containsKey("retcode") || respBody.getInteger("retcode") != 0) {
                        p.fail("BatchDownload 请求失败: " + respBody.encodePrettily());
                        return;
                    }
                    JsonArray downloadRsp = respBody.getJsonObject("data").getJsonArray("download_rsp");
                    if (downloadRsp == null || downloadRsp.isEmpty()) {
                        p.fail("BatchDownload 响应中缺少 download_rsp");
                        return;
                    }
                    String url = downloadRsp.getJsonObject(0).getString("url");
                    if (url != null && url.startsWith("&filename=")) {
                        p.fail("该文件已被和谐");
                        return;
                    }
                    if (fileName != null) {
                        url = url + "&filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                    }
                    p.complete(url);
                })
                .onFailure(e -> {
                    LOG.error("BatchDownload 请求异常", e);
                    p.fail(e);
                });
    }

    /**
     * 调用 GetFileList API 获取指定目录下的文件列表
     */
    private Future<JsonArray> fetchFileList(String filesetId, String parentId) {
        Promise<JsonArray> p = Promise.promise();
        JsonObject body = new JsonObject()
                .put("fileset_id", filesetId)
                .put("req_infos", new JsonArray()
                        .add(new JsonObject()
                                .put("parent_id", parentId)
                                .put("req_depth", 1)
                                .put("count", 50)
                                .put("filter_condition", new JsonObject().put("file_category", 0))
                                .put("sort_conditions", new JsonArray()
                                        .add(new JsonObject()
                                                .put("sort_field", 0)
                                                .put("sort_order", 0)))))
                .put("support_folder_status", true);

        MultiMap headers = GET_FILE_LIST_HEADERS.set("Referer", shareLinkInfo.getShareUrl());

        client.postAbs(GET_FILE_LIST_API)
                .putHeaders(headers)
                .sendJsonObject(body)
                .onSuccess(resp -> {
                    if (resp.statusCode() != 200) {
                        p.fail("GetFileList 请求失败，状态码: " + resp.statusCode());
                        return;
                    }
                    JsonObject respBody = asJson(resp);
                    if (respBody.getInteger("retcode", -1) != 0) {
                        p.fail("GetFileList 请求失败: " + respBody.getString("message", "未知错误"));
                        return;
                    }
                    JsonArray fileLists = respBody.getJsonObject("data").getJsonArray("file_lists");
                    if (fileLists == null || fileLists.isEmpty()) {
                        p.fail("GetFileList 响应中缺少 file_lists");
                        return;
                    }
                    JsonArray fileList = fileLists.getJsonObject(0).getJsonArray("file_list");
                    p.complete(fileList != null ? fileList : new JsonArray());
                })
                .onFailure(e -> {
                    LOG.error("GetFileList 请求异常", e);
                    p.fail(e);
                });
        return p.future();
    }

    /**
     * 从 HTML 的 __NUXT_DATA__ 中提取 fileset_id
     */
    String extractFilesetId(String html) {
        // 匹配 UUID 格式的 fileset_id（出现在 Nuxt 数据的 fileset_id 字段值位置）
        Pattern pattern = Pattern.compile(
                "\"fileset_id\"[:\\s]*\"([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 旧版解析方式（兼容单文件链接，通过 HTML 字符串搜索提取 UUID）
     */
    private void parseLegacy(String html, String fileName) {
        String fileUUID = getFileUUID(html);
        if (fileUUID == null) {
            promise.fail("未能提取到文件UUID");
            return;
        }
        LOG.info("使用旧版解析，提取到的文件UUID: {}", fileUUID);
        downloadFile(fileUUID, fileName);
    }

    String getFileUUID(String htmlJs) {
        String keyword = "\"download_limit_status\"";
        String marker = "},\"";

        int startIndex = htmlJs.indexOf(keyword);
        if (startIndex != -1) {
            int markerIndex = htmlJs.indexOf(marker, startIndex);
            if (markerIndex != -1) {
                int quoteStart = markerIndex + marker.length();
                int quoteEnd = htmlJs.indexOf("\"", quoteStart);
                if (quoteEnd != -1) {
                    String extracted = htmlJs.substring(quoteStart, quoteEnd);
                    LOG.debug("提取结果: {}", extracted);
                    return extracted;
                }
            }
        }
        return null;
    }

    public static String extractFileNameFromTitle(String content) {
        Pattern pattern = Pattern.compile("<title>(.*?)</title>");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String fullTitle = matcher.group(1);
            int sepIndex = fullTitle.indexOf("｜");
            if (sepIndex != -1) {
                return fullTitle.substring(0, sepIndex);
            }
            return fullTitle;
        }
        return null;
    }
}
