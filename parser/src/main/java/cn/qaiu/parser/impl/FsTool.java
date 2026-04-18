package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <a href="https://www.feishu.cn/">飞书云盘</a>
 * <p>
 * 支持飞书公开分享文件和文件夹的解析。
 * <ul>
 *     <li>文件链接: https://xxx.feishu.cn/file/{token}</li>
 *     <li>文件夹链接: https://xxx.feishu.cn/drive/folder/{token}</li>
 * </ul>
 * 飞书下载需要先获取匿名会话Cookie，然后使用Cookie请求下载接口。
 * </p>
 */
public class FsTool extends PanBase {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    /**
     * 飞书 obj_type: type=12 表示上传文件可下载
     */
    private static final int OBJ_TYPE_FILE = 12;

    /**
     * v3 列表 API 支持的 obj_type
     */
    private static final int[] LIST_OBJ_TYPES = {
            0, 2, 22, 44, 3, 30, 8, 11, 12, 84, 123, 124
    };

    /** 每页返回条目数 */
    private static final int PAGE_SIZE = 50;

    /**
     * 从分享链接中提取 tenant 的正则
     */
    private static final Pattern TENANT_PATTERN =
            Pattern.compile("https://([^.]+)\\.feishu\\.cn/");

    /** 解析 Content-Disposition: filename*=UTF-8''xxx */
    private static final Pattern CD_FILENAME_STAR_PATTERN =
            Pattern.compile("filename\\*=UTF-8''(.+?)(?:;|$)");

    /** 解析 Content-Disposition: filename="xxx" 或 filename=xxx */
    private static final Pattern CD_FILENAME_PATTERN =
            Pattern.compile("filename=\"?([^\";]+)\"?");

    /** 解析 Content-Range 中的总大小 */
    private static final Pattern CONTENT_RANGE_SIZE_PATTERN =
            Pattern.compile("/(\\d+)");

    public FsTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    @Override
    public Future<String> parse() {
        String shareUrl = shareLinkInfo.getShareUrl();
        String tenant = extractTenant(shareUrl);
        String token = shareLinkInfo.getShareKey();

        if (tenant == null || token == null) {
            fail("无法从链接中提取tenant或token: {}", shareUrl);
            return promise.future();
        }

        boolean isFolder = shareUrl.contains("/drive/folder/");
        if (isFolder) {
            fetchSessionAndParseFolder(tenant, token, shareUrl);
        } else {
            fetchSessionAndParseFile(tenant, token, shareUrl);
        }

        return promise.future();
    }

    /**
     * 获取匿名session后解析文件
     */
    private void fetchSessionAndParseFile(String tenant, String token, String shareUrl) {
        clientSession.getAbs(shareUrl)
                .putHeader("User-Agent", UA)
                .putHeader("Accept", "text/html,*/*")
                .send()
                .onSuccess(res -> {
                    String dlUrl = buildDownloadUrl(tenant, token);

                    // Range探测获取文件名和大小
                    clientSession.getAbs(dlUrl)
                            .putHeader("User-Agent", UA)
                            .putHeader("Referer", shareUrl)
                            .putHeader("Range", "bytes=0-0")
                            .send()
                            .onSuccess(probeRes -> {
                                String fileName = parseFileNameFromContentDisposition(
                                        probeRes.getHeader("Content-Disposition"));

                                Map<String, String> headers = new HashMap<>();
                                headers.put("Referer", shareUrl);
                                headers.put("User-Agent", UA);

                                String cookies = extractCookiesFromResponse(probeRes);
                                if (cookies != null && !cookies.isEmpty()) {
                                    headers.put("Cookie", cookies);
                                }

                                if (fileName != null) {
                                    FileInfo fileInfo = new FileInfo();
                                    fileInfo.setFileName(fileName);
                                    fileInfo.setPanType(shareLinkInfo.getType());
                                    parseSizeFromContentRange(
                                            probeRes.getHeader("Content-Range"), fileInfo);
                                    shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
                                }

                                completeWithMeta(dlUrl, headers);
                            })
                            .onFailure(handleFail("探测文件信息失败"));
                })
                .onFailure(handleFail("获取匿名会话失败"));
    }

    /**
     * 获取匿名session后解析文件夹（取第一个可下载文件）
     */
    private void fetchSessionAndParseFolder(String tenant, String folderToken,
                                             String shareUrl) {
        clientSession.getAbs(shareUrl)
                .putHeader("User-Agent", UA)
                .putHeader("Accept", "text/html,*/*")
                .send()
                .onSuccess(res ->
                        listFolderAll(tenant, folderToken, "").onSuccess(items -> {
                            if (items.isEmpty()) {
                                fail("文件夹中没有可下载的文件");
                                return;
                            }
                            FileInfo first = items.get(0);
                            String objToken = first.getFileId();
                            String dlUrl = buildDownloadUrl(tenant, objToken);
                            String referer = "https://" + tenant
                                    + ".feishu.cn/drive/folder/" + folderToken;

                            Map<String, String> headers = new HashMap<>();
                            headers.put("Referer", referer);
                            headers.put("User-Agent", UA);

                            shareLinkInfo.getOtherParam().put("fileInfo", first);
                            completeWithMeta(dlUrl, headers);
                        }).onFailure(t -> fail("列出文件夹内容失败: {}", t.getMessage())))
                .onFailure(handleFail("获取匿名会话失败"));
    }

    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> listPromise = Promise.promise();
        String shareUrl = shareLinkInfo.getShareUrl();
        String tenant = extractTenant(shareUrl);
        String token = shareLinkInfo.getShareKey();

        if (tenant == null || token == null) {
            listPromise.fail("无法从链接中提取tenant或token: " + shareUrl);
            return listPromise.future();
        }

        boolean isFolder = shareUrl.contains("/drive/folder/");

        clientSession.getAbs(shareUrl)
                .putHeader("User-Agent", UA)
                .putHeader("Accept", "text/html,*/*")
                .send()
                .onSuccess(res -> {
                    if (isFolder) {
                        listFolderAll(tenant, token, "")
                                .onSuccess(listPromise::complete)
                                .onFailure(listPromise::fail);
                    } else {
                        probeSingleFile(tenant, token, shareUrl)
                                .onSuccess(fileInfo -> {
                                    List<FileInfo> list = new ArrayList<>();
                                    list.add(fileInfo);
                                    listPromise.complete(list);
                                })
                                .onFailure(listPromise::fail);
                    }
                })
                .onFailure(t -> listPromise.fail("获取匿名会话失败: " + t.getMessage()));

        return listPromise.future();
    }

    /**
     * 分页获取文件夹所有可下载文件
     */
    private Future<List<FileInfo>> listFolderAll(String tenant, String folderToken,
                                                  String pageLabel) {
        Promise<List<FileInfo>> p = Promise.promise();

        listFolderPage(tenant, folderToken, pageLabel).onSuccess(pageResult -> {
            List<FileInfo> items = new ArrayList<>(pageResult.items);
            if (pageResult.hasMore) {
                listFolderAll(tenant, folderToken, pageResult.nextLabel)
                        .onSuccess(moreItems -> {
                            items.addAll(moreItems);
                            p.complete(items);
                        })
                        .onFailure(p::fail);
            } else {
                p.complete(items);
            }
        }).onFailure(p::fail);

        return p.future();
    }

    /**
     * 列出文件夹内容（单页）
     */
    private Future<FolderPageResult> listFolderPage(String tenant, String folderToken,
                                                     String pageLabel) {
        Promise<FolderPageResult> p = Promise.promise();
        String baseUrl = "https://" + tenant + ".feishu.cn";

        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(baseUrl)
                .append("/space/api/explorer/v3/children/list/")
                .append("?length=").append(PAGE_SIZE)
                .append("&asc=1&rank=5&token=").append(folderToken);

        for (int type : LIST_OBJ_TYPES) {
            urlBuilder.append("&obj_type=").append(type);
        }

        if (pageLabel != null && !pageLabel.isEmpty()) {
            urlBuilder.append("&last_label=").append(pageLabel);
        }

        String url = urlBuilder.toString();
        String referer = baseUrl + "/drive/folder/" + folderToken;

        clientSession.getAbs(url)
                .putHeader("User-Agent", UA)
                .putHeader("Accept", "application/json, text/plain, */*")
                .putHeader("Referer", referer)
                .send()
                .onSuccess(res -> {
                    try {
                        JsonObject json = asJson(res);
                        int code = json.getInteger("code", -1);
                        if (code != 0) {
                            p.fail("飞书API错误: " + json.getString("msg"));
                            return;
                        }

                        JsonObject data = json.getJsonObject("data");
                        JsonObject entities = data.getJsonObject("entities",
                                new JsonObject());
                        JsonObject nodes = entities.getJsonObject("nodes",
                                new JsonObject());
                        JsonArray nodeList = data.getJsonArray("node_list",
                                new JsonArray());

                        List<FileInfo> items = new ArrayList<>();
                        for (int i = 0; i < nodeList.size(); i++) {
                            String nid = nodeList.getString(i);
                            JsonObject node = nodes.getJsonObject(nid,
                                    new JsonObject());
                            int objType = node.getInteger("type", -1);
                            String objToken = node.getString("obj_token", "");
                            String name = node.getString("name", "unknown");

                            // 排除文件夹自身节点
                            if (objToken.equals(folderToken)) {
                                continue;
                            }

                            // 只返回可下载的文件(type=12)
                            if (objType == OBJ_TYPE_FILE) {
                                FileInfo fileInfo = new FileInfo();
                                fileInfo.setFileName(name);
                                fileInfo.setFileId(objToken);
                                fileInfo.setPanType(shareLinkInfo.getType());
                                fileInfo.setFileType("file");

                                JsonObject extra = node.getJsonObject("extra",
                                        new JsonObject());
                                try {
                                    long size = Long.parseLong(
                                            extra.getString("size", "0"));
                                    fileInfo.setSize(size);
                                } catch (NumberFormatException e) {
                                    log.warn("无法解析文件大小: {}", extra.getString("size"), e);
                                }

                                fileInfo.setParserUrl(
                                        buildDownloadUrl(tenant, objToken));
                                items.add(fileInfo);
                            }
                        }

                        boolean hasMore = data.getBoolean("has_more", false);
                        String nextLabel = data.getString("last_label", "");

                        p.complete(new FolderPageResult(items, hasMore, nextLabel));
                    } catch (Exception e) {
                        p.fail("解析文件列表响应失败: " + e.getMessage());
                    }
                })
                .onFailure(t -> p.fail("请求文件列表失败: " + t.getMessage()));

        return p.future();
    }

    /**
     * 探测单个文件信息
     */
    private Future<FileInfo> probeSingleFile(String tenant, String token,
                                              String referer) {
        Promise<FileInfo> p = Promise.promise();
        String dlUrl = buildDownloadUrl(tenant, token);

        clientSession.getAbs(dlUrl)
                .putHeader("User-Agent", UA)
                .putHeader("Referer", referer)
                .putHeader("Range", "bytes=0-0")
                .send()
                .onSuccess(probeRes -> {
                    FileInfo fileInfo = new FileInfo();
                    String fileName = parseFileNameFromContentDisposition(
                            probeRes.getHeader("Content-Disposition"));
                    if (fileName != null) {
                        fileInfo.setFileName(fileName);
                    }
                    parseSizeFromContentRange(
                            probeRes.getHeader("Content-Range"), fileInfo);
                    fileInfo.setFileId(token);
                    fileInfo.setPanType(shareLinkInfo.getType());
                    fileInfo.setFileType("file");
                    fileInfo.setParserUrl(dlUrl);
                    p.complete(fileInfo);
                })
                .onFailure(t -> p.fail("探测文件失败: " + t.getMessage()));

        return p.future();
    }

    // ─── 工具方法 ────────────────────────────────────────

    private String buildDownloadUrl(String tenant, String objToken) {
        return "https://" + tenant
                + ".feishu.cn/space/api/box/stream/download/all/" + objToken;
    }

    private String extractTenant(String url) {
        if (url == null) return null;
        Matcher m = TENANT_PATTERN.matcher(url);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * 从Content-Disposition头解析文件名。
     * 支持 filename*=UTF-8''xxx 和 filename="xxx" 两种格式。
     */
    private String parseFileNameFromContentDisposition(String cd) {
        if (cd == null || cd.isEmpty()) return null;

        // 优先解析 filename*=UTF-8''xxx
        Matcher m1 = CD_FILENAME_STAR_PATTERN.matcher(cd);
        if (m1.find()) {
            try {
                return URLDecoder.decode(m1.group(1).trim(), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }

        // 降级解析 filename="xxx" 或 filename=xxx
        Matcher m2 = CD_FILENAME_PATTERN.matcher(cd);
        if (m2.find()) {
            try {
                return URLDecoder.decode(m2.group(1).trim(), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private void parseSizeFromContentRange(String cr, FileInfo fileInfo) {
        if (cr != null) {
            Matcher m = CONTENT_RANGE_SIZE_PATTERN.matcher(cr);
            if (m.find()) {
                fileInfo.setSize(Long.parseLong(m.group(1)));
            }
        }
    }

    private String extractCookiesFromResponse(
            io.vertx.ext.web.client.HttpResponse<?> response) {
        List<String> setCookies = response.cookies();
        if (setCookies == null || setCookies.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (String cookie : setCookies) {
            String nameValue = cookie.split(";")[0].trim();
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(nameValue);
        }
        return sb.toString();
    }

    /**
     * 文件夹分页结果
     */
    private record FolderPageResult(List<FileInfo> items, boolean hasMore,
                                     String nextLabel) {
    }
}
